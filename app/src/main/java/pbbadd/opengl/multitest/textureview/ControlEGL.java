package pbbadd.opengl.multitest.textureview;

import android.opengl.EGL14;
import android.opengl.EGL15;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLES30;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

public class ControlEGL {

    private final String tag="ControlEGL";

    private EGLDisplay egl_display;
    private EGLContext egl_context;
    private EGLConfig egl_config;
    private boolean is_egl_inited=false;
    private int index;
    private boolean released_done=false;

    private int program_id;
    public ControlEGL(int i) {
        index=i;
    }

    public boolean egl_init() {
        if(is_egl_inited) {
            Log.e(tag,"egl_init has been called, check code!");
            return false;
        }
        egl_display= EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if(egl_display==EGL14.EGL_NO_DISPLAY) {
            Log.e(tag,"EGL_NO_DISPLAY");
            return false;
        }
        Log.d(tag,"display created,"+index);
        int[] egl_version_major=new int[2];
        int[] elg_version_minor=new int[2];
        if(!EGL14.eglInitialize(egl_display,egl_version_major,0,elg_version_minor,0)) {
            Log.e(tag,"version error");
            return false;
        }
        String s_egl_version = String.format(Locale.US,"%d.%d",egl_version_major[0], elg_version_minor[0]);
        Log.d(tag,"egl version,"+s_egl_version);
        int[] egl_config_attribute = {
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_RENDERABLE_TYPE, EGL15.EGL_OPENGL_ES3_BIT,
                EGL14.EGL_NONE
        };

        android.opengl.EGLConfig[] egl_configs = new android.opengl.EGLConfig[1];
        int[] egl_config_cnt = new int[1];
        if(!EGL14.eglChooseConfig(egl_display, egl_config_attribute,0, egl_configs, 0,1,egl_config_cnt,0)) {
            return false;
        } else {
            Log.d(tag,"config cnt="+egl_config_cnt[0]);
            egl_config = egl_configs[0];
        }

        int[] egl_context_attribute = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
        };
        egl_context = EGL14.eglCreateContext(egl_display, egl_configs[0], EGL14.EGL_NO_CONTEXT, egl_context_attribute,0);
        if (egl_context == EGL14.EGL_NO_CONTEXT) {
            return false;
        }
        Log.d(tag,"context created,"+index);
        // this is just for test
        if (!EGL14.eglMakeCurrent(egl_display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, egl_context)) {
            return false;
        }

