package pbbadd.opengl.multitest.glgetintegerv;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import pbbadd.opengl.multitest.R;

public class GlGetIntegervActivity extends AppCompatActivity {
    private GlGetIntegervView glGetIntegervView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gl_get_integerv);

        TextView infoText = findViewById(R.id.text_gl_get_integerv_info);
        glGetIntegervView = findViewById(R.id.view_gl_get_integerv);
        glGetIntegervView.setResultListener(result ->
                runOnUiThread(() -> infoText.setText(result)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        glGetIntegervView.onResume();
    }

    @Override
    protected void onPause() {
        glGetIntegervView.onPause();
        super.onPause();
    }
}
