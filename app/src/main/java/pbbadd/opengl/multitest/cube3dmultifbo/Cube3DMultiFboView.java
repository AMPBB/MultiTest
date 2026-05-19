package pbbadd.opengl.multitest.cube3dmultifbo;

import android.content.Context;
import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class Cube3DMultiFboView extends GLSurfaceView {
    private final String tag="cube3d multi fbo view";
    private final Cube3DMultiFboRender renderer;

    public Cube3DMultiFboView(Context context) {
        super(context);
        // 使用 OpenGL ES 3.0
        setEGLContextClientVersion(3);
        // 设置渲染器
        renderer = new Cube3DMultiFboRender();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }
}
