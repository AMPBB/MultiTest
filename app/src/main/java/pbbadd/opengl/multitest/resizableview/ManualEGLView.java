package pbbadd.opengl.multitest.resizableview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import pbbadd.opengl.multitest.R;

public class ManualEGLView extends SurfaceView implements SurfaceHolder.Callback{

    private final String tag="manual egl view";

    public ManualEGLView(Context context) {
        super(context);
        init();
    }

    public ManualEGLView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    private void init() {
        getHolder().addCallback(this); // 监听surface创建
    }

    public native void eglInit(Surface surface);
    public native void egldeinit();
    public native void recreateSurface(Surface surface);
    public native void render(int w,int h);
    public native void updateTexture(byte[] pixels, int width, int height);
    public native void eglinitanother(Surface surface);
    public native void makeanothercontext();

    private Bitmap bgBitmap;
    private byte[] bgPixels;
    private Thread render_thread;
    private boolean render_start=false;
    private int render_interval=1;
    private boolean resource_initialized=false;
    private boolean need_recreate=false;
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if(!resource_initialized) {
            bgBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.resizable_view_res);
            bgPixels = new byte[bgBitmap.getWidth() * bgBitmap.getHeight() * 4];
            bgBitmap.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(bgPixels));
            Log.d(tag, "surfaceCreated");
            resource_initialized=true;
            create_render(getWidth(),getHeight());
//            make_another_egl_init();
        }
    }

    public void make_another_egl_init() {
        eglinitanother(getHolder().getSurface());
    }

    public void make_another_context() {
        makeanothercontext();
    }

    public void set_need_recreate() {
        need_recreate=true;
        render_interval=1;
    }

    private long recreate_delay=10;
    public void set_need_recreate(long milli_second) {
        set_need_recreate();
        recreate_delay=milli_second;
    }

    public void busy_wait(long busy_time) {
        long start = System.currentTimeMillis();
        long current=System.currentTimeMillis();
        long last=current;
        while (true) {
            current=System.currentTimeMillis();
            if((current-start)>=busy_time) {
                Log.d(tag, current + "-" + start);
                break;
            }
            if(last!=current) {
                Log.d(tag, current + "-" + start);
                last=current;
            }
        }
    }

    public void create_render(int current_w, int current_h) {
        if(render_thread ==null) {
            render_thread =new Thread(()->{
                eglInit(getHolder().getSurface());
                updateTexture(bgPixels, bgBitmap.getWidth(), bgBitmap.getHeight());
                render(getWidth(), getHeight());
                while(!render_destroy) {
                    if(need_recreate) {
                        if(recreate_delay!=0) {
                            Log.d(tag,"delay "+recreate_delay+" ms");
                            busy_wait(recreate_delay);
                            recreate_delay=0;
                        }
                        recreateSurface(getHolder().getSurface());
                        need_recreate=false;
                        Log.d(tag,"recreate done");
                    }
                    if(render_start) {
                        Log.d(tag,"render started");
                    }
                    while (render_start) {
                        render(getWidth(), getHeight());
                        try {
                            Thread.sleep(render_interval);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
//                    Log.d(tag,"render stopped");
//                    try {
//                        Thread.sleep(render_interval);
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
                }
            },"render-s");
            render_thread.start();
        }
    }

    private boolean render_destroy=false;
    public void render_start(int current_w, int current_h) {
        if(render_thread!=null) {
            render_start=true;
        } else {
            Log.e(tag,"render_thread is null");
        }
    }

    public void render_stop() {
        render_start =false;
    }
    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        render_start =false;
        render_destroy=true;
        render_thread =null;
        egldeinit();
    }
    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }


}