package pbbadd.opengl.multitest.fbomulti;

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
import pbbadd.opengl.multitest.resizableview.ManualEGLView;
import pbbadd.opengl.multitest.resizableview.ResizeableView;

public class ActivityFboMulti extends AppCompatActivity {

    private FboMultiView fbo_multi_view;
    private Button render_start;
    private final String tag="ActivityFboMultiView";

    static {
        System.loadLibrary("gles30testdemo");
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_fbo_multi);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        find_id();

        render_start.setClickable(false);
        render_start.setText("render stop");
        fbo_multi_view.render_start();
        render_start_value=true;
        render_start.setClickable(true);
    }

    public void find_id() {
        fbo_multi_view=findViewById(R.id.fbo_multi_view);
        render_start=findViewById(R.id.btn_render_start);
        render_start_set();
    }

    private boolean render_start_value=false;
    public void render_start_set() {
        render_start.setOnClickListener(v-> {
            render_start.setClickable(false);
            if(!render_start_value) {
                render_start.setText("render stop");
                fbo_multi_view.render_start();
                render_start_value=true;
            } else {
                render_start_value=false;
                fbo_multi_view.render_stop();
                render_start.setText("render start");
            }
            render_start.setClickable(true);
        });
    }
}