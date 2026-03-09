package pbbadd.opengl.multitest.textureview;

import android.content.Context;
import android.opengl.EGL15;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLES30;
import android.opengl.EGL14;
import android.util.Log;
import android.view.TextureView;
import android.widget.GridLayout;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 纯GLES3.0版本的TextureView渲染线程
 * 设备确认支持GLES3.0，无任何2.0兼容逻辑
 */
public class TextureviewRenderThread extends Thread {
    private static final String tag = "TextureViewRenderThread";

    // 线程控制标记
    private volatile boolean is_running = false;
    private volatile boolean is_egl_initialized = false;

    // Surface管理（key=TextureView ID，value=渲染信息）
    public final Map<Integer, SurfaceInfo> surfaceInfoMap = new HashMap<>();
    public boolean released_done=false;

    // 纹理尺寸参数（GLES3.0下最大支持512x512）
    private int tv_w;
    private int tv_h;

    // GL 3.0程序ID
    private int program_id;
    private int index;
    private boolean should_swap_buffer=false;
    private boolean should_sync=false;
    private boolean should_sync_dup=false;
    private boolean should_sync_every_rending=false;
    private boolean should_sync_dup_every_rending=false;
    private int sync_interval_millis=0;

    private Context context=null;
    private AssetReader asset_reader=null;

    public TextureviewRenderThread(int w, int h,int i) {
        tv_w = w;
        tv_h = h;
        index=i;
    }

    public void set_sync_interval_millis(int i) {
        sync_interval_millis=i;
    }

    public void set_context(Context c) {
        context=c;
        asset_reader=new AssetReader(context);
    }

    public void set_should_swap_buffer(boolean value) {
        should_swap_buffer=value;
    }

    public void set_should_sync(boolean value) {
        should_sync=value;
    }

    public void set_should_sync_dup(boolean value) {
        should_sync_dup=value;
    }

    public void set_should_sync_every_rending(boolean value) {
        should_sync_every_rending=value;
    }

    public void set_should_sync_dup_every_rending(boolean value) {
        should_sync_dup_every_rending=value;
    }

