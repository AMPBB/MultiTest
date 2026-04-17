package pbbadd.opengl.multitest.wallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Objects;

import pbbadd.opengl.multitest.R;

public class WallpaperService extends android.service.wallpaper.WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new WallpaperEngine();
    }

    class WallpaperEngine extends Engine {
        private HandlerThread drawThread;
        private Handler drawHandler;
        private Bitmap mBitmap;
        private boolean mVisible = false;
        private boolean mRunning = false;

        private final long FRAME_DELAY_MS = 33;
        private int cnt = 0;
        private Paint paint;
        private Rect src;
        private RectF dst;
        private RectF dst_offset;
        private Bitmap bm_cache;
        private Canvas cv_cache;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            // 提前加载图片
            mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.wallpaper_color_horizental);
            src = new Rect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
            paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTextSize(128);
            paint.setColor(Color.RED);     // 红色更显眼
            paint.setFakeBoldText(true);   // 加粗
            bm_cache=Bitmap.createBitmap(mBitmap.getWidth(),mBitmap.getHeight(),Bitmap.Config.ARGB_8888);
            cv_cache=new Canvas(bm_cache);
            dst=new RectF(0, 0, cv_cache.getWidth(), cv_cache.getHeight());
            dst_offset=new RectF(0, 0, cv_cache.getWidth(), cv_cache.getHeight());
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            mVisible = visible;
            if (visible) {
                startRender();
            } else {
                stopRender();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            stopRender();
            if (mBitmap != null) {
                mBitmap.recycle();
                mBitmap = null;
            }
        }

        private boolean offsets_changed=false;
        private float mOffsetX = 0f;
        private float mOffsetY = 0f;
        @Override
        public void onOffsetsChanged(float xOffset, float yOffset,
                                     float xStep, float yStep,
                                     int xPixels, int yPixels) {
            super.onOffsetsChanged(xOffset, yOffset, xStep, yStep, xPixels, yPixels);
            mOffsetX = xOffset;
            mOffsetY = yOffset;
            offsets_changed=true;
        }

        private final Runnable mDrawRunnable = new Runnable() {
            @Override
            public void run() {
                while(mRunning||mVisible) {
                    drawFrame();
                }
            }
        };

        private float scrollY = 0f;
        private final float SCROLL_SPEED = 1.0f;

        SurfaceHolder holder = getSurfaceHolder();
        Canvas canvas = null;
        private void drawFrame() {
            //draw in cache
            cv_cache.drawColor(0xffffffff);
            dst_offset.top = scrollY;
            dst_offset.bottom = scrollY + cv_cache.getHeight();
            cv_cache.drawBitmap(mBitmap,src,dst_offset,paint);
            scrollY -= SCROLL_SPEED;
            if (scrollY < -cv_cache.getHeight()) {
                scrollY = 0;
            }
            cv_cache.drawText("cnt: " + cnt, 200, 200, paint);
            cnt++;
            if(offsets_changed) {
                cv_cache.drawText("offset x: "+mOffsetX,200,360,paint);
                offsets_changed=false;
            }

            //draw in canvas
            try {
                canvas = holder.lockCanvas();
                if (canvas != null && mBitmap != null && !mBitmap.isRecycled()) {
                    canvas.drawBitmap(bm_cache, src, dst, paint);
                    Trace.beginSection("pbb add,"+cnt);
                    Trace.endSection();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (Exception ignored) {}
                }
            }
        }

        private void startRender() {
            if (mRunning) return;

            mRunning = true;
            drawThread = new HandlerThread("WallpaperDraw");
            drawThread.start();
            drawHandler = new Handler(drawThread.getLooper());
            drawHandler.post(mDrawRunnable);
        }

        private void stopRender() {
            if (!mRunning) return;

            mRunning = false;
            if (drawHandler != null) {
                drawHandler.removeCallbacks(mDrawRunnable);
                drawHandler = null;
            }
            if (drawThread != null) {
                drawThread.quitSafely();
                try {
                    drawThread.join();
                } catch (InterruptedException ignored) {}
                drawThread = null;
            }
        }
    }
}