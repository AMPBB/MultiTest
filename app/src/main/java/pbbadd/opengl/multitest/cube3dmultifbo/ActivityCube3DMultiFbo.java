package pbbadd.opengl.multitest.cube3dmultifbo;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import pbbadd.opengl.multitest.R;

public class ActivityCube3DMultiFbo extends AppCompatActivity {
    private final String tag="activity cube3d multi fbo";
    private Cube3DMultiFboView cube3d_multi_fbo_view;

    static {
        System.loadLibrary("gles30testdemo");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
//        setContentView(R.layout.activity_cube3_dmulti_fbo);
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });
//        find_id();

//        cube3d_multi_fbo_view=new Cube3DMultiFboView(this);
//        setContentView(cube3d_multi_fbo_view);

        cube3d_multi_fbo_view=new Cube3DMultiFboView(this);
        FrameLayout layout = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                800,  // 宽度
                800,  // 高度
                FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY
        );
        params.setMargins(0, 100, 0, 0);
        layout.addView(cube3d_multi_fbo_view, params);
        setContentView(layout);
    }

    private void find_id() {
        cube3d_multi_fbo_view=findViewById(R.id.cube3d_multi_fbo_view);
    }

    @Override
    protected void onPause() {
        super.onPause();
        cube3d_multi_fbo_view.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cube3d_multi_fbo_view.onResume();
    }
}