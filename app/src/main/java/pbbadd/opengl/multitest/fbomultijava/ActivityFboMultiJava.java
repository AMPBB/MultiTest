package pbbadd.opengl.multitest.fbomultijava;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import pbbadd.opengl.multitest.R;

public class ActivityFboMultiJava extends AppCompatActivity {

    private FboMultiViewJava fbo_multi_view_java;
    private final String tag="ActivityFboMultiViewJava";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_fbo_multi_java);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        find_id();
    }

    private void find_id() {
        fbo_multi_view_java=findViewById(R.id.fbo_multi_view_java);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fbo_multi_view_java.startRender();
    }

    @Override
    protected void onPause() {
        super.onPause();
        fbo_multi_view_java.stopRender();
    }
}