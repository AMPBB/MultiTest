package pbbadd.opengl.multitest.texsubimageformat;

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

import pbbadd.opengl.multitest.R;

public class TexSubImageFormatActivity extends AppCompatActivity {
    private TextInputEditText loopInput;
    private Button startButton;
    private Button stopButton;
    private TextView infoText;
    private TexSubImageFormatView texSubImageFormatView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_tex_sub_image_format);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findId();
        initListener();
    }

    private void findId() {
        loopInput = findViewById(R.id.textfield_tex_sub_image_format_loop_count);
        startButton = findViewById(R.id.button_tex_sub_image_format_start);
        stopButton = findViewById(R.id.button_tex_sub_image_format_stop);
        infoText = findViewById(R.id.textview_tex_sub_image_format_info);
        texSubImageFormatView = findViewById(R.id.tex_sub_image_format_view);
    }

    private void initListener() {
        texSubImageFormatView.setStatusListener(status -> infoText.setText(status));

        startButton.setOnClickListener(v -> {
            int loopCount = parseInput(loopInput, "loop count");
            if (loopCount <= 0) {
                return;
            }
            infoText.setText("running...");
            texSubImageFormatView.startTest(loopCount);
        });

        stopButton.setOnClickListener(v -> texSubImageFormatView.stopTest(true));
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
        texSubImageFormatView.onResume();
    }

    @Override
    protected void onPause() {
        texSubImageFormatView.stopTest(false);
        texSubImageFormatView.onPause();
        super.onPause();
    }
}
