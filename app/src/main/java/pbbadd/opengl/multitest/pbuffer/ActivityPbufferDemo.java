package pbbadd.opengl.multitest.pbuffer;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.EGL14;
import android.opengl.EGL15;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;

import pbbadd.opengl.multitest.R;

public class ActivityPbufferDemo extends AppCompatActivity {
    private static final String TAG = "pbuffer";
    private static final int SIZE_LT_65536 = 256;
    private static final int SIZE_GE_65536 = 65537;

    private TextView statusView;
    private ImageView resultView;
    private final String tag = "pbuffer";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pbuffer_demo);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        statusView = findViewById(R.id.text_pbuffer_status);
        resultView = findViewById(R.id.image_pbuffer_result);
        Button ltLtButton = findViewById(R.id.button_pbuffer_width_lt_height_lt);
        Button ltGeButton = findViewById(R.id.button_pbuffer_width_lt_height_ge);
        Button geLtButton = findViewById(R.id.button_pbuffer_width_ge_height_lt);
        Button geGeButton = findViewById(R.id.button_pbuffer_width_ge_height_ge);
        ltLtButton.setOnClickListener(v -> renderPbuffer(SIZE_LT_65536, SIZE_LT_65536));
        ltGeButton.setOnClickListener(v -> renderPbuffer(SIZE_LT_65536, SIZE_GE_65536));
        geLtButton.setOnClickListener(v -> renderPbuffer(SIZE_GE_65536, SIZE_LT_65536));
        geGeButton.setOnClickListener(v -> renderPbuffer(SIZE_GE_65536, SIZE_GE_65536));

        renderPbuffer(SIZE_LT_65536, SIZE_LT_65536);
    }

    private void renderPbuffer(int width, int height) {
        try {
            RenderResult result = PbufferRenderer.render(width, height);
            if (result.bitmap != null) {
                resultView.setImageBitmap(result.bitmap);
            } else {
                resultView.setImageDrawable(null);
            }
            statusView.setText(result.message);
            Log.d(TAG, result.message.replace('\n', ' '));
        } catch (RuntimeException e) {
            String message = "pbuffer render failed (" + width + "x" + height + "): "
                    + e.getMessage();
            statusView.setText(message);
            resultView.setImageDrawable(null);
            Log.e(TAG, message, e);
        }
    }

    private static final class RenderResult {
        final Bitmap bitmap;
        final String message;

        RenderResult(Bitmap bitmap, String message) {
            this.bitmap = bitmap;
            this.message = message;
        }
    }

    private static final class PbufferRenderer {
        private static final String tag = "pbuffer render";
        private static final long MAX_PREVIEW_PIXELS = 1024L * 1024L;

        private static final float[] VERTEX_DATA = {
                -1.0f, -1.0f, 1.0f, 0.0f, 0.0f,
                 1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
                -1.0f,  1.0f, 0.0f, 0.0f, 1.0f,
                 1.0f,  1.0f, 1.0f, 1.0f, 0.0f,
        };

        private static final String VERTEX_SHADER =
                "#version 300 es\n" +
                        "layout(location = 0) in vec2 aPosition;\n" +
                        "layout(location = 1) in vec3 aColor;\n" +
                        "out vec3 vColor;\n" +
                        "void main() {\n" +
                        "    gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
                        "    vColor = aColor;\n" +
                        "}\n";

        private static final String FRAGMENT_SHADER =
                "#version 300 es\n" +
                        "precision mediump float;\n" +
                        "in vec3 vColor;\n" +
                        "out vec4 outColor;\n" +
                        "void main() {\n" +
                        "    outColor = vec4(vColor, 1.0);\n" +
                        "}\n";

        static RenderResult render(int width, int height) {
            EGLDisplay display = EGL14.EGL_NO_DISPLAY;
            EGLContext context = EGL14.EGL_NO_CONTEXT;
            EGLSurface surface = EGL14.EGL_NO_SURFACE;
            int program = 0;

            try {
                display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
                if (display == EGL14.EGL_NO_DISPLAY) {
                    throw new RuntimeException("eglGetDisplay returned EGL_NO_DISPLAY");
                }

                int[] version = new int[2];
                if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                    throwEgl("eglInitialize");
                }

                EGLConfig config = chooseConfig(display);
                context = createContext(display, config);
                surface = createPbufferSurface(display, config, width, height);

                if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                    throwEgl("eglMakeCurrent");
                }

                String eglVersion = String.format(Locale.US, "%d.%d", version[0], version[1]);
                String glVersion = GLES30.glGetString(GLES30.GL_VERSION);
                String glRenderer = GLES30.glGetString(GLES30.GL_RENDERER);

                Bitmap bitmap = null;
                String previewMessage = "\npreview skipped: pbuffer is too large for glReadPixels";
                if (canRenderPreview(width, height)) {
                    program = createProgram();
                    drawGradient(program, width, height);
                    bitmap = readBitmap(width, height);
                    previewMessage = "\npreview rendered with glReadPixels";
                }

                String message = "eglCreatePbufferSurface width=" + width + ", height=" + height
                        + previewMessage
                        + "\nEGL " + eglVersion
                        + "\n" + glVersion
                        + "\n" + glRenderer;
                return new RenderResult(bitmap, message);
            } finally {
                if (display != EGL14.EGL_NO_DISPLAY) {
                    if (program != 0) {
                        GLES30.glDeleteProgram(program);
                    }
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                    if (surface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(display, surface);
                    }
                    if (context != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(display, context);
                    }
                    EGL14.eglTerminate(display);
                }
                EGL14.eglReleaseThread();
            }
        }

        private static boolean canRenderPreview(int width, int height) {
            return (long) width * height <= MAX_PREVIEW_PIXELS;
        }

        private static EGLConfig chooseConfig(EGLDisplay display) {
            int[] configAttributes = {
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RENDERABLE_TYPE, EGL15.EGL_OPENGL_ES3_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_DEPTH_SIZE, 0,
                    EGL14.EGL_STENCIL_SIZE, 0,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] configCount = new int[1];
            if (!EGL14.eglChooseConfig(display, configAttributes, 0,
                    configs, 0, configs.length, configCount, 0)) {
                throwEgl("eglChooseConfig");
            }
            if (configCount[0] == 0 || configs[0] == null) {
                throw new RuntimeException("no ES3 pbuffer EGLConfig found");
            }
            return configs[0];
        }

        private static EGLContext createContext(EGLDisplay display, EGLConfig config) {
            int[] contextAttributes = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                    EGL14.EGL_NONE
            };
            EGLContext context = EGL14.eglCreateContext(display, config,
                    EGL14.EGL_NO_CONTEXT, contextAttributes, 0);
            if (context == EGL14.EGL_NO_CONTEXT) {
                throwEgl("eglCreateContext");
            }
            return context;
        }

        private static EGLSurface createPbufferSurface(
                EGLDisplay display, EGLConfig config, int width, int height) {
            int[] surfaceAttributes = {
                    EGL14.EGL_WIDTH, width,
                    EGL14.EGL_HEIGHT, height,
                    EGL14.EGL_NONE
            };
            Log.d(tag,"eglCreatePbufferSurface,w"+width+",h"+height);
            EGLSurface surface = EGL14.eglCreatePbufferSurface(display, config,
                    surfaceAttributes, 0);
            if (surface == EGL14.EGL_NO_SURFACE) {
                throwEgl("eglCreatePbufferSurface");
            }
            return surface;
        }

        private static void drawGradient(int program, int width, int height) {
            GLES30.glViewport(0, 0, width, height);
            GLES30.glClearColor(0.05f, 0.07f, 0.10f, 1.0f);
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

            FloatBuffer vertexBuffer = ByteBuffer
                    .allocateDirect(VERTEX_DATA.length * Float.BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            vertexBuffer.put(VERTEX_DATA).position(0);

            GLES30.glUseProgram(program);
            vertexBuffer.position(0);
            GLES30.glEnableVertexAttribArray(0);
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false,
                    5 * Float.BYTES, vertexBuffer);

            vertexBuffer.position(2);
            GLES30.glEnableVertexAttribArray(1);
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false,
                    5 * Float.BYTES, vertexBuffer);

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
            GLES30.glDisableVertexAttribArray(0);
            GLES30.glDisableVertexAttribArray(1);
            checkGl("drawGradient");
        }

        private static Bitmap readBitmap(int width, int height) {
            ByteBuffer buffer = ByteBuffer
                    .allocateDirect(width * height * 4)
                    .order(ByteOrder.nativeOrder());
            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA,
                    GLES30.GL_UNSIGNED_BYTE, buffer);
            checkGl("glReadPixels");

            buffer.position(0);
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                int targetY = height - 1 - y;
                for (int x = 0; x < width; x++) {
                    int r = buffer.get() & 0xff;
                    int g = buffer.get() & 0xff;
                    int b = buffer.get() & 0xff;
                    int a = buffer.get() & 0xff;
                    pixels[targetY * width + x] = Color.argb(a, r, g, b);
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        }

        private static int createProgram() {
            int vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            int program = GLES30.glCreateProgram();
            GLES30.glAttachShader(program, vertexShader);
            GLES30.glAttachShader(program, fragmentShader);
            GLES30.glLinkProgram(program);

            int[] linked = new int[1];
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0);
            GLES30.glDeleteShader(vertexShader);
            GLES30.glDeleteShader(fragmentShader);
            if (linked[0] == 0) {
                String info = GLES30.glGetProgramInfoLog(program);
                GLES30.glDeleteProgram(program);
                throw new RuntimeException("program link failed: " + info);
            }
            return program;
        }

        private static int compileShader(int type, String source) {
            int shader = GLES30.glCreateShader(type);
            GLES30.glShaderSource(shader, source);
            GLES30.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String info = GLES30.glGetShaderInfoLog(shader);
                GLES30.glDeleteShader(shader);
                throw new RuntimeException("shader compile failed: " + info);
            }
            return shader;
        }

        private static void checkGl(String op) {
            int error = GLES30.glGetError();
            if (error != GLES30.GL_NO_ERROR) {
                throw new RuntimeException(op + " GL error 0x" + Integer.toHexString(error));
            }
        }

        private static void throwEgl(String op) {
            int error = EGL14.eglGetError();
            throw new RuntimeException(op + " EGL error 0x" + Integer.toHexString(error));
        }
    }
}
