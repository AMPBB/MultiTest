package pbbadd.opengl.multitest.cube3d;

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

public class CubeRenderMultiFbo implements GLSurfaceView.Renderer {
    private static final String TAG = "FBOCubeRenderer";

    // ==============================================
    // FBO 配置：3个离屏缓冲，尺寸统一 800x800
    // ==============================================
    private static final int FBO_WIDTH = 800;
    private static final int FBO_HEIGHT = 800;
    private int[] fboHandles = new int[3];       // fbo_a, fbo_b, fbo_c
    private int[] textureHandles = new int[3];   // fbo绑定的纹理

    // ==============================================
    // 屏幕渲染专用（画FBO_C到屏幕）
    // ==============================================
    private int screenProgram;
    private int screenPositionHandle;
    private int screenTextureHandle;
    private FloatBuffer screenVertexBuffer;
    private FloatBuffer screenTexCoordBuffer;

    // 全屏四边形顶点
    private final float[] screenVertices = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
    };

    // 纹理坐标
    private final float[] screenTexCoords = {
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
    };

    // ==============================================
    // 3个立方体：完全独立数据 + 独立观察距离
    // ==============================================
    private CubeData cubeA; // 最近，最大
    private CubeData cubeB; // 中等
    private CubeData cubeC; // 最远，最小

    // ==============================================
    // 立方体数据封装（独立顶点、颜色、矩阵、缓冲区）
    // ==============================================
    private static class CubeData {
        // 顶点
        final float[] vertices = {
                -0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f, -0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f,
                -0.5f,-0.5f,-0.5f,0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,0.5f,-0.5f
        };
        // 索引
        final short[] indices = {
                0,1,2,2,1,3, 4,6,5,5,6,7, 4,0,6,6,0,2,
                1,5,3,3,5,7, 2,3,6,6,3,7, 4,5,0,0,5,1
        };
        // 颜色（区分三个立方体）
        final float[] colors;
        // 观察距离（决定大小）
        final float cameraZ;

        // 缓冲区
        FloatBuffer vertexBuffer;
        FloatBuffer colorBuffer;
        ShortBuffer indexBuffer;

        // 矩阵
        final float[] mvpMatrix = new float[16];
        final float[] projMatrix = new float[16];
        final float[] viewMatrix = new float[16];
        final float[] modelMatrix = new float[16];
        final float[] rotMatrix = new float[16];

        float angle = 0;

        public CubeData(float[] color, float cameraZ) {
            this.colors = color;
            this.cameraZ = cameraZ;
            initBuffers();
            initMatrix();
        }

        private void initBuffers() {
            // 顶点
            ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length *4);
            vbb.order(ByteOrder.nativeOrder());
            vertexBuffer = vbb.asFloatBuffer();
            vertexBuffer.put(vertices);
            vertexBuffer.position(0);

            // 颜色
            ByteBuffer cbb = ByteBuffer.allocateDirect(colors.length *4);
            cbb.order(ByteOrder.nativeOrder());
            colorBuffer = cbb.asFloatBuffer();
            colorBuffer.put(colors);
            colorBuffer.position(0);

            // 索引
            ByteBuffer ibb = ByteBuffer.allocateDirect(indices.length *2);
            ibb.order(ByteOrder.nativeOrder());
            indexBuffer = ibb.asShortBuffer();
            indexBuffer.put(indices);
            indexBuffer.position(0);
        }

        private void initMatrix() {
            Matrix.setIdentityM(modelMatrix,0);
            Matrix.setLookAtM(viewMatrix,0, 0,0,cameraZ, 0,0,0, 0,1,0);
        }
    }

    // 立方体着色器（3个共用一套着色器程序）
    private int cubeProgram;
    private int aPosition;
    private int aColor;
    private int uMVPMatrix;

    public CubeRenderMultiFbo() {
        // 初始化三个立方体：颜色不同 + 距离不同
        // A：红色，最近（Z=3.0）→ 最大
        cubeA = new CubeData(getColorArray(1,0,0), 3.0f);
        // B：绿色，中等（Z=4.5）→ 中等
        cubeB = new CubeData(getColorArray(0,1,0), 4.5f);
        // C：蓝色，最远（Z=6.5）→ 最小
        cubeC = new CubeData(getColorArray(0,0,1), 6.5f);

        // 初始化屏幕四边形缓冲区
        initScreenBuffers();
    }

    // 生成8个顶点的颜色数组
    private float[] getColorArray(float r,float g,float b) {
        return new float[]{
                r,g,b,1, r,g,b,1, r,g,b,1, r,g,b,1,
                r,g,b,1, r,g,b,1, r,g,b,1, r,g,b,1
        };
    }

    private void initScreenBuffers() {
        // 顶点
        ByteBuffer vbb = ByteBuffer.allocateDirect(screenVertices.length*4);
        vbb.order(ByteOrder.nativeOrder());
        screenVertexBuffer = vbb.asFloatBuffer();
        screenVertexBuffer.put(screenVertices);
        screenVertexBuffer.position(0);

        // 纹理坐标
        ByteBuffer tbb = ByteBuffer.allocateDirect(screenTexCoords.length*4);
        tbb.order(ByteOrder.nativeOrder());
        screenTexCoordBuffer = tbb.asFloatBuffer();
        screenTexCoordBuffer.put(screenTexCoords);
        screenTexCoordBuffer.position(0);
    }

    // ==============================================
    // 渲染生命周期
    // ==============================================
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0,0,0,1);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);

        // 初始化着色器
        initCubeShader();
        initScreenShader();

        // 创建3个FBO
        createFBOs();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        // FBO 固定尺寸
        float ratio = 1.0f;
        Matrix.perspectiveM(cubeA.projMatrix,0,45,ratio,0.1f,100);
        Matrix.perspectiveM(cubeB.projMatrix,0,45,ratio,0.1f,100);
        Matrix.perspectiveM(cubeC.projMatrix,0,45,ratio,0.1f,100);

        // 屏幕视口
        GLES20.glViewport(0,0,width,height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // 每帧按顺序渲染：FBO_A → FBO_B → FBO_C
        renderCubeToFBO(cubeA, 0);
        renderCubeToFBO(cubeB, 1);
        renderCubeToFBO(cubeC, 2);

        // 只把 FBO_C 绘制到屏幕
        renderFBOToScreen(2);
    }

    // ==============================================
    // 渲染立方体到指定FBO
    // ==============================================
    private void renderCubeToFBO(CubeData cube, int fboIndex) {
        // 绑定FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboHandles[fboIndex]);
        GLES20.glViewport(0,0, FBO_WIDTH, FBO_HEIGHT);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glUseProgram(cubeProgram);

        // 更新旋转
        cube.angle = (cube.angle + 0.5f) % 360;
        Matrix.setIdentityM(cube.modelMatrix,0);
        Matrix.setRotateM(cube.rotMatrix,0, cube.angle, 1,1,1);
        Matrix.multiplyMM(cube.modelMatrix,0, cube.rotMatrix,0, cube.modelMatrix,0);

        // 计算MVP
        Matrix.multiplyMM(cube.mvpMatrix,0, cube.viewMatrix,0, cube.modelMatrix,0);
        Matrix.multiplyMM(cube.mvpMatrix,0, cube.projMatrix,0, cube.mvpMatrix,0);

        // 传顶点
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition,3,GLES20.GL_FLOAT,false,0,cube.vertexBuffer);

        // 传颜色
        GLES20.glEnableVertexAttribArray(aColor);
        GLES20.glVertexAttribPointer(aColor,4,GLES20.GL_FLOAT,false,0,cube.colorBuffer);

        // 传矩阵
        GLES20.glUniformMatrix4fv(uMVPMatrix,1,false,cube.mvpMatrix,0);

        // 绘制
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, cube.indices.length,
                GLES20.GL_UNSIGNED_SHORT, cube.indexBuffer);

        // 禁用
        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glDisableVertexAttribArray(aColor);

        // 解绑FBO
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
    }

    // ==============================================
    // 将指定FBO纹理绘制到屏幕
    // ==============================================
    private void renderFBOToScreen(int fboIndex) {
        GLES20.glViewport(0,0, 800,800);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(screenProgram);
        int texHandle = GLES20.glGetUniformLocation(screenProgram, "uTexture");

        // 绑定FBO纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandles[fboIndex]);
        GLES20.glUniform1i(texHandle,0);

        // 顶点
        GLES20.glEnableVertexAttribArray(screenPositionHandle);
        GLES20.glVertexAttribPointer(screenPositionHandle,2,GLES20.GL_FLOAT,false,0,screenVertexBuffer);

        // 纹理坐标
        GLES20.glEnableVertexAttribArray(screenTextureHandle);
        GLES20.glVertexAttribPointer(screenTextureHandle,2,GLES20.GL_FLOAT,false,0,screenTexCoordBuffer);

        // 绘制全屏四边形
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);

        // 清理
        GLES20.glDisableVertexAttribArray(screenPositionHandle);
        GLES20.glDisableVertexAttribArray(screenTextureHandle);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);
    }

    // ==============================================
    // FBO 创建
    // ==============================================
    private void createFBOs() {
        GLES20.glGenFramebuffers(3, fboHandles,0);
        GLES20.glGenTextures(3, textureHandles,0);

        for(int i=0;i<3;i++) {
            // 纹理配置
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandles[i]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,
                    FBO_WIDTH,FBO_HEIGHT,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);

            // 绑定到FBO
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboHandles[i]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,textureHandles[i],0);

            // 检查FBO状态
            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if(status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                Log.e(TAG,"FBO "+i+" 初始化失败");
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,0);
    }

    // ==============================================
    // 立方体着色器
    // ==============================================
    private void initCubeShader() {
        String vertex =
                "attribute vec3 vPosition;" +
                        "attribute vec4 aColor;" +
                        "uniform mat4 uMVPMatrix;" +
                        "varying vec4 vColor;" +
                        "void main(){gl_Position=uMVPMatrix*vec4(vPosition,1);vColor=aColor;}";

        String fragment =
                "precision mediump float;" +
                        "varying vec4 vColor;" +
                        "void main(){gl_FragColor=vColor;}";

        cubeProgram = createProgram(vertex,fragment);
        aPosition = GLES20.glGetAttribLocation(cubeProgram,"vPosition");
        aColor = GLES20.glGetAttribLocation(cubeProgram,"aColor");
        uMVPMatrix = GLES20.glGetUniformLocation(cubeProgram,"uMVPMatrix");
    }

    // ==============================================
    // 屏幕渲染着色器（显示FBO纹理）
    // ==============================================
    private void initScreenShader() {
        String vertex =
                "attribute vec2 aPosition;" +
                        "attribute vec2 aTexCoord;" +
                        "varying vec2 vTexCoord;" +
                        "void main(){gl_Position=vec4(aPosition,0,1);vTexCoord=aTexCoord;}";

        String fragment =
                "precision mediump float;" +
                        "uniform sampler2D uTexture;" +
                        "varying vec2 vTexCoord;" +
                        "void main(){gl_FragColor=texture2D(uTexture,vTexCoord);}";

        screenProgram = createProgram(vertex,fragment);
        screenPositionHandle = GLES20.glGetAttribLocation(screenProgram,"aPosition");
        screenTextureHandle = GLES20.glGetAttribLocation(screenProgram,"aTexCoord");
    }

    // ==============================================
    // 工具：编译链接着色器
    // ==============================================
    private int createProgram(String vertexSource,String fragmentSource) {
        int vShader = loadShader(GLES20.GL_VERTEX_SHADER,vertexSource);
        int fShader = loadShader(GLES20.GL_FRAGMENT_SHADER,fragmentSource);

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program,vShader);
        GLES20.glAttachShader(program,fShader);
        GLES20.glLinkProgram(program);

        int[] link = new int[1];
        GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,link,0);
        if(link[0]==0) {
            Log.e(TAG,"链接失败: "+GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    private int loadShader(int type,String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader,source);
        GLES20.glCompileShader(shader);

        int[] comp = new int[1];
        GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,comp,0);
        if(comp[0]==0) {
            Log.e(TAG,"编译失败: "+GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}