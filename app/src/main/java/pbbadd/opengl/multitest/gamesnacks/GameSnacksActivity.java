package pbbadd.opengl.multitest.gamesnacks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import pbbadd.opengl.multitest.R;

public class GameSnacksActivity extends AppCompatActivity {
    private static final String TAG = "GameSnacksActivity";
    private static final String ASSET_HTML = "GameSnacks_Personalization_settings.html";
    private static final String BASE_URL = "https://gamesnacks.com/";
    private static final boolean ENABLE_GPU_COMPOSITION_STRESS = true;
    private static final int GPU_COMPOSITION_STRESS_LAYER_COUNT = 1;

    private WebView gameSnacksWebView;
    private FrameLayout gpuCompositionStressContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_game_snacks);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        gameSnacksWebView = findViewById(R.id.gamesnacks_webview);
        gpuCompositionStressContainer = findViewById(R.id.gamesnacks_gpu_composition_stress_container);
        initWebView();
        initGpuCompositionStressLayers();
        loadGameSnacksHtml();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebView.setWebContentsDebuggingEnabled(true);
        gameSnacksWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        gameSnacksWebView.setWebViewClient(new WebViewClient());
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
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setOffscreenPreRaster(true);
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

    private void loadGameSnacksHtml() {
        try {
            String html = readAssetText(ASSET_HTML);
            gameSnacksWebView.loadDataWithBaseURL(BASE_URL, html, "text/html", "UTF-8", BASE_URL);
        } catch (IOException e) {
            Log.e(TAG, "load asset failed", e);
            Toast.makeText(this, "load html failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String readAssetText(String assetName) throws IOException {
        try (InputStream inputStream = getAssets().open(assetName);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameSnacksWebView.onResume();
        gameSnacksWebView.resumeTimers();
    }

    @Override
    protected void onPause() {
        gameSnacksWebView.onPause();
        gameSnacksWebView.pauseTimers();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        gameSnacksWebView.destroy();
        super.onDestroy();
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
