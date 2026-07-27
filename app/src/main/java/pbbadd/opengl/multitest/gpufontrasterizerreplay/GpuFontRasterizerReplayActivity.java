package pbbadd.opengl.multitest.gpufontrasterizerreplay;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import pbbadd.opengl.multitest.R;

public class GpuFontRasterizerReplayActivity extends AppCompatActivity {
    private GpuFontRasterizerReplayView replayView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpu_font_rasterizer_replay);

        replayView = findViewById(R.id.gpu_font_rasterizer_replay_view);
        TextView infoText = findViewById(R.id.text_replay_info);
        CheckBox fitCheckBox = findViewById(R.id.checkbox_fit_to_bounds);

        replayView.setFitToBounds(fitCheckBox.isChecked());
        infoText.setText(replayView.getReplayInfo());
        fitCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                replayView.setFitToBounds(isChecked));
    }

    @Override
    protected void onResume() {
        super.onResume();
        replayView.onResume();
    }

    @Override
    protected void onPause() {
        replayView.onPause();
        super.onPause();
    }
}
