package pbbadd.opengl.multitest.textureview;

import android.opengl.EGL15;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLES30;
import android.opengl.EGL14;
import android.util.Log;

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

    private ControlEGL control_egl;

    public TextureviewRenderThread(int w, int h,int i) {
        tv_w = w;
        tv_h = h;
        index=i;
        control_egl=new ControlEGL(i);
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
        if(control_egl.egl_init()) {
            if(control_egl.create_program_30()) {
                GLES30.glViewport(0, 0, w, h);
                GLES30.glUseProgram(programId);
                create_data(w,h);
                Log.d(tag,"render_prepare done,index="+index);
                return;
            }
        }
        Log.e(tag,"render_prepare failed,index="+index);
    }

    private void render_ing(int w,int h) {
        int[] data_order=get_data_with_order_color();
        int texture_id = create_texture(data_order, w, h);
        if (texture_id == 0) {
            Log.e(tag,"create texture id failed");
            return;
        }
        Log.d(tag,"draw...,index="+index);
        control_egl.draw_texture_on_screen(texture_id);
        Log.d(tag,"draw done,index="+index);
        GLES30.glDeleteTextures(1, new int[]{texture_id}, 0);
    }

    private void render_unprepare() {
        is_running=false;
        control_egl.destroy_program_30();
        control_egl.egl_destroy();
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
        Log.d(tag,"create_data done,index="+index);
    }

    private void destroy_data() {
        data_map.clear();
        Log.d(tag,"destroy_data done,index="+index);
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
//        Buffer data2buffer = IntBuffer.wrap(data);

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

        return texture_id;
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

}