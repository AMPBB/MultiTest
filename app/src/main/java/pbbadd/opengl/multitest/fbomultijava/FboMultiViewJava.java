package pbbadd.opengl.multitest.fbomultijava;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import androidx.annotation.NonNull;
import java.nio.FloatBuffer;
import pbbadd.opengl.multitest.R;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FboMultiViewJava extends SurfaceView implements SurfaceHolder.Callback{

    private final String tag ="fbo multi view java";

    public FboMultiViewJava(Context context) {
        super(context);
        init();
    }

    public FboMultiViewJava(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    // EGL 核心对象
    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;
    private EGLConfig mEGLConfig;

    // 着色器程序
    private int mProgram = 0;
    // 顶点/纹理坐标Buffer
    private FloatBuffer mVertexBuffer, mTexCoordBuffer;

    // 纹理 & FBO ID
    private int mTexRed, mTexGreen, mTexActual;
    private int mFboRed, mFboGreen, mFboActual;
    private int mFboTexRed, mFboTexGreen, mFboTexActual;

    // 尺寸参数
    private int mTexWidth, mTexHeight;
    private int mViewWidth, mViewHeight;

    // 渲染线程
    private Thread mRenderThread;
    private boolean mIsRenderRunning = false;
    private boolean mIsThreadDestroy = false;
    private final int RENDER_INTERVAL = 16; // ~60fps

    // 图片资源
    private Bitmap mBgBitmap;
    private byte[] mBgPixels;

    // 顶点坐标（全屏四边形）
    private final float[] VERTEX_POS = {
            -1.0f,  1.0f,
            1.0f,  1.0f,
            -1.0f, -1.0f,
            1.0f, -1.0f
    };

    // 纹理坐标（正常）
    private final float[] TEX_COORD = {
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
    };

    // 着色器源码
    private final String VERTEX_SHADER =
            "attribute vec2 aPos;\n" +
                    "attribute vec2 aTexCoord;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
                    "    vTexCoord = aTexCoord;\n" +
                    "}";

    private final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
                    "varying vec2 vTexCoord;\n" +
                    "uniform sampler2D uTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
                    "}";

    private void init() {
        getHolder().addCallback(this);
        initBuffer(); // 初始化顶点坐标Buffer
    }

    /**
     * 初始化顶点/纹理坐标Buffer
     */
    private void initBuffer() {
        // 顶点坐标
        mVertexBuffer = ByteBuffer.allocateDirect(VERTEX_POS.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mVertexBuffer.put(VERTEX_POS).position(0);

        // 纹理坐标
        mTexCoordBuffer = ByteBuffer.allocateDirect(TEX_COORD.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mTexCoordBuffer.put(TEX_COORD).position(0);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        // 加载图片（禁止系统缩放）
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        mBgBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.fbo_multi, options);
        mTexWidth = mBgBitmap.getWidth();
        mTexHeight = mBgBitmap.getHeight();

        // 提取图片像素数据
        mBgPixels = new byte[mTexWidth * mTexHeight * 4];
        ByteBuffer.wrap(mBgPixels).order(ByteOrder.nativeOrder());
        mBgBitmap.copyPixelsToBuffer(ByteBuffer.wrap(mBgPixels));

        Log.d(tag, "图片尺寸: " + mTexWidth + "x" + mTexHeight);
        startRenderThread();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        mViewWidth = width;
        mViewHeight = height;
        Log.d(tag, "视图尺寸: " + width + "x" + height);
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        stopRenderThread();
        releaseResources();
    }

    /**
     * 启动渲染线程
     */
    private void startRenderThread() {
        if (mRenderThread == null) {
            mIsThreadDestroy = false;
            mRenderThread = new Thread(this::renderLoop, "OpenGL-Render-Thread");
            mRenderThread.start();
        }
    }

    /**
     * 渲染循环（核心逻辑）
     */
    private void renderLoop() {
        // 1. 初始化EGL环境
        initEGL(getHolder().getSurface());
        // 2. 初始化着色器、纹理、FBO
        initShader();
        initTextures();
        initFBO();
        // 3. 更新图片纹理
        updateTexture(mBgPixels);

        // 渲染主循环
        while (!mIsThreadDestroy) {
            if (mIsRenderRunning) {
                // 执行FBO离屏渲染 + 屏幕渲染
                doRender();
                // 交换缓冲区显示画面
                EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
                try {
                    Thread.sleep(RENDER_INTERVAL);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    /**
     * 核心渲染逻辑（FBO离屏渲染 → 屏幕渲染）
     */
    private void doRender() {
        // 1. 渲染到FBO（离屏）
        renderToFbo(mFboRed, mTexRed);
        renderToFbo(mFboGreen, mTexGreen);
        renderToFbo(mFboActual, mTexActual);

        // 2. 绑定默认帧缓冲（屏幕）
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, mViewWidth, mViewHeight);

        // 3. 渲染最终FBO纹理到屏幕
        drawTexture(mFboTexActual);
    }

    /**
     * 初始化EGL环境（Java层实现）
     */
    private void initEGL(Surface surface) {
        // 获取EGL显示设备
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.e(tag, "EGL获取Display失败");
            return;
        }

        // 初始化EGL
        int[] version = new int[2];
        EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1);

        // 配置EGL参数
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
        EGL14.eglChooseConfig(mEGLDisplay, configAttrs, 0, configs, 0, 1, numConfigs, 0);
        mEGLConfig = configs[0];

        // 创建EGL上下文（OpenGL ES 2.0）
        int[] ctxAttrs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, mEGLConfig, EGL14.EGL_NO_CONTEXT, ctxAttrs, 0);

        // 创建Window Surface
        int[] surfaceAttrs = {EGL14.EGL_NONE};
        mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, mEGLConfig, surface, surfaceAttrs, 0);

        // 绑定上下文
        EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext);

        Log.d(tag, "EGL初始化完成");
    }

    /**
     * 初始化着色器程序
     */
    private void initShader() {
        // 创建顶点着色器
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        // 创建片段着色器
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

        // 创建程序并链接着色器
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);
        GLES20.glLinkProgram(mProgram);

        // 删除着色器（已链接无需保留）
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);

        Log.d(tag, "着色器初始化完成");
    }

    /**
     * 加载编译着色器
     */
    private int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        return shader;
    }

    /**
     * 初始化所有纹理（红/绿/青 + 图片纹理）
     */
    private void initTextures() {
        // 创建3个原始纹理
        mTexRed = createColorTexture(0xFF0000ff); // 红色
        mTexGreen = createColorTexture(0xFF00FF00); // 绿色
        mTexActual = createColorTexture(0xFFffFF00); // 青色

        // 创建3个FBO附着纹理
        mFboTexRed = createEmptyTexture();
        mFboTexGreen = createEmptyTexture();
        mFboTexActual = createEmptyTexture();

        Log.d(tag, "纹理初始化完成");
    }

    /**
     * 创建纯色纹理
     */
    private int createColorTexture(int color) {
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        int texId = texture[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // 填充纯色像素
        int[] pixels = new int[mTexWidth * mTexHeight];
        java.util.Arrays.fill(pixels, color);
        ByteBuffer buffer = ByteBuffer.allocateDirect(mTexWidth * mTexHeight * 4);
        buffer.order(ByteOrder.nativeOrder());
        buffer.asIntBuffer().put(pixels);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                mTexWidth, mTexHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);

        return texId;
    }

    /**
     * 创建空纹理（用于FBO附着）
     */
    private int createEmptyTexture() {
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        int texId = texture[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                mTexWidth, mTexHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        return texId;
    }

    /**
     * 初始化FBO（3个离屏帧缓冲）
     */
    private void initFBO() {
        int[] fbos = new int[3];
        GLES20.glGenFramebuffers(3, fbos, 0);
        mFboRed = fbos[0];
        mFboGreen = fbos[1];
        mFboActual = fbos[2];

        // 绑定FBO和纹理
        bindFboWithTexture(mFboRed, mFboTexRed);
        bindFboWithTexture(mFboGreen, mFboTexGreen);
        bindFboWithTexture(mFboActual, mFboTexActual);

        Log.d(tag, "FBO初始化完成");
    }

    /**
     * 绑定FBO和纹理
     */
    private void bindFboWithTexture(int fboId, int texId) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texId, 0);

        // 检查FBO是否可用
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(tag, "FBO绑定失败: " + status);
        }
    }

    /**
     * 渲染到指定FBO
     */
    private void renderToFbo(int fboId, int texId) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
        GLES20.glViewport(0, 0, mTexWidth, mTexHeight);
        drawTexture(texId);
    }

    /**
     * 绘制纹理（通用绘制方法）
     */
    private void drawTexture(int texId) {
        GLES20.glUseProgram(mProgram);

        // 绑定顶点坐标
        int posLoc = GLES20.glGetAttribLocation(mProgram, "aPos");
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 8, mVertexBuffer);
        GLES20.glEnableVertexAttribArray(posLoc);

        // 绑定纹理坐标
        int texLoc = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 8, mTexCoordBuffer);
        GLES20.glEnableVertexAttribArray(texLoc);

        // 绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(mProgram, "uTexture"), 0);

        // 绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        // 可选：绘制后解绑，避免状态残留
        GLES20.glDisableVertexAttribArray(posLoc);
        GLES20.glDisableVertexAttribArray(texLoc);
    }

    /**
     * 更新图片纹理（替换青色纹理）
     */
    public void updateTexture(byte[] pixels) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexActual);
        ByteBuffer buffer = ByteBuffer.wrap(pixels);
        buffer.order(ByteOrder.nativeOrder());

        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0,
                mTexWidth, mTexHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
    }

    /**
     * 开始渲染
     */
    public void startRender() {
        mIsRenderRunning = true;
    }

    /**
     * 停止渲染
     */
    public void stopRender() {
        mIsRenderRunning = false;
    }

    /**
     * 停止渲染线程
     */
    private void stopRenderThread() {
        mIsRenderRunning = false;
        mIsThreadDestroy = true;
        try {
            if (mRenderThread != null) {
                mRenderThread.join();
                mRenderThread = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 释放所有资源
     */
    private void releaseResources() {
        // 删除纹理
        int[] textures = {mTexRed, mTexGreen, mTexActual, mFboTexRed, mFboTexGreen, mFboTexActual};
        GLES20.glDeleteTextures(textures.length, textures, 0);

        // 删除FBO
        int[] fbos = {mFboRed, mFboGreen, mFboActual};
        GLES20.glDeleteFramebuffers(fbos.length, fbos, 0);

        // 删除程序
        GLES20.glDeleteProgram(mProgram);

        // 释放EGL
        EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
        EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
        EGL14.eglTerminate(mEGLDisplay);

        // 回收Bitmap
        if (mBgBitmap != null) {
            mBgBitmap.recycle();
        }

        Log.d(tag, "资源释放完成");
    }
}