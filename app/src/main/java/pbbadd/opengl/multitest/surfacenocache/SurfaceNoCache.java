package pbbadd.opengl.multitest.surfacenocache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Trace;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.Objects;

import pbbadd.opengl.multitest.R;

public class SurfaceNoCache extends SurfaceView implements SurfaceHolder.Callback{
    public SurfaceHolder surface_holder;
    public DrawThread draw_thread;

    public SurfaceNoCache(Context c) {
        super(c);
        surface_holder=getHolder();
        surface_holder.addCallback(this);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public SurfaceNoCache(Context c, AttributeSet a) {
        super(c, a);
        surface_holder=getHolder();
        surface_holder.addCallback(this);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    private int v_w,v_h;

    private RectF rectf_bg;
    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        v_w=width;v_h=height;
        rectf_bg=new RectF(0.0f,0.0f,bg.getWidth(),bg.getHeight());
        Log.d("pbb add","surface view size w="+v_w+",h="+v_h);
    }

    private Bitmap bg;
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        holder.setFormat(PixelFormat.RGBA_8888); //force gpu accelerate
        if (bg == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false; // 关键：禁止系统缩放
            bg = BitmapFactory.decodeResource(getResources(), R.drawable.c_bg_xxxxhd, options);

            Log.d("pbb add", "bg w,h" + bg.getWidth() + "," + bg.getHeight());
        }
        draw_thread=new DrawThread(surface_holder);
        draw_thread.control(true);
        draw_thread.start();
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        boolean retry = true;
        draw_thread.wait_done();
        while (retry) {
            try {
                draw_thread.join();
                retry = false;
                break;
            } catch (InterruptedException e) {
                Log.d("pbb add", Objects.requireNonNull(e.getMessage()));
            }
        }
        if (bg != null && !bg.isRecycled()) {
            bg.recycle();
            bg = null;
        }
        Log.d("pbb add","surface no cache surfaceDestroyed");
    }

    public class DrawThread extends Thread {
        public SurfaceHolder draw_thread_surface_holder;
        public DrawThread(SurfaceHolder s_h) {
            draw_thread_surface_holder=s_h;
        }
        public boolean is_running;
        public final Object lock=new Object();
        public boolean is_done=false;
        public void wait_done() {
            control(false);
            synchronized (lock) {
                if(!is_done) {
                    try {
                        lock.wait();
                        Log.d("pbb add","wait done");
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        @Override
        public void run() {
            super.run();
            drawFrameNoCache();
            synchronized (lock) {
                lock.notifyAll();
                is_done = true;
            }
        }

        public void control(boolean b) {
            is_running =b;
        }

        private void drawFrameNoCache() {
            int bg_width=bg.getWidth();
            int loc=bg_width;
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setAntiAlias(true);
            p.setTextSize(64);
            Canvas c=null;
            while(is_running) {
                try {
                    c=draw_thread_surface_holder.lockCanvas();
                    if(c!=null) {
                        c.drawColor(0xffffffff);
                        c.drawBitmap(bg, null, rectf_bg, p);
                        int p_color = 0xffff0000;
                        for (int i = 0; i < (0xff / 2); ++i) {
                            p_color += 2;
                            p.setColor(p_color);
                            float f_x = (float) (loc + i);
                            c.drawLine(f_x, 0, f_x, (float) v_h, p);
                        }
                        p.setColor(0xff00ff00); //green
                        c.drawText("" + loc, 64, 64, p);
                        Log.d("pbb add",""+loc);
                        Trace.beginSection("pbb add," + loc);
                        Trace.endSection();
                    }
                } catch (Exception e) {
                    Log.d("pbb add", Objects.requireNonNull(e.getMessage()));
                } finally {
                    if (c != null) {
                        draw_thread_surface_holder.unlockCanvasAndPost(c);
                    }
                }
                loc-=64;
                if(loc<=0) {
                    loc=bg_width;
                }
            }
        }
    }
}
