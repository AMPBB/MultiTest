package pbbadd.opengl.multitest.doublesurfacefbo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import pbbadd.opengl.multitest.R;

public class DoubleSurfaceFboRenderThread extends Thread {
    private static final String TAG = "DoubleSurfaceFboRender";

    private final Context mContext;

    // Surfaces for two views
    private Surface mSurfaceA;
    private Surface mSurfaceB;
    private int mViewWidthA, mViewHeightA;
    private int mViewWidthB, mViewHeightB;

    // EGL objects
    private EGLDisplay mEglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEglContext = EGL14.EGL_NO_CONTEXT;
    private EGLConfig mEglConfig;
    private EGLSurface mEglSurfaceA = EGL14.EGL_NO_SURFACE;
    private EGLSurface mEglSurfaceB = EGL14.EGL_NO_SURFACE;

    // Shared source textures (loaded from PNG)
    private int mTexRed = 0;
    private int mTexGreen = 0;
    private int mTexWidth, mTexHeight;

    // View A FBOs
    private int mFboA1, mFboA2;
    private int mFboTexA1, mFboTexA2;

    // View B FBOs
    private int mFboB1, mFboB2;
    private int mFboTexB1, mFboTexB2;

    // Shader program
    private int mProgram = 0;

    // Vertex/texcoord buffers
    private FloatBuffer mVertexBuffer;
    private FloatBuffer mTexCoordBuffer;

    // Thread control
    private volatile boolean mIsRunning = false;
    private volatile boolean mIsThreadStop = false;

    private final int RENDER_INTERVAL_MS = 16;

    private static final float[] VERTEX_POS = {
            -1.0f,  1.0f,
             1.0f,  1.0f,
            -1.0f, -1.0f,
             1.0f, -1.0f
    };

    private static final float[] TEX_COORD = {
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
    };

    private static final String VERTEX_SHADER =
            "attribute vec2 aPos;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "    vTexCoord = aTexCoord;\n" +
            "}";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform sampler2D uTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
            "}";

    public DoubleSurfaceFboRenderThread(Context context) {
        mContext = context.getApplicationContext();
        initBuffers();
    }

    public void setSurfaceInfoA(Surface surface, int width, int height) {
        mSurfaceA = surface;
        mViewWidthA = width;
        mViewHeightA = height;
    }

    public void setSurfaceInfoB(Surface surface, int width, int height) {
        mSurfaceB = surface;
        mViewWidthB = width;
        mViewHeightB = height;
    }

    private void initBuffers() {
        mVertexBuffer = ByteBuffer.allocateDirect(VERTEX_POS.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mVertexBuffer.put(VERTEX_POS).position(0);

        mTexCoordBuffer = ByteBuffer.allocateDirect(TEX_COORD.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mTexCoordBuffer.put(TEX_COORD).position(0);
    }

    @Override
    public void run() {
        if (mSurfaceA == null || mSurfaceB == null) {
            Log.e(TAG, "surfaces not ready");
            return;
        }

        if (!initEGL()) return;
        if (!initShader()) return;
        initTextures();
        initFBOs();

        mIsRunning = true;
        Log.d(TAG, "render loop starting");

        while (!mIsThreadStop) {
            if (mIsRunning) {
                // --- render to View A ---
                EGL14.eglMakeCurrent(mEglDisplay, mEglSurfaceA, mEglSurfaceA, mEglContext);
                renderToFbo(mFboA1, mTexRed);
                renderToFbo(mFboA2, mTexGreen);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                GLES20.glViewport(0, 0, mViewWidthA, mViewHeightA);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                drawTexture(mFboTexA1);
                EGL14.eglSwapBuffers(mEglDisplay, mEglSurfaceA);

                // --- render to View B ---
                EGL14.eglMakeCurrent(mEglDisplay, mEglSurfaceB, mEglSurfaceB, mEglContext);
                renderToFbo(mFboB1, mTexRed);
                renderToFbo(mFboB2, mTexGreen);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                GLES20.glViewport(0, 0, mViewWidthB, mViewHeightB);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                drawTexture(mFboTexB1);
                EGL14.eglSwapBuffers(mEglDisplay, mEglSurfaceB);

                try {
                    Thread.sleep(RENDER_INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        releaseGLResources();
        releaseEGL();
        Log.d(TAG, "render loop stopped");
    }

    private boolean initEGL() {
        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEglDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.e(TAG, "eglGetDisplay failed");
            return false;
        }

        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
            Log.e(TAG, "eglInitialize failed");
            return false;
        }

        int[] configAttrs = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEglDisplay, configAttrs, 0, configs, 0, 1, numConfigs, 0)) {
            Log.e(TAG, "eglChooseConfig failed");
            return false;
        }
        mEglConfig = configs[0];

        int[] ctxAttrs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        mEglContext = EGL14.eglCreateContext(mEglDisplay, mEglConfig, EGL14.EGL_NO_CONTEXT, ctxAttrs, 0);
        if (mEglContext == EGL14.EGL_NO_CONTEXT) {
            Log.e(TAG, "eglCreateContext failed");
            return false;
        }

        int[] surfaceAttrs = {EGL14.EGL_NONE};
        mEglSurfaceA = EGL14.eglCreateWindowSurface(mEglDisplay, mEglConfig, mSurfaceA, surfaceAttrs, 0);
        if (mEglSurfaceA == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreateWindowSurface A failed");
            return false;
        }

        mEglSurfaceB = EGL14.eglCreateWindowSurface(mEglDisplay, mEglConfig, mSurfaceB, surfaceAttrs, 0);
        if (mEglSurfaceB == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreateWindowSurface B failed");
            return false;
        }

        if (!EGL14.eglMakeCurrent(mEglDisplay, mEglSurfaceA, mEglSurfaceA, mEglContext)) {
            Log.e(TAG, "eglMakeCurrent failed");
            return false;
        }

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        Log.d(TAG, "EGL init done, view A=" + mViewWidthA + "x" + mViewHeightA
                + ", view B=" + mViewWidthB + "x" + mViewHeightB);
        return true;
    }

    private boolean initShader() {
        int vs = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        if (vs == 0 || fs == 0) return false;

        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vs);
        GLES20.glAttachShader(mProgram, fs);
        GLES20.glLinkProgram(mProgram);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(mProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "shader link failed: " + GLES20.glGetProgramInfoLog(mProgram));
            return false;
        }

        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        Log.d(TAG, "shader init done");
        return true;
    }

