package pbbadd.opengl.multitest.doublesurfacefbocube3d;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class DoubleSurfaceFboCube3DRenderThread extends Thread {
    private static final String TAG = "DoubleSurfFboCube3D";

    private static final int FBO_WIDTH = 800;
    private static final int FBO_HEIGHT = 800;

    // Surfaces
    private Surface mSurfaceA;
    private Surface mSurfaceB;
    private int mViewWidthA, mViewHeightA;
    private int mViewWidthB, mViewHeightB;

    // EGL
    private EGLDisplay mEglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEglContext = EGL14.EGL_NO_CONTEXT;
    private EGLConfig mEglConfig;
    private EGLSurface mEglSurfaceA = EGL14.EGL_NO_SURFACE;
    private EGLSurface mEglSurfaceB = EGL14.EGL_NO_SURFACE;

    // FBOs: View A has a1,a2; View B has b1
    private int mFboA1, mFboA2, mFboB1;
    private int mFboTexA1, mFboTexA2, mFboTexB1;
    private int mDepthRbA1, mDepthRbA2, mDepthRbB1;

    // Cube shader
    private int mCubeProgram;
    private int mCubePosLoc;
    private int mCubeColorLoc;
    private int mCubeMvpLoc;

    // Screen blit shader
    private int mScreenProgram;
    private int mScreenPosLoc;
    private int mScreenTexLoc;

    // Screen quad buffers
    private FloatBuffer mScreenVertexBuffer;
    private FloatBuffer mScreenTexCoordBuffer;

    // Three cubes
    private CubeData mCubeA1;
    private CubeData mCubeA2;
    private CubeData mCubeB1;

    // Thread control
    private volatile boolean mIsRunning = false;
    private volatile boolean mIsThreadStop = false;
    private final int RENDER_INTERVAL_MS = 16;

    // ----- Cube data class -----
    private static class CubeData {
        // Vertex positions: 8 corners of a unit cube centered at origin, half-size 0.5
        static final float[] VERTICES = {
            -0.5f, -0.5f,  0.5f,  // 0 front-bottom-left
             0.5f, -0.5f,  0.5f,  // 1 front-bottom-right
            -0.5f,  0.5f,  0.5f,  // 2 front-top-left
             0.5f,  0.5f,  0.5f,  // 3 front-top-right
            -0.5f, -0.5f, -0.5f,  // 4 back-bottom-left
             0.5f, -0.5f, -0.5f,  // 5 back-bottom-right
            -0.5f,  0.5f, -0.5f,  // 6 back-top-left
             0.5f,  0.5f, -0.5f,  // 7 back-top-right
        };

        static final short[] INDICES = {
            0,1,2, 2,1,3,   // front
            4,6,5, 5,6,7,   // back
            4,0,6, 6,0,2,   // left
            1,5,3, 3,5,7,   // right
            2,3,6, 6,3,7,   // top
            4,5,0, 0,5,1,   // bottom
        };

        final float[] colors;
        final float rotAxisX, rotAxisY, rotAxisZ;
        final float rotSpeed;

        FloatBuffer vertexBuffer;
        FloatBuffer colorBuffer;
        ShortBuffer indexBuffer;

        final float[] modelMatrix = new float[16];
        final float[] mvpMatrix = new float[16];
        final float[] viewMatrix = new float[16];
        final float[] projMatrix = new float[16];
        float angle = 0;

        CubeData(float[] colorPerVertex, float ax, float ay, float az, float speed) {
            this.colors = colorPerVertex;
            this.rotAxisX = ax;
            this.rotAxisY = ay;
            this.rotAxisZ = az;
            this.rotSpeed = speed;
            initBuffers();
            initMatrices();
        }

        private void initBuffers() {
            ByteBuffer vbb = ByteBuffer.allocateDirect(VERTICES.length * 4);
            vbb.order(ByteOrder.nativeOrder());
            vertexBuffer = vbb.asFloatBuffer();
            vertexBuffer.put(VERTICES).position(0);

            ByteBuffer cbb = ByteBuffer.allocateDirect(colors.length * 4);
            cbb.order(ByteOrder.nativeOrder());
            colorBuffer = cbb.asFloatBuffer();
            colorBuffer.put(colors).position(0);

            ByteBuffer ibb = ByteBuffer.allocateDirect(INDICES.length * 2);
            ibb.order(ByteOrder.nativeOrder());
            indexBuffer = ibb.asShortBuffer();
            indexBuffer.put(INDICES).position(0);
        }

        private void initMatrices() {
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.setLookAtM(viewMatrix, 0, 0, 0, 2.5f, 0, 0, 0, 0, 1, 0);
            Matrix.perspectiveM(projMatrix, 0, 45, 1, 0.1f, 100);
        }

        void updateRotation() {
            angle = (angle + rotSpeed) % 360;
            if (angle < 0) angle += 360;
            Matrix.setIdentityM(modelMatrix, 0);
            Matrix.setRotateM(modelMatrix, 0, angle, rotAxisX, rotAxisY, rotAxisZ);
        }

        void computeMvp() {
            float[] temp = new float[16];
            Matrix.multiplyMM(temp, 0, viewMatrix, 0, modelMatrix, 0);
            Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, temp, 0);
        }
    }

    // ----- Shader sources -----
    private static final String CUBE_VS =
        "attribute vec3 vPosition;" +
        "attribute vec4 aColor;" +
        "uniform mat4 uMVPMatrix;" +
        "varying vec4 vColor;" +
        "void main() {" +
        "  gl_Position = uMVPMatrix * vec4(vPosition, 1.0);" +
        "  vColor = aColor;" +
        "}";

    private static final String CUBE_FS =
        "precision mediump float;" +
        "varying vec4 vColor;" +
        "void main() {" +
        "  gl_FragColor = vColor;" +
        "}";

    private static final String SCREEN_VS =
        "attribute vec2 aPos;" +
        "attribute vec2 aTexCoord;" +
        "varying vec2 vTexCoord;" +
        "void main() {" +
        "  gl_Position = vec4(aPos, 0.0, 1.0);" +
        "  vTexCoord = aTexCoord;" +
        "}";

    private static final String SCREEN_FS =
        "precision mediump float;" +
        "varying vec2 vTexCoord;" +
        "uniform sampler2D uTexture;" +
        "void main() {" +
        "  gl_FragColor = texture2D(uTexture, vTexCoord);" +
        "}";

    private static final float[] SCREEN_VERTS = {
        -1.0f,  1.0f,
         1.0f,  1.0f,
        -1.0f, -1.0f,
         1.0f, -1.0f,
    };

    private static final float[] SCREEN_TEX = {
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
    };

    // ----- Helpers to build per-vertex color arrays -----
    // Alternates two colors across the 8 vertices: c1,c1,c1,c1,c2,c2,c2,c2
    private static float[] makeTwoColors(float r1, float g1, float b1,
                                         float r2, float g2, float b2) {
        return new float[]{
            r1,g1,b1,1, r1,g1,b1,1, r1,g1,b1,1, r1,g1,b1,1,
            r2,g2,b2,1, r2,g2,b2,1, r2,g2,b2,1, r2,g2,b2,1,
        };
    }

    public DoubleSurfaceFboCube3DRenderThread() {
        initScreenBuffers();
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

    private void initScreenBuffers() {
        ByteBuffer vbb = ByteBuffer.allocateDirect(SCREEN_VERTS.length * 4);
        vbb.order(ByteOrder.nativeOrder());
        mScreenVertexBuffer = vbb.asFloatBuffer();
        mScreenVertexBuffer.put(SCREEN_VERTS).position(0);

        ByteBuffer tbb = ByteBuffer.allocateDirect(SCREEN_TEX.length * 4);
        tbb.order(ByteOrder.nativeOrder());
        mScreenTexCoordBuffer = tbb.asFloatBuffer();
        mScreenTexCoordBuffer.put(SCREEN_TEX).position(0);
    }

    @Override
    public void run() {
        if (mSurfaceA == null || mSurfaceB == null) {
            Log.e(TAG, "surfaces not ready");
            return;
        }

        if (!initEGL()) return;
        if (!initShaders()) return;
        initCubes();
        initFBOs();

        mIsRunning = true;
        Log.d(TAG, "render loop starting");

        while (!mIsThreadStop) {
            if (mIsRunning) {
                renderViewA();
                renderViewB();

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

    // ==================== View A render ====================
    private void renderViewA() {
        EGL14.eglMakeCurrent(mEglDisplay, mEglSurfaceA, mEglSurfaceA, mEglContext);

        // --- FBO a1: red clear + cube_a1 ---
        mCubeA1.updateRotation();
        mCubeA1.computeMvp();
        renderCubeToFbo(mFboA1, mCubeA1, 1.0f, 0.1f, 0.1f); // red bg

        // --- FBO a2: green clear + cube_a2 ---
        mCubeA2.updateRotation();
        mCubeA2.computeMvp();
        renderCubeToFbo(mFboA2, mCubeA2, 0.1f, 0.8f, 0.1f); // green bg

        // --- Blit a1 to default framebuffer (screen) ---
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, mViewWidthA, mViewHeightA);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        drawScreenQuad(mFboTexA1);

        EGL14.eglSwapBuffers(mEglDisplay, mEglSurfaceA);
    }

    // ==================== View B render ====================
    private void renderViewB() {
        EGL14.eglMakeCurrent(mEglDisplay, mEglSurfaceB, mEglSurfaceB, mEglContext);

        // --- FBO b1: yellow clear + cube_b1 ---
        mCubeB1.updateRotation();
        mCubeB1.computeMvp();
        renderCubeToFbo(mFboB1, mCubeB1, 0.9f, 0.9f, 0.1f); // yellow bg

        // --- Blit b1 to default framebuffer (screen) ---
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, mViewWidthB, mViewHeightB);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        drawScreenQuad(mFboTexB1);

        EGL14.eglSwapBuffers(mEglDisplay, mEglSurfaceB);
    }

    // ==================== Render cube into FBO ====================
    private void renderCubeToFbo(int fboId, CubeData cube, float r, float g, float b) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
        GLES20.glViewport(0, 0, FBO_WIDTH, FBO_HEIGHT);
        GLES20.glClearColor(r, g, b, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);

        GLES20.glUseProgram(mCubeProgram);

        GLES20.glEnableVertexAttribArray(mCubePosLoc);
        GLES20.glVertexAttribPointer(mCubePosLoc, 3, GLES20.GL_FLOAT, false, 0, cube.vertexBuffer);

        GLES20.glEnableVertexAttribArray(mCubeColorLoc);
        GLES20.glVertexAttribPointer(mCubeColorLoc, 4, GLES20.GL_FLOAT, false, 0, cube.colorBuffer);

        GLES20.glUniformMatrix4fv(mCubeMvpLoc, 1, false, cube.mvpMatrix, 0);

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, CubeData.INDICES.length,
                GLES20.GL_UNSIGNED_SHORT, cube.indexBuffer);

        GLES20.glDisableVertexAttribArray(mCubePosLoc);
        GLES20.glDisableVertexAttribArray(mCubeColorLoc);
    }

    // ==================== Draw screen quad ====================
    private void drawScreenQuad(int texId) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        GLES20.glUseProgram(mScreenProgram);

        GLES20.glEnableVertexAttribArray(mScreenPosLoc);
        GLES20.glVertexAttribPointer(mScreenPosLoc, 2, GLES20.GL_FLOAT, false, 8, mScreenVertexBuffer);

        GLES20.glEnableVertexAttribArray(mScreenTexLoc);
        GLES20.glVertexAttribPointer(mScreenTexLoc, 2, GLES20.GL_FLOAT, false, 8, mScreenTexCoordBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(mScreenProgram, "uTexture"), 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(mScreenPosLoc);
        GLES20.glDisableVertexAttribArray(mScreenTexLoc);
    }

    // ==================== EGL init ====================
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
                EGL14.EGL_DEPTH_SIZE, 16,
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

        Log.d(TAG, "EGL init done, view A=" + mViewWidthA + "x" + mViewHeightA
                + ", view B=" + mViewWidthB + "x" + mViewHeightB);
        return true;
    }

    // ==================== Shader init ====================
    private boolean initShaders() {
        mCubeProgram = createProgram(CUBE_VS, CUBE_FS);
        if (mCubeProgram == 0) return false;
        mCubePosLoc = GLES20.glGetAttribLocation(mCubeProgram, "vPosition");
        mCubeColorLoc = GLES20.glGetAttribLocation(mCubeProgram, "aColor");
        mCubeMvpLoc = GLES20.glGetUniformLocation(mCubeProgram, "uMVPMatrix");

        mScreenProgram = createProgram(SCREEN_VS, SCREEN_FS);
        if (mScreenProgram == 0) return false;
        mScreenPosLoc = GLES20.glGetAttribLocation(mScreenProgram, "aPos");
        mScreenTexLoc = GLES20.glGetAttribLocation(mScreenProgram, "aTexCoord");

        Log.d(TAG, "shaders inited, cubeProg=" + mCubeProgram + " screenProg=" + mScreenProgram);
        return true;
    }

    private int createProgram(String vs, String fs) {
        int vShader = loadShader(GLES20.GL_VERTEX_SHADER, vs);
        int fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fs);
        if (vShader == 0 || fShader == 0) return 0;

        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vShader);
        GLES20.glAttachShader(prog, fShader);
        GLES20.glLinkProgram(prog);

        int[] status = new int[1];
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "link failed: " + GLES20.glGetProgramInfoLog(prog));
            GLES20.glDeleteProgram(prog);
            return 0;
        }
        GLES20.glDeleteShader(vShader);
        GLES20.glDeleteShader(fShader);
        return prog;
    }

    private int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            Log.e(TAG, "shader err: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    // ==================== Cube init ====================
    private void initCubes() {
        // cube_a1: on red bg → White + Blue, rotate around (1,1,0), speed 1.0
        mCubeA1 = new CubeData(
                makeTwoColors(1,1,1, 0.2f,0.3f,1),  // White + Blue
                1, 1, 0, 1.0f);

        // cube_a2: on green bg → Red + Yellow, rotate around (0,1,1), speed -1.5
        mCubeA2 = new CubeData(
                makeTwoColors(1,0.1f,0.1f, 1,1,0),  // Red + Yellow
                0, 1, 1, -1.5f);

        // cube_b1: on yellow bg → Blue + Magenta, rotate around (1,0,1), speed 0.7
        mCubeB1 = new CubeData(
                makeTwoColors(0.1f,0.2f,1, 1,0,1),  // Blue + Magenta
                1, 0, 1, 0.7f);

        Log.d(TAG, "cubes inited");
    }

    // ==================== FBO init ====================
    private void initFBOs() {
        // View A: FBO a1, a2
        mFboA1 = createFboWithDepth();
        mFboTexA1 = createFboColorTexture();
        bindFbo(mFboA1, mFboTexA1);

        mFboA2 = createFboWithDepth();
        mFboTexA2 = createFboColorTexture();
        bindFbo(mFboA2, mFboTexA2);

        // View B: FBO b1
        mFboB1 = createFboWithDepth();
        mFboTexB1 = createFboColorTexture();
        bindFbo(mFboB1, mFboTexB1);

        Log.d(TAG, "FBOs inited, A: " + mFboA1 + "/" + mFboA2 + ", B: " + mFboB1);
    }

    private int createFboWithDepth() {
        int[] fbos = new int[1];
        GLES20.glGenFramebuffers(1, fbos, 0);
        return fbos[0];
    }

    private int createFboColorTexture() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int texId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                FBO_WIDTH, FBO_HEIGHT, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        return texId;
    }

    private int createDepthRenderbuffer() {
        int[] rbs = new int[1];
        GLES20.glGenRenderbuffers(1, rbs, 0);
        int rb = rbs[0];
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, rb);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16,
                FBO_WIDTH, FBO_HEIGHT);
        return rb;
    }

    private void bindFbo(int fboId, int colorTexId) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, colorTexId, 0);

        int depthRb = createDepthRenderbuffer();
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                GLES20.GL_RENDERBUFFER, depthRb);

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "FBO incomplete: " + status);
        }

        // Store depth RB reference for cleanup (use array or field)
        if (fboId == mFboA1) mDepthRbA1 = depthRb;
        else if (fboId == mFboA2) mDepthRbA2 = depthRb;
        else if (fboId == mFboB1) mDepthRbB1 = depthRb;
    }

    // ==================== Thread control ====================
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

    // ==================== Cleanup ====================
    private void releaseGLResources() {
        EGL14.eglMakeCurrent(mEglDisplay, mEglSurfaceA, mEglSurfaceA, mEglContext);

        int[] textures = {mFboTexA1, mFboTexA2, mFboTexB1};
        GLES20.glDeleteTextures(textures.length, textures, 0);

        int[] fbos = {mFboA1, mFboA2, mFboB1};
        GLES20.glDeleteFramebuffers(fbos.length, fbos, 0);

        int[] rbs = {mDepthRbA1, mDepthRbA2, mDepthRbB1};
        GLES20.glDeleteRenderbuffers(rbs.length, rbs, 0);

        if (mCubeProgram != 0) {
            GLES20.glDeleteProgram(mCubeProgram);
            mCubeProgram = 0;
        }
        if (mScreenProgram != 0) {
            GLES20.glDeleteProgram(mScreenProgram);
            mScreenProgram = 0;
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
