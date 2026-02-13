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

import pbbadd.opengl.multitest.egl.ActivityEGL;
import pbbadd.opengl.multitest.textureview.ActivityTextureview;

public class MainActivity extends AppCompatActivity {
    private static final String log_tag = "main";
    private Button jump_to_tex_image_2d;

    private Button jump_to_textureview=null;
    private Button jump_to_egl=null;

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
}