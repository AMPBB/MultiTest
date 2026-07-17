package pbbadd.opengl.multitest.textattrib;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class TextAttribPointerView extends GLSurfaceView implements GLSurfaceView.Renderer {

    private static final String TAG = "TextAttribPointerView";
    private static final int FLOAT_STRIDE_BYTES = 4 * 4;
    private static final int INT_STRIDE_BYTES = 4 * 4;

    private final float[] floatVertices = {
            -0.90f,  0.88f, 0.0f, 0.0f,
            -0.90f,  0.12f, 0.0f, 1.0f,
             0.90f,  0.88f, 1.0f, 0.0f,
             0.90f,  0.12f, 1.0f, 1.0f
    };

    private final int[] intVertices = {
            -900, -120,    0,    0,
            -900, -880,    0, 1000,
             900, -120, 1000,    0,
             900, -880, 1000, 1000
    };

    private FloatBuffer floatVertexBuffer;
    private IntBuffer intVertexBuffer;

    private int floatProgram;
    private int intProgram;
    private int textTextureId;
    private int viewWidth;
    private int viewHeight;

    public TextAttribPointerView(Context context) {
        this(context, null);
    }

    public TextAttribPointerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setEGLContextClientVersion(3);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        floatVertexBuffer = ByteBuffer.allocateDirect(floatVertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        floatVertexBuffer.put(floatVertices).position(0);

        intVertexBuffer = ByteBuffer.allocateDirect(intVertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
        intVertexBuffer.put(intVertices).position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        String glVersion = GLES30.glGetString(GLES30.GL_VERSION);
        Log.i(TAG, "gles version=" + glVersion);

        floatProgram = createProgram(FLOAT_VERTEX_SHADER, FRAGMENT_SHADER);
        intProgram = createProgram(INT_VERTEX_SHADER, FRAGMENT_SHADER);
        textTextureId = createTextTexture();

        GLES30.glClearColor(0.04f, 0.04f, 0.06f, 1.0f);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewWidth = width;
        viewHeight = height;
        GLES30.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        drawWithGlVertexAttribPointer();
        drawWithGlVertexAttribIPointer();
    }

    private void drawWithGlVertexAttribPointer() {
        GLES30.glUseProgram(floatProgram);
        GLES30.glViewport(0, 0, viewWidth, viewHeight);
        bindTextTexture(floatProgram);

        int positionLocation = GLES30.glGetAttribLocation(floatProgram, "aPosition");
        int texCoordLocation = GLES30.glGetAttribLocation(floatProgram, "aTexCoord");

        floatVertexBuffer.position(0);
        GLES30.glEnableVertexAttribArray(positionLocation);
        GLES30.glVertexAttribPointer(positionLocation, 2, GLES30.GL_FLOAT,
                false, FLOAT_STRIDE_BYTES, floatVertexBuffer);

        floatVertexBuffer.position(2);
        GLES30.glEnableVertexAttribArray(texCoordLocation);
        GLES30.glVertexAttribPointer(texCoordLocation, 2, GLES30.GL_FLOAT,
                false, FLOAT_STRIDE_BYTES, floatVertexBuffer);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glDisableVertexAttribArray(positionLocation);
        GLES30.glDisableVertexAttribArray(texCoordLocation);
    }

    private void drawWithGlVertexAttribIPointer() {
        GLES30.glUseProgram(intProgram);
        GLES30.glViewport(0, 0, viewWidth, viewHeight);
        bindTextTexture(intProgram);

        int positionLocation = GLES30.glGetAttribLocation(intProgram, "aPositionFixed");
        int texCoordLocation = GLES30.glGetAttribLocation(intProgram, "aTexCoordFixed");

        intVertexBuffer.position(0);
        GLES30.glEnableVertexAttribArray(positionLocation);
        GLES30.glVertexAttribIPointer(positionLocation, 2, GLES30.GL_INT,
                INT_STRIDE_BYTES, intVertexBuffer);

        intVertexBuffer.position(2);
        GLES30.glEnableVertexAttribArray(texCoordLocation);
        GLES30.glVertexAttribIPointer(texCoordLocation, 2, GLES30.GL_INT,
                INT_STRIDE_BYTES, intVertexBuffer);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glDisableVertexAttribArray(positionLocation);
        GLES30.glDisableVertexAttribArray(texCoordLocation);
    }

    private void bindTextTexture(int program) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textTextureId);
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uTexture"), 0);
    }

    private int createTextTexture() {
        Bitmap bitmap = createTextBitmap();
        int[] texture = new int[1];
        GLES30.glGenTextures(1, texture, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0]);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();
        return texture[0];
    }

    private Bitmap createTextBitmap() {
        int width = 1024;
        int height = 320;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(20, 24, 34));

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTypeface(Typeface.DEFAULT_BOLD);
        titlePaint.setTextSize(54.0f);

        Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(Color.rgb(190, 225, 255));
        bodyPaint.setTypeface(Typeface.MONOSPACE);
        bodyPaint.setTextSize(36.0f);

        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.rgb(80, 170, 120));
        linePaint.setStrokeWidth(6.0f);

        canvas.drawLine(42.0f, 78.0f, width - 42.0f, 78.0f, linePaint);
        canvas.drawText("\u6587\u5b57 rasterized to texture", 48.0f, 62.0f, titlePaint);
        canvas.drawText("top: glVertexAttribPointer(float vec2)", 48.0f, 150.0f, bodyPaint);
        canvas.drawText("bottom: glVertexAttribIPointer(ivec2)", 48.0f, 210.0f, bodyPaint);
        canvas.drawText("same text bitmap, two attribute paths", 48.0f, 270.0f, bodyPaint);
        return bitmap;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, vertexShader);
        GLES30.glAttachShader(program, fragmentShader);
        GLES30.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG, "program link failed: " + GLES30.glGetProgramInfoLog(program));
            GLES30.glDeleteProgram(program);
            return 0;
        }

        GLES30.glDeleteShader(vertexShader);
        GLES30.glDeleteShader(fragmentShader);
        return program;
    }

    private int loadShader(int type, String source) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "shader compile failed: " + GLES30.glGetShaderInfoLog(shader));
            GLES30.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static final String FLOAT_VERTEX_SHADER =
            "#version 300 es\n" +
                    "in vec2 aPosition;\n" +
                    "in vec2 aTexCoord;\n" +
                    "out vec2 vTexCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
                    "    vTexCoord = aTexCoord;\n" +
                    "}\n";

    private static final String INT_VERTEX_SHADER =
            "#version 300 es\n" +
                    "in ivec2 aPositionFixed;\n" +
                    "in ivec2 aTexCoordFixed;\n" +
                    "out vec2 vTexCoord;\n" +
                    "void main() {\n" +
                    "    vec2 position = vec2(aPositionFixed) / 1000.0;\n" +
                    "    gl_Position = vec4(position, 0.0, 1.0);\n" +
                    "    vTexCoord = vec2(aTexCoordFixed) / 1000.0;\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "in vec2 vTexCoord;\n" +
                    "uniform sampler2D uTexture;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    fragColor = texture(uTexture, vTexCoord);\n" +
                    "}\n";
}
