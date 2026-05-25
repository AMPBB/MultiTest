package pbbadd.opengl.multitest.doublesurfacefbo;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

public class DoubleSurfaceFboView extends SurfaceView implements SurfaceHolder.Callback {

    private Surface mSurface;
    private SurfaceReadyListener mListener;

    public interface SurfaceReadyListener {
        void onSurfaceReady(DoubleSurfaceFboView view, Surface surface, int width, int height);
        void onSurfaceDestroyed(DoubleSurfaceFboView view);
    }

    public DoubleSurfaceFboView(Context context) {
        super(context);
        init();
    }

    public DoubleSurfaceFboView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        getHolder().addCallback(this);
    }

    public void setSurfaceReadyListener(SurfaceReadyListener listener) {
        mListener = listener;
    }

    public Surface getSurface() {
        return mSurface;
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        mSurface = holder.getSurface();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        mSurface = holder.getSurface();
        if (mListener != null) {
            mListener.onSurfaceReady(this, mSurface, width, height);
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        if (mListener != null) {
            mListener.onSurfaceDestroyed(this);
        }
        mSurface = null;
    }
}
