package pbbadd.opengl.multitest.surfaceview;

import android.app.Activity;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import pbbadd.opengl.multitest.R;

public class ActivitySurfaceView extends Activity {

    public static Resources resource=null;

    public CanvasSurfaceView canvas_surface_view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        resource = getResources();

        canvas_surface_view=new CanvasSurfaceView(this);
        setContentView(canvas_surface_view);

//        EdgeToEdge.enable(this);
//        setContentView(R.layout.activity_surface_view);
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        resource=null;
    }
}