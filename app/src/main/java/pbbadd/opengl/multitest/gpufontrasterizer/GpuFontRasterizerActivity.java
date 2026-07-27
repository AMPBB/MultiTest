package pbbadd.opengl.multitest.gpufontrasterizer;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import pbbadd.opengl.multitest.R;

public class GpuFontRasterizerActivity extends AppCompatActivity {
    private static final String TAG = "GpuFontRasterizer";
    private static final String GPU_FONT_RASTERIZER_URL =
            "file:///android_asset/gpu_font_rasterizer.html";

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
        setContentView(R.layout.activity_gpu_font_rasterizer);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        webView = findViewById(R.id.gpu_font_rasterizer_webview);
        initWebView();
        webView.loadUrl(GPU_FONT_RASTERIZER_URL);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebView.setWebContentsDebuggingEnabled(true);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, consoleMessage.message() + " -- "
                        + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
                return true;
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setTextZoom(100);
        settings.setOffscreenPreRaster(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        webView.resumeTimers();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        webView.pauseTimers();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