    @Override
    public void run() {
        if(!render_prepare(tv_w,tv_h)) {
            return;
        }

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
                            if(should_swap_buffer) EGL14.eglSwapBuffers(egl_display, info.eglSurface);
                            if(should_sync_every_rending) fence_sync_test();
                            if(should_sync_dup_every_rending) fence_sync_dup_test();
                            if(sync_interval_millis!=0) {
                                try {
                                    Thread.sleep(sync_interval_millis);
                                } catch (InterruptedException e) {
                                    Log.w(tag, "render thread sleep error" + e.getMessage());
                                }
                            }
                        }
                    }
                }
                if(should_sync) fence_sync_test();
                if(should_sync_dup) fence_sync_dup_test();
                if(sync_interval_millis!=0) {
                    try {
                        Thread.sleep(sync_interval_millis);
                    } catch (InterruptedException e) {
                        Log.w(tag, "render thread sleep error" + e.getMessage());
                    }
                }
                pres_size=surface_info_list.size();
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
//                Thread.sleep(32,10);
            } catch (InterruptedException e) {
                Log.w(tag, "render thread sleep error" + e.getMessage());
                break;
            }
        }

        render_unprepare();
        Log.d(tag, "render thread stopped");
    }

    static {
        System.loadLibrary("gles30testdemo");
    }

    private native void fence_sync_test();
    private native void fence_sync_dup_test();

    private EGLDisplay egl_display;
    private EGLContext egl_context;
    private EGLConfig egl_config;
    private boolean egl_init() {
        if(is_egl_initialized) {
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

        released_done = true;
    }

    private boolean render_prepare(int w,int h) {
        if(!egl_init()) {
            return false;
        }
        if(!create_shader_program()) {
            return false;
        }
        GLES30.glViewport(0, 0, w, h);
        GLES30.glUseProgram(program_id);
        create_data(w,h);

        synchronized (synchronized_flag) {
            is_running = true;
            is_egl_initialized = true;
        }
        return true;
    }

    private void render_ing(int w,int h) {
        int[] data_order=get_data_with_order_color();
        int texture_id = create_texture(data_order, w, h);
        if (texture_id == 0) {
            Log.e(tag,"create texture id failed");
            return;
        }
        draw_on_screen(texture_id);
        GLES30.glDeleteTextures(1, new int[]{texture_id}, 0);
    }

    private void render_unprepare() {
        destroy_data();
        egl_destroy();
        destroy_shader_program();
    }

    private Map<Integer,int[]> data_map;

    private void create_data(int w,int h) {
        data_map=new HashMap<>();
        int i;
        int color_base=0xff000000;
        int color_stack=0x00000ff; //0xff--->red,0xff00--->green
        int color;
        for(i=0;i<3;++i) {
            int[] data=new int[w*h];
            color=color_base|(color_stack<<(i*8));
            Arrays.fill(data,color);
            data_map.put(i,data);
        }
        Log.d(tag,"create_data done,"+data_map.size());
    }

    private void destroy_data() {
        data_map.clear();
        Log.d(tag,"destroy_data done");
    }

    private int next_color_index=0;
    private int[] get_data_with_order_color() {
        int[] res= data_map.get(next_color_index);
        ++next_color_index;
        next_color_index=(next_color_index)%data_map.size();
        return res;
    }

    /**
     * 创建GLES3.0 2D纹理（纯3.0调用）
     */
    private int create_texture(int[] data, int w, int h) {
        // 生成GLES3.0纹理ID
        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);
        int texture_id = textures[0];
        if (texture_id == 0) {
            Log.e(tag, "GLES3.0生成纹理ID失败");
            return 0;
        }

        // 绑定GLES3.0纹理
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture_id);

        // 设置GLES3.0纹理参数（优化采样效率）
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

        Buffer data_to_buffer = IntBuffer.wrap(data);

        // 上传纹理数据到GLES3.0
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, data_to_buffer);

        // 检查GLES3.0错误
        int glErr = GLES30.glGetError();
        if (glErr != GLES30.GL_NO_ERROR) {
            Log.e(tag, "GLES3.0上传纹理失败，错误码：" + glErr);
            GLES30.glDeleteTextures(1, textures, 0);
            return 0;
        }

        return texture_id;
    }

    private void destroy_texture(int texture_id) {

    }

    private void draw_on_screen(int texId) {
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
        int uTexture = GLES30.glGetUniformLocation(program_id, "ourTexture");
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
        Log.d(tag,"delete surface");
    }

    public void stopRender() {
        Log.d(tag,"stop render");
        synchronized (synchronized_flag) {
            is_running = false;
            is_egl_initialized = false;
        }
        try {
            // 等待线程退出（最多1秒）
            join(1000);
        } catch (InterruptedException e) {
            Log.e(tag, "stopRender() error" + e.getMessage());
        }
    }

    private boolean create_shader_program() {
        if(null==asset_reader) {
            Log.e(tag,"asset reader error");
            return false;
        }
        String vertexShader30 = asset_reader.readTextFromAssets("vertex_shader.txt");
        String fragShader30 = asset_reader.readTextFromAssets("fragment_shader.txt");

        int vertexShader = loadShader30(GLES30.GL_VERTEX_SHADER, vertexShader30);
        if (vertexShader == 0) {
            Log.e(tag,"vertex shader error");
            return false;
        }

        int fragShader = loadShader30(GLES30.GL_FRAGMENT_SHADER, fragShader30);
        if (fragShader == 0) {
            Log.e(tag,"frag shader error");
            return false;
        }

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
            Log.e(tag, "shader program create failed" + linkLog);
            GLES30.glDeleteProgram(program_id);
            program_id = 0;
            is_running=false;
            return false;
        }

        // 删除临时着色器（释放资源）
        GLES30.glDeleteShader(vertexShader);
        GLES30.glDeleteShader(fragShader);
        return true;
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

    private void destroy_shader_program() {
        GLES30.glDeleteProgram(program_id);
    }

}