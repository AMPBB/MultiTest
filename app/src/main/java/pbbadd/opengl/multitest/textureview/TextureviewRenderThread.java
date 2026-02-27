package pbbadd.opengl.multitest.textureview;

import android.opengl.EGL15;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLES30;
import android.opengl.EGL14;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 纯GLES3.0版本的TextureView渲染线程
 * 设备确认支持GLES3.0，无任何2.0兼容逻辑
 */
public class TextureviewRenderThread extends Thread {
    private static final String tag = "TextureViewRenderThread";

    // 线程控制标记
    private volatile boolean is_running = false;
    private volatile boolean is_egl_inited = false;

    // Surface管理（key=TextureView ID，value=渲染信息）
    public final Map<Integer, SurfaceInfo> surfaceInfoMap = new HashMap<>();
    public boolean released_done=false;

    // 纹理尺寸参数（GLES3.0下最大支持512x512）
    private int tv_w;
    private int tv_h;

    // GL 3.0程序ID
    private int programId;
    private int index;

    public TextureviewRenderThread(int w, int h,int i) {
        tv_w = w;
        tv_h = h;
        index=i;
    }

    public TextureviewRenderThread() {

    }

    private void frame_print_debug(int frame_cnt) {
        int cnt_to_print=120;
        if((frame_cnt%cnt_to_print)==0) {
            Log.d(tag,cnt_to_print+" frames done");
        }
    }

    @Override
    public void run() {
        int frame_cnt=1;
        is_running = false;

        if (!egl_init()) {
            Log.e(tag, "egl init failed, stop render run");
            is_running = false;
            return;
        } else {
            is_running =true;
            is_egl_inited =true;
        }

        initGL30Resources();
        render_prepare(tv_w,tv_h);

        int last_size=-1;
        int pres_size=-1;
        int sleep_interval=100;
        synchronized (synchronized_flag) {
            while(surface_info_list.isEmpty()) {
                try {
                    synchronized_flag.wait();
                    last_size=surface_info_list.size();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        while (is_running) {
            synchronized (synchronized_flag) {
                for (SurfaceInfo info : surface_info_list) {
                    if (info.eglSurface != null && info.eglSurface != EGL14.EGL_NO_SURFACE) {
                        if (EGL14.eglMakeCurrent(egl_display, info.eglSurface, info.eglSurface, egl_context)) {
                            render_ing(info.w, info.h);
                            EGL14.eglSwapBuffers(egl_display, info.eglSurface);
                            fencesynctest();
//                            fencesyncduptest();
                        }
                    }
                }
                pres_size=surface_info_list.size();
//                fencesynctest();
            }

            if(pres_size!=last_size) {
                sleep_interval=sleep_interval+100;
            } else {
                sleep_interval=1;
            }
            last_size=pres_size;
            // speed control
            try {
                Thread.sleep(sleep_interval,10);
                ++frame_cnt;
//                frame_print_debug(frame_cnt);
            } catch (InterruptedException e) {
                Log.w(tag, "render thread sleep error" + e.getMessage());
                break;
            }
        }

        egl_destroy();
        render_unprepare();
        is_egl_inited = false;
        Log.d(tag, "render thread stopped run()");
    }


    static {
        System.loadLibrary("gles30testdemo");
    }

    private native void fencesynctest();
    private native void fencesyncduptest();

    private EGLDisplay egl_display;
    private EGLContext egl_context;
    private EGLConfig egl_config;
    private boolean egl_init() {
        if(is_egl_inited) {
            Log.e(tag,"egl_init has been called, check code!");
            return false;
        }
        egl_display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
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
                EGL14.EGL_RENDERABLE_TYPE,EGL15.EGL_OPENGL_ES3_BIT,
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

        Log.d(tag, "egl init done");
        return true;
    }

    private void egl_destroy() {
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
        if (programId != 0) {
            GLES30.glDeleteProgram(programId);
        }

        released_done = true;
    }


    private void render_prepare(int w,int h) {
        GLES30.glViewport(0, 0, w, h);
        GLES30.glUseProgram(programId);
        create_data(w,h);
    }

    private void render_ing(int w,int h) {
//        int[] data=get_data_with_random_color();
        int[] data_order=get_data_with_order_color();
        int texId = createTexture30(data_order, w, h);
        if (texId == 0) {
            Log.e(tag,"create texture id failed");
            return;
        }
        drawFullScreenQuad30(texId);
        GLES30.glDeleteTextures(1, new int[]{texId}, 0);
    }

    private void render_unprepare() {
        destroy_data();
    }

    private Map<Integer,int[]> data_map;

    private void create_data(int w,int h) {
        data_map=new HashMap<>();
        int i;
        int color_base=0xff000000;
        int color_stack=0xff0000;
        int color;
        for(i=0;i<3;++i) {
            int[] data=new int[w*h];
            color=color_base|(color_stack>>(i*8));
            Arrays.fill(data,color);
            data_map.put(i,data);
        }
        Log.d(tag,"create_data done,"+data_map.size());
    }

    private void destroy_data() {
        data_map.clear();
        Log.d(tag,"destroy_data done");
    }

    private int[] get_data_with_random_color() {
        int index=random.nextInt(data_map.size());
        return data_map.get(index);
    }

    private int next_color_index=0;
    private int[] get_data_with_order_color() {
        int[] res= data_map.get(next_color_index);
        ++next_color_index;
        next_color_index=(next_color_index)%data_map.size();
        return res;
    }

    private final Random random=new Random();
    private void fill_data_with_random_color(int[] data) {
        int color_index=random.nextInt()%3;
        int color_red  =0xffff0000; //red
        int color_blue =0xff0000ff; //bb
        int color_green=0xff00ff00; //gg
        int color=0xff000000;
        color=color|(0xff<<(color_index*8));
        Arrays.fill(data,color);
    }

    /**
     * 创建GLES3.0 2D纹理（纯3.0调用）
     */
    private int createTexture30(int[] data, int w, int h) {
        // 生成GLES3.0纹理ID
        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);
        int texId = textures[0];
        if (texId == 0) {
            Log.e(tag, "GLES3.0生成纹理ID失败");
            return 0;
        }

        // 绑定GLES3.0纹理
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId);

        // 设置GLES3.0纹理参数（优化采样效率）
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

        // 转换数据为GLES3.0要求的ByteBuffer
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(data.length * 4);
        byteBuffer.order(ByteOrder.nativeOrder());
        for (int pixel : data) {
            // ========== 修复：颜色字节顺序（0xAARRGGBB → R G B A） ==========
            byte r = (byte) ((pixel >> 16) & 0xFF); // RR
            byte g = (byte) ((pixel >> 8) & 0xFF);  // GG
            byte b = (byte) (pixel & 0xFF);         // BB
            byte a = (byte) ((pixel >> 24) & 0xFF); // AA
            byteBuffer.put(r);
            byteBuffer.put(g);
            byteBuffer.put(b);
            byteBuffer.put(a);
        }
        byteBuffer.position(0);

        // 上传纹理数据到GLES3.0
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, byteBuffer);

        // 检查GLES3.0错误
        int glErr = GLES30.glGetError();
        if (glErr != GLES30.GL_NO_ERROR) {
            Log.e(tag, "GLES3.0上传纹理失败，错误码：" + glErr);
            GLES30.glDeleteTextures(1, textures, 0);
            return 0;
        }

        return texId;
    }

