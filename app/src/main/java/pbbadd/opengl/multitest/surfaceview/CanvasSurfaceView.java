package pbbadd.opengl.multitest.surfaceview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import pbbadd.opengl.multitest.R;

public class CanvasSurfaceView extends SurfaceView implements SurfaceHolder.Callback{
    public SurfaceHolder surface_holder;
    public DrawThread draw_thread;
    public List<Bitmap> bl;

    public CanvasSurfaceView(Context c) {
        super(c);
        surface_holder=getHolder();
        surface_holder.addCallback(this);
    }

    public CanvasSurfaceView(Context c, AttributeSet a) {
        super(c, a);
        surface_holder=getHolder();
        surface_holder.addCallback(this);
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
        if(bg==null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false; // 关键：禁止系统缩放
            bg = BitmapFactory.decodeResource(ActivitySurfaceView.resource, R.drawable.c_bg_xxxxhd, options);

            Log.d("pbb add","bg w,h"+bg.getWidth()+","+bg.getHeight());
        }
        draw_thread=new DrawThread(surface_holder);
        draw_thread.control(true);
        draw_thread.start();
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        boolean retry = true;
        draw_thread.control(false);
        while (retry) {
            try {
                draw_thread.join();
                retry = false;
            } catch (InterruptedException e) {
                Log.d("pbb add", Objects.requireNonNull(e.getMessage()));
            }
        }
        if (bg != null && !bg.isRecycled()) {
            bg.recycle();
            bg = null;
        }
        bl.clear();
    }

    public class DrawThread extends Thread {
        public SurfaceHolder draw_thread_surface_holder;
        public DrawThread(SurfaceHolder s_h) {
            draw_thread_surface_holder=s_h;
        }
        public boolean is_running;
        public void control(boolean b) {
            is_running =b;
        }
        @Override
        public void run() {
            super.run();
            int loc=bg.getWidth();
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            Canvas c=null;
            while(is_running) {
                p.setAntiAlias(true);
                Bitmap tb=Bitmap.createBitmap(bg.getWidth(),bg.getHeight(),Bitmap.Config.ARGB_8888);
                Canvas tc=new Canvas(tb);
                tc.drawBitmap(bg,null, rectf_bg,p);
                int p_color=0xffff0000;
                for(int i=0;i<(0xff/2);++i) {
                    p_color+=2;
                    p.setColor(p_color);
                    float f_x=(float)(loc+i);
                    tc.drawLine(f_x,0,f_x,(float)v_h,p);
                }
                p.setColor(0xff00ff00); //green
                p.setTextSize(64);
                tc.drawText(""+loc,64,64,p);
                try {
                    c=draw_thread_surface_holder.lockCanvas();
                    c.drawBitmap(tb, null, rectf_bg, p);
                } catch (Exception e) {
                    Log.d("pbb add", Objects.requireNonNull(e.getMessage()));
                } finally {
                    if (c != null) {
                        draw_thread_surface_holder.unlockCanvasAndPost(c);
                    }
                }
                loc-=(bg.getWidth()/80);
                loc=loc%((int)(bg.getWidth()));
                if(loc<=0) {
                    loc=bg.getWidth();
                }
            }
        }
    }
}
