package pbbadd.opengl.multitest.teximage2d;

import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pbbadd.opengl.multitest.R;

public class ActivityTexImage2DOnScreen extends AppCompatActivity {

    private String log_tag="teximage2donscreen";
    private int texture_width;
    private int texture_height;
    private int glsfv_cnt;
    private TextView textview_width;
    private TextView textview_height;
    private TextView textview_glsfv_cnt;
    private TextView textview_column;
    private Button teximage2d_onscreen_control;
    private boolean is_resumed =true;

    private GridLayout glsfv_container;
    private int glsfv_container_column;
    private List<GLSurfaceView> glsfv_list;
    private List<TexImage2DRendererOnScreen> render_list;
    private Map<GLSurfaceView, TexImage2DRendererOnScreen> map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_tex_image_2d_on_screen);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Bundle params = getIntent().getExtras();
        if(params!=null) {
            texture_width =params.getInt("width");
            texture_height =params.getInt("height");
            glsfv_cnt=params.getInt("cnt");
            glsfv_container_column=params.getInt("column");
        }
        teximage2d_onscreen_control=findViewById(R.id.teximage2d_onscreen_control);
        textview_width=findViewById(R.id.textview_teximage2d_onscreen_width);
        textview_height=findViewById(R.id.textview_teximage2d_onscreen_height);
        textview_glsfv_cnt=findViewById(R.id.textview_teximage2d_onscreen_glsfv_cnt);
        textview_column=findViewById(R.id.textview_teximage2d_onscreen_glsfv_column);
        String sw="w:"+String.format(Locale.US,"%d", texture_width);
        textview_width.setText(sw);
        String sh="h:"+String.format(Locale.US,"%d", texture_height);
        textview_height.setText(sh);
        String scnt="cnt:"+String.format(Locale.US,"%d",glsfv_cnt);
        textview_glsfv_cnt.setText(scnt);
        String scolumn="column:"+String.format(Locale.US,"%d",glsfv_container_column);
        textview_column.setText(scolumn);
        glsfv_container=findViewById(R.id.glsfv_container);

        createGlsfvRender();

        teximage2d_onscreen_control.setOnClickListener(v->{
            if(is_resumed) {
                teximage2d_onscreen_control.setClickable(false);
                is_resumed =false;
                onPauseGlsfvRender();
                teximage2d_onscreen_control.setText("paused");
                teximage2d_onscreen_control.setClickable(true);
            } else {
                teximage2d_onscreen_control.setClickable(false);
                onResumeGlsfvRender();
                is_resumed =true;
                teximage2d_onscreen_control.setText("resumed");
                teximage2d_onscreen_control.setClickable(true);
            }
        });
    }

    private void onPauseGlsfvRender() {
        for(GLSurfaceView _glsfv:glsfv_list) {
            if(null!=_glsfv) {
                _glsfv.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            }
        }
        for(TexImage2DRendererOnScreen _render:render_list) {
            if(null!=_render) {
                _render.onPause();
            }
        }
    }

    private void onResumeGlsfvRender() {
        for(TexImage2DRendererOnScreen _render:render_list) {
            if(null!=_render) {
                _render.onResume();
            }
        }
        for(GLSurfaceView _glsfv:glsfv_list) {
            if(null!=_glsfv) {
                _glsfv.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            }
        }
    }

    private void createGlsfvRender() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenWidth = metrics.widthPixels;
        int size_calculated=(screenWidth-glsfv_container_column*2-2)/glsfv_container_column;
        if(size_calculated<=0) size_calculated=1;
        glsfv_container.setColumnCount(glsfv_container_column);

        glsfv_list=new ArrayList<>();

        render_list=new ArrayList<>();
        for(int i=0;i<glsfv_cnt;++i) {
            GLSurfaceView _glsfv_add=new GLSurfaceView(this);
            _glsfv_add.setEGLContextClientVersion(3);
            TexImage2DRendererOnScreen _render_add=new TexImage2DRendererOnScreen((texture_width -i)>0?(texture_width -i):1,(texture_height -i)>0?(texture_height -i):1);
            _glsfv_add.setRenderer(_render_add);
            _glsfv_add.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
            glsfv_list.add(_glsfv_add);
            render_list.add(_render_add);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width=size_calculated;
            params.height=size_calculated;
            params.setMargins(1,1,1,1);
            _glsfv_add.setLayoutParams(params);
            glsfv_container.addView(_glsfv_add);
        }
    }

    private void destroyGlsfvRender() {
        onPauseGlsfvRender();
        onStopRender();
        for(GLSurfaceView _glsfv:glsfv_list) {
            if(null!=_glsfv) {
                glsfv_container.removeView(_glsfv);
            }
        }
    }

    private void onStopRender() {
        for(TexImage2DRendererOnScreen _render:render_list) {
            if(null!=_render) {
                _render.onStop();
            }
        }
    }

    @Override
    protected void onResume() {
        Log.i(log_tag,"activity resume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(log_tag,"activity pause");
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        destroyGlsfvRender();
        super.onDestroy();
    }
}