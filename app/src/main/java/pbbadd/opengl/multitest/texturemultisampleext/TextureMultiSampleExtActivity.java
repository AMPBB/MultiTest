package pbbadd.opengl.multitest.texturemultisampleext;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import pbbadd.opengl.multitest.R;

public class TextureMultiSampleExtActivity extends AppCompatActivity {
    private TextureMultiSampleExtView textureMultiSampleExtView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_texture_multi_sample_ext);

        TextView infoText = findViewById(R.id.text_texture_multi_sample_ext_info);
        textureMultiSampleExtView = findViewById(R.id.view_texture_multi_sample_ext);
        textureMultiSampleExtView.setResultListener(result ->
                runOnUiThread(() -> infoText.setText(result)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        textureMultiSampleExtView.startRendering();
    }

    @Override
    protected void onPause() {
        textureMultiSampleExtView.pauseRendering();
        super.onPause();
    }
}
