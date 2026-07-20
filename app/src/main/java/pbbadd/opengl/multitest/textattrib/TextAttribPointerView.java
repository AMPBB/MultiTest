package pbbadd.opengl.multitest.textattrib;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class TextAttribPointerView extends GLSurfaceView implements GLSurfaceView.Renderer {

    private static final String TAG = "TextAttribPointerView";
    private static final String DEMO_TEXT =
            "GPU glyph atlas\n" +
                    "unorder string -> shelf packing -> one texture\n" +
                    "glBufferData + glBufferSubData\n" +
                    "glVertexAttribPointer draws text";

    private static final int ATLAS_WIDTH = 1024;
    private static final int GLYPH_PADDING = 4;
    private static final int FLOATS_PER_VERTEX = 4;
    private static final int BYTES_PER_FLOAT = 4;
    private static final int VERTEX_STRIDE_BYTES = FLOATS_PER_VERTEX * BYTES_PER_FLOAT;
    private static final int MAX_QUAD_COUNT = DEMO_TEXT.length() + 4;
    private static final int MAX_VERTEX_COUNT = MAX_QUAD_COUNT * 6;
    private static final int MAX_VERTEX_BYTES = MAX_VERTEX_COUNT * VERTEX_STRIDE_BYTES;

    private final Map<Character, GlyphInfo> glyphInfoMap = new HashMap<>();
    private final ByteBuffer vertexByteBuffer = ByteBuffer.allocateDirect(MAX_VERTEX_BYTES)
            .order(ByteOrder.nativeOrder());
    private final FloatBuffer vertexFloatBuffer = vertexByteBuffer.asFloatBuffer();

    private Paint glyphPaint;
    private Paint.FontMetrics fontMetrics;
    private int program;
    private int textureId;
    private int vboId;
    private int positionLocation;
    private int texCoordLocation;
    private int samplerLocation;
    private int atlasHeight;
    private int vertexCount;
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
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        String glVersion = GLES30.glGetString(GLES30.GL_VERSION);
        Log.i(TAG, "gles version=" + glVersion);

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        positionLocation = GLES30.glGetAttribLocation(program, "aPosition");
        texCoordLocation = GLES30.glGetAttribLocation(program, "aTexCoord");
        samplerLocation = GLES30.glGetUniformLocation(program, "uTexture");

        initGlyphPaint();
        textureId = createPackedGlyphAtlasTexture();
        vboId = createTextVertexBuffer();

        GLES30.glClearColor(0.03f, 0.035f, 0.05f, 1.0f);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewWidth = width;
        viewHeight = height;
        GLES30.glViewport(0, 0, width, height);
        uploadTextVertexData();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        if (vertexCount <= 0) {
            return;
        }

        GLES30.glUseProgram(program);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
        GLES30.glUniform1i(samplerLocation, 0);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId);
        GLES30.glEnableVertexAttribArray(positionLocation);
        GLES30.glVertexAttribPointer(positionLocation, 2, GLES30.GL_FLOAT,
                false, VERTEX_STRIDE_BYTES, 0);
        GLES30.glEnableVertexAttribArray(texCoordLocation);
        GLES30.glVertexAttribPointer(texCoordLocation, 2, GLES30.GL_FLOAT,
                false, VERTEX_STRIDE_BYTES, 2 * BYTES_PER_FLOAT);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount);

        GLES30.glDisableVertexAttribArray(positionLocation);
        GLES30.glDisableVertexAttribArray(texCoordLocation);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    private void initGlyphPaint() {
        glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
        glyphPaint.setColor(Color.WHITE);
        glyphPaint.setTypeface(Typeface.DEFAULT_BOLD);
        glyphPaint.setTextSize(42.0f);
        fontMetrics = glyphPaint.getFontMetrics();
    }

    private int createPackedGlyphAtlasTexture() {
        List<GlyphInfo> drawableGlyphs = buildGlyphInfoList();
        Collections.shuffle(drawableGlyphs, new Random(20260720L));
        packGlyphsByShelf(drawableGlyphs);
        logPackedGlyphUvs(drawableGlyphs);

        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0]);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1);

        ByteBuffer emptyAtlas = ByteBuffer.allocateDirect(ATLAS_WIDTH * atlasHeight);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8,
                ATLAS_WIDTH, atlasHeight, 0,
                GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, emptyAtlas);

        for (GlyphInfo glyphInfo : drawableGlyphs) {
            ByteBuffer glyphAlphaBuffer = createGlyphAlphaBuffer(glyphInfo);
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0,
                    glyphInfo.atlasX + GLYPH_PADDING,
                    glyphInfo.atlasY + GLYPH_PADDING,
                    glyphInfo.width,
                    glyphInfo.height,
                    GLES30.GL_RED,
                    GLES30.GL_UNSIGNED_BYTE,
                    glyphAlphaBuffer);
        }
        return textures[0];
    }

    private ByteBuffer createGlyphAlphaBuffer(GlyphInfo glyphInfo) {
        Bitmap glyphBitmap = Bitmap.createBitmap(glyphInfo.width, glyphInfo.height, Bitmap.Config.ARGB_8888);
        glyphBitmap.eraseColor(Color.TRANSPARENT);
        Canvas canvas = new Canvas(glyphBitmap);
        float baselineX = -glyphInfo.bounds.left;
        float baselineY = -glyphInfo.bounds.top;
        canvas.drawText(String.valueOf(glyphInfo.character), baselineX, baselineY, glyphPaint);

        int[] pixels = new int[glyphInfo.width * glyphInfo.height];
        glyphBitmap.getPixels(pixels, 0, glyphInfo.width, 0, 0, glyphInfo.width, glyphInfo.height);
        glyphBitmap.recycle();

        ByteBuffer alphaBuffer = ByteBuffer.allocateDirect(glyphInfo.width * glyphInfo.height);
        for (int pixel : pixels) {
            alphaBuffer.put((byte) (pixel >>> 24));
        }
        alphaBuffer.position(0);
        return alphaBuffer;
    }

    private List<GlyphInfo> buildGlyphInfoList() {
        Set<Character> orderedChars = new LinkedHashSet<>();
        for (int i = 0; i < DEMO_TEXT.length(); i++) {
            char character = DEMO_TEXT.charAt(i);
            if (character != '\n') {
                orderedChars.add(character);
            }
        }

        List<GlyphInfo> drawableGlyphs = new ArrayList<>();
        for (char character : orderedChars) {
            GlyphInfo glyphInfo = new GlyphInfo(character);
            glyphInfo.advance = glyphPaint.measureText(String.valueOf(character));
            glyphPaint.getTextBounds(String.valueOf(character), 0, 1, glyphInfo.bounds);
            glyphInfo.drawable = character != ' ' && !glyphInfo.bounds.isEmpty();
            glyphInfo.width = Math.max(1, glyphInfo.bounds.width());
            glyphInfo.height = Math.max(1, glyphInfo.bounds.height());
            glyphInfo.packWidth = glyphInfo.width + GLYPH_PADDING * 2;
            glyphInfo.packHeight = glyphInfo.height + GLYPH_PADDING * 2;
            glyphInfoMap.put(character, glyphInfo);
            if (glyphInfo.drawable) {
                drawableGlyphs.add(glyphInfo);
            }
        }
        return drawableGlyphs;
    }

    private void packGlyphsByShelf(List<GlyphInfo> glyphs) {
        int cursorX = GLYPH_PADDING;
        int cursorY = GLYPH_PADDING;
        int shelfHeight = 0;

        for (GlyphInfo glyphInfo : glyphs) {
            if (cursorX + glyphInfo.packWidth + GLYPH_PADDING > ATLAS_WIDTH) {
                cursorY += shelfHeight + GLYPH_PADDING;
                cursorX = GLYPH_PADDING;
                shelfHeight = 0;
            }

            glyphInfo.atlasX = cursorX;
            glyphInfo.atlasY = cursorY;
            cursorX += glyphInfo.packWidth + GLYPH_PADDING;
            shelfHeight = Math.max(shelfHeight, glyphInfo.packHeight);
        }

        int usedHeight = cursorY + shelfHeight + GLYPH_PADDING;
        atlasHeight = nextPowerOfTwo(Math.max(64, usedHeight));
        Log.i(TAG, "packed glyph count=" + glyphs.size()
                + ", atlas=" + ATLAS_WIDTH + "x" + atlasHeight);
    }

    private void logPackedGlyphUvs(List<GlyphInfo> glyphs) {
        for (int i = 0; i < glyphs.size(); i++) {
            GlyphInfo glyphInfo = glyphs.get(i);
            float u0 = (glyphInfo.atlasX + GLYPH_PADDING) / (float) ATLAS_WIDTH;
            float v0 = (glyphInfo.atlasY + GLYPH_PADDING) / (float) atlasHeight;
            float u1 = (glyphInfo.atlasX + GLYPH_PADDING + glyphInfo.width) / (float) ATLAS_WIDTH;
            float v1 = (glyphInfo.atlasY + GLYPH_PADDING + glyphInfo.height) / (float) atlasHeight;

            Log.i(TAG, String.format(Locale.US,
                    "packed glyph[%02d] char=\"%s\" atlasRect=(%d,%d,%d,%d) uv=(%.6f,%.6f)-(%.6f,%.6f)",
                    i,
                    glyphToLogString(glyphInfo.character),
                    glyphInfo.atlasX + GLYPH_PADDING,
                    glyphInfo.atlasY + GLYPH_PADDING,
                    glyphInfo.width,
                    glyphInfo.height,
                    u0,
                    v0,
                    u1,
                    v1));
        }
    }

    private String glyphToLogString(char character) {
        if (character == '\t') {
            return "\\t";
        }
        if (character == '\r') {
            return "\\r";
        }
        if (character == '\n') {
            return "\\n";
        }
        return String.valueOf(character);
    }

    private int createTextVertexBuffer() {
        int[] buffers = new int[1];
        GLES30.glGenBuffers(1, buffers, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, MAX_VERTEX_BYTES, null, GLES30.GL_DYNAMIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        return buffers[0];
    }

    private void uploadTextVertexData() {
        if (viewWidth <= 0 || viewHeight <= 0 || vboId == 0) {
            return;
        }

        vertexFloatBuffer.clear();
        appendAtlasPreviewQuad();
        appendTextQuads();
        vertexFloatBuffer.flip();

        vertexCount = vertexFloatBuffer.remaining() / FLOATS_PER_VERTEX;
        int usedBytes = vertexFloatBuffer.remaining() * BYTES_PER_FLOAT;
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId);
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, usedBytes, vertexFloatBuffer);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    private void appendAtlasPreviewQuad() {
        float previewWidth = Math.min(viewWidth * 0.36f, 360.0f);
        float previewHeight = previewWidth * atlasHeight / ATLAS_WIDTH;
        float right = viewWidth - 32.0f;
        float bottom = viewHeight - 32.0f;
        appendQuad(right - previewWidth, bottom - previewHeight, right, bottom,
                0.0f, 0.0f, 1.0f, 1.0f);
    }

    private void appendTextQuads() {
        float marginX = 36.0f;
        float baselineY = 88.0f;
        float lineHeight = fontMetrics.descent - fontMetrics.ascent + 16.0f;
        float cursorX = marginX;

        for (int i = 0; i < DEMO_TEXT.length(); i++) {
            char character = DEMO_TEXT.charAt(i);
            if (character == '\n') {
                cursorX = marginX;
                baselineY += lineHeight;
                continue;
            }

            GlyphInfo glyphInfo = glyphInfoMap.get(character);
            if (glyphInfo == null) {
                continue;
            }

            if (glyphInfo.drawable) {
                float left = cursorX + glyphInfo.bounds.left;
                float top = baselineY + glyphInfo.bounds.top;
                float right = cursorX + glyphInfo.bounds.right;
                float bottom = baselineY + glyphInfo.bounds.bottom;

                float u0 = (glyphInfo.atlasX + GLYPH_PADDING) / (float) ATLAS_WIDTH;
                float v0 = (glyphInfo.atlasY + GLYPH_PADDING) / (float) atlasHeight;
                float u1 = (glyphInfo.atlasX + GLYPH_PADDING + glyphInfo.width) / (float) ATLAS_WIDTH;
                float v1 = (glyphInfo.atlasY + GLYPH_PADDING + glyphInfo.height) / (float) atlasHeight;
                appendQuad(left, top, right, bottom, u0, v0, u1, v1);
            }

            cursorX += glyphInfo.advance;
        }
    }

    private void appendQuad(float leftPx, float topPx, float rightPx, float bottomPx,
                            float u0, float v0, float u1, float v1) {
        float left = toNdcX(leftPx);
        float right = toNdcX(rightPx);
        float top = toNdcY(topPx);
        float bottom = toNdcY(bottomPx);

        appendVertex(left, top, u0, v0);
        appendVertex(left, bottom, u0, v1);
        appendVertex(right, top, u1, v0);

        appendVertex(right, top, u1, v0);
        appendVertex(left, bottom, u0, v1);
        appendVertex(right, bottom, u1, v1);
    }

    private void appendVertex(float x, float y, float u, float v) {
        vertexFloatBuffer.put(x);
        vertexFloatBuffer.put(y);
        vertexFloatBuffer.put(u);
        vertexFloatBuffer.put(v);
    }

    private float toNdcX(float px) {
        return px * 2.0f / viewWidth - 1.0f;
    }

    private float toNdcY(float px) {
        return 1.0f - px * 2.0f / viewHeight;
    }

    private int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value) {
            result <<= 1;
        }
        return result;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
        int glProgram = GLES30.glCreateProgram();
        GLES30.glAttachShader(glProgram, vertexShader);
        GLES30.glAttachShader(glProgram, fragmentShader);
        GLES30.glLinkProgram(glProgram);

        int[] linkStatus = new int[1];
        GLES30.glGetProgramiv(glProgram, GLES30.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG, "program link failed: " + GLES30.glGetProgramInfoLog(glProgram));
            GLES30.glDeleteProgram(glProgram);
            return 0;
        }

        GLES30.glDeleteShader(vertexShader);
        GLES30.glDeleteShader(fragmentShader);
        return glProgram;
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

    private static class GlyphInfo {
        final char character;
        final Rect bounds = new Rect();
        boolean drawable;
        float advance;
        int width;
        int height;
        int packWidth;
        int packHeight;
        int atlasX;
        int atlasY;

        GlyphInfo(char character) {
            this.character = character;
        }
    }

    private static final String VERTEX_SHADER =
            "#version 300 es\n" +
                    "in vec2 aPosition;\n" +
                    "in vec2 aTexCoord;\n" +
                    "out vec2 vTexCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
                    "    vTexCoord = aTexCoord;\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "in vec2 vTexCoord;\n" +
                    "uniform sampler2D uTexture;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    float coverage = texture(uTexture, vTexCoord).r;\n" +
                    "    if (coverage < 0.02) discard;\n" +
                    "    fragColor = vec4(1.0, 1.0, 1.0, coverage);\n" +
                    "}\n";
}
