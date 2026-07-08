package pbbadd.opengl.multitest.gamesnacks;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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

    private WebView gameSnacksWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_game_snacks);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        gameSnacksWebView = findViewById(R.id.gamesnacks_webview);
        initWebView();
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
}
