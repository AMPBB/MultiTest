package pbbadd.opengl.multitest.resizableview;

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
        // 加载 drawable 中的 PNG 图片
        if(bg_res ==null) {
            bg_res = BitmapFactory.decodeResource(getResources(), R.drawable.resizable_view_res);
            if (bg_res != null) {
                // 转换为 RGBA 像素数据
                bg_pixels = new byte[bg_res.getWidth() * bg_res.getHeight() * 4];
                bg_res.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(bg_pixels));
                nativeOnSurfaceCreated(bg_res.getWidth(), bg_res.getHeight());
                // 传递像素数据到 Native
                nativeSetTextureData(bg_pixels, bg_res.getWidth(), bg_res.getHeight());
                nativeOnDrawFrame();
            }
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int vw, int vh) {
        nativeOnSurfaceChanged(vw, vh);
        Log.d(tag,"surface changed, vw="+vw+",vh="+vh);
    }

    boolean is_first_draw=false;
    @Override
    public void onDrawFrame(GL10 gl) {
//        nativeOnDrawFrame();
        if(!is_first_draw) {
            onDrawOnlyTriangle();
//            is_first_draw=true;
        }
    }

    public void manualDrawFrame() {
        nativeOnDrawFrame();
    }
}
