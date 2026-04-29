package pbbadd.opengl.multitest.surfaceusecache;

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

public class SurfaceUseCache extends SurfaceView implements SurfaceHolder.Callback{
    private final String tag="surface use cache";
    public SurfaceHolder surface_holder;
    public DrawThread draw_thread;
    private Bitmap bg;
    public SurfaceUseCache(Context c, AttributeSet a) {
        super(c, a);
        surface_holder=getHolder();
        surface_holder.addCallback(this);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (bg == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false; // 关键：禁止系统缩放
            bg = BitmapFactory.decodeResource(getResources(), R.drawable.c_bg_xxxxhd, options);
            Log.d("pbb add create", "bg w,h" + bg.getWidth() + "," + bg.getHeight());
            rectf_bg=new RectF(0.0f,0.0f,bg.getWidth(),bg.getHeight());
            Log.d(tag,"surface view size w="+v_w+",h="+v_h);
        }
    }

    private int v_w,v_h;

    private RectF rectf_bg;
    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        v_w=width;v_h=height;
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if(draw_thread!=null) {
            Log.e(tag,"surfaceCreated another time!");
            return;
        }
        holder.setFormat(PixelFormat.RGBA_8888); //force gpu accelerate
        draw_thread=new DrawThread(surface_holder);
        draw_thread.control(true);
        draw_thread.start();
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        if(draw_thread==null) {
            Log.e(tag,"surfaceDestroyed another time!");
            return;
        }
        draw_thread.wait_done();
        try {
            draw_thread.join();
            Log.d(tag,"joined");
        } catch (InterruptedException e) {
            Log.e("pbb add", Objects.requireNonNull(e.getMessage()));
        }
        if (bg != null && !bg.isRecycled()) {
            bg.recycle();
            Log.d(tag,"bg recycled");
            bg = null;
        }
    }

    public class DrawThread extends Thread {
        public SurfaceHolder draw_thread_surface_holder;
        public volatile boolean is_running;
        public final Object lock=new Object();
        public boolean is_done=false;

        public DrawThread(SurfaceHolder s_h) {
            draw_thread_surface_holder=s_h;
        }

        public void wait_done() {
            control(false);
            synchronized (lock) {
                if(!is_done) {
                    try {
                        lock.wait();
                        Log.d(tag,"wait done "+this.hashCode());
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        @Override
        public void run() {
            super.run();
            Log.d(tag,"run "+this.hashCode());
            drawFrameUseCache();
        }

        public void control(boolean b) {
            synchronized (lock) {
                is_running = b;
                Log.d(tag,"control "+is_running);
            }
        }

        private void drawFrameUseCache() {
            Log.d(tag,"start draw use cache");
            int loc=bg.getWidth();
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setAntiAlias(true);
            p.setColor(0xff00ff00); //green
            p.setTextSize(64);
            Canvas c=null;
            Bitmap tb=Bitmap.createBitmap(bg.getWidth(),bg.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas tc=new Canvas(tb);
            while(is_running) {
                synchronized (lock) {
                    if(!is_running) {
                        Log.d(tag,"is_running=="+is_running+" break");
                        break;
                    }
                }
                if(bg==null) {
                    Log.e(tag,"bg is null,break,please check!");
                    break;
                }
                tc.drawColor(0xffffffff);
                tc.drawBitmap(bg,null, rectf_bg,p);
                int p_color=0xffff0000;
                for(int i=0;i<(0xff/2);++i) {
                    p_color+=2;
                    p.setColor(p_color);
                    float f_x=(float)(loc+i);
                    tc.drawLine(f_x,0,f_x,(float)v_h,p);
                }
                tc.drawText(""+loc,64,64,p);
                tc.save();
                tc.restore();

                try {
                    Trace.beginSection("pbb add" + loc);
                    c=draw_thread_surface_holder.lockCanvas();
                    if(c!=null) {
                        c.drawColor(0xffffffff);
                        c.drawColor(0xff00ff00);
                        c.drawColor(0xff0000ff);
                        c.drawColor(0xffff0000);
                        c.drawBitmap(tb, null, rectf_bg, p);
                        Log.e("pbb add,",""+loc);
                    }
                } catch (Exception e) {
                    Log.d(tag, Objects.requireNonNull(e.getMessage()));
                } finally {
                    Trace.endSection();
                    if (c != null) {
                        draw_thread_surface_holder.unlockCanvasAndPost(c);
                    }
                }
                loc-=64;
                if(loc<=0) {
                    loc=bg.getWidth();
                }
            }
            tb.recycle();

            synchronized (lock) {
                lock.notifyAll();
                Log.d(tag,"notify");
                is_done = true;
            }
        }
    }
}
