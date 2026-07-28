package pbbadd.opengl.multitest.gpufontrasterizerreplay;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

import pbbadd.opengl.multitest.R;

public class GpuFontRasterizerReplayActivity extends AppCompatActivity {
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 1;
    private static final String TEXTURE_PATH = "/data/texture";
    private static final String BUFFERDATA_PATH = "/data/bufferdata";
    private static final String NO_FILE_PERMISSION_MESSAGE =
            "\u6ca1\u6709\u6587\u4ef6\u6743\u9650\uff0c\u65e0\u6cd5\u8bfb\u53d6 "
                    + TEXTURE_PATH + " \u548c " + BUFFERDATA_PATH;
    private static final String REQUEST_ALL_FILES_ACCESS_MESSAGE =
            "\u8bf7\u5728\u6253\u5f00\u7684\u7cfb\u7edf\u9875\u9762\u4e2d\u6388\u4e88\u6240\u6709\u6587\u4ef6\u8bbf\u95ee\u6743\u9650";
    private static final String FILE_NOT_FOUND_PREFIX =
            "\u6587\u4ef6\u4e0d\u5b58\u5728\uff1a";
    private static final String READ_FILE_FAILED_PREFIX =
            "\u8bfb\u53d6\u6587\u4ef6\u5931\u8d25\uff1a";

    private GpuFontRasterizerReplayView replayView;
    private TextView infoText;
    private boolean startedManageAllFilesAccessRequest;
    private boolean reloadAfterPermissionRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpu_font_rasterizer_replay);

        replayView = findViewById(R.id.gpu_font_rasterizer_replay_view);
        infoText = findViewById(R.id.text_replay_info);
        CheckBox fitCheckBox = findViewById(R.id.checkbox_fit_to_bounds);

        replayView.setFitToBounds(fitCheckBox.isChecked());
        fitCheckBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                replayView.setFitToBounds(isChecked));
        loadReplayDataOrShowMessage(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        replayView.onResume();
        if (reloadAfterPermissionRequest) {
            reloadAfterPermissionRequest = false;
            loadReplayDataOrShowMessage(false);
        }
    }

    @Override
    protected void onPause() {
        replayView.onPause();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE) {
            loadReplayDataOrShowMessage(false);
        }
    }

    private void loadReplayDataOrShowMessage(boolean autoRequestPermission) {
        File textureFile = new File(TEXTURE_PATH);
        File bufferDataFile = new File(BUFFERDATA_PATH);

        try {
            if (!textureFile.exists()) {
                showLongMessage(FILE_NOT_FOUND_PREFIX + TEXTURE_PATH);
                return;
            }
            if (!bufferDataFile.exists()) {
                showLongMessage(FILE_NOT_FOUND_PREFIX + BUFFERDATA_PATH);
                return;
            }

            GpuFontRasterizerReplayView.ReplayData replayData =
                    GpuFontRasterizerReplayView.loadReplayData(textureFile, bufferDataFile);
            replayView.setReplayData(replayData);
            infoText.setText(replayData.infoText);
        } catch (FileNotFoundException e) {
            if (isPermissionDenied(e)) {
                showNoPermissionAndRequest(autoRequestPermission);
            } else {
                showLongMessage(READ_FILE_FAILED_PREFIX + e.getMessage());
            }
        } catch (SecurityException e) {
            showNoPermissionAndRequest(autoRequestPermission);
        } catch (IOException e) {
            if (isPermissionDenied(e)) {
                showNoPermissionAndRequest(autoRequestPermission);
            } else {
                showLongMessage(READ_FILE_FAILED_PREFIX + e.getMessage());
            }
        }
    }

    private void showNoPermissionAndRequest(boolean autoRequestPermission) {
        showLongMessage(NO_FILE_PERMISSION_MESSAGE);
        if (autoRequestPermission) {
            requestFilePermission();
        }
    }

    private boolean isPermissionDenied(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lowerMessage = message.toLowerCase(Locale.US);
        return lowerMessage.contains("permission denied") || lowerMessage.contains("eacces");
    }

    private void requestFilePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestManageAllFilesAccessPermission();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_READ_EXTERNAL_STORAGE);
        }
    }

    private void requestManageAllFilesAccessPermission() {
        if (startedManageAllFilesAccessRequest) {
            return;
        }
        startedManageAllFilesAccessRequest = true;
        showLongMessage(REQUEST_ALL_FILES_ACCESS_MESSAGE);

        Intent appAllFilesIntent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
        appAllFilesIntent.setData(Uri.parse("package:" + getPackageName()));
        if (startActivityIfAvailable(appAllFilesIntent)) {
            return;
        }

        Intent allFilesIntent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        if (startActivityIfAvailable(allFilesIntent)) {
            return;
        }

        Intent appDetailsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        appDetailsIntent.setData(Uri.parse("package:" + getPackageName()));
        if (startActivityIfAvailable(appDetailsIntent)) {
            return;
        }

        startedManageAllFilesAccessRequest = false;
        reloadAfterPermissionRequest = false;
        showLongMessage(NO_FILE_PERMISSION_MESSAGE);
    }

    private boolean startActivityIfAvailable(Intent intent) {
        if (intent.resolveActivity(getPackageManager()) == null) {
            return false;
        }
        try {
            reloadAfterPermissionRequest = true;
            startActivity(intent);
            return true;
        } catch (Exception e) {
            reloadAfterPermissionRequest = false;
            return false;
        }
    }

    private void showLongMessage(String message) {
        infoText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
