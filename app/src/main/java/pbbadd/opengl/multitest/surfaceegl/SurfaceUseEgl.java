package pbbadd.opengl.multitest.surfaceegl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Trace;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import pbbadd.opengl.multitest.R;

public class SurfaceUseEgl extends GLSurfaceView implements GLSurfaceView.Renderer {

    private int mLoc = 0;
    private Bitmap mBgBitmap;
    private int mBgTextureId;
    private int mScreenWidth, mScreenHeight;
    private int mBgWidth, mBgHeight;

    // OpenGL 绘制需要
    private int mProgram;
    private int mPositionHandle;
    private int mTexCoordHandle;
    private int mMatrixHandle;
    private int mSamplerHandle;

    private final float[] mVertexMatrix = new float[16];
    private FloatBuffer mVertexBuffer;
    private FloatBuffer mTexCoordBuffer;

    static final float[] VERTEX = {
            -1.0f,  1.0f,
            -1.0f, -1.0f,
            1.0f,  1.0f,
            1.0f, -1.0f,
    };

    static final float[] TEX_COORD = {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            1.0f, 1.0f,
    };

    public SurfaceUseEgl(Context context) {
        this(context, null);
    }

    public SurfaceUseEgl(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // 关键：开启 OpenGL ES 2.0
        setEGLContextClientVersion(2);
        // 设置渲染器
        setRenderer(this);
        // 持续渲染
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        // 强制硬件加速
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // 加载背景图（和你原来完全一样）
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        mBgBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.c_bg_xxxxhd, options);
        Log.d("EGL_DEBUG", "bg w=" + mBgBitmap.getWidth() + ", h=" + mBgBitmap.getHeight());
        mBgWidth = mBgBitmap.getWidth();
        mBgHeight = mBgBitmap.getHeight();
        mLoc = mBgWidth;
    }

    // ==============================================
    // 渲染开始（系统回调）
    // ==============================================
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // 初始化 OpenGL
        initGL();
        // 创建纹理
        mBgTextureId = createTexture();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mScreenWidth = width;
        mScreenHeight = height;
        GLES20.glViewport(0, 0, width, height);
    }

    // ==============================================
    // 每一帧都会调用 → 自动 eglSwapBuffers()
    // ==============================================
    @Override
    public void onDrawFrame(GL10 gl) {
        Trace.beginSection("pbbadd-" + mLoc);
        // 清屏（你原来的四层颜色效果）
        GLES20.glClearColor(1, 1, 1, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // 绘制背景图
        drawTexture(mBgTextureId);

        // 绘制红色滚动竖线（完全复刻你的逻辑）
        drawScrollLine();

        // 滚动位置更新
        mLoc -= 64;
        if (mLoc <= 0) {
            mLoc = mBgWidth;
        }
        Trace.endSection();
    }

    // ==============================================
    // 绘制滚动线（和你原来效果完全一样）
    // ==============================================
    private void drawScrollLine() {
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        int color = 0xFFFF0000;
        for (int i = 0; i < 128; i++) {
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            color += 2;

            float x = (mLoc + i) * 2.0f / mBgWidth - 1.0f;
//            float x = (mLoc+i)/mBgWidth;
            drawLine(x, -1, x, 1, r / 255f, g / 255f, b / 255f);
        }
    }

    private void drawLine(float x1, float y1, float x2, float y2, float r, float g, float b) {
        GLES20.glUseProgram(mLineProgram);

        float[] color = {r, g, b, 1.0f};
        GLES20.glUniform4fv(mLineColorHandle, 1, color, 0);

        float[] v = {x1, y1, x2, y2};
        FloatBuffer fb = ByteBuffer.allocateDirect(8 * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        fb.put(v).position(0);

        GLES20.glEnableVertexAttribArray(mLinePositionHandle);
        GLES20.glVertexAttribPointer(mLinePositionHandle, 2, GLES20.GL_FLOAT, false, 0, fb);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);
    }

    // ==============================================
    // 初始化 OpenGL
    // ==============================================
    private void initGL() {
        String vertexShader =
                "attribute vec4 vPosition;\n" +
                        "attribute vec2 vTexCoord;\n" +
                        "uniform mat4 uMatrix;\n" +
                        "varying vec2 vCoord;\n" +
                        "void main() {\n" +
                        "    gl_Position = vPosition;\n" +
                        "    vCoord = vTexCoord;\n" +
                        "}";

        String fragShader =
                "precision mediump float;\n" +
                        "uniform sampler2D uSampler;\n" +
                        "varying vec2 vCoord;\n" +
                        "void main() {\n" +
                        "    gl_FragColor = texture2D(uSampler, vCoord);\n" +
                        "}";

        mProgram = GLES20.glCreateProgram();
        int vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragShader);
        GLES20.glAttachShader(mProgram, vs);
        GLES20.glAttachShader(mProgram, fs);
        GLES20.glLinkProgram(mProgram);
        GLES20.glUseProgram(mProgram);

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        mTexCoordHandle = GLES20.glGetAttribLocation(mProgram, "vTexCoord");
        mMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMatrix");
        mSamplerHandle = GLES20.glGetUniformLocation(mProgram, "uSampler");

        mVertexBuffer = ByteBuffer.allocateDirect(VERTEX.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mVertexBuffer.put(VERTEX).position(0);

        mTexCoordBuffer = ByteBuffer.allocateDirect(TEX_COORD.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTexCoordBuffer.put(TEX_COORD).position(0);

        initLineShader(); // 初始化线条着色器
    }

    private int createTexture() {
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mBgBitmap, 0);
        return tex[0];
    }

    private void drawTexture(int texId) {
        GLES20.glUseProgram(mProgram); // 切换到背景图的着色器
        // 激活纹理单元 0，并绑定我们的背景纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);
        // 告诉着色器：uSampler 对应纹理单元 0
        GLES20.glUniform1i(mSamplerHandle, 0);

        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glEnableVertexAttribArray(mTexCoordHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, 2, GLES20.GL_FLOAT, false, 8, mVertexBuffer);
        GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, mTexCoordBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    // ==========================
    // 纯 GLES20 画线（无报错版本）
    // ==========================
    private int mLineProgram;
    private int mLinePositionHandle;
    private int mLineColorHandle;

    private void initLineShader() {
        String vertex =
                "attribute vec2 vPos;\n" +
                        "void main() {\n" +
                        "    gl_Position = vec4(vPos, 0.0, 1.0);\n" +
                        "}";

        String frag =
                "precision mediump float;\n" +
                        "uniform vec4 vColor;\n" +
                        "void main() {\n" +
                        "    gl_FragColor = vColor;\n" +
                        "}";

        mLineProgram = GLES20.glCreateProgram();
        int v = loadShader(GLES20.GL_VERTEX_SHADER, vertex);
        int f = loadShader(GLES20.GL_FRAGMENT_SHADER, frag);
        GLES20.glAttachShader(mLineProgram, v);
        GLES20.glAttachShader(mLineProgram, f);
        GLES20.glLinkProgram(mLineProgram);

        mLinePositionHandle = GLES20.glGetAttribLocation(mLineProgram, "vPos");
        mLineColorHandle = GLES20.glGetUniformLocation(mLineProgram, "vColor");
    }

    private int loadShader(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);
        return shader;
    }
}