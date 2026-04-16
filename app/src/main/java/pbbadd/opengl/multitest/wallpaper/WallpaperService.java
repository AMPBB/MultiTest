package pbbadd.opengl.multitest.wallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import pbbadd.opengl.multitest.R;

public class WallpaperService extends android.service.wallpaper.WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new GLWallpaperEngine();
    }

    class GLWallpaperEngine extends Engine implements SurfaceHolder.Callback {
        private HandlerThread drawThread;
        private Handler drawHandler;
        private boolean isVisible = true;
        private Bitmap mBitmap;

        // 绘制间隔（故意设短，更容易触发退帧闪烁）
        private final long FRAME_DELAY_MS = 30;

        public GLWallpaperEngine() {
            // 加载一张大图，让纹理上传耗时明显
            mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.c_bg_xxxxhd);
            getSurfaceHolder().addCallback(this);
        }

        private int cnt=0;
        private final Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isVisible) return;

                SurfaceHolder holder = getSurfaceHolder();
                Canvas canvas = null;
                Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
                try {
                    // 关键：lockCanvas 会触发系统准备纹理
                    canvas = holder.lockCanvas();
                    if (canvas != null) {
                        // 清屏
                        canvas.drawColor(Color.BLACK);
                        // 全屏绘制 bitmap
                        if (mBitmap != null && !mBitmap.isRecycled()) {
                            canvas.drawBitmap(mBitmap, 0, 0, null);
                            canvas.drawText(""+cnt,64,64,p);
                        }
                    }
                } finally {
                    if (canvas != null) {
                        // unlockCanvas 触发：
                        // 1. 数据提交
                        // 2. glTexImage2D / glTexSubImage2D 纹理上传
                        holder.unlockCanvasAndPost(canvas);
                    }
                }

                // 循环下一帧
                drawHandler.postDelayed(this, FRAME_DELAY_MS);
            }
        };

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            startDrawThread();
        }

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {

        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

        }

        private void startDrawThread() {
            stopDrawThread();

            drawThread = new HandlerThread("WallpaperDrawThread");
            drawThread.start();
            drawHandler = new Handler(drawThread.getLooper());
            drawHandler.post(drawRunnable);
        }

        private void stopDrawThread() {
            if (drawHandler != null) {
                drawHandler.removeCallbacks(drawRunnable);
                drawHandler = null;
            }
            if (drawThread != null) {
                drawThread.quitSafely();
                try {
                    drawThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                drawThread = null;
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            isVisible = visible;
            if (visible) {
                startDrawThread();
            } else {
                stopDrawThread();
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            stopDrawThread();
            if (mBitmap != null) {
                mBitmap.recycle();
                mBitmap = null;
            }
        }

    }
}
