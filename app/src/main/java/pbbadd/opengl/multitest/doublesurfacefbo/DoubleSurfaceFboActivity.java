package pbbadd.opengl.multitest.doublesurfacefbo;

import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import pbbadd.opengl.multitest.R;

public class DoubleSurfaceFboActivity extends AppCompatActivity
        implements DoubleSurfaceFboView.SurfaceReadyListener {

    private static final String TAG = "DoubleSurfaceFboActivity";

    private DoubleSurfaceFboView mViewA;
    private DoubleSurfaceFboView mViewB;
    private DoubleSurfaceFboRenderThread mRenderThread;

    private Surface mSurfaceA;
    private Surface mSurfaceB;
    private int mWidthA, mHeightA;
    private int mWidthB, mHeightB;
    private boolean mSurfaceAReady = false;
    private boolean mSurfaceBReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.double_surface_fbo);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mViewA = findViewById(R.id.double_surface_view_a);
        mViewB = findViewById(R.id.double_surface_view_b);
        mViewA.setSurfaceReadyListener(this);
        mViewB.setSurfaceReadyListener(this);
    }

    @Override
    public void onSurfaceReady(DoubleSurfaceFboView view, Surface surface, int width, int height) {
        if (view == mViewA) {
            mSurfaceA = surface;
            mWidthA = width;
            mHeightA = height;
            mSurfaceAReady = true;
            Log.d(TAG, "surface A ready: " + width + "x" + height);
        } else if (view == mViewB) {
            mSurfaceB = surface;
            mWidthB = width;
            mHeightB = height;
            mSurfaceBReady = true;
            Log.d(TAG, "surface B ready: " + width + "x" + height);
        }

        if (mSurfaceAReady && mSurfaceBReady && mRenderThread == null
                && mWidthA > 0 && mHeightA > 0 && mWidthB > 0 && mHeightB > 0) {
            startRenderThread();
        }
    }

    @Override
    public void onSurfaceDestroyed(DoubleSurfaceFboView view) {
        if (view == mViewA) {
            mSurfaceAReady = false;
            mSurfaceA = null;
        } else if (view == mViewB) {
            mSurfaceBReady = false;
            mSurfaceB = null;
        }
    }

    private void startRenderThread() {
        mRenderThread = new DoubleSurfaceFboRenderThread(this);
        mRenderThread.setSurfaceInfoA(mSurfaceA, mWidthA, mHeightA);
        mRenderThread.setSurfaceInfoB(mSurfaceB, mWidthB, mHeightB);
        mRenderThread.start();
        mRenderThread.startRendering();
        Log.d(TAG, "render thread started");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mRenderThread != null) {
            mRenderThread.startRendering();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mRenderThread != null) {
            mRenderThread.stopRendering();
        }
    }

    @Override
    protected void onDestroy() {
        if (mRenderThread != null) {
            mRenderThread.stopThread();
            mRenderThread = null;
        }
        super.onDestroy();
    }
}
