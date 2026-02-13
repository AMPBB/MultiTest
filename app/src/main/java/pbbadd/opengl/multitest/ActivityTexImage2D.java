package pbbadd.opengl.multitest;

import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.text.Editable;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Objects;

import pbbadd.opengl.multitest.teximage2d.ActivityTexImage2DOnScreen;

public class ActivityTexImage2D extends AppCompatActivity {
    private static final String tag = "MultiTest";
    private LinearLayout linear_layout;
    private Button jump_to_tex_image_2d_on_screen;
    private TextInputEditText teximage2d_onscreen_width;
    private TextInputEditText teximage2d_onscreen_height;
    private TextInputEditText teximage2d_onscreen_glsurfaceview_cnt;
    private TextInputEditText teximage2d_onscreen_column;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_teximage2d);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        jump_to_tex_image_2d_on_screen=findViewById(R.id.button_test_tex_image2d_on_screen);
        teximage2d_onscreen_width=findViewById(R.id.textfield_teximage2d_onscreen_width);
        teximage2d_onscreen_height=findViewById(R.id.textfield_teximage2d_onscreen_height);
        teximage2d_onscreen_glsurfaceview_cnt=findViewById(R.id.textfield_teximage2d_onscreen_glsurfaceview_cnt);
        teximage2d_onscreen_column=findViewById(R.id.textfield_teximage2d_onscreen_column);

        jump_to_tex_image_2d_on_screen.setOnClickListener(v -> {
            Bundle params = new Bundle();
            if(Objects.requireNonNull(teximage2d_onscreen_width.getText()).toString().isEmpty()) {
                Toast.makeText(this,"width is empty",Toast.LENGTH_SHORT).show();
                return;
            }
            if(Objects.requireNonNull(teximage2d_onscreen_height.getText()).toString().isEmpty()) {
                Toast.makeText(this,"height is empty",Toast.LENGTH_SHORT).show();
                return;
            }
            if(Objects.requireNonNull(teximage2d_onscreen_glsurfaceview_cnt.getText()).toString().isEmpty()) {
                Toast.makeText(this,"cnt is empty",Toast.LENGTH_SHORT).show();
                return;
            }
            if(Objects.requireNonNull(teximage2d_onscreen_column.getText()).toString().isEmpty()) {
                Toast.makeText(this,"column is empty",Toast.LENGTH_SHORT).show();
                return;
            }
            int width = Integer.parseInt(teximage2d_onscreen_width.getText().toString());
            int height = Integer.parseInt(teximage2d_onscreen_height.getText().toString());
            int cnt=Integer.parseInt(teximage2d_onscreen_glsurfaceview_cnt.getText().toString());
            int column=Integer.parseInt(teximage2d_onscreen_column.getText().toString());
            params.putInt("width",width);
            params.putInt("height",height);
            params.putInt("cnt",cnt);
            params.putInt("column",column);
            Intent intent = new Intent(ActivityTexImage2D.this, ActivityTexImage2DOnScreen.class);
            intent.putExtras(params);
            startActivity(intent);
        });
    }

}