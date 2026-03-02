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

        if(!render_prepare(tv_w,tv_h)) {
            is_running = false;
            return;
        } else {
            is_running =true;
            is_egl_inited =true;
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
                        if (EGL14.eglMakeCurrent(control_egl.egl_display, info.eglSurface, info.eglSurface, control_egl.egl_context)) {
                            render_ing(info.w, info.h);
                            EGL14.eglSwapBuffers(control_egl.egl_display, info.eglSurface);
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

        control_egl.egl_destroy();
        render_unprepare();
        is_egl_inited = false;
        Log.d(tag, "render thread stopped run()");
    }


    static {
        System.loadLibrary("gles30testdemo");
    }

    private native void fencesynctest();
    private native void fencesyncduptest();

    private boolean render_prepare(int w,int h) {
        if(control_egl.egl_init()) {
            if(control_egl.create_program_30()) {
                GLES30.glViewport(0, 0, w, h);
                GLES30.glUseProgram(programId);
                create_data(w,h);
                Log.d(tag,"render_prepare done,index="+index);
                return true;
            }
        }
        Log.e(tag,"render_prepare failed,index="+index);
        return false;
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
        sfi.eglSurface=EGL14.eglCreateWindowSurface(control_egl.egl_display,control_egl.egl_config,sfi.surfaceTexture,windows_surface_attribute,0);
        synchronized (synchronized_flag) {
            surface_info_list.add(sfi);
            synchronized_flag.notify();
        }
    }

    public void del_surface_info(SurfaceInfo sfi) {
        synchronized (synchronized_flag) {
            surface_info_list.remove(sfi);
        }
        EGL14.eglDestroySurface(control_egl.egl_display,sfi.eglSurface);
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