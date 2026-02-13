package pbbadd.opengl.multitest.egl;

import android.opengl.EGL14;
import android.opengl.EGL15;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLES30;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


import java.util.Locale;

import javax.microedition.khronos.egl.EGL11;

import pbbadd.opengl.multitest.R;

public class ActivityEGL extends AppCompatActivity {
    private String tag="activityegl";

    private EGL14 egl;
    private EGLDisplay egl_display;
    private EGLContext egl_context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_egl);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        egl_init();
    }

    private void egl_init() {
        egl_display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if(egl_display==EGL14.EGL_NO_DISPLAY) {
            Log.e(tag,"EGL_NO_DISPLAY");
            return;
        }
        int[] egl_version_major=new int[2];
        int[] elg_version_minor=new int[2];
        if(!EGL14.eglInitialize(egl_display,egl_version_major,0,elg_version_minor,0)) {
            Log.e(tag,"version error");
            return;
        }
        String s_egl_version = String.format(Locale.US,"%d.%d",egl_version_major[0], elg_version_minor[0]);
        Log.d(tag,"egl version,"+s_egl_version);
        int[] egl_config_attribute = {
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_RENDERABLE_TYPE,EGL15.EGL_OPENGL_ES3_BIT,
                EGL14.EGL_NONE
        };

        EGLConfig[] egl_configs = new EGLConfig[1];
        int[] egl_config_cnt = new int[1];
        if(!EGL14.eglChooseConfig(egl_display, egl_config_attribute,0, egl_configs, 0,1,egl_config_cnt,0)) {
            return;
        } else {
            Log.d(tag,"config cnt="+egl_config_cnt[0]);
        }

        int[] egl_context_attribute = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
        };
        egl_context = EGL14.eglCreateContext(egl_display, egl_configs[0], EGL14.EGL_NO_CONTEXT, egl_context_attribute,0);
        if (egl_context == EGL14.EGL_NO_CONTEXT) {
            return;
        }

        if (!EGL14.eglMakeCurrent(egl_display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, egl_context)) {
            return;
        }

        Log.d(tag, "init done");
    }
}