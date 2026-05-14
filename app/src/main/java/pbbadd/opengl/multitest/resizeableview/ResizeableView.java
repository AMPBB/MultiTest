package pbbadd.opengl.multitest.resizeableview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import pbbadd.opengl.multitest.R;

public class ResizeableView extends GLSurfaceView implements GLSurfaceView.Renderer {
    public native void nativeOnSurfaceCreated(int pw,int ph);
    public native void nativeOnSurfaceChanged(int width, int height);
    public native void nativeOnSurfaceChangedAndUpdate(int vw, int vh,byte[] pixels,int pw,int ph);
    public native void nativeOnDrawFrame();
    public native void onDrawOnlyTriangle();
    public native void nativeSetTextureData(byte[] pixels, int width, int height);
    public native void recreateSurface(Object surface);
    private final String tag="resizeable view";

    public ResizeableView(Context context) {
        this(context, null);
    }

    public ResizeableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2); // 使用 OpenGL ES 2.0

        // ++++++++++ 新增这 2 行，强制背景透明，不盖住画面 ++++++++++
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        // +++++++++++++++++++++++++++++++++++++++++++++++++++++++
        setRenderer(this);
        setRenderMode(RENDERMODE_WHEN_DIRTY); // 按需渲染
    }

    Bitmap bg_res;
    byte[] bg_pixels;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        if(bg_res ==null) {
            bg_res = BitmapFactory.decodeResource(getResources(), R.drawable.resizable_view_res);
            Log.d(tag,"bg create");
            if (bg_res != null) {
                Log.d(tag,"create");
                bg_pixels = new byte[bg_res.getWidth() * bg_res.getHeight() * 4];
                bg_res.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(bg_pixels));
                nativeOnSurfaceCreated(bg_res.getWidth(), bg_res.getHeight());
                nativeSetTextureData(bg_pixels, bg_res.getWidth(), bg_res.getHeight());
            }
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int vw, int vh) {
        nativeOnSurfaceChanged(vw, vh);
        Log.d(tag,"surface changed, vw="+vw+",vh="+vh);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        nativeOnDrawFrame();
    }
}
