package pbbadd.opengl.multitest.texsubimageformat;

import android.content.Context;
import android.opengl.GLES11Ext;
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

public class TexSubImageFormatView extends GLSurfaceView {
    private static final String TAG = "TexSubImageFormat";
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int GL_BGRA_EXT = GLES11Ext.GL_BGRA;

    private final FormatRenderer renderer;
    private StatusListener statusListener;

    public TexSubImageFormatView(Context context) {
        this(context, null);
    }

    public TexSubImageFormatView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
        renderer = new FormatRenderer();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void setStatusListener(StatusListener listener) {
        statusListener = listener;
    }

    public void startTest(int loopCount) {
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        queueEvent(() -> renderer.start(loopCount));
        requestRender();
    }

    public void stopTest(boolean notify) {
        queueEvent(() -> renderer.stop(notify));
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        requestRender();
    }

    private void notifyStatus(String status) {
        StatusListener listener = statusListener;
        if (listener != null) {
            post(() -> listener.onStatus(status));
        }
    }

    public interface StatusListener {
        void onStatus(String status);
    }

    private class FormatRenderer implements Renderer {
        private final int[] textureIds = new int[FormatCase.CASES.length];
        private final int[] texImageErrors = new int[FormatCase.CASES.length];
        private final int[] texSubImageErrors = new int[FormatCase.CASES.length];
        private final ByteBuffer[] updateBuffers = new ByteBuffer[FormatCase.CASES.length];

        private final FloatBuffer[] panelPositions = new FloatBuffer[]{
                createFloatBuffer(new float[]{
                        -0.96f,  0.74f,
                        -0.36f,  0.74f,
                        -0.96f, -0.74f,
                        -0.36f, -0.74f
                }),
                createFloatBuffer(new float[]{
                        -0.30f,  0.74f,
                         0.30f,  0.74f,
                        -0.30f, -0.74f,
                         0.30f, -0.74f
                }),
                createFloatBuffer(new float[]{
                         0.36f,  0.74f,
                         0.96f,  0.74f,
                         0.36f, -0.74f,
                         0.96f, -0.74f
                })
        };

        private final FloatBuffer texCoords = createFloatBuffer(new float[]{
                0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 1.0f
        });

        private int program;
        private int viewWidth;
        private int viewHeight;
        private boolean textureReady;
        private boolean bgraSupported;
        private boolean running;
        private int requestedLoops;
        private int completedLoops;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
            bgraSupported = hasBgraExtension(extensions);
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            createTextures();
            notifyStatus(buildStatus("ready"));
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            viewWidth = width;
            viewHeight = height;
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glViewport(0, 0, viewWidth, viewHeight);
            GLES20.glClearColor(0.04f, 0.04f, 0.04f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            if (running && textureReady) {
                int loopIndex = completedLoops + 1;
                updateTextures(loopIndex);
                completedLoops = loopIndex;
                if (completedLoops >= requestedLoops) {
                    running = false;
                    notifyStatus(buildStatus("finished"));
                    post(() -> setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY));
                } else if (completedLoops == 1
                        || completedLoops % 10 == 0
                        || completedLoops == requestedLoops) {
                    notifyStatus(buildStatus("running"));
                }
            }

            drawTextures();
        }

        private void start(int loopCount) {
            if (!textureReady) {
                createTextures();
            }
            requestedLoops = loopCount;
            completedLoops = 0;
            running = true;
            notifyStatus(buildStatus("running"));
        }

        private void stop(boolean notify) {
            running = false;
            if (notify) {
                notifyStatus(buildStatus("stopped"));
            }
        }

        private void createTextures() {
            if (program == 0) {
                textureReady = false;
                return;
            }

            if (textureIds[0] != 0) {
                GLES20.glDeleteTextures(textureIds.length, textureIds, 0);
            }

            GLES20.glGenTextures(textureIds.length, textureIds, 0);
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);

