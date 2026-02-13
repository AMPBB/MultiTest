package pbbadd.opengl.multitest.textureview;

import android.graphics.SurfaceTexture;
import android.view.TextureView;

import androidx.annotation.NonNull;

public class SurfaceTextureListenerEnhanced implements TextureView.SurfaceTextureListener {

    private TextureviewRenderThread t_r_t;
    private SurfaceInfo sfi;
    public SurfaceTextureListenerEnhanced(TextureviewRenderThread trt) {
        t_r_t =trt;
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture sf, int w, int h) {
        sfi=new SurfaceInfo(sf,w,h);
        t_r_t.add_surface_info(sfi);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture sf) {
        t_r_t.del_surface_info(sfi);
        sf.release();
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture sf, int w, int h) {

    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture sf) {

    }
}