    private int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "shader compile error: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private void initTextures() {
        mTexRed = loadPngTexture(R.drawable.tex_red);
        mTexGreen = loadPngTexture(R.drawable.tex_green);

        Log.d(TAG, "textures loaded, red=" + mTexRed + " green=" + mTexGreen + " size=" + mTexWidth + "x" + mTexHeight);
    }

    private int loadPngTexture(int drawableId) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        Bitmap bmp = BitmapFactory.decodeResource(mContext.getResources(), drawableId, opts);
        if (bmp == null) {
            Log.e(TAG, "failed to decode bitmap: " + drawableId);
            return 0;
        }

        if (mTexWidth == 0) {
            mTexWidth = bmp.getWidth();
            mTexHeight = bmp.getHeight();
        }

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int texId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        ByteBuffer buf = ByteBuffer.allocateDirect(bmp.getWidth() * bmp.getHeight() * 4);
        buf.order(ByteOrder.nativeOrder());
        bmp.copyPixelsToBuffer(buf);
        buf.position(0);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                bmp.getWidth(), bmp.getHeight(), 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);

        bmp.recycle();
        return texId;
    }

    private void initFBOs() {
        // View A FBOs
        int[] fbosA = new int[2];
        GLES20.glGenFramebuffers(2, fbosA, 0);
        mFboA1 = fbosA[0];
        mFboA2 = fbosA[1];
        mFboTexA1 = createFboTexture();
        mFboTexA2 = createFboTexture();
        bindFboWithTexture(mFboA1, mFboTexA1);
        bindFboWithTexture(mFboA2, mFboTexA2);

        // View B FBOs
        int[] fbosB = new int[2];
        GLES20.glGenFramebuffers(2, fbosB, 0);
        mFboB1 = fbosB[0];
        mFboB2 = fbosB[1];
        mFboTexB1 = createFboTexture();
        mFboTexB2 = createFboTexture();
        bindFboWithTexture(mFboB1, mFboTexB1);
        bindFboWithTexture(mFboB2, mFboTexB2);

        Log.d(TAG, "FBOs inited, A: " + mFboA1 + "/" + mFboA2 + ", B: " + mFboB1 + "/" + mFboB2);
    }

    private int createFboTexture() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int texId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                mTexWidth, mTexHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        return texId;
    }

    private void bindFboWithTexture(int fboId, int texId) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texId, 0);

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "FBO bind failed: " + status + " fbo=" + fboId);
        }
    }

    private void renderToFbo(int fboId, int srcTexId) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
        GLES20.glViewport(0, 0, mTexWidth, mTexHeight);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        drawTexture(srcTexId);
    }

    private void drawTexture(int texId) {
        GLES20.glUseProgram(mProgram);

        int posLoc = GLES20.glGetAttribLocation(mProgram, "aPos");
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 8, mVertexBuffer);
        GLES20.glEnableVertexAttribArray(posLoc);

        int texLoc = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 8, mTexCoordBuffer);
        GLES20.glEnableVertexAttribArray(texLoc);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(mProgram, "uTexture"), 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(posLoc);
        GLES20.glDisableVertexAttribArray(texLoc);
    }

    public void startRendering() {
        mIsRunning = true;
    }

    public void stopRendering() {
        mIsRunning = false;
    }

    public void stopThread() {
        mIsRunning = false;
        mIsThreadStop = true;
        try {
            join(2000);
        } catch (InterruptedException e) {
            Log.e(TAG, "stopThread interrupted: " + e.getMessage());
        }
    }

    private void releaseGLResources() {
        EGL14.eglMakeCurrent(mEglDisplay, mEglSurfaceA, mEglSurfaceA, mEglContext);

        int[] textures = {mTexRed, mTexGreen, mFboTexA1, mFboTexA2, mFboTexB1, mFboTexB2};
        GLES20.glDeleteTextures(textures.length, textures, 0);

        int[] fbos = {mFboA1, mFboA2, mFboB1, mFboB2};
        GLES20.glDeleteFramebuffers(fbos.length, fbos, 0);

        if (mProgram != 0) {
            GLES20.glDeleteProgram(mProgram);
            mProgram = 0;
        }
    }

    private void releaseEGL() {
        EGL14.eglMakeCurrent(mEglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);

        if (mEglSurfaceA != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(mEglDisplay, mEglSurfaceA);
            mEglSurfaceA = EGL14.EGL_NO_SURFACE;
        }
        if (mEglSurfaceB != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(mEglDisplay, mEglSurfaceB);
            mEglSurfaceB = EGL14.EGL_NO_SURFACE;
        }
        if (mEglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(mEglDisplay, mEglContext);
            mEglContext = EGL14.EGL_NO_CONTEXT;
        }
        if (mEglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglTerminate(mEglDisplay);
            mEglDisplay = EGL14.EGL_NO_DISPLAY;
        }
    }
}
