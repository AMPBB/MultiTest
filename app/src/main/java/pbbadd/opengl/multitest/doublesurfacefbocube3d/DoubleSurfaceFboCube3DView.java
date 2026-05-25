package pbbadd.opengl.multitest.doublesurfacefbocube3d;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

public class DoubleSurfaceFboCube3DView extends SurfaceView implements SurfaceHolder.Callback {

    private Surface mSurface;
    private SurfaceReadyListener mListener;

    public interface SurfaceReadyListener {
        void onSurfaceReady(DoubleSurfaceFboCube3DView view, Surface surface, int width, int height);
        void onSurfaceDestroyed(DoubleSurfaceFboCube3DView view);
    }

    public DoubleSurfaceFboCube3DView(Context context) {
        super(context);
        init();
    }

    public DoubleSurfaceFboCube3DView(Context context, AttributeSet attrs) {
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
