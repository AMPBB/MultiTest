package pbbadd.opengl.multitest;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import pbbadd.opengl.multitest.background.BackgroundActivity;
import pbbadd.opengl.multitest.cube3d.Cube3DActivity;
import pbbadd.opengl.multitest.cube3dmultifbo.ActivityCube3DMultiFbo;
import pbbadd.opengl.multitest.doublesurfacefbo.DoubleSurfaceFboActivity;
import pbbadd.opengl.multitest.doublesurfacefbocube3d.DoubleSurfaceFboCube3DActivity;
import pbbadd.opengl.multitest.egl.ActivityEGL;
import pbbadd.opengl.multitest.fbomulti.ActivityFboMulti;
import pbbadd.opengl.multitest.fbomultijava.ActivityFboMultiJava;
import pbbadd.opengl.multitest.gamesnacks.GameSnacksActivity;
import pbbadd.opengl.multitest.gpufontrasterizer.GpuFontRasterizerActivity;
import pbbadd.opengl.multitest.gpufontrasterizerreplay.GpuFontRasterizerReplayActivity;
import pbbadd.opengl.multitest.graphicbuffer.GraphicBufferActivity;
import pbbadd.opengl.multitest.hugeteximage2d.HugeTexActivity;
import pbbadd.opengl.multitest.pbuffer.ActivityPbufferDemo;
import pbbadd.opengl.multitest.resizeableview.ActivityResizeableView;
import pbbadd.opengl.multitest.surfaceegl.ActivitySurfaceEgl;
import pbbadd.opengl.multitest.surfacenocache.ActivitySurfaceNoCache;
import pbbadd.opengl.multitest.surfaceusecache.ActivitySurfaceUseCache;
import pbbadd.opengl.multitest.surfaceviewchoregrapher.ActivitySurfaceViewChoreographer;
import pbbadd.opengl.multitest.textattrib.TextAttribPointerActivity;
import pbbadd.opengl.multitest.textureview.ActivityTextureview;
import pbbadd.opengl.multitest.wallpaper.ActivityWallpaper;

public class MainActivity extends AppCompatActivity {
    private static final String log_tag = "main";
    private Button jump_to_tex_image_2d;
    private Button jump_to_huge_tex_image_2d;
    private Button jump_to_gamesnacks;
    private Button jump_to_text_attrib_pointer;
    private Button jump_to_gpu_font_rasterizer;
    private Button jump_to_gpu_font_rasterizer_replay;
    private EditText edit_gamesnacks_width_percent;
    private EditText edit_gamesnacks_height_percent;

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
    private Button jump_to_pbuffer;
    private Button jump_to_graphic_buffer;
    private Button jump_to_background;

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
        init_jump_to_huge_tex_image_2d();
        init_jump_to_gamesnacks();
        init_jump_to_text_attrib_pointer();
        init_jump_to_gpu_font_rasterizer();
        init_jump_to_gpu_font_rasterizer_replay();

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
        init_jump_to_pbuffer();
        init_jump_to_graphic_buffer();
        init_jump_to_background();
    }

    private void init_jump_to_text_attrib_pointer() {
        jump_to_text_attrib_pointer=findViewById(R.id.button_jump_to_text_attrib_pointer);
        jump_to_text_attrib_pointer.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, TextAttribPointerActivity.class);
            Log.d(log_tag,"jump to text attrib pointer");
            startActivity(intent);
        });
    }

    private void init_jump_to_gpu_font_rasterizer() {
        jump_to_gpu_font_rasterizer=findViewById(R.id.button_jump_to_gpu_font_rasterizer);
        jump_to_gpu_font_rasterizer.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, GpuFontRasterizerActivity.class);
            Log.d(log_tag,"jump to gpu font rasterizer");
            startActivity(intent);
        });
    }

    private void init_jump_to_gpu_font_rasterizer_replay() {
        jump_to_gpu_font_rasterizer_replay=findViewById(R.id.button_jump_to_gpu_font_rasterizer_replay);
        jump_to_gpu_font_rasterizer_replay.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, GpuFontRasterizerReplayActivity.class);
            Log.d(log_tag,"jump to gpu font rasterizer replay");
            startActivity(intent);
        });
    }

    private void init_jump_to_gamesnacks() {
        jump_to_gamesnacks=findViewById(R.id.button_jump_to_gamesnacks);
        edit_gamesnacks_width_percent=findViewById(R.id.edit_gamesnacks_width_percent);
        edit_gamesnacks_height_percent=findViewById(R.id.edit_gamesnacks_height_percent);
        jump_to_gamesnacks.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, GameSnacksActivity.class);
            float widthPercent = parsePercent(edit_gamesnacks_width_percent, 0.7f);
            float heightPercent = parsePercent(edit_gamesnacks_height_percent, 0.7f);
            intent.putExtra(GameSnacksActivity.EXTRA_WIDTH_PERCENT, widthPercent);
            intent.putExtra(GameSnacksActivity.EXTRA_HEIGHT_PERCENT, heightPercent);
            Log.d(log_tag,"jump to gamesnacks");
            startActivity(intent);
        });
    }

    private float parsePercent(EditText editText, float defaultValue) {
        try {
            float value = Float.parseFloat(editText.getText().toString().trim());
            if (value > 0.0f && value <= 1.0f) {
                return value;
            }
        } catch (NumberFormatException e) {
            Log.d(log_tag, "invalid gamesnacks percent: " + editText.getText(), e);
        }
        return defaultValue;
    }

    private void init_jump_to_huge_tex_image_2d() {
        jump_to_huge_tex_image_2d=findViewById(R.id.button_jump_to_huge_tex_image_2d);
        jump_to_huge_tex_image_2d.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, HugeTexActivity.class);
            Log.d(log_tag,"jump to huge teximage2d");
            startActivity(intent);
        });
    }

    private void init_jump_to_graphic_buffer() {
        jump_to_graphic_buffer=findViewById(R.id.button_jump_to_graphic_buffer);
        jump_to_graphic_buffer.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, GraphicBufferActivity.class);
            Log.d(log_tag,"jump to graphic buffer");
            startActivity(intent);
        });
    }

    private void init_jump_to_background() {
        jump_to_background=findViewById(R.id.button_jump_to_background);
        jump_to_background.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, BackgroundActivity.class);
            Log.d(log_tag,"jump to background gl load");
            startActivity(intent);
        });
    }

    private void init_jump_to_pbuffer() {
        jump_to_pbuffer=findViewById(R.id.button_jump_to_pbuffer);
        jump_to_pbuffer.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, ActivityPbufferDemo.class);
            Log.d(log_tag,"jump to pbuffer");
            startActivity(intent);
        });
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
