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

    class GLWallpaperEngine extends Engine {
        private HandlerThread drawThread;
        private Handler drawHandler;
        private Bitmap mBitmap;
        private boolean mVisible = false;
        private boolean mRunning = false;

        private final long FRAME_DELAY_MS = 33;
        private int cnt = 0;

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            // 提前加载图片
            mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.wallpaper_color_horizental);
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

        private float mOffsetX = 0f;
        private float mOffsetY = 0f;
        @Override
        public void onOffsetsChanged(float xOffset, float yOffset,
                                     float xStep, float yStep,
                                     int xPixels, int yPixels) {
            super.onOffsetsChanged(xOffset, yOffset, xStep, yStep, xPixels, yPixels);
            mOffsetX = xOffset;
            mOffsetY = yOffset;
        }

        private final Runnable mDrawRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mVisible || !mRunning) return;

                SurfaceHolder holder = getSurfaceHolder();
                Canvas canvas = null;

                try {
                    canvas = holder.lockCanvas();
                    if (canvas != null) {
                        canvas.drawColor(Color.BLACK);

                        if (mBitmap != null && !mBitmap.isRecycled()) {
                            // 壁纸跟随桌面滚动，不拉伸、不适应屏幕
                            float scrollX = (canvas.getWidth() - mBitmap.getWidth()) * mOffsetX;
                            float scrollY = (canvas.getHeight() - mBitmap.getHeight()) * mOffsetY;
                            canvas.drawBitmap(mBitmap, scrollX, scrollY, null);
                        }

                        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        paint.setTextSize(64);
                        canvas.drawText("cnt: " + cnt, 200, 200, paint);
                        cnt++;
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

                if (drawHandler != null && mRunning) {
                    drawHandler.postDelayed(this, FRAME_DELAY_MS);
                }
            }
        };

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