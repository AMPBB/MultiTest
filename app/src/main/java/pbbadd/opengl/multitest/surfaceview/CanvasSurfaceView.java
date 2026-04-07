package pbbadd.opengl.multitest.surfaceview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        bl=new ArrayList<>();
        int i;
        for(i=0;i<30;++i) {
            Bitmap b=BitmapFactory.decodeResource(ActivitySurfaceView.resource, R.drawable.c_1920x1080_0);
            if(b!=null) {
                bl.add(b);
            }
        }
        for(;i<60;++i) {
            Bitmap b=BitmapFactory.decodeResource(ActivitySurfaceView.resource, R.drawable.c_1920x1080_1);
            if(b!=null) {
                bl.add(b);
            }
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
                e.printStackTrace();
            }
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
            int i=0;
            while(is_running) {
                Canvas c=null;
                Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setAntiAlias(true);
                try {
                    c=draw_thread_surface_holder.lockCanvas();
                    if(null!=c) {
//                        for(Bitmap b:bl) {
//                            c.drawBitmap(b,0.0f,0.0f,p);
//                        }
                        c.drawBitmap(bl.get(i),0.0f,0.0f,p);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (c != null) {
                        draw_thread_surface_holder.unlockCanvasAndPost(c);
                    }
                }
                ++i;
                i=i%bl.size();
            }
        }
    }
}
