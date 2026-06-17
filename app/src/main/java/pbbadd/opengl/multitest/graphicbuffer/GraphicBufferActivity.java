package pbbadd.opengl.multitest.graphicbuffer;

import android.hardware.HardwareBuffer;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import pbbadd.opengl.multitest.R;

public class GraphicBufferActivity extends AppCompatActivity {
    private static final String tag = "graphicbuffer";

    private EditText edit_width;
    private EditText edit_height;
    private TextView text_info;
    private HardwareBuffer graphic_buffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.graphic_buffer_layout);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        edit_width = findViewById(R.id.edit_graphic_buffer_width);
        edit_height = findViewById(R.id.edit_graphic_buffer_height);
        text_info = findViewById(R.id.text_graphic_buffer_info);
        Button start = findViewById(R.id.button_graphic_buffer_start);
        Button destroy = findViewById(R.id.button_graphic_buffer_destroy);

        start.setOnClickListener(v -> createGraphicBuffer());
        destroy.setOnClickListener(v -> destroyGraphicBuffer());
    }

    private void createGraphicBuffer() {
        if (graphic_buffer != null && !graphic_buffer.isClosed()) {
            text_info.setText("graphicbuffer already created");
            Log.d(tag, "graphicbuffer already created");
            return;
        }

        int width = parseSize(edit_width);
        int height = parseSize(edit_height);
        if (width <= 0 || height <= 0) {
            text_info.setText("create failed");
            Log.e(tag, "invalid size, width=" + width + ", height=" + height);
            return;
        }

        try {
            graphic_buffer = HardwareBuffer.create(
                    width,
                    height,
                    HardwareBuffer.RGBA_8888,
                    1,
                    HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT);
            text_info.setText("create success");
            Log.d(tag, "create success, width=" + width + ", height=" + height);
        } catch (RuntimeException e) {
            graphic_buffer = null;
            text_info.setText("create failed");
            Log.e(tag, "create failed, width=" + width + ", height=" + height, e);
        }
    }

    private void destroyGraphicBuffer() {
        if (graphic_buffer == null || graphic_buffer.isClosed()) {
            graphic_buffer = null;
            text_info.setText("destroy failed");
            Log.e(tag, "destroy failed, graphicbuffer is null");
            return;
        }

        graphic_buffer.close();
        graphic_buffer = null;
        text_info.setText("destroy success");
        Log.d(tag, "destroy success");
    }

    @Override
    protected void onDestroy() {
        if (graphic_buffer != null && !graphic_buffer.isClosed()) {
            graphic_buffer.close();
            graphic_buffer = null;
        }
        super.onDestroy();
    }

    private int parseSize(EditText editText) {
        String value = editText.getText().toString().trim();
        if (value.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
