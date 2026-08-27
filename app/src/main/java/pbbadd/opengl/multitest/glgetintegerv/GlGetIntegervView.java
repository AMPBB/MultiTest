package pbbadd.opengl.multitest.glgetintegerv;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GlGetIntegervView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private static final String TAG = "GlGetIntegerv";

    private ResultListener resultListener;

    public GlGetIntegervView(Context context) {
        this(context, null);
    }

    public GlGetIntegervView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void setResultListener(ResultListener resultListener) {
        this.resultListener = resultListener;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        StringBuilder builder = new StringBuilder();
        appendStringValue(builder, "GL_VENDOR", GLES20.GL_VENDOR);
        appendStringValue(builder, "GL_RENDERER", GLES20.GL_RENDERER);
        appendStringValue(builder, "GL_VERSION", GLES20.GL_VERSION);
        appendIntValue(builder, "GL_MAX_VERTEX_ATTRIBS", GLES20.GL_MAX_VERTEX_ATTRIBS);
        appendIntValue(builder, "GL_MAX_VERTEX_UNIFORM_VECTORS", GLES20.GL_MAX_VERTEX_UNIFORM_VECTORS);
        appendIntValue(builder, "GL_MAX_VARYING_VECTORS", GLES20.GL_MAX_VARYING_VECTORS);
        appendIntValue(builder, "GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS",
                GLES20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        appendIntValue(builder, "GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS",
                GLES20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS);
        appendIntValue(builder, "GL_MAX_TEXTURE_IMAGE_UNITS", GLES20.GL_MAX_TEXTURE_IMAGE_UNITS);
        appendIntValue(builder, "GL_MAX_FRAGMENT_UNIFORM_VECTORS",
                GLES20.GL_MAX_FRAGMENT_UNIFORM_VECTORS);
        appendIntValue(builder, "GL_MAX_RENDERBUFFER_SIZE", GLES20.GL_MAX_RENDERBUFFER_SIZE);

        String result = builder.toString();
        Log.i(TAG, "\n" + result);
        if (resultListener != null) {
            resultListener.onResult(result);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }

    private void appendIntValue(StringBuilder builder, String name, int pname) {
        int[] values = new int[1];
        GLES20.glGetIntegerv(pname, values, 0);
        builder.append(name).append(" = ").append(values[0]).append('\n');
    }

    private void appendStringValue(StringBuilder builder, String name, int pname) {
        builder.append(name).append(" = ").append(GLES20.glGetString(pname)).append('\n');
    }

    public interface ResultListener {
        void onResult(String result);
    }
}
