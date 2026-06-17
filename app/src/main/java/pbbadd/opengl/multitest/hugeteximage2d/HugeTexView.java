package pbbadd.opengl.multitest.hugeteximage2d;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class HugeTexView extends GLSurfaceView {

    public interface StatusListener {
        void onTextureCreated(int width, int height, long byteSize);
        void onTextureDestroyed();
        void onError(String message);
    }

    private final HugeTexRenderer renderer;
    private StatusListener statusListener;

    static {
        System.loadLibrary("gles30testdemo");
    }

    public HugeTexView(Context context) {
        this(context, null);
    }

    public HugeTexView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
        renderer = new HugeTexRenderer();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void setStatusListener(StatusListener listener) {
        statusListener = listener;
    }

    public void startTexture(int width, int height) {
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        queueEvent(() -> renderer.createTexture(width, height));
        requestRender();
    }

    public void stopTexture(boolean notify) {
        queueEvent(() -> renderer.destroyTexture(notify));
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        requestRender();
    }

    private void notifyCreated(int width, int height, long byteSize) {
        StatusListener listener = statusListener;
        if (listener != null) {
            post(() -> listener.onTextureCreated(width, height, byteSize));
        }
    }

    private void notifyDestroyed() {
        StatusListener listener = statusListener;
        if (listener != null) {
            post(listener::onTextureDestroyed);
        }
    }

    private void notifyError(String message) {
        StatusListener listener = statusListener;
        if (listener != null) {
            post(() -> listener.onError(message));
        }
    }

    private static native int nativeCreateTexture(int width, int height);
    private static native boolean nativeUpdateTexture();
    private static native void nativeDestroyTexture();
    private static native String nativeGetLastError();

    private class HugeTexRenderer implements Renderer {
        private static final String TAG = "HugeTexView";

        private final float[] vertex = {
                -1.0f,  1.0f,
                -1.0f, -1.0f,
                 1.0f,  1.0f,
                 1.0f, -1.0f
        };

        private final float[] texCoord = {
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 1.0f,
                1.0f, 0.0f
        };

        private final String vertexShaderCode =
                "attribute vec2 aPosition;\n" +
                        "attribute vec2 aTexCoord;\n" +
                        "varying vec2 vTexCoord;\n" +
                        "void main() {\n" +
                        "    gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
                        "    vTexCoord = aTexCoord;\n" +
                        "}";

        private final String fragmentShaderCode =
                "precision mediump float;\n" +
                        "varying vec2 vTexCoord;\n" +
                        "uniform sampler2D uTexture;\n" +
                        "void main() {\n" +
                        "    gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
                        "}";

        private FloatBuffer vertexBuffer;
        private FloatBuffer texCoordBuffer;

        private int program;
        private int positionHandle;
        private int texCoordHandle;
        private int samplerHandle;
        private int textureId;
        private int texWidth;
        private int texHeight;
        private int frameIndex;
        private int viewWidth;
        private int viewHeight;
        private boolean textureReady;

        HugeTexRenderer() {
            vertexBuffer = ByteBuffer.allocateDirect(vertex.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            vertexBuffer.put(vertex).position(0);

            texCoordBuffer = ByteBuffer.allocateDirect(texCoord.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            texCoordBuffer.put(texCoord).position(0);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            program = buildProgram(vertexShaderCode, fragmentShaderCode);
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord");
            samplerHandle = GLES20.glGetUniformLocation(program, "uTexture");
            textureId = 0;
            textureReady = false;
            nativeDestroyTexture();
            GLES20.glClearColor(0.05f, 0.05f, 0.05f, 1.0f);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            viewWidth = width;
            viewHeight = height;
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (!textureReady) {
                return;
            }

            updateTexture();
            if (textureReady) {
                drawTexture();
            }
        }

        private void createTexture(int width, int height) {
            destroyTexture(false);

            long byteSize = (long) width * height * 4L;
            int createdTextureId = nativeCreateTexture(width, height);
            if (createdTextureId == 0) {
                notifyError(nativeGetLastError());
                return;
            }

            textureId = createdTextureId;
            texWidth = width;
            texHeight = height;
            frameIndex = 1;
            textureReady = true;
            notifyCreated(texWidth, texHeight, byteSize);
        }

        private void destroyTexture(boolean notify) {
            if (textureId != 0) {
                nativeDestroyTexture();
            }
            textureId = 0;
            texWidth = 0;
            texHeight = 0;
            frameIndex = 0;
            textureReady = false;
            if (notify) {
                notifyDestroyed();
            }
        }

        private void updateTexture() {
            if (textureId == 0) {
                return;
            }

            if (!nativeUpdateTexture()) {
                Log.e(TAG, nativeGetLastError());
                destroyTexture(false);
                notifyError(nativeGetLastError());
                return;
            }
            frameIndex++;
        }

        private void drawTexture() {
            GLES20.glViewport(0, 0, viewWidth, viewHeight);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            GLES20.glUniform1i(samplerHandle, 0);

            vertexBuffer.position(0);
            texCoordBuffer.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
            GLES20.glEnableVertexAttribArray(texCoordHandle);
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(texCoordHandle);
        }

        private int buildProgram(String vertexCode, String fragmentCode) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode);
            int glProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(glProgram, vertexShader);
            GLES20.glAttachShader(glProgram, fragmentShader);
            GLES20.glLinkProgram(glProgram);

            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(glProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(glProgram);
                Log.e(TAG, "program link failed: " + log);
                GLES20.glDeleteProgram(glProgram);
                return 0;
            }

            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            return glProgram;
        }

        private int loadShader(int type, String code) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, code);
            GLES20.glCompileShader(shader);

            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(shader);
                Log.e(TAG, "shader compile failed: " + log);
                GLES20.glDeleteShader(shader);
                return 0;
            }
            return shader;
        }
    }
}