        is_egl_inited=true;
        Log.d(tag, "egl init done,index="+index);
        return true;
    }

    public void egl_destroy() {
        if(!is_egl_inited) {
            Log.e(tag,"is_egl_inited==false");
            return;
        }
        // 2. 销毁GLES3.0 Context
        if (egl_context != null) {
            EGL14.eglDestroyContext(egl_display, egl_context);
            Log.d(tag,"context released,"+index);
        }

        // 3. 终止EGL Display
        if (egl_display != null) {
            EGL14.eglTerminate(egl_display);
            Log.d(tag,"display terminated,"+index);
        }

        // 4. 删除GLES3.0着色器程序
        if (program_id != 0) {
            GLES30.glDeleteProgram(program_id);
        }

        released_done = true;
        is_egl_inited=false;
    }

    public boolean create_program_30() {
        // 1. GLES3.0顶点着色器（复用你提供的代码）
        String vertexShader30 = "#version 300 es\n" +
                "layout (location = 0) in vec3 aPos;\n" +
                "layout (location = 1) in vec2 aTexCoord;\n" +
                "out vec2 TexCoord;\n" +
                "void main() {\n" +
                "    gl_Position = vec4(aPos, 1.0);\n" +
                "    TexCoord = aTexCoord;\n" +
                "}\n";

        // 2. GLES3.0片段着色器（复用你提供的代码）
        String fragShader30 = "#version 300 es\n" +
                "precision mediump float;\n" +
                "in vec2 TexCoord;\n" +
                "out vec4 FragColor;\n" +
                "uniform sampler2D ourTexture;\n" +
                "void main() {\n" +
                "    FragColor = texture(ourTexture, TexCoord);\n" +
                "}\n";

        // 编译顶点着色器
        int vertexShader = loadShader30(GLES30.GL_VERTEX_SHADER, vertexShader30);
        if (vertexShader == 0) return false;

        // 编译片段着色器
        int fragShader = loadShader30(GLES30.GL_FRAGMENT_SHADER, fragShader30);
        if (fragShader == 0) return false;

        // 链接程序
        program_id = GLES30.glCreateProgram();
        GLES30.glAttachShader(program_id, vertexShader);
        GLES30.glAttachShader(program_id, fragShader);
        GLES30.glLinkProgram(program_id);

        // 检查链接状态
        int[] linkStatus = new int[1];
        GLES30.glGetProgramiv(program_id, GLES30.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES30.GL_TRUE) {
            String linkLog = GLES30.glGetProgramInfoLog(program_id);
            Log.e(tag, "shader compile failed,index="+index+",log:" + linkLog);
            GLES30.glDeleteProgram(program_id);
            program_id = 0;
        }

        // 删除临时着色器（释放资源）
        GLES30.glDeleteShader(vertexShader);
        GLES30.glDeleteShader(fragShader);
        Log.d(tag, "shader compile success,index="+index+",program id:"+program_id);
        return true;
    }

    public int loadShader30(int type, String source) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);

        // 检查编译状态
        int[] compileStatus = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] != GLES30.GL_TRUE) {
            String compileLog = GLES30.glGetShaderInfoLog(shader);
            String shaderType = type == GLES30.GL_VERTEX_SHADER ? "顶点" : "片段";
            Log.e(tag, "GLES3.0" + shaderType + "着色器编译失败：\n日志：" + compileLog + "\n代码：\n" + source);
            GLES30.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    public void destroy_program_30() {
        GLES30.glDeleteProgram(program_id);
    }

    public void print_gl_get_error(String s) {
        if(GLES30.glGetError()!=GLES30.GL_NO_ERROR) {
            Log.e(tag,s);
        }
    }

    public void draw_texture_on_screen(int texture_id) {
        // GLES3.0全屏顶点数据（位置+纹理坐标，复用你的代码）
        float[] fullScreenVertices = {
                // 位置坐标（NDC）  // 纹理坐标
                -1.0f,  1.0f, 0.0f,  0.0f, 1.0f, // 左上
                -1.0f, -1.0f, 0.0f,  0.0f, 0.0f, // 左下
                1.0f,  1.0f, 0.0f,  1.0f, 1.0f, // 右上
                1.0f, -1.0f, 0.0f,  1.0f, 0.0f  // 右下
        };

        // 创建GLES3.0 VBO（顶点缓冲对象）
        int[] vbo = new int[1];
        GLES30.glGenBuffers(1, vbo, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fullScreenVertices.length * 4,
                ByteBuffer.allocateDirect(fullScreenVertices.length * 4)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer()
                        .put(fullScreenVertices)
                        .position(0),
                GLES30.GL_STATIC_DRAW);
        print_gl_get_error("216");
        // GLES3.0 location布局绑定（无需glGetAttribLocation）
        // location=0：顶点位置
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 20, 0);
        print_gl_get_error("221");
        // location=1：纹理坐标
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 20, 12);

        // 绑定GLES3.0纹理到采样器
        if(program_id<=0) {
            Log.e(tag,"program_id="+program_id);
            return;
        }
        int uTexture = GLES30.glGetUniformLocation(program_id, "ourTexture");
        print_gl_get_error("glGetUniformLocation");
        GLES30.glUniform1i(uTexture, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture_id);
        // GLES3.0绘制（三角带）
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        print_gl_get_error("glDrawArrays");
        // 释放GLES3.0 VBO
        GLES30.glDeleteBuffers(1, vbo, 0);

        int glErr = GLES30.glGetError();
        if (glErr != GLES30.GL_NO_ERROR) {
            Log.e(tag, "绘制全屏矩形后GL错误：0x" + Integer.toHexString(glErr));
        }
    }
}