    /**
     * 绘制GLES3.0全屏矩形（复用你提供的顶点数据+location布局）
     */
    private void drawFullScreenQuad30(int texId) {
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

        // GLES3.0 location布局绑定（无需glGetAttribLocation）
        // location=0：顶点位置
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 20, 0);

        // location=1：纹理坐标
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 20, 12);

        // 绑定GLES3.0纹理到采样器
        int uTexture = GLES30.glGetUniformLocation(programId, "ourTexture");
        GLES30.glUniform1i(uTexture, 0);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId);

        // GLES3.0绘制（三角带）
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

        // 释放GLES3.0 VBO
        GLES30.glDeleteBuffers(1, vbo, 0);

        int glErr = GLES30.glGetError();
        if (glErr != GLES30.GL_NO_ERROR) {
            Log.e(tag, "绘制全屏矩形后GL错误：0x" + Integer.toHexString(glErr));
        }
    }

//    private final String synchronized_flag="sync";
    private final Object synchronized_flag=new Object();
    private final List<SurfaceInfo> surface_info_list=new ArrayList<>();
    public void add_surface_info(SurfaceInfo sfi) {
        int[] windows_surface_attribute = {EGL14.EGL_NONE};
        sfi.eglSurface=EGL14.eglCreateWindowSurface(egl_display,egl_config,sfi.surfaceTexture,windows_surface_attribute,0);
        synchronized (synchronized_flag) {
            surface_info_list.add(sfi);
            synchronized_flag.notify();
        }
    }

    public void del_surface_info(SurfaceInfo sfi) {
        synchronized (synchronized_flag) {
            surface_info_list.remove(sfi);
        }
        EGL14.eglDestroySurface(egl_display,sfi.eglSurface);
    }

    public void stopRender() {
        is_running = false;
        try {
            // 等待线程退出（最多1秒）
            join(1000);
        } catch (InterruptedException e) {
            Log.e(tag, "stopRender() error" + e.getMessage());
        }
    }

    /**
     * 初始化GLES3.0资源（仅创建3.0着色器程序）
     */
    private void initGL30Resources() {
        // 创建GLES3.0着色器程序
        programId = createProgram30();
        if (programId == 0) {
            Log.e(tag, "创建GLES3.0着色器程序失败");
            is_running = false;
            return;
        }
        Log.d(tag, "GLES3.0着色器程序初始化成功，程序ID：" + programId);
    }

    /**
     * 创建GLES3.0着色器程序（纯3.0语法，无2.0分支）
     */
    private int createProgram30() {
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
        if (vertexShader == 0) return 0;

        // 编译片段着色器
        int fragShader = loadShader30(GLES30.GL_FRAGMENT_SHADER, fragShader30);
        if (fragShader == 0) return 0;

        // 链接程序
        int program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, vertexShader);
        GLES30.glAttachShader(program, fragShader);
        GLES30.glLinkProgram(program);

        // 检查链接状态
        int[] linkStatus = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES30.GL_TRUE) {
            String linkLog = GLES30.glGetProgramInfoLog(program);
            Log.e(tag, "GLES3.0着色器程序链接失败：\n" + linkLog);
            GLES30.glDeleteProgram(program);
            program = 0;
        }

        // 删除临时着色器（释放资源）
        GLES30.glDeleteShader(vertexShader);
        GLES30.glDeleteShader(fragShader);
        return program;
    }

    /**
     * 加载GLES3.0着色器（带详细错误日志）
     */
    private int loadShader30(int type, String source) {
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

}