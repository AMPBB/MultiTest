package pbbadd.opengl.multitest;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Objects;

import pbbadd.opengl.multitest.cube3d.Cube3DActivity;
import pbbadd.opengl.multitest.egl.ActivityEGL;
import pbbadd.opengl.multitest.surfaceview.ActivitySurfaceView;
import pbbadd.opengl.multitest.textureview.ActivityTextureview;
import pbbadd.opengl.multitest.wallpaper.ActivityWallpaper;

public class MainActivity extends AppCompatActivity {
    private static final String log_tag = "main";
    private Button jump_to_tex_image_2d;

    private Button jump_to_textureview=null;
    private Button jump_to_egl=null;
    private Button jump_to_cube3d=null;

    private Button jump_to_surface_view=null;

    private Button jump_to_wallpaper=null;

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
        init_jump_to_surface_view();
        init_jump_to_wallpaper();
    }

    private void init_jump_to_textureview() {
        jump_to_textureview=findViewById(R.id.button_jump_to_textureview);
        jump_to_textureview.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, ActivityTextureview.class);
            Log.i(log_tag,"jump to textureview activity");
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

    private void init_jump_to_cube3d() {
        jump_to_cube3d=findViewById(R.id.button_jump_to_cube3d);
        jump_to_cube3d.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, Cube3DActivity.class);
            Log.d(log_tag,"jump to activity cube3d");
            startActivity(intent);
        });
    }

    private void init_jump_to_surface_view() {
        jump_to_surface_view=findViewById(R.id.button_jump_to_surface_view);
        jump_to_surface_view.setOnClickListener(v->{
            Intent intent=new Intent(MainActivity.this, ActivitySurfaceView.class);
            Log.d(log_tag,"jump to activity surface view");
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
}