package pbbadd.opengl.multitest;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import pbbadd.opengl.multitest.cube3d.Cube3DActivity;
import pbbadd.opengl.multitest.cube3dmultifbo.ActivityCube3DMultiFbo;
import pbbadd.opengl.multitest.doublesurfacefbo.DoubleSurfaceFboActivity;
import pbbadd.opengl.multitest.doublesurfacefbocube3d.DoubleSurfaceFboCube3DActivity;
import pbbadd.opengl.multitest.egl.ActivityEGL;
import pbbadd.opengl.multitest.fbomulti.ActivityFboMulti;
import pbbadd.opengl.multitest.fbomultijava.ActivityFboMultiJava;
import pbbadd.opengl.multitest.resizeableview.ActivityResizeableView;
import pbbadd.opengl.multitest.surfaceegl.ActivitySurfaceEgl;
import pbbadd.opengl.multitest.surfacenocache.ActivitySurfaceNoCache;
import pbbadd.opengl.multitest.surfaceusecache.ActivitySurfaceUseCache;
import pbbadd.opengl.multitest.surfaceviewchoregrapher.ActivitySurfaceViewChoreographer;
import pbbadd.opengl.multitest.textureview.ActivityTextureview;
import pbbadd.opengl.multitest.wallpaper.ActivityWallpaper;

public class MainActivity extends AppCompatActivity {
    private static final String log_tag = "main";
    private Button jump_to_tex_image_2d;

    private Button jump_to_textureview;
    private Button jump_to_egl;
    private Button jump_to_cube3d;

    private Button jump_to_surface_no_cache;
    private Button jump_to_surface_use_cache;
    private Button jump_to_surface_egl;
    private Button jump_to_surface_choreographer;

    private Button jump_to_wallpaper;
    private Button jump_to_resizeable_view;
    private Button jump_to_fbomulti_view;
    private Button jump_to_fbomulti_view_java;
    private Button jump_to_double_surface_fbo;
    private Button jump_to_cube3d_multi_fbo;
    private Button jump_to_double_surface_fbo_cube3d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        jump_to_tex_image_2d=findViewById(R.id.button_jump_to_tex_image_2d);
        jump_to_tex_image_2d.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ActivityTexImage2D.class);
            startActivity(intent);
        });

        init_jump_to_textureview();
        init_jump_to_egl();
        init_jump_to_cube3d();
        init_jump_to_surface_no_cache();
        init_jump_to_surface_use_cache();
        init_jump_to_surface_egl();
        init_jump_to_surface_view_choreographer();
        init_jump_to_wallpaper();
        init_jump_to_resizeable_view();
        init_jump_to_fbomulti_view();
        init_jump_to_fbomulti_view_java();
        init_jump_to_cube3d_multi_fbo();
        init_jump_to_double_surface_fbo();
        init_jump_to_double_surface_fbo_cube3d();
    }

    private void init_jump_to_double_surface_fbo() {
        jump_to_double_surface_fbo=findViewById(R.id.button_jump_to_double_surface_fbo);
        jump_to_double_surface_fbo.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, DoubleSurfaceFboActivity.class);
            Log.d(log_tag,"jump to double surface fbo");
            startActivity(intent);
        });
    }

    private void init_jump_to_double_surface_fbo_cube3d() {
        jump_to_double_surface_fbo_cube3d=findViewById(R.id.button_jump_to_double_surface_fbo_cube3d);
        jump_to_double_surface_fbo_cube3d.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, DoubleSurfaceFboCube3DActivity.class);
            Log.d(log_tag,"jump to double surface fbo cube3d");
            startActivity(intent);
        });
    }

    private void init_jump_to_cube3d_multi_fbo() {
        jump_to_cube3d_multi_fbo=findViewById(R.id.button_jump_to_cube3d_multi_fbo);
        jump_to_cube3d_multi_fbo.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, ActivityCube3DMultiFbo.class);
            Log.d(log_tag,"jump to cube3d multi fbo");
            startActivity(intent);
        });
    }

    private void init_jump_to_fbomulti_view_java() {
        jump_to_fbomulti_view_java=findViewById(R.id.button_jump_to_fbomulti_view_java);
        jump_to_fbomulti_view_java.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, ActivityFboMultiJava.class);
            Log.d(log_tag,"jump to resizeable view java");
            startActivity(intent);
        });
    }
    private void init_jump_to_fbomulti_view() {
        jump_to_fbomulti_view=findViewById(R.id.button_jump_to_fbomulti_view);
        jump_to_fbomulti_view.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, ActivityFboMulti.class);
            Log.d(log_tag,"jump to resizeable view");
            startActivity(intent);
        });
    }
    private void init_jump_to_resizeable_view() {
        jump_to_resizeable_view=findViewById(R.id.button_jump_to_resizeable_view);
        jump_to_resizeable_view.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, ActivityResizeableView.class);
            Log.d(log_tag,"jump to resizeable view");
            startActivity(intent);
        });
    }
    private void init_jump_to_wallpaper() {
        jump_to_wallpaper=findViewById(R.id.button_jump_to_wallpaper);
        jump_to_wallpaper.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, ActivityWallpaper.class);
            Log.d(log_tag,"jump to activity wallpaper");
            startActivity(intent);
        });
    }
    private void init_jump_to_surface_view_choreographer() {
        jump_to_surface_choreographer =findViewById(R.id.button_jump_to_surface_choreographer);
        jump_to_surface_choreographer.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, ActivitySurfaceViewChoreographer.class);
            Log.d(log_tag,"jump to activity surface view choreographer");
            startActivity(intent);
        });
    }
    private void init_jump_to_surface_egl() {
        jump_to_surface_egl =findViewById(R.id.button_jump_to_surface_egl);
        jump_to_surface_egl.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, ActivitySurfaceEgl.class);
            Log.d(log_tag,"jump to activity surface egl");
            startActivity(intent);
        });
    }
    private void init_jump_to_surface_use_cache() {
        jump_to_surface_use_cache =findViewById(R.id.button_jump_to_surface_use_cache);
        jump_to_surface_use_cache.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, ActivitySurfaceUseCache.class);
            Log.d(log_tag,"jump to activity surface use cache");
            startActivity(intent);
        });
    }
    private void init_jump_to_surface_no_cache() {
        jump_to_surface_no_cache =findViewById(R.id.button_jump_to_surface_no_cache);
        jump_to_surface_no_cache.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, ActivitySurfaceNoCache.class);
            Log.d(log_tag,"jump to activity surface no cache");
            startActivity(intent);
        });
    }
    private void init_jump_to_cube3d() {
        jump_to_cube3d=findViewById(R.id.button_jump_to_cube3d);
        jump_to_cube3d.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, Cube3DActivity.class);
            Log.d(log_tag,"jump to activity cube3d");
            startActivity(intent);
        });
    }
    private void init_jump_to_egl() {
        jump_to_egl=findViewById(R.id.button_jump_to_egl);
        jump_to_egl.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, ActivityEGL.class);
            Log.d(log_tag,"jump to activity egl");
            startActivity(intent);
        });
    }
    private void init_jump_to_textureview() {
        jump_to_textureview=findViewById(R.id.button_jump_to_textureview);
        jump_to_textureview.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, ActivityTextureview.class);
            Log.i(log_tag,"jump to textureview activity");
            startActivity(intent);
        });
    }
}