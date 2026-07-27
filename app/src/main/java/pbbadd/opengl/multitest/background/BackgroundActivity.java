package pbbadd.opengl.multitest.background;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import pbbadd.opengl.multitest.R;

public class BackgroundActivity extends AppCompatActivity {
    private static final int DEFAULT_TEXTURE_WIDTH = 1024;
    private static final int DEFAULT_TEXTURE_HEIGHT = 1024;
    private static final int DEFAULT_BUFFER_BYTES = 4 * 1024 * 1024;
    private static final int DEFAULT_THREAD_COUNT = 4;
    private static final int MAX_THREAD_COUNT = 32;

    private EditText textureWidthInput;
    private EditText textureHeightInput;
    private EditText bufferBytesInput;
    private EditText threadCountInput;
    private TextView infoText;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateStatusRunnable = new Runnable() {
        @Override
        public void run() {
            updateStatusText();
            statusHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_background);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        textureWidthInput = findViewById(R.id.edit_background_texture_width);
        textureHeightInput = findViewById(R.id.edit_background_texture_height);
        bufferBytesInput = findViewById(R.id.edit_background_buffer_bytes);
        threadCountInput = findViewById(R.id.edit_background_thread_count);
        infoText = findViewById(R.id.text_background_info);
        Button startButton = findViewById(R.id.button_background_start);
        Button stopButton = findViewById(R.id.button_background_stop);

        startButton.setOnClickListener(v -> startBackgroundLoad());
        stopButton.setOnClickListener(v -> {
            infoText.setText("stopping...");
            BackgroundGlLoadService.stopLoad(this);
        });
        updateStatusText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusHandler.post(updateStatusRunnable);
    }

    @Override
    protected void onPause() {
        statusHandler.removeCallbacks(updateStatusRunnable);
        super.onPause();
    }

    private void startBackgroundLoad() {
        int textureWidth = parsePositiveInt(textureWidthInput, "texture width",
                DEFAULT_TEXTURE_WIDTH);
        int textureHeight = parsePositiveInt(textureHeightInput, "texture height",
                DEFAULT_TEXTURE_HEIGHT);
        int bufferBytes = parsePositiveInt(bufferBytesInput, "buffer bytes",
                DEFAULT_BUFFER_BYTES);
        int threadCount = parsePositiveInt(threadCountInput, "thread count",
                DEFAULT_THREAD_COUNT);
        if (textureWidth <= 0 || textureHeight <= 0 || bufferBytes <= 0 || threadCount <= 0) {
            return;
        }
        if (threadCount > MAX_THREAD_COUNT) {
            Toast.makeText(this, "thread count must be <= " + MAX_THREAD_COUNT,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        long textureBytes = (long) textureWidth * textureHeight * 4L;
        if (textureBytes > Integer.MAX_VALUE) {
            Toast.makeText(this, "texture data is too large for Java ByteBuffer",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        infoText.setText("starting...");
        BackgroundGlLoadService.startLoad(this, textureWidth, textureHeight, bufferBytes,
                threadCount);
    }

    private int parsePositiveInt(EditText editText, String name, int defaultValue) {
        Editable editable = editText.getText();
        String text = editable == null ? "" : editable.toString().trim();
        if (text.isEmpty()) {
            return defaultValue;
        }

        try {
            int value = Integer.parseInt(text);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }

        Toast.makeText(this, name + " is invalid", Toast.LENGTH_SHORT).show();
        return -1;
    }

    private void updateStatusText() {
        infoText.setText(BackgroundGlLoadService.getSnapshot().toDisplayText());
    }
}
