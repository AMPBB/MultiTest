package pbbadd.opengl.multitest.cube3d;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.FrameLayout;

public class Cube3DActivity extends Activity {
    private GLSurfaceView glSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 创建GLSurfaceView
        glSurfaceView = new GLSurfaceView(this);

        // 设置OpenGL ES 2.0上下文
        glSurfaceView.setEGLContextClientVersion(2);

        // 设置渲染器
        glSurfaceView.setRenderer(new CubeRenderer());
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // 创建布局容器
        FrameLayout layout = new FrameLayout(this);

        // 创建布局参数，固定为800x800像素
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                800,  // 宽度
                800,  // 高度
                FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY
        );

        // 设置边距使其居中
        params.setMargins(0, 100, 0, 0);

        // 添加GLSurfaceView到布局
        layout.addView(glSurfaceView, params);

        // 设置布局
        setContentView(layout);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) {
            glSurfaceView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glSurfaceView != null) {
            glSurfaceView.onResume();
        }
    }
}
