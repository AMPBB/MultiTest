package pbbadd.opengl.multitest.gpufontrasterizerreplay;

import android.content.Context;
import android.content.res.AssetManager;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GpuFontRasterizerReplayView extends GLSurfaceView implements GLSurfaceView.Renderer {
    private static final String TAG = "GpuFontReplayView";
    private static final String TEXTURE_ASSET = "gpufontrasterizer_texture";
    private static final String BUFFER_ASSET = "gpufontrasterizer_bufferdata";
    private static final int TEXTURE_WIDTH = 512;
    private static final int FONT_VERTEX_STRIDE_BYTES = 16;
    private static final int BYTES_PER_SHORT = 2;

    private final ReplayData replayData;

    private int program;
    private int textureId;
    private int vertexBufferId;
    private int indexBufferId;
    private int positionLocation;
    private int colorLocation;
    private int texCoordLocation;
    private int samplerLocation;
    private int viewSizeLocation;
    private int textureSizeLocation;
    private int boundsLocation;
    private int fitToBoundsLocation;
    private int viewWidth;
    private int viewHeight;
    private volatile boolean fitToBounds = true;

    public GpuFontRasterizerReplayView(Context context) {
        this(context, null);
    }

    public GpuFontRasterizerReplayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        try {
            replayData = loadReplayData(context.getAssets());
        } catch (IOException e) {
            throw new IllegalStateException("load gpu font rasterizer replay assets failed", e);
        }
        init();
    }

    public String getReplayInfo() {
        return replayData.infoText;
    }

    public void setFitToBounds(boolean fitToBounds) {
        this.fitToBounds = fitToBounds;
        requestRender();
    }

    private void init() {
        setEGLContextClientVersion(3);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.i(TAG, "gles version=" + GLES30.glGetString(GLES30.GL_VERSION));
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        positionLocation = GLES30.glGetAttribLocation(program, "aPositionPx");
        colorLocation = GLES30.glGetAttribLocation(program, "aColor");
        texCoordLocation = GLES30.glGetAttribLocation(program, "aTexCoordPx");
        samplerLocation = GLES30.glGetUniformLocation(program, "uTexture");
        viewSizeLocation = GLES30.glGetUniformLocation(program, "uViewSize");
        textureSizeLocation = GLES30.glGetUniformLocation(program, "uTextureSize");
        boundsLocation = GLES30.glGetUniformLocation(program, "uBounds");
        fitToBoundsLocation = GLES30.glGetUniformLocation(program, "uFitToBounds");

        textureId = createTexture();
        vertexBufferId = createVertexBuffer();
        indexBufferId = createIndexBuffer();

        GLES30.glClearColor(0.02f, 0.02f, 0.025f, 1.0f);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewWidth = width;
        viewHeight = height;
        GLES30.glViewport(0, 0, width, height);
        requestRender();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        if (program == 0 || replayData.indexCount == 0 || viewWidth <= 0 || viewHeight <= 0) {
            return;
        }

        GLES30.glUseProgram(program);
        GLES30.glUniform2f(viewSizeLocation, viewWidth, viewHeight);
        GLES30.glUniform2f(textureSizeLocation, TEXTURE_WIDTH, replayData.textureHeight);
        GLES30.glUniform4f(boundsLocation,
                replayData.minX, replayData.minY, replayData.maxX, replayData.maxY);
        GLES30.glUniform1i(fitToBoundsLocation, fitToBounds ? 1 : 0);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
        GLES30.glUniform1i(samplerLocation, 0);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBufferId);
        GLES30.glEnableVertexAttribArray(positionLocation);
        GLES30.glVertexAttribPointer(positionLocation, 2, GLES30.GL_FLOAT,
                false, FONT_VERTEX_STRIDE_BYTES, 0);
        GLES30.glEnableVertexAttribArray(colorLocation);
        GLES30.glVertexAttribPointer(colorLocation, 4, GLES30.GL_UNSIGNED_BYTE,
                true, FONT_VERTEX_STRIDE_BYTES, 8);
        GLES30.glEnableVertexAttribArray(texCoordLocation);
        GLES30.glVertexAttribIPointer(texCoordLocation, 2, GLES30.GL_UNSIGNED_SHORT,
                FONT_VERTEX_STRIDE_BYTES, 12);

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, replayData.indexCount,
                GLES30.GL_UNSIGNED_SHORT, 0);

        GLES30.glDisableVertexAttribArray(positionLocation);
        GLES30.glDisableVertexAttribArray(colorLocation);
        GLES30.glDisableVertexAttribArray(texCoordLocation);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    private int createTexture() {
        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0]);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1);

        ByteBuffer textureBuffer = ByteBuffer.allocateDirect(replayData.texturePixels.length);
        textureBuffer.put(replayData.texturePixels);
        textureBuffer.position(0);
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8,
                TEXTURE_WIDTH, replayData.textureHeight, 0,
                GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, textureBuffer);
        return textures[0];
    }

    private int createVertexBuffer() {
        ByteBuffer vertexBuffer = ByteBuffer.allocateDirect(replayData.vertexBytes.length)
                .order(ByteOrder.nativeOrder());
        vertexBuffer.put(replayData.vertexBytes);
        vertexBuffer.position(0);

        int[] buffers = new int[1];
        GLES30.glGenBuffers(1, buffers, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, replayData.vertexBytes.length,
                vertexBuffer, GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        return buffers[0];
    }

    private int createIndexBuffer() {
        ByteBuffer indexByteBuffer = ByteBuffer.allocateDirect(replayData.indexCount * BYTES_PER_SHORT)
                .order(ByteOrder.nativeOrder());
        ShortBuffer indexBuffer = indexByteBuffer.asShortBuffer();
        for (int quad = 0; quad < replayData.quadCount; quad++) {
            int base = quad * 4;
            indexBuffer.put((short) base);
            indexBuffer.put((short) (base + 1));
            indexBuffer.put((short) (base + 2));
            indexBuffer.put((short) (base + 2));
            indexBuffer.put((short) (base + 1));
            indexBuffer.put((short) (base + 3));
        }
        indexBuffer.position(0);

        int[] buffers = new int[1];
        GLES30.glGenBuffers(1, buffers, 0);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[0]);
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,
                replayData.indexCount * BYTES_PER_SHORT, indexBuffer, GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);
        return buffers[0];
    }

    private static ReplayData loadReplayData(AssetManager assetManager) throws IOException {
        byte[] bufferBytes = readAsset(assetManager, BUFFER_ASSET);
        if (bufferBytes.length < FONT_VERTEX_STRIDE_BYTES) {
            throw new IOException("empty font vertex buffer asset");
        }

        int vertexCount = bufferBytes.length / FONT_VERTEX_STRIDE_BYTES;
        int quadCount = vertexCount / 4;
        int indexCount = quadCount * 6;
        Bounds bounds = parseBounds(bufferBytes, vertexCount);

        byte[] textureRawBytes = readAsset(assetManager, TEXTURE_ASSET);
        TextureData textureData = decodeTexture(textureRawBytes);

        String infoText = String.format(Locale.US,
                "texture=%dx%d GL_RED, vertex=%d, quad=%d, index=%d, bounds=(%.0f,%.0f)-(%.0f,%.0f), uvMax=(%d,%d)",
                TEXTURE_WIDTH,
                textureData.height,
                vertexCount,
                quadCount,
                indexCount,
                bounds.minX,
                bounds.minY,
                bounds.maxX,
                bounds.maxY,
                bounds.maxU,
                bounds.maxV);
        Log.i(TAG, infoText);

        return new ReplayData(bufferBytes, textureData.pixels, textureData.height,
                vertexCount, quadCount, indexCount, bounds, infoText);
    }

    private static TextureData decodeTexture(byte[] rawBytes) throws IOException {
        if (rawBytes.length % TEXTURE_WIDTH != 0) {
            throw new IOException("texture asset size is not aligned to width 512: " + rawBytes.length);
        }

        return new TextureData(rawBytes, rawBytes.length / TEXTURE_WIDTH);
    }

    private static Bounds parseBounds(byte[] bufferBytes, int vertexCount) {
        ByteBuffer buffer = ByteBuffer.wrap(bufferBytes).order(ByteOrder.LITTLE_ENDIAN);
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        int maxU = 0;
        int maxV = 0;

        for (int i = 0; i < vertexCount; i++) {
            int offset = i * FONT_VERTEX_STRIDE_BYTES;
            float x = buffer.getFloat(offset);
            float y = buffer.getFloat(offset + 4);
            int u = buffer.getShort(offset + 12) & 0xffff;
            int v = buffer.getShort(offset + 14) & 0xffff;

            if (Float.isFinite(x)) {
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
            }
            if (Float.isFinite(y)) {
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
            maxU = Math.max(maxU, u);
            maxV = Math.max(maxV, v);
        }

        return new Bounds(minX, minY, maxX, maxY, maxU, maxV);
    }

    private static byte[] readAsset(AssetManager assetManager, String name) throws IOException {
        InputStream inputStream = assetManager.open(name);
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        } finally {
            inputStream.close();
        }
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

    private static final class ReplayData {
        final byte[] vertexBytes;
        final byte[] texturePixels;
        final int textureHeight;
        final int vertexCount;
        final int quadCount;
        final int indexCount;
        final float minX;
        final float minY;
        final float maxX;
        final float maxY;
        final String infoText;

        ReplayData(byte[] vertexBytes, byte[] texturePixels, int textureHeight,
                   int vertexCount, int quadCount, int indexCount,
                   Bounds bounds, String infoText) {
            this.vertexBytes = vertexBytes;
            this.texturePixels = texturePixels;
            this.textureHeight = textureHeight;
            this.vertexCount = vertexCount;
            this.quadCount = quadCount;
            this.indexCount = indexCount;
            this.minX = bounds.minX;
            this.minY = bounds.minY;
            this.maxX = bounds.maxX;
            this.maxY = bounds.maxY;
            this.infoText = infoText;
        }
    }

    private static final class TextureData {
        final byte[] pixels;
        final int height;

        TextureData(byte[] pixels, int height) {
            this.pixels = pixels;
            this.height = height;
        }
    }

    private static final class Bounds {
        final float minX;
        final float minY;
        final float maxX;
        final float maxY;
        final int maxU;
        final int maxV;

        Bounds(float minX, float minY, float maxX, float maxY, int maxU, int maxV) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxU = maxU;
            this.maxV = maxV;
        }
    }

    private static final String VERTEX_SHADER =
            "#version 300 es\n" +
                    "precision highp float;\n" +
                    "precision highp int;\n" +
                    "in vec2 aPositionPx;\n" +
                    "in vec4 aColor;\n" +
                    "in uvec2 aTexCoordPx;\n" +
                    "uniform vec2 uViewSize;\n" +
                    "uniform vec2 uTextureSize;\n" +
                    "uniform vec4 uBounds;\n" +
                    "uniform int uFitToBounds;\n" +
                    "out vec4 vColor;\n" +
                    "out vec2 vTexCoord;\n" +
                    "void main() {\n" +
                    "    vec2 p = aPositionPx;\n" +
                    "    if (uFitToBounds != 0) {\n" +
                    "        vec2 boundsMin = uBounds.xy;\n" +
                    "        vec2 boundsMax = uBounds.zw;\n" +
                    "        vec2 boundsSize = max(boundsMax - boundsMin, vec2(1.0));\n" +
                    "        vec2 targetSize = max(uViewSize - vec2(48.0), vec2(1.0));\n" +
                    "        float scale = min(targetSize.x / boundsSize.x, targetSize.y / boundsSize.y);\n" +
                    "        vec2 fittedSize = boundsSize * scale;\n" +
                    "        vec2 offset = (uViewSize - fittedSize) * 0.5;\n" +
                    "        p = (p - boundsMin) * scale + offset;\n" +
                    "    }\n" +
                    "    vec2 ndc = vec2(p.x * 2.0 / uViewSize.x - 1.0,\n" +
                    "                    1.0 - p.y * 2.0 / uViewSize.y);\n" +
                    "    gl_Position = vec4(ndc, 0.0, 1.0);\n" +
                    "    vTexCoord = vec2(aTexCoordPx) / uTextureSize;\n" +
                    "    vColor = aColor;\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "in vec4 vColor;\n" +
                    "in vec2 vTexCoord;\n" +
                    "uniform sampler2D uTexture;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    float coverage = texture(uTexture, vTexCoord).r;\n" +
                    "    if (coverage < 0.01) {\n" +
                    "        discard;\n" +
                    "    }\n" +
                    "    fragColor = vec4(vColor.rgb, vColor.a * coverage);\n" +
                    "}\n";
}
