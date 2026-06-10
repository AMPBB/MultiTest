package pbbadd.opengl.multitest.textureview;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLSurface;


public class SurfaceInfo {
    public SurfaceTexture surfaceTexture;
    public EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    public int w;
    public int h;

    public SurfaceInfo(SurfaceTexture st, int w, int h) {
        surfaceTexture = st;
        this.w = w;
        this.h = h;
    }
}