            for (int i = 0; i < FormatCase.CASES.length; i++) {
                FormatCase formatCase = FormatCase.CASES[i];
                updateBuffers[i] = ByteBuffer
                        .allocateDirect(TEXTURE_WIDTH * TEXTURE_HEIGHT
                                * formatCase.bytesPerPixel)
                        .order(ByteOrder.nativeOrder());
                fillBuffer(updateBuffers[i], formatCase, 0);

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[i]);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                        GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                        GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                        GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                        GLES20.GL_CLAMP_TO_EDGE);

                clearGlErrors();
                GLES20.glTexImage2D(
                        GLES20.GL_TEXTURE_2D,
                        0,
                        formatCase.format,
                        TEXTURE_WIDTH,
                        TEXTURE_HEIGHT,
                        0,
                        formatCase.format,
                        GLES20.GL_UNSIGNED_BYTE,
                        updateBuffers[i]);
                texImageErrors[i] = GLES20.glGetError();
                texSubImageErrors[i] = GLES20.GL_NO_ERROR;
                Log.i(TAG, "glTexImage2D format=" + formatCase.name
                        + ", error=" + glErrorToString(texImageErrors[i]));
            }

            textureReady = true;
        }

        private void updateTextures(int loopIndex) {
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
            for (int i = 0; i < FormatCase.CASES.length; i++) {
                FormatCase formatCase = FormatCase.CASES[i];
                fillBuffer(updateBuffers[i], formatCase, loopIndex);

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[i]);
                clearGlErrors();
                GLES20.glTexSubImage2D(
                        GLES20.GL_TEXTURE_2D,
                        0,
                        0,
                        0,
                        TEXTURE_WIDTH,
                        TEXTURE_HEIGHT,
                        formatCase.format,
                        GLES20.GL_UNSIGNED_BYTE,
                        updateBuffers[i]);
                texSubImageErrors[i] = GLES20.glGetError();
                Log.i(TAG, String.format(Locale.US,
                        "loop=%d glTexSubImage2D format=%s(0x%04x) error=%s",
                        loopIndex,
                        formatCase.name,
                        formatCase.format,
                        glErrorToString(texSubImageErrors[i])));
            }
        }

        private void drawTextures() {
            if (!textureReady || program == 0) {
                return;
            }

            GLES20.glUseProgram(program);
            int positionLocation = GLES20.glGetAttribLocation(program, "aPosition");
            int texCoordLocation = GLES20.glGetAttribLocation(program, "aTexCoord");
            int textureLocation = GLES20.glGetUniformLocation(program, "uTexture");

            for (int i = 0; i < textureIds.length; i++) {
                panelPositions[i].position(0);
                GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false,
                        0, panelPositions[i]);
                GLES20.glEnableVertexAttribArray(positionLocation);

                texCoords.position(0);
                GLES20.glVertexAttribPointer(texCoordLocation, 2, GLES20.GL_FLOAT, false,
                        0, texCoords);
                GLES20.glEnableVertexAttribArray(texCoordLocation);

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[i]);
                GLES20.glUniform1i(textureLocation, 0);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            }

            GLES20.glDisableVertexAttribArray(positionLocation);
            GLES20.glDisableVertexAttribArray(texCoordLocation);
        }

        private String buildStatus(String state) {
            return String.format(Locale.US,
                    "state: %s, loops: %d/%d, texture: %dx%d\n"
                            + "left GL_RGBA texImage=%s texSub=%s\n"
                            + "middle GL_RGB texImage=%s texSub=%s\n"
                            + "right GL_BGRA_EXT texImage=%s texSub=%s, ext=%s",
                    state,
                    completedLoops,
                    requestedLoops,
                    TEXTURE_WIDTH,
                    TEXTURE_HEIGHT,
                    glErrorToString(texImageErrors[0]),
                    glErrorToString(texSubImageErrors[0]),
                    glErrorToString(texImageErrors[1]),
                    glErrorToString(texSubImageErrors[1]),
                    glErrorToString(texImageErrors[2]),
                    glErrorToString(texSubImageErrors[2]),
                    bgraSupported ? "yes" : "no");
        }
    }

    private static void fillBuffer(ByteBuffer buffer, FormatCase formatCase, int loopIndex) {
        buffer.clear();
        int offset = (loopIndex * 9) & 0xff;
        for (int y = 0; y < TEXTURE_HEIGHT; y++) {
            for (int x = 0; x < TEXTURE_WIDTH; x++) {
                int r = (x + offset) & 0xff;
                int g = (y + offset * 2) & 0xff;
                int b = (x + y + offset * 3) & 0xff;
                int a = 0xff;

                if (((x / 32) + (y / 32) + loopIndex) % 2 == 0) {
                    r = 255 - r;
                    b = 255 - b;
                }

                switch (formatCase.format) {
                    case GLES20.GL_RGBA:
                        buffer.put((byte) r);
                        buffer.put((byte) g);
                        buffer.put((byte) b);
                        buffer.put((byte) a);
                        break;
                    case GLES20.GL_RGB:
                        buffer.put((byte) r);
                        buffer.put((byte) g);
                        buffer.put((byte) b);
                        break;
                    case GL_BGRA_EXT:
                        buffer.put((byte) b);
                        buffer.put((byte) g);
                        buffer.put((byte) r);
                        buffer.put((byte) a);
                        break;
                    default:
                        break;
                }
            }
        }
        buffer.position(0);
    }

    private static boolean hasBgraExtension(String extensions) {
        if (extensions == null) {
            return false;
        }
        return extensions.contains("GL_EXT_texture_format_BGRA8888")
                || extensions.contains("GL_IMG_texture_format_BGRA8888")
                || extensions.contains("GL_APPLE_texture_format_BGRA8888");
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (vertexShader == 0 || fragmentShader == 0) {
            return 0;
        }

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG, "program link failed: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }

        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            Log.e(TAG, "shader compile failed: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static FloatBuffer createFloatBuffer(float[] values) {
        FloatBuffer buffer = ByteBuffer
                .allocateDirect(values.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer.put(values).position(0);
        return buffer;
    }

    private static void clearGlErrors() {
        while (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
            // Clear stale errors before checking the call under test.
        }
    }

    private static String glErrorToString(int error) {
        if (error == GLES20.GL_NO_ERROR) {
            return "GL_NO_ERROR";
        }
        return "0x" + Integer.toHexString(error);
    }

    private static final class FormatCase {
        private static final FormatCase[] CASES = new FormatCase[]{
                new FormatCase("GL_RGBA", GLES20.GL_RGBA, 4),
                new FormatCase("GL_RGB", GLES20.GL_RGB, 3),
                new FormatCase("GL_BGRA_EXT", GL_BGRA_EXT, 4)
        };

        private final String name;
        private final int format;
        private final int bytesPerPixel;

        private FormatCase(String name, int format, int bytesPerPixel) {
            this.name = name;
            this.format = format;
            this.bytesPerPixel = bytesPerPixel;
        }
    }

    private static final String VERTEX_SHADER =
            "attribute vec2 aPosition;\n"
                    + "attribute vec2 aTexCoord;\n"
                    + "varying vec2 vTexCoord;\n"
                    + "void main() {\n"
                    + "  gl_Position = vec4(aPosition, 0.0, 1.0);\n"
                    + "  vTexCoord = aTexCoord;\n"
                    + "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "varying vec2 vTexCoord;\n"
                    + "uniform sampler2D uTexture;\n"
                    + "void main() {\n"
                    + "  gl_FragColor = texture2D(uTexture, vTexCoord);\n"
                    + "}\n";
}
