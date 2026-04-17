package pbbadd.opengl.multitest.surfaceview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Trace;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import pbbadd.opengl.multitest.R;

public class CanvasSurfaceViewChoreographer extends SurfaceView implements SurfaceHolder.Callback{
    public SurfaceHolder surface_holder;

    public CanvasSurfaceViewChoreographer(Context c) {
        super(c);
        surface_holder=getHolder();
        surface_holder.addCallback(this);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public CanvasSurfaceViewChoreographer(Context c, AttributeSet a) {
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
    private SurfaceHolder sf_h;
    private boolean sf_h_running;
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        sf_h=holder;
        holder.setFormat(PixelFormat.RGBA_8888); //force gpu accelerate
        if(bg==null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false; // 关键：禁止系统缩放
            bg = BitmapFactory.decodeResource(ActivitySurfaceView.resource, R.drawable.c_bg_xxxxhd, options);

            Log.d("pbb add","bg w,h"+bg.getWidth()+","+bg.getHeight());
        }
        choreographerStart();
        sf_h_running=true;
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        sf_h_running=false;
        if (bg != null && !bg.isRecycled()) {
            bg.recycle();
            bg = null;
        }
    }

    public void choreographerStart() {
        SurfaceHolder draw_thread_surface_holder=sf_h;
        class DrawOneFrame {
            int loc;
            Paint p;
            Canvas c;
            boolean init=false;

            private DrawOneFrame() {
                if(!init) {
                    loc = bg.getWidth();
                    p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setAntiAlias(true);
                    p.setColor(0xff00ff00); //green
                    p.setTextSize(64);
                    init=true;
                }
            }
            private void drawFrame() {
                Bitmap tb=Bitmap.createBitmap(bg.getWidth(),bg.getHeight(),Bitmap.Config.ARGB_8888);
                Canvas tc=new Canvas(tb);
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
                Log.d("pbb add", "drawFrame: "+loc);

                try {
                    c=draw_thread_surface_holder.lockCanvas();
                    if(c!=null) {
                        c.drawColor(0xffffffff);
                        c.drawColor(0xff00ff00);
                        c.drawColor(0xff0000ff);
                        c.drawColor(0xffff0000);
                        c.drawBitmap(tb, null, rectf_bg, p);
                        Trace.beginSection("pbb add," + loc);
                        Trace.endSection();
                    }
                } catch (Exception e) {
                    Log.d("pbb add", Objects.requireNonNull(e.getMessage()));
                } finally {
                    if (c != null) {
                        draw_thread_surface_holder.unlockCanvasAndPost(c);
                        draw_thread_surface_holder.setKeepScreenOn(true);
                    }
                }
                loc-=64;
                if(loc<=0) {
                    loc=bg.getWidth();
                }
                tb.recycle();
            }
        }

        DrawOneFrame dof = new DrawOneFrame();
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (!sf_h_running) return;
                dof.drawFrame();
                // 继续下一帧
                Choreographer.getInstance().postFrameCallback(this);
            }
        });
    }
}
