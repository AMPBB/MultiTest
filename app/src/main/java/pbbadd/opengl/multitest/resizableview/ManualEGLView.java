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
    public native void recreateSurface(Surface surface);
    public native void render(int w,int h);
    public native void updateTexture(byte[] pixels, int width, int height);
    public native void makeanothercontext();

    private Bitmap bgBitmap;
    private byte[] bgPixels;
    private Thread render_thread1;
    private boolean render_start1=false;
    private Thread render_thread2;
    private boolean render_start2=true; //render_recreate() trigger create render_thread1
    private final int render_interval=32;
    private boolean resource_initialized=false;
    private boolean need_recreate=false;
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if(!resource_initialized) {
//            eglInit(holder.getSurface());
            // 加载图片
            bgBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.resizable_view_res);
            bgPixels = new byte[bgBitmap.getWidth() * bgBitmap.getHeight() * 4];
            bgBitmap.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(bgPixels));
//            updateTexture(bgPixels, bgBitmap.getWidth(), bgBitmap.getHeight());
            Log.d(tag, "surfaceCreated");
            resource_initialized=true;
//            render(getWidth(),getHeight());
            create_render(getWidth(),getHeight());
        }
    }

    public void make_another_context() {
        makeanothercontext();
    }

    public void set_need_recreate() {
        need_recreate=true;
    }

    public void create_render(int current_w, int current_h) {
        if(render_thread1==null) {
            render_thread1=new Thread(()->{
                eglInit(getHolder().getSurface());
                updateTexture(bgPixels, bgBitmap.getWidth(), bgBitmap.getHeight());
                render(getWidth(), getHeight());
                while(!render_destroy) {
                    if(need_recreate) {
                        recreateSurface(getHolder().getSurface());
                        need_recreate=false;
                    }
                    while (render_start1) {
                        render(getWidth(), getHeight());
                        try {
                            Thread.sleep(render_interval);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    try {
                        Thread.sleep(render_interval*10);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            render_start1=true;
            render_thread1.start();
        }
    }

    private boolean render_destroy=false;
    public void render_start(int current_w, int current_h) {
        if(render_thread1!=null) {
            render_start1=true;
        }
    }

    public void render_stop() {
        render_start1=false;
    }

    public void render_recreate() {
        if(render_start1) {
            render_start1=false;
            render_thread2=new Thread(()->{
                while(render_start2) {
                    render(bgBitmap.getWidth(), bgBitmap.getHeight());
                    try {
                        Thread.sleep(render_interval);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            render_start2=true;
            render_thread2.start();
        } else if(render_start2) {
            render_start2=false;
            render_thread1=new Thread(()->{
                while(render_start1) {
                    render(bgBitmap.getWidth(), bgBitmap.getHeight());
                    try {
                        Thread.sleep(render_interval);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            render_start1 =true;
            render_thread1.start();
        } else {
            Log.e(tag,"what? render thread error!");
        }
    }
    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        render_start1=false;
        render_start2=false;
        render_destroy=true;
        render_thread1=null;
        render_thread2=null;
    }
    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }


}