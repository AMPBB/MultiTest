package pbbadd.opengl.multitest.cube3d;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class CubeRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "CubeRenderer";

    private final float[] mvpMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] rotationMatrix = new float[16];

    private float angle = 0.0f;  // 旋转角度

    // 顶点坐标（立方体，以原点为中心，边长为1.0）
    private final float[] cubeVertices = {
            // 前
            -0.5f, -0.5f,  0.5f,  // 0: 左下前
            0.5f, -0.5f,  0.5f,  // 1: 右下前
            -0.5f,  0.5f,  0.5f,  // 2: 左上前
            0.5f,  0.5f,  0.5f,  // 3: 右上前
            // 后
            -0.5f, -0.5f, -0.5f,  // 4: 左下后
            0.5f, -0.5f, -0.5f,  // 5: 右下后
            -0.5f,  0.5f, -0.5f,  // 6: 左上后
            0.5f,  0.5f, -0.5f   // 7: 右上后
    };

    // 索引（12个三角形，每个面2个三角形）
    private final short[] indices = {
            // 前
            0, 1, 2, 2, 1, 3,
            // 后
            4, 6, 5, 5, 6, 7,
            // 左
            4, 0, 6, 6, 0, 2,
            // 右
            1, 5, 3, 3, 5, 7,
            // 上
            2, 3, 6, 6, 3, 7,
            // 下
            4, 5, 0, 0, 5, 1
    };

    // 颜色（每个顶点一个颜色）
    private final float[] colors = {
            // 前 - 红色
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            // 后 - 绿色
            0.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f
    };

    private FloatBuffer vertexBuffer;
    private FloatBuffer colorBuffer;
    private ShortBuffer indexBuffer;
    private int program;
    private int positionHandle;
    private int colorHandle;
    private int mvpMatrixHandle;

    public CubeRenderer() {
        // 初始化顶点缓冲区
        ByteBuffer vbb = ByteBuffer.allocateDirect(cubeVertices.length * 4);
        vbb.order(ByteOrder.nativeOrder());
        vertexBuffer = vbb.asFloatBuffer();
        vertexBuffer.put(cubeVertices);
        vertexBuffer.position(0);

        // 初始化颜色缓冲区
        ByteBuffer cbb = ByteBuffer.allocateDirect(colors.length * 4);
        cbb.order(ByteOrder.nativeOrder());
        colorBuffer = cbb.asFloatBuffer();
        colorBuffer.put(colors);
        colorBuffer.position(0);

        // 初始化索引缓冲区
        ByteBuffer ibb = ByteBuffer.allocateDirect(indices.length * 2);
        ibb.order(ByteOrder.nativeOrder());
        indexBuffer = ibb.asShortBuffer();
        indexBuffer.put(indices);
        indexBuffer.position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // 设置黑色背景
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        // 启用深度测试
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        // 启用面剔除（提高性能）
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        // 初始化着色器
        initShaders();

        // 初始化矩阵
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.setLookAtM(viewMatrix, 0, 0, 0, 4.0f, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        // 设置视口为800x800
        GLES20.glViewport(0, 0, 800, 800);

        // 计算宽高比
        float ratio = (float) width / height;

        // 创建透视投影矩阵
        // 参数：投影矩阵, 偏移, 视角, 宽高比, 近平面, 远平面
        Matrix.perspectiveM(projectionMatrix, 0, 45.0f, ratio, 0.1f, 100.0f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // 清除颜色和深度缓冲区
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // 创建旋转矩阵
        angle = (angle + 0.5f) % 360.0f;  // 每帧旋转0.5度
        Matrix.setRotateM(rotationMatrix, 0, angle, 1.0f, 1.0f, 1.0f);
        Matrix.multiplyMM(modelMatrix, 0, rotationMatrix, 0, modelMatrix, 0);

        // 计算MVP矩阵
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0);

        // 使用着色器程序
        GLES20.glUseProgram(program);

        // 传递顶点数据
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        // 传递颜色数据
        colorHandle = GLES20.glGetAttribLocation(program, "aColor");
        GLES20.glEnableVertexAttribArray(colorHandle);
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer);

        // 传递MVP矩阵
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        // 绘制立方体
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length,
                GLES20.GL_UNSIGNED_SHORT, indexBuffer);

        // 禁用顶点数组
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(colorHandle);
    }

    private void initShaders() {
        // 顶点着色器
        String vertexShaderCode =
                "attribute vec3 vPosition;" +
                        "attribute vec4 aColor;" +
                        "uniform mat4 uMVPMatrix;" +
                        "varying vec4 vColor;" +
                        "void main() {" +
                        "  gl_Position = uMVPMatrix * vec4(vPosition, 1.0);" +
                        "  vColor = aColor;" +
                        "}";

        // 片段着色器
        String fragmentShaderCode =
                "precision mediump float;" +
                        "varying vec4 vColor;" +
                        "void main() {" +
                        "  gl_FragColor = vColor;" +
                        "}";

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        // 检查链接状态
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
        }
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        // 检查编译状态
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + type + ": " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }
}