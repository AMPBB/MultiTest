package pbbadd.opengl.multitest.cube3dmultifbo;

import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class Cube3DMultiFboRender  implements GLSurfaceView.Renderer {
    private final String tag="cube3d multi fbo render";
    public static native void onSurfaceCreated();
    public static native void onSurfaceChanged(int width, int height);
    public static native void onDrawFrame();
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // 初始化 Native 渲染
        onSurfaceCreated();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        // 通知 Native 窗口大小
        onSurfaceChanged(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // 每帧绘制
        onDrawFrame();
    }
}
