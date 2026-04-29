package pbbadd.opengl.multitest.resizableview;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import pbbadd.opengl.multitest.R;

public class ActivityResizeableView extends AppCompatActivity {

    private ResizeableView glSurfaceView;
    private EditText etWidth, etHeight;
    private Button apply;
    private final String tag="ActivityResizeableView";

    static {
        System.loadLibrary("gles30testdemo");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_resizable_view);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        glSurfaceView = findViewById(R.id.gl_surface_view);
        etWidth = findViewById(R.id.et_width);
        etHeight = findViewById(R.id.et_height);
        apply=findViewById(R.id.btn_apply);

        // 按钮点击事件：动态修改 View 宽高
        apply.setOnClickListener(v -> {
            try {
                apply.setClickable(false);
                etWidth.clearFocus();
                etHeight.clearFocus();
                hideKeyboard();

                int width = Integer.parseInt(etWidth.getText().toString());
                int height = Integer.parseInt(etHeight.getText().toString());

                // 更新 OpenGL View 的布局宽高
                glSurfaceView.setLayoutParams(new LinearLayout.LayoutParams(width, height));
                glSurfaceView.requestLayout(); // 刷新布局
//                glSurfaceView.requestRender();
//                glSurfaceView.manualDrawFrame();
                apply.setClickable(true);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        });
    }

    // 关闭软键盘
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(etWidth.getWindowToken(), 0);
    }
}