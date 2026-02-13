package pbbadd.opengl.multitest.teximage2d;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class TexImage2DRendererOnScreen implements GLSurfaceView.Renderer {
    private static final String log_tag = "TexImage2DRenderer";
    private final int texWidth;  // 从Activity传递的纹理宽度
    private final int texHeight; // 从Activity传递的纹理高度

    private int draw_frame_interval_calculator=1;

    public TexImage2DRendererOnScreen(int width, int height) {
        this.texWidth = width;
        this.texHeight = height;
    }

    private boolean isSupportGLES30(String glVersion) {
        if (glVersion == null || glVersion.isEmpty()) {
            return false;
        }
        try {
            String versionCore = glVersion.replace("OpenGL ES ", "").split(" ")[0];
            String[] versionArr = versionCore.split("\\.");
            if (versionArr.length < 1) {
                return false;
            }
            int mainVersion = Integer.parseInt(versionArr[0]);
            return mainVersion >= 3;
        } catch (Exception e) {
            Log.w(log_tag, "gles version string parse failed" + glVersion, e);
            return false;
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        String glVersion = GLES30.glGetString(GLES30.GL_VERSION);
        Log.i(log_tag, "gles version" + glVersion);
        if (!isSupportGLES30(glVersion)) {
            Log.e(log_tag, "gles 3.0 not support!");
        } else {
            TestglTexImage2DSetSize(texWidth,texHeight);
            TestglTexImage2DInit();
            TestglTexSubImage2DUpdate();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        TestViewport(width,height);
        Log.i(log_tag,"pass through, width="+width+",height="+height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        TestglTexSubImage2DUpdate();
        ++draw_frame_interval_calculator;
//        if((draw_frame_interval_calculator%120)==0) {
//            Log.i(log_tag,"%d="+draw_frame_interval_calculator);
//        }
    }

    public void onStart() {
        TestglTexImage2DInit();
    }

    public void onStop() {
        TestglTexImage2DDeinit();
    }

    public void onPause() {
        TestglTexImage2DPause();
    }

    public void onResume() {
        TestglTexImage2DResume();
    }

    static {
        System.loadLibrary("gles30testdemo");
    }

    private native void TestViewport(int width,int height);

    private native void TestglTexImage2DSetSize(int width,int height);
    private native void TestglTexImage2DPause();
    private native void TestglTexImage2DResume();
    private native void TestglTexImage2DInit();
    private native void TestglTexSubImage2DUpdate();
    private native void TestglTexImage2DDeinit();
}