package pbbadd.opengl.multitest.gamesnacks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

import pbbadd.opengl.multitest.R;

public class GameSnacksActivity extends AppCompatActivity {
    private static final String TAG = "GameSnacksActivity";
    private static final String GAME_SNACKS_URL = "https://gamesnacks.com/";
    public static final String EXTRA_WIDTH_PERCENT = "gamesnacks_width_percent";
    public static final String EXTRA_HEIGHT_PERCENT = "gamesnacks_height_percent";
    private static final float DEFAULT_WEBVIEW_PERCENT = 1.0f;
    private static final boolean ENABLE_GPU_COMPOSITION_STRESS = true;
    private static final int GPU_COMPOSITION_STRESS_LAYER_COUNT = 1;
    private static final boolean ENABLE_GAME_SNACKS_FRAME_LIMIT = true;
    private static final float GAME_SNACKS_TARGET_FPS = 10.0f;

    private WebView gameSnacksWebView;
    private FrameLayout gpuCompositionStressContainer;
    private final Handler frameLimitHandler = new Handler(Looper.getMainLooper());
    private final Runnable injectFrameLimitRunnable = this::injectWebFrameRateLimiter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        gameMode();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_snacks);

        gameSnacksWebView = findViewById(R.id.gamesnacks_webview);
        gpuCompositionStressContainer = findViewById(R.id.gamesnacks_gpu_composition_stress_container);
        applyWebViewSizePercent();
        initWebView();
        initGpuCompositionStressLayers();
        requestLowDisplayFrameRate();
        loadGameSnacksUrl();
    }

    private void applyWebViewSizePercent() {
        float widthPercent = getClampedPercentExtra(EXTRA_WIDTH_PERCENT, DEFAULT_WEBVIEW_PERCENT);
        float heightPercent = getClampedPercentExtra(EXTRA_HEIGHT_PERCENT, DEFAULT_WEBVIEW_PERCENT);

        ConstraintLayout.LayoutParams params =
                (ConstraintLayout.LayoutParams) gameSnacksWebView.getLayoutParams();
        params.matchConstraintPercentWidth = widthPercent;
        params.matchConstraintPercentHeight = heightPercent;
        gameSnacksWebView.setLayoutParams(params);
        Log.d(TAG, "webview percent width=" + widthPercent + ", height=" + heightPercent);
    }

    private float getClampedPercentExtra(String name, float defaultValue) {
        float value = getIntent().getFloatExtra(name, defaultValue);
        if (value > 0.0f && value <= 1.0f) {
            return value;
        }
        return defaultValue;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebView.setWebContentsDebuggingEnabled(true);
        gameSnacksWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        gameSnacksWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageCommitVisible(WebView view, String url) {
                scheduleWebFrameRateLimiterInjection();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                scheduleWebFrameRateLimiterInjection();
            }
        });
        gameSnacksWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, consoleMessage.message() + " -- "
                        + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
                return true;
            }
        });

        WebSettings settings = gameSnacksWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setOffscreenPreRaster(true);
    }

    private void requestLowDisplayFrameRate() {
        if (!ENABLE_GAME_SNACKS_FRAME_LIMIT) {
            return;
        }

        if (trySetRequestedFrameRate(gameSnacksWebView, GAME_SNACKS_TARGET_FPS)) {
            return;
        }
        trySetFrameRate(gameSnacksWebView, GAME_SNACKS_TARGET_FPS);
    }

    private boolean trySetRequestedFrameRate(View view, float frameRate) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }

        try {
            Method method = View.class.getMethod("setRequestedFrameRate", float.class);
            method.invoke(view, frameRate);
            Log.d(TAG, "setRequestedFrameRate: " + frameRate);
            return true;
        } catch (Exception e) {
            Log.d(TAG, "setRequestedFrameRate is not available", e);
            return false;
        }
    }

    private boolean trySetFrameRate(View view, float frameRate) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }

        try {
            Method method = View.class.getMethod("setFrameRate", float.class, int.class);
            Class<?> surfaceClass = Class.forName("android.view.Surface");
            Field compatibilityField =
                    surfaceClass.getField("FRAME_RATE_COMPATIBILITY_FIXED_SOURCE");
            int compatibility = compatibilityField.getInt(null);
            method.invoke(view, frameRate, compatibility);
            Log.d(TAG, "setFrameRate: " + frameRate);
            return true;
        } catch (Exception e) {
            Log.d(TAG, "setFrameRate is not available", e);
            return false;
        }
    }

    private void scheduleWebFrameRateLimiterInjection() {
        if (!ENABLE_GAME_SNACKS_FRAME_LIMIT) {
            return;
        }

        frameLimitHandler.removeCallbacks(injectFrameLimitRunnable);
        injectWebFrameRateLimiter();
        frameLimitHandler.postDelayed(injectFrameLimitRunnable, 500);
        frameLimitHandler.postDelayed(injectFrameLimitRunnable, 1500);
        frameLimitHandler.postDelayed(injectFrameLimitRunnable, 3000);
    }

    private void injectWebFrameRateLimiter() {
        if (!ENABLE_GAME_SNACKS_FRAME_LIMIT || gameSnacksWebView == null) {
            return;
        }

        long frameIntervalMs = Math.max(1L, Math.round(1000.0f / GAME_SNACKS_TARGET_FPS));
        String script = buildFrameRateLimiterScript(frameIntervalMs);
        gameSnacksWebView.evaluateJavascript(script, null);
    }

    private String buildFrameRateLimiterScript(long frameIntervalMs) {
        return String.format(Locale.US,
                "(function(){" +
                        "var target=%d;" +
                        "if(window.__multitestFrameLimiterInstalled){" +
                        "window.__multitestFrameMs=target;" +
                        "console.log('[MultiTest] frame limiter updated to '+target+'ms');" +
                        "return;" +
                        "}" +
                        "window.__multitestFrameLimiterInstalled=true;" +
                        "window.__multitestFrameMs=target;" +
                        "var nativeRAF=window.requestAnimationFrame&&window.requestAnimationFrame.bind(window);" +
                        "var nativeCAF=window.cancelAnimationFrame&&window.cancelAnimationFrame.bind(window);" +
                        "var nativeSetTimeout=window.setTimeout.bind(window);" +
                        "var nativeClearTimeout=window.clearTimeout.bind(window);" +
                        "var nativeSetInterval=window.setInterval.bind(window);" +
                        "var nativeClearInterval=window.clearInterval.bind(window);" +
                        "var callbacks={};" +
                        "var nextId=1;" +
                        "var scheduled=false;" +
                        "var timerId=0;" +
                        "var lastFrameTime=0;" +
                        "function now(){" +
                        "return window.performance&&performance.now?performance.now():Date.now();" +
                        "}" +
                        "function clampDelay(delay){" +
                        "var value=Number(delay);" +
                        "if(!isFinite(value)||value<0){value=0;}" +
                        "return value<window.__multitestFrameMs?window.__multitestFrameMs:value;" +
                        "}" +
                        "function callCallback(cb,args){" +
                        "if(typeof cb==='function'){return cb.apply(window,args);}" +
                        "return (0,eval)(String(cb));" +
                        "}" +
                        "function hasCallbacks(){" +
                        "for(var id in callbacks){return true;}" +
                        "return false;" +
                        "}" +
                        "function schedule(delay){" +
                        "if(scheduled){return;}" +
                        "scheduled=true;" +
                        "timerId=nativeSetTimeout(function(){" +
                        "timerId=0;" +
                        "if(nativeRAF){nativeRAF(flush);}else{flush(now());}" +
                        "},delay||0);" +
                        "}" +
                        "function flush(timestamp){" +
                        "scheduled=false;" +
                        "var frameTime=timestamp||now();" +
                        "var remaining=window.__multitestFrameMs-(frameTime-lastFrameTime);" +
                        "if(lastFrameTime>0&&remaining>1){" +
                        "schedule(remaining);" +
                        "return;" +
                        "}" +
                        "lastFrameTime=frameTime;" +
                        "var pending=callbacks;" +
                        "callbacks={};" +
                        "Object.keys(pending).forEach(function(id){" +
                        "try{pending[id](frameTime);}catch(e){nativeSetTimeout(function(){throw e;},0);}" +
                        "});" +
                        "if(hasCallbacks()){schedule(window.__multitestFrameMs);}" +
                        "}" +
                        "window.requestAnimationFrame=function(cb){" +
                        "var id=nextId++;" +
                        "callbacks[id]=cb;" +
                        "schedule(0);" +
                        "return id;" +
                        "};" +
                        "window.cancelAnimationFrame=function(id){" +
                        "delete callbacks[id];" +
                        "if(!hasCallbacks()&&timerId){nativeClearTimeout(timerId);scheduled=false;timerId=0;}" +
                        "if(nativeCAF){try{nativeCAF(id);}catch(e){}}" +
                        "};" +
                        "window.setTimeout=function(cb,delay){" +
                        "var args=Array.prototype.slice.call(arguments,2);" +
                        "return nativeSetTimeout(function(){callCallback(cb,args);},clampDelay(delay));" +
                        "};" +
                        "window.clearTimeout=function(id){nativeClearTimeout(id);};" +
                        "window.setInterval=function(cb,delay){" +
                        "var args=Array.prototype.slice.call(arguments,2);" +
                        "return nativeSetInterval(function(){callCallback(cb,args);},clampDelay(delay));" +
                        "};" +
                        "window.clearInterval=function(id){nativeClearInterval(id);};" +
                        "console.log('[MultiTest] frame limiter installed: '+target+'ms');" +
                        "})();",
                frameIntervalMs);
    }

    private void initGpuCompositionStressLayers() {
        if (!ENABLE_GPU_COMPOSITION_STRESS) {
            return;
        }

        int size = dpToPx(8);
        int margin = dpToPx(2);
        for (int i = 0; i < GPU_COMPOSITION_STRESS_LAYER_COUNT; i++) {
            CompositionStressSurfaceView surfaceView = new CompositionStressSurfaceView(this, i);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
            params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
            params.rightMargin = margin + (i % 8) * (size + margin);
            params.bottomMargin = margin + (i / 8) * (size + margin);
            gpuCompositionStressContainer.addView(surfaceView, params);
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void loadGameSnacksUrl() {
        gameSnacksWebView.loadUrl(GAME_SNACKS_URL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveMode();
        gameSnacksWebView.onResume();
        gameSnacksWebView.resumeTimers();
        requestLowDisplayFrameRate();
        scheduleWebFrameRateLimiterInjection();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    @Override
    protected void onPause() {
        gameSnacksWebView.onPause();
        gameSnacksWebView.pauseTimers();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        frameLimitHandler.removeCallbacks(injectFrameLimitRunnable);
        gameSnacksWebView.destroy();
        super.onDestroy();
    }

    private void gameMode() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        applyImmersiveMode();
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private static class CompositionStressSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int layerIndex;

        CompositionStressSurfaceView(Context context, int layerIndex) {
            super(context);
            this.layerIndex = layerIndex;
            setZOrderOnTop(true);
            setClickable(false);
            setFocusable(false);
            setAlpha(0.08f);
            getHolder().setFormat(PixelFormat.TRANSLUCENT);
            getHolder().addCallback(this);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            drawStressLayer(holder);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            drawStressLayer(holder);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
        }

        private void drawStressLayer(SurfaceHolder holder) {
            Canvas canvas = holder.lockCanvas();
            if (canvas == null) {
                return;
            }

            try {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                int red = (40 + layerIndex * 37) & 0xff;
                int green = (120 + layerIndex * 29) & 0xff;
                int blue = (220 + layerIndex * 19) & 0xff;
                paint.setColor(Color.argb(64, red, green, blue));
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), paint);
            } finally {
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }
}
