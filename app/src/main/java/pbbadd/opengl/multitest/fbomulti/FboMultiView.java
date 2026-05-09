package pbbadd.opengl.multitest.fbomulti;

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

public class FboMultiView extends SurfaceView implements SurfaceHolder.Callback{

    private final String tag="manual egl view";

    public FboMultiView(Context context) {
        super(context);
        init();
    }

    public FboMultiView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    private void init() {
        getHolder().addCallback(this);
    }

    public native void setTexSize(int w,int h);
    public native void eglinit(Surface surface);
    public native void egldeinit();
    public native void render(int w,int h);
    public native void updateTexture(byte[] pixels, int width, int height);

    private Bitmap bgBitmap;
    private byte[] bgPixels;
    private Thread render_thread;
    private boolean render_start=false;
    private int render_interval=1;
    private boolean resource_initialized=false;
    private int bg_w,bg_h;
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if(!resource_initialized) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false; // 关键：禁止系统缩放
            bgBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.fbo_multi,options);
            bg_w=bgBitmap.getWidth();
            bg_h=bgBitmap.getHeight();
            Log.d(tag,"surfaceCreated,bgw="+bg_w+",bgh="+bg_h);
            bgPixels = new byte[bg_w*bg_h*4];
            bgBitmap.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(bgPixels));
            Log.d(tag, "surfaceCreated");
            resource_initialized=true;
            create_render();
        }
    }

    public void create_render() {
        if(render_thread ==null) {
            render_thread =new Thread(()->{
                setTexSize(bg_w,bg_h);
                eglinit(getHolder().getSurface());
                updateTexture(bgPixels, bgBitmap.getWidth(), bgBitmap.getHeight());
                render(getWidth(), getHeight());
                while(!render_destroy) {
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
                }
            },"render-s");
            render_thread.start();
        }
    }

    private boolean render_destroy=false;
    public void render_start() {
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
        Log.d(tag,"surfaceChanged");
    }
}