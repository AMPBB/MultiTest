package pbbadd.opengl.multitest.textattrib;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import pbbadd.opengl.multitest.R;

public class TextAttribPointerActivity extends AppCompatActivity {

    private TextAttribPointerView textAttribPointerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_text_attrib_pointer);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        textAttribPointerView = findViewById(R.id.text_attrib_pointer_view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        textAttribPointerView.onResume();
    }

    @Override
    protected void onPause() {
        textAttribPointerView.onPause();
        super.onPause();
    }
}
