package pbbadd.opengl.multitest.hugeteximage2d;

import android.os.Bundle;
import android.text.Editable;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;

import pbbadd.opengl.multitest.R;

public class HugeTexActivity extends AppCompatActivity {

    private TextInputEditText widthInput;
    private TextInputEditText heightInput;
    private Button startButton;
    private Button stopButton;
    private TextView infoText;
    private HugeTexView hugeTexView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_huge_tex);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findId();
        initListener();
    }

    private void findId() {
        widthInput = findViewById(R.id.textfield_huge_tex_width);
        heightInput = findViewById(R.id.textfield_huge_tex_height);
        startButton = findViewById(R.id.button_huge_tex_start);
        stopButton = findViewById(R.id.button_huge_tex_stop);
        infoText = findViewById(R.id.textview_huge_tex_info);
        hugeTexView = findViewById(R.id.huge_tex_view);
    }

    private void initListener() {
        hugeTexView.setStatusListener(new HugeTexView.StatusListener() {
            @Override
            public void onTextureCreated(int width, int height, long byteSize) {
                double mb = byteSize / 1024.0 / 1024.0;
                String info = String.format(Locale.US, "texture: %d x %d, %.2f MB", width, height, mb);
                infoText.setText(info);
            }

            @Override
            public void onTextureDestroyed() {
                infoText.setText("destroyed");
            }

            @Override
            public void onError(String message) {
                infoText.setText(message);
            }
        });

        startButton.setOnClickListener(v -> {
            int width = parseInput(widthInput, "width");
            int height = parseInput(heightInput, "height");
            if (width <= 0 || height <= 0) {
                return;
            }
            infoText.setText("creating...");
            hugeTexView.startTexture(width, height);
        });

        stopButton.setOnClickListener(v -> hugeTexView.stopTexture(true));
    }

    private int parseInput(TextInputEditText editText, String name) {
        Editable editable = editText.getText();
        String text = editable == null ? "" : editable.toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, name + " is empty", Toast.LENGTH_SHORT).show();
            return -1;
        }
        try {
            int value = Integer.parseInt(text);
            if (value <= 0) {
                Toast.makeText(this, name + " must be > 0", Toast.LENGTH_SHORT).show();
                return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            Toast.makeText(this, name + " is invalid", Toast.LENGTH_SHORT).show();
            return -1;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hugeTexView.onResume();
    }

    @Override
    protected void onPause() {
        hugeTexView.stopTexture(false);
        hugeTexView.onPause();
        super.onPause();
    }
}
