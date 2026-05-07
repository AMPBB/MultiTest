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
    private ManualEGLView manual_egl_view;
    private EditText etWidth, etHeight;
    private Button apply;
    private Button start;
    private Button render_start;
    private Button make_another_context;
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
        find_id();
    }

    public void find_id() {
//        glSurfaceView = findViewById(R.id.gl_surface_view);
        etWidth = findViewById(R.id.et_width);
        etHeight = findViewById(R.id.et_height);
        apply=findViewById(R.id.btn_apply);
        apply_set();
        manual_egl_view=findViewById(R.id.manual_egl_view);
        start=findViewById(R.id.btn_start);
        start_set();
        render_start=findViewById(R.id.btn_render_start);
        render_start_set();
        make_another_context=findViewById(R.id.btn_make_another_context);
        make_another_context_set();
    }

    private boolean applying=false;
    public void apply_set() {
        // 按钮点击事件：动态修改 View 宽高
        apply.setOnClickListener(v -> {
            try {
                if(applying) {
                    Log.e(tag,"applying, wait!");
                    return;
                }
                applying=true;
                etWidth.clearFocus();
                etHeight.clearFocus();
                hideKeyboard();

                int width = Integer.parseInt(etWidth.getText().toString());
                int height = Integer.parseInt(etHeight.getText().toString());
                manual_egl_view_update(width,height);
                manual_egl_view.set_need_recreate(20);

                applying=false;
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
    private void manual_egl_view_update(int w,int h) {
        Log.d(tag,"requestLayout start");
        manual_egl_view.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        manual_egl_view.requestLayout();
        Log.d(tag,"requestLayout done");
    }
    public boolean start_value=false;
    public Thread start_thread;
    public void start_set() {
        start.setOnClickListener(v->{
            start.setClickable(false);
            if(start_value==false) {
                start.setText("stop");
                if (start_thread == null) {
                    start_thread = new Thread(() -> {
                        int wait=0;
                        while(!start_value) {
                            ++wait;
                            if(wait>=2000) {
                                Log.d(tag,"wait too much time");
                            }
                        };
                        int width_c=200;
                        while(start_value) {
                            final int width_c_f=width_c;
                            runOnUiThread(()->{
                                manual_egl_view.set_need_recreate();
                                manual_egl_view_update(width_c_f, 400);
                            });
                            current_w=width_c_f;
                            current_h=400;
                            try {
                                Thread.sleep(16);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            width_c+=20;
                            if(width_c>800) {
                                width_c=200;
                            }
                        }
                    },"resize-s");
                    start_thread.start();
                    start_value = true;
                    Log.d(tag,"start done");
                }
            } else {
                start.setText("start");
                start_value = false;
                //no join
                start_thread=null;
                Log.d(tag,"stop done");
            }
            start.setClickable(true);
        });
    }

    private boolean render_start_value=false;
    private int current_w=256;
    private int current_h=256;
    public void render_start_set() {
        render_start.setOnClickListener(v-> {
            render_start.setClickable(false);
            if(false==render_start_value) {
                render_start.setText("render stop");
                manual_egl_view.render_start(current_w,current_h);
                render_start_value=true;
            } else {
                render_start_value=false;
                manual_egl_view.render_stop();
                render_start.setText("render start");
            }
            render_start.setClickable(true);
        });
    }

    public void make_another_context_set() {
        make_another_context.setOnClickListener(v->{
            manual_egl_view.make_another_egl_init();
            manual_egl_view.make_another_context();
        });
    }
}