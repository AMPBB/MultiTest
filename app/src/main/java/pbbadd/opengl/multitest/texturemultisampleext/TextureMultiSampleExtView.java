package pbbadd.opengl.multitest.texturemultisampleext;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES32;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;

public class TextureMultiSampleExtView extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "TextureMultiSampleExt";
    private static final String REQUIRED_EXTENSION =
            "GL_EXT_multisampled_render_to_texture";

    private static final int OFFSCREEN_WIDTH = 512;
    private static final int OFFSCREEN_HEIGHT = 512;
    private static final int REQUESTED_SAMPLES = 4;
    private static final int EGL_OPENGL_ES3_BIT_KHR = 0x0040;

    static {
        System.loadLibrary("gles30testdemo");
    }

    private final Object frameLock = new Object();
    private final FloatBuffer scenePositions = createFloatBuffer(new float[]{
            -0.78f, -0.68f,
             0.78f, -0.68f,
             0.00f,  0.82f
    });
    private final FloatBuffer sceneColors = createFloatBuffer(new float[]{
            1.0f, 0.15f, 0.10f, 1.0f,
            0.10f, 0.85f, 0.25f, 1.0f,
            0.15f, 0.35f, 1.00f, 1.0f
    });
    private final FloatBuffer blitPositions = createFloatBuffer(new float[]{
            -1.0f,  1.0f,
             1.0f,  1.0f,
            -1.0f, -1.0f,
             1.0f, -1.0f
    });
    private final FloatBuffer blitTexCoords = createFloatBuffer(new float[]{
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
    });

    private ResultListener resultListener;
    private String initializationResult = "waiting for EGL contexts...";
    private Surface windowSurface;
    private int viewWidth;
    private int viewHeight;
    private boolean surfaceAvailable;
    private boolean renderingEnabled = true;

    private Thread mainGlThread;
    private Thread multisampleGlThread;
    private volatile boolean stopRequested;
    private boolean mainContextReady;
    private boolean mainContextFailed;
    private boolean frameReady;
    private boolean extensionSupported;
    private boolean procSupported;
    private boolean renderbufferProcSupported;

    public TextureMultiSampleExtView(Context context) {
        this(context, null);
    }

    public TextureMultiSampleExtView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
    }

    public void setResultListener(ResultListener listener) {
        resultListener = listener;
        if (listener != null) {
            post(() -> listener.onResult(initializationResult));
        }
    }

    public void startRendering() {
        synchronized (frameLock) {
            renderingEnabled = true;
            frameLock.notifyAll();
        }
    }

    public void pauseRendering() {
        synchronized (frameLock) {
            renderingEnabled = false;
            frameLock.notifyAll();
        }
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        windowSurface = holder.getSurface();
        surfaceAvailable = true;
        startGlThreads();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        synchronized (frameLock) {
            viewWidth = width;
            viewHeight = height;
            frameLock.notifyAll();
        }
        Log.i(TAG, "window surface size: " + width + "x" + height);
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        surfaceAvailable = false;
        stopGlThreads();
        windowSurface = null;
    }

    private void startGlThreads() {
        stopRequested = false;
        frameReady = false;
        mainContextReady = false;
        mainContextFailed = false;

        mainGlThread = new Thread(this::mainGlLoop, "MainGlThread");
        multisampleGlThread = new Thread(this::multisampleGlLoop, "MultisampleGlThread");
        mainGlThread.start();
        multisampleGlThread.start();
    }

    private void stopGlThreads() {
        synchronized (frameLock) {
            stopRequested = true;
            frameLock.notifyAll();
        }

        joinThread(multisampleGlThread);
        joinThread(mainGlThread);
        mainGlThread = null;
        multisampleGlThread = null;
    }

    private void joinThread(Thread thread) {
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        try {
            thread.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void mainGlLoop() {
        MainEglState egl = new MainEglState();
        try {
            if (!egl.initialize(windowSurface)) {
                publishResult("MainGlThread EGL initialization failed: "
                        + eglErrorString());
                signalMainContextFailed();
                return;
            }

            int blitProgram = createProgram(BLIT_VERTEX_SHADER, BLIT_FRAGMENT_SHADER);
            if (blitProgram == 0) {
                publishResult("MainGlThread blit shader initialization failed");
                signalMainContextFailed();
                return;
            }

            synchronized (frameLock) {
                eglDisplay = egl.display;
                eglConfig = egl.config;
                mainContext = egl.context;
                mainContextReady = true;
                frameLock.notifyAll();
            }

            Log.i(TAG, "MainGlThread: window EGLContext is ready");

            while (!isStopRequested()) {
                synchronized (frameLock) {
                    while (!stopRequested
                            && (!renderingEnabled
                            || !frameReady
                            || viewWidth <= 0
                            || viewHeight <= 0)) {
                        waitForFrameChange();
                    }
                    if (stopRequested) {
                        // If a frame is waiting but the main thread has not
                        // started drawing it, release the worker handshake.
                        frameReady = false;
                        frameLock.notifyAll();
                        break;
                    }
                }

                // This is the only thread that owns the window surface.
                // The multisample thread never binds framebuffer 0.
                GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, 0);
                LogFrameBindingOnce.logMainFramebuffer0();
                GLES32.glViewport(0, 0, viewWidth, viewHeight);
                GLES32.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT);
                drawOffscreenTexture(blitProgram, sharedOffscreenTexture);
                GLES32.glFinish();

                if (!EGL14.eglSwapBuffers(egl.display, egl.surface)) {
                    Log.e(TAG, "MainGlThread eglSwapBuffers failed: " + eglErrorString());
                    requestStop();
                    break;
                }

                synchronized (frameLock) {
                    frameReady = false;
                    frameLock.notifyAll();
                }
            }

            GLES32.glDeleteProgram(blitProgram);
            GLES32.glFinish();
        } finally {
            synchronized (frameLock) {
                stopRequested = true;
                frameReady = false;
                mainContextReady = false;
                frameLock.notifyAll();
            }
            // Keep the main EGLDisplay alive until the shared worker context
            // has deleted its FBO and texture.
            joinThread(multisampleGlThread);
            egl.release();
            Log.i(TAG, "MainGlThread stopped");
        }
    }

    private void multisampleGlLoop() {
        WorkerEglState egl = new WorkerEglState();
        try {
            synchronized (frameLock) {
                while (!stopRequested && !mainContextReady && !mainContextFailed) {
                    waitForFrameChange();
                }
                if (stopRequested || mainContextFailed) {
                    return;
                }
            }

            if (!egl.initialize(eglDisplay, eglConfig, mainContext)) {
                publishResult("MultisampleGlThread EGL initialization failed: "
                        + eglErrorString());
                return;
            }

            String extensions = GLES32.glGetString(GLES32.GL_EXTENSIONS);
            extensionSupported = extensions != null && extensions.contains(REQUIRED_EXTENSION);
            procSupported = nativeHasFramebufferTexture2DMultisampleEXT();
            renderbufferProcSupported = nativeHasRenderbufferStorageMultisampleEXT();
            String glVersion = GLES32.glGetString(GLES32.GL_VERSION);
            String glslVersion = GLES32.glGetString(GLES32.GL_SHADING_LANGUAGE_VERSION);
            int sceneProgram = createProgram(SCENE_VERTEX_SHADER, SCENE_FRAGMENT_SHADER);
            int textureProgram = createProgram(BLIT_VERTEX_SHADER, BLIT_FRAGMENT_SHADER);
            if (sceneProgram == 0 || textureProgram == 0) {
                publishResult("MultisampleGlThread scene shader initialization failed");
                return;
            }

            int[] fboTextures = new int[1];
            GLES32.glGenTextures(1, fboTextures, 0);
            int fboTexture = fboTextures[0];
            Log.i(TAG, "MultisampleGlThread: glGenTextures(fbo_tex=" + fboTexture + ")");
            createFboTexture(fboTexture);

            int[] fbos = new int[1];
            GLES32.glGenFramebuffers(1, fbos, 0);
            int offscreenFbo = fbos[0];
            Log.i(TAG, "MultisampleGlThread: glGenFramebuffers(fbo_offscreen="
                    + offscreenFbo + ")");

            int[] drawTextures = new int[1];
            GLES32.glGenTextures(1, drawTextures, 0);
            int drawTexture = drawTextures[0];
            Log.i(TAG, "MultisampleGlThread: glGenTextures(tex_draw=" + drawTexture + ")");
            ByteBuffer drawPixels = createDrawTexturePixels();
            createDrawTexture(drawTexture);
            uploadDrawTexture(drawTexture, drawPixels);

            GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, offscreenFbo);
            Log.i(TAG, "MultisampleGlThread: glBindFramebuffer(offscreenFbo="
                    + offscreenFbo + ")");

            int[] renderbuffers = new int[1];
            GLES32.glGenRenderbuffers(1, renderbuffers, 0);
            int depthRenderbuffer = renderbuffers[0];
            Log.i(TAG, "MultisampleGlThread: glGenRenderbuffers(depth="
                    + depthRenderbuffer + ")");
            boolean wantsMultisample = extensionSupported && procSupported;
            boolean usingRenderbufferExtension =
                    updateDepthRenderbufferStorage(depthRenderbuffer, wantsMultisample);
            boolean usingExtension = attachOffscreenTexture(fboTexture);
            if (usingExtension != wantsMultisample) {
                usingRenderbufferExtension =
                        updateDepthRenderbufferStorage(depthRenderbuffer, usingExtension);
            }

            int fboStatus = GLES32.glCheckFramebufferStatus(GLES32.GL_FRAMEBUFFER);
            boolean fboComplete = fboStatus == GLES32.GL_FRAMEBUFFER_COMPLETE;
            Log.i(TAG, "MultisampleGlThread: glCheckFramebufferStatus=0x"
                    + Integer.toHexString(fboStatus));

            synchronized (frameLock) {
                sharedOffscreenTexture = fboTexture;
                frameLock.notifyAll();
            }

            String result = String.format(Locale.US,
                    "GL_EXT_multisampled_render_to_texture: %s\n"
                            + "GL_VERSION: %s\n"
                            + "GLSL_VERSION: %s\n"
                            + "native glFramebufferTexture2DMultisampleEXT: %s\n"
                            + "native glRenderbufferStorageMultisampleEXT: %s\n"
                            + "fbo_tex/shared_tex: %d, %dx%d RGBA\n"
                            + "tex_draw: %d, immutable %dx%d RGBA8\n"
                            + "depth renderbuffer: %dx%d DEPTH_COMPONENT16\n"
                            + "requested samples: %d\n"
                            + "FBO mode: %s\n"
                            + "Renderbuffer mode: %s\n"
                            + "FBO status: %s\n"
                            + "contexts: shared EGLContext\n\n"
                            + "MultisampleGlThread:\n"
                            + "  glBindFramebuffer(offscreenFbo)\n"
                            + "  glFramebufferTexture2DMultisampleEXT\n"
                            + "  glBindRenderbuffer\n"
                            + "  glRenderbufferStorageMultisampleEXT\n"
                            + "  glFramebufferRenderbuffer(GL_DEPTH_ATTACHMENT)\n"
                            + "  bind tex_draw + glDrawArrays(texture)\n"
                            + "  glDrawArrays(scene)\n"
                            + "  glFinish()\n\n"
                            + "MainGlThread:\n"
                            + "  glBindFramebuffer(0)\n"
                            + "  bind shared_tex(fbo_tex)\n"
                            + "  glDrawArrays(texture)\n"
                            + "  glFinish()\n"
                            + "  eglSwapBuffers(windowSurface)",
                    extensionSupported ? "yes" : "no",
                    glVersion,
                    glslVersion,
                    procSupported ? "yes" : "no",
                    renderbufferProcSupported ? "yes" : "no",
                    fboTexture,
                    OFFSCREEN_WIDTH,
                    OFFSCREEN_HEIGHT,
                    drawTexture,
                    OFFSCREEN_WIDTH,
                    OFFSCREEN_HEIGHT,
                    OFFSCREEN_WIDTH,
                    OFFSCREEN_HEIGHT,
                    REQUESTED_SAMPLES,
                    usingExtension ? "EXT multisampled texture" : "regular texture FBO fallback",
                    usingRenderbufferExtension
                            ? "EXT multisampled renderbuffer"
                            : "regular renderbuffer or skipped fallback",
                    fboComplete ? "complete" : "incomplete");
            initializationResult = result;
            Log.i(TAG, result);
            publishResult(result);

            float angle = 0.0f;
            while (!isStopRequested()) {
                synchronized (frameLock) {
                    while (!stopRequested && (!renderingEnabled || frameReady)) {
                        waitForFrameChange();
                    }
                    if (stopRequested) {
                        break;
                    }
                }

                angle += 0.02f;
                GLES32.glBindFramebuffer(GLES32.GL_FRAMEBUFFER, offscreenFbo);
                LogFrameBindingOnce.logMultisampleFramebuffer(offscreenFbo);
                boolean frameWantsMultisample = extensionSupported && procSupported;
                updateDepthRenderbufferStorage(depthRenderbuffer, frameWantsMultisample);
                usingExtension = attachOffscreenTexture(fboTexture);
                if (usingExtension != frameWantsMultisample) {
                    updateDepthRenderbufferStorage(depthRenderbuffer, usingExtension);
                }
                GLES32.glViewport(0, 0, OFFSCREEN_WIDTH, OFFSCREEN_HEIGHT);
                GLES32.glClearColor(0.04f, 0.05f, 0.09f, 1.0f);
                GLES32.glEnable(GLES32.GL_DEPTH_TEST);
                GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT | GLES32.GL_DEPTH_BUFFER_BIT);
//                drawOffscreenTexture(textureProgram, drawTexture);
                GLES32.glEnable(GLES32.GL_DEPTH_TEST);
                drawScene(sceneProgram, angle);
                GLES32.glFinish();

                synchronized (frameLock) {
                    if (stopRequested) {
                        break;
                    }
                    frameReady = true;
                    frameLock.notifyAll();
                    while (!stopRequested && frameReady) {
                        waitForFrameChange();
                    }
                    while (stopRequested && frameReady) {
                        waitForFrameChange();
                    }
                }
            }

            GLES32.glDeleteFramebuffers(1, new int[]{offscreenFbo}, 0);
            GLES32.glDeleteRenderbuffers(1, new int[]{depthRenderbuffer}, 0);
            GLES32.glDeleteTextures(1, new int[]{drawTexture}, 0);
            GLES32.glDeleteTextures(1, new int[]{fboTexture}, 0);
            GLES32.glDeleteProgram(textureProgram);
            GLES32.glDeleteProgram(sceneProgram);
            GLES32.glFinish();
        } finally {
            synchronized (frameLock) {
                frameLock.notifyAll();
            }
            egl.release();
            Log.i(TAG, "MultisampleGlThread stopped");
        }
    }

    private void createFboTexture(int texture) {
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, texture);
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER,
                GLES32.GL_LINEAR);
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER,
                GLES32.GL_LINEAR);
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_S,
                GLES32.GL_CLAMP_TO_EDGE);
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_T,
                GLES32.GL_CLAMP_TO_EDGE);
        Log.i(TAG, "MultisampleGlThread: glTexImage2D(texture=" + texture
                + ", width=" + OFFSCREEN_WIDTH
                + ", height=" + OFFSCREEN_HEIGHT + ")");
        GLES32.glTexImage2D(
                GLES32.GL_TEXTURE_2D,
                0,
                GLES32.GL_RGBA,
                OFFSCREEN_WIDTH,
                OFFSCREEN_HEIGHT,
                0,
                GLES32.GL_RGBA,
                GLES32.GL_UNSIGNED_BYTE,
                null);
    }

    private void createDrawTexture(int texture) {
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, texture);
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MIN_FILTER,
                GLES32.GL_LINEAR);
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_MAG_FILTER,
                GLES32.GL_LINEAR);
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_S,
                GLES32.GL_CLAMP_TO_EDGE);
        GLES32.glTexParameteri(GLES32.GL_TEXTURE_2D, GLES32.GL_TEXTURE_WRAP_T,
                GLES32.GL_CLAMP_TO_EDGE);
        Log.i(TAG, "MultisampleGlThread: glTexStorage2D(tex_draw=" + texture
                + ", levels=1, internalformat=GL_RGBA8"
                + ", width=" + OFFSCREEN_WIDTH
                + ", height=" + OFFSCREEN_HEIGHT + ")");
        GLES32.glTexStorage2D(
                GLES32.GL_TEXTURE_2D,
                1,
                GLES32.GL_RGBA8,
                OFFSCREEN_WIDTH,
                OFFSCREEN_HEIGHT);
    }

    private void uploadDrawTexture(int texture, ByteBuffer pixels) {
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, texture);
        pixels.position(0);
        Log.i(TAG, "MultisampleGlThread: glTexSubImage2D(tex_draw=" + texture
                + ", width=" + OFFSCREEN_WIDTH
                + ", height=" + OFFSCREEN_HEIGHT
                + ", format=GL_RGBA)");
        GLES32.glTexSubImage2D(
                GLES32.GL_TEXTURE_2D,
                0,
                0,
                0,
                OFFSCREEN_WIDTH,
                OFFSCREEN_HEIGHT,
                GLES32.GL_RGBA,
                GLES32.GL_UNSIGNED_BYTE,
                pixels);
    }

    private ByteBuffer createDrawTexturePixels() {
        ByteBuffer pixels = ByteBuffer
                .allocateDirect(OFFSCREEN_WIDTH * OFFSCREEN_HEIGHT * 4)
                .order(ByteOrder.nativeOrder());
        for (int y = 0; y < OFFSCREEN_HEIGHT; y++) {
            for (int x = 0; x < OFFSCREEN_WIDTH; x++) {
                int cell = ((x / 32) + (y / 32)) & 1;
                int red = cell == 0 ? 28 : 210;
                int green = cell == 0 ? 88 : 150;
                int blue = cell == 0 ? 180 : 54;
                pixels.put((byte) red);
                pixels.put((byte) green);
                pixels.put((byte) blue);
                pixels.put((byte) 255);
            }
        }
        pixels.position(0);
        return pixels;
    }

    private boolean attachOffscreenTexture(int texture) {
        if (extensionSupported && procSupported) {
            Log.i(TAG, "MultisampleGlThread: glFramebufferTexture2DMultisampleEXT("
                    + "texture=" + texture
                    + ", samples=" + REQUESTED_SAMPLES + ")");
            nativeFramebufferTexture2DMultisampleEXT(
                    GLES32.GL_FRAMEBUFFER,
                    GLES32.GL_COLOR_ATTACHMENT0,
                    GLES32.GL_TEXTURE_2D,
                    texture,
                    0,
                    REQUESTED_SAMPLES);
            int error = GLES32.glGetError();
            if (error == GLES32.GL_NO_ERROR) {
                return true;
            }
            Log.e(TAG, "MultisampleGlThread extension call failed: "
                    + errorToString(error));
        }

        Log.i(TAG, "MultisampleGlThread: glFramebufferTexture2D(texture=" + texture + ")");
        GLES32.glFramebufferTexture2D(
                GLES32.GL_FRAMEBUFFER,
                GLES32.GL_COLOR_ATTACHMENT0,
                GLES32.GL_TEXTURE_2D,
                texture,
                0);
        return false;
    }

    private boolean updateDepthRenderbufferStorage(int renderbuffer, boolean colorIsMultisampled) {
        GLES32.glBindRenderbuffer(GLES32.GL_RENDERBUFFER, renderbuffer);

        if (colorIsMultisampled && extensionSupported && renderbufferProcSupported) {
            Log.i(TAG, "MultisampleGlThread: glRenderbufferStorageMultisampleEXT("
                    + "renderbuffer=" + renderbuffer
                    + ", samples=" + REQUESTED_SAMPLES
                    + ", internalformat=GL_DEPTH_COMPONENT16"
                    + ", width=" + OFFSCREEN_WIDTH
                    + ", height=" + OFFSCREEN_HEIGHT + ")");
            nativeRenderbufferStorageMultisampleEXT(
                    GLES32.GL_RENDERBUFFER,
                    REQUESTED_SAMPLES,
                    GLES32.GL_DEPTH_COMPONENT16,
                    OFFSCREEN_WIDTH,
                    OFFSCREEN_HEIGHT);
            int error = GLES32.glGetError();
            if (error == GLES32.GL_NO_ERROR) {
                attachDepthRenderbuffer(renderbuffer);
                return true;
            }
            Log.e(TAG, "MultisampleGlThread renderbuffer extension call failed: "
                    + errorToString(error));
        }

        if (colorIsMultisampled) {
            Log.w(TAG, "MultisampleGlThread: skip regular depth renderbuffer because "
                    + "color attachment is multisampled");
            attachDepthRenderbuffer(0);
            return false;
        }

        Log.i(TAG, "MultisampleGlThread: glRenderbufferStorage("
                + "renderbuffer=" + renderbuffer
                + ", internalformat=GL_DEPTH_COMPONENT16"
                + ", width=" + OFFSCREEN_WIDTH
                + ", height=" + OFFSCREEN_HEIGHT + ")");
        GLES32.glRenderbufferStorage(
                GLES32.GL_RENDERBUFFER,
                GLES32.GL_DEPTH_COMPONENT16,
                OFFSCREEN_WIDTH,
                OFFSCREEN_HEIGHT);
        attachDepthRenderbuffer(renderbuffer);
        return false;
    }

    private void attachDepthRenderbuffer(int renderbuffer) {
        Log.i(TAG, "MultisampleGlThread: glFramebufferRenderbuffer("
                + "attachment=GL_DEPTH_ATTACHMENT, renderbuffer=" + renderbuffer + ")");
        GLES32.glFramebufferRenderbuffer(
                GLES32.GL_FRAMEBUFFER,
                GLES32.GL_DEPTH_ATTACHMENT,
                GLES32.GL_RENDERBUFFER,
                renderbuffer);
    }

    private void drawScene(int program, float angle) {
        GLES32.glUseProgram(program);

        int positionLocation = GLES32.glGetAttribLocation(program, "aPosition");
        int colorLocation = GLES32.glGetAttribLocation(program, "aColor");
        int angleLocation = GLES32.glGetUniformLocation(program, "uAngle");

        scenePositions.position(0);
        GLES32.glVertexAttribPointer(positionLocation, 2, GLES32.GL_FLOAT, false,
                0, scenePositions);
        GLES32.glEnableVertexAttribArray(positionLocation);

        sceneColors.position(0);
        GLES32.glVertexAttribPointer(colorLocation, 4, GLES32.GL_FLOAT, false,
                0, sceneColors);
        GLES32.glEnableVertexAttribArray(colorLocation);

        GLES32.glUniform1f(angleLocation, angle);
        GLES32.glDrawArrays(GLES32.GL_TRIANGLES, 0, 3);

        GLES32.glDisableVertexAttribArray(positionLocation);
        GLES32.glDisableVertexAttribArray(colorLocation);
    }

    private void drawOffscreenTexture(int program, int texture) {
        GLES32.glDisable(GLES32.GL_DEPTH_TEST);
        GLES32.glUseProgram(program);

        int positionLocation = GLES32.glGetAttribLocation(program, "aPosition");
        int texCoordLocation = GLES32.glGetAttribLocation(program, "aTexCoord");
        int textureLocation = GLES32.glGetUniformLocation(program, "uTexture");

        blitPositions.position(0);
        GLES32.glVertexAttribPointer(positionLocation, 2, GLES32.GL_FLOAT, false,
                0, blitPositions);
        GLES32.glEnableVertexAttribArray(positionLocation);

        blitTexCoords.position(0);
        GLES32.glVertexAttribPointer(texCoordLocation, 2, GLES32.GL_FLOAT, false,
                0, blitTexCoords);
        GLES32.glEnableVertexAttribArray(texCoordLocation);

        GLES32.glActiveTexture(GLES32.GL_TEXTURE0);
        GLES32.glBindTexture(GLES32.GL_TEXTURE_2D, texture);
        GLES32.glUniform1i(textureLocation, 0);
        GLES32.glDrawArrays(GLES32.GL_TRIANGLE_STRIP, 0, 4);

        GLES32.glDisableVertexAttribArray(positionLocation);
        GLES32.glDisableVertexAttribArray(texCoordLocation);
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(GLES32.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compileShader(GLES32.GL_FRAGMENT_SHADER, fragmentSource);
        if (vertexShader == 0 || fragmentShader == 0) {
            return 0;
        }

        int program = GLES32.glCreateProgram();
        GLES32.glAttachShader(program, vertexShader);
        GLES32.glAttachShader(program, fragmentShader);
        GLES32.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES32.glGetProgramiv(program, GLES32.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG, "program link failed: " + GLES32.glGetProgramInfoLog(program));
            GLES32.glDeleteProgram(program);
            program = 0;
        }

        GLES32.glDeleteShader(vertexShader);
        GLES32.glDeleteShader(fragmentShader);
        return program;
    }

    private int compileShader(int type, String source) {
        int shader = GLES32.glCreateShader(type);
        GLES32.glShaderSource(shader, source);
        GLES32.glCompileShader(shader);

        int[] compileStatus = new int[1];
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            Log.e(TAG, "shader compile failed: " + GLES32.glGetShaderInfoLog(shader));
            GLES32.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private void publishResult(String result) {
        initializationResult = result;
        ResultListener listener = resultListener;
        if (listener != null) {
            post(() -> listener.onResult(result));
        }
    }

    private void signalMainContextFailed() {
        synchronized (frameLock) {
            mainContextFailed = true;
            mainContextReady = true;
            frameLock.notifyAll();
        }
    }

    private boolean isStopRequested() {
        return stopRequested;
    }

    private void requestStop() {
        synchronized (frameLock) {
            stopRequested = true;
            frameReady = false;
            frameLock.notifyAll();
        }
    }

    private void waitForFrameChange() {
        try {
            frameLock.wait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopRequested = true;
        }
    }

    private void resetEglFields() {
        synchronized (frameLock) {
            eglDisplay = EGL14.EGL_NO_DISPLAY;
            eglConfig = null;
            mainContext = EGL14.EGL_NO_CONTEXT;
        }
    }

    private static String eglErrorString() {
        return "0x" + Integer.toHexString(EGL14.eglGetError());
    }

    private static String errorToString(int error) {
        return "0x" + Integer.toHexString(error);
    }

    private static FloatBuffer createFloatBuffer(float[] values) {
        FloatBuffer buffer = ByteBuffer
                .allocateDirect(values.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buffer.put(values).position(0);
        return buffer;
    }

    private static final class LogFrameBindingOnce {
        private static boolean mainLogged;
        private static boolean multisampleLogged;

        private static synchronized void logMainFramebuffer0() {
            if (!mainLogged) {
                Log.i(TAG, "MainGlThread: glBindFramebuffer(GL_FRAMEBUFFER, 0)");
                mainLogged = true;
            }
        }

        private static synchronized void logMultisampleFramebuffer(int fbo) {
            if (!multisampleLogged) {
                Log.i(TAG, "MultisampleGlThread: glBindFramebuffer(GL_FRAMEBUFFER, "
                        + fbo + ")");
                multisampleLogged = true;
            }
        }
    }

    private final class MainEglState {
        private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
        private EGLContext context = EGL14.EGL_NO_CONTEXT;
        private EGLSurface surface = EGL14.EGL_NO_SURFACE;
        private EGLConfig config;

        private boolean initialize(Surface window) {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (display == EGL14.EGL_NO_DISPLAY) {
                return false;
            }

            int[] version = new int[2];
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                return false;
            }

            config = chooseConfig(display);
            if (config == null) {
                return false;
            }

            int[] contextAttributes = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                    EGL14.EGL_NONE
            };
            context = EGL14.eglCreateContext(
                    display,
                    config,
                    EGL14.EGL_NO_CONTEXT,
                    contextAttributes,
                    0);
            if (context == null || context == EGL14.EGL_NO_CONTEXT) {
                return false;
            }

            surface = EGL14.eglCreateWindowSurface(
                    display,
                    config,
                    window,
                    new int[]{EGL14.EGL_NONE},
                    0);
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) {
                return false;
            }

            return EGL14.eglMakeCurrent(display, surface, surface, context);
        }

        private void release() {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                        display,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT);
                if (surface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(display, surface);
                }
                if (context != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(display, context);
                }
                EGL14.eglTerminate(display);
            }
            resetEglFields();
        }
    }

    private final class WorkerEglState {
        private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
        private EGLContext context = EGL14.EGL_NO_CONTEXT;
        private EGLSurface surface = EGL14.EGL_NO_SURFACE;

        private boolean initialize(EGLDisplay sharedDisplay, EGLConfig sharedConfig,
                                   EGLContext sharedContext) {
            display = sharedDisplay;
            int[] contextAttributes = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                    EGL14.EGL_NONE
            };
            context = EGL14.eglCreateContext(
                    display,
                    sharedConfig,
                    sharedContext,
                    contextAttributes,
                    0);
            if (context == null || context == EGL14.EGL_NO_CONTEXT) {
                return false;
            }

            surface = EGL14.eglCreatePbufferSurface(
                    display,
                    sharedConfig,
                    new int[]{
                            EGL14.EGL_WIDTH, 1,
                            EGL14.EGL_HEIGHT, 1,
                            EGL14.EGL_NONE
                    },
                    0);
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) {
                return false;
            }
            return EGL14.eglMakeCurrent(display, surface, surface, context);
        }

        private void release() {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                        display,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT);
                if (surface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(display, surface);
                }
                if (context != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(display, context);
                }
            }
        }
    }

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLConfig eglConfig;
    private EGLContext mainContext = EGL14.EGL_NO_CONTEXT;
    private int sharedOffscreenTexture;

    public interface ResultListener {
        void onResult(String result);
    }

    private static native boolean nativeHasFramebufferTexture2DMultisampleEXT();

    private static native boolean nativeHasRenderbufferStorageMultisampleEXT();

    private static native void nativeFramebufferTexture2DMultisampleEXT(
            int target,
            int attachment,
            int textarget,
            int texture,
            int level,
            int samples);

    private static native void nativeRenderbufferStorageMultisampleEXT(
            int target,
            int samples,
            int internalformat,
            int width,
            int height);

    private static final String SCENE_VERTEX_SHADER =
            "#version 300 es\n"
                    + "layout(location = 0) in vec2 aPosition;\n"
                    + "layout(location = 1) in vec4 aColor;\n"
                    + "uniform float uAngle;\n"
                    + "out vec4 vColor;\n"
                    + "void main() {\n"
                    + "  float c = cos(uAngle);\n"
                    + "  float s = sin(uAngle);\n"
                    + "  vec2 p = vec2(\n"
                    + "    aPosition.x * c - aPosition.y * s,\n"
                    + "    aPosition.x * s + aPosition.y * c);\n"
                    + "  gl_Position = vec4(p, 0.0, 1.0);\n"
                    + "  vColor = aColor;\n"
                    + "}\n";

    private static final String SCENE_FRAGMENT_SHADER =
            "#version 300 es\n"
                    + "precision mediump float;\n"
                    + "in vec4 vColor;\n"
                    + "out vec4 fragColor;\n"
                    + "void main() {\n"
                    + "  fragColor = vColor;\n"
                    + "}\n";

    private static final String BLIT_VERTEX_SHADER =
            "#version 300 es\n"
                    + "layout(location = 0) in vec2 aPosition;\n"
                    + "layout(location = 1) in vec2 aTexCoord;\n"
                    + "out vec2 vTexCoord;\n"
                    + "void main() {\n"
                    + "  gl_Position = vec4(aPosition, 0.0, 1.0);\n"
                    + "  vTexCoord = aTexCoord;\n"
                    + "}\n";

    private static final String BLIT_FRAGMENT_SHADER =
            "#version 300 es\n"
                    + "precision mediump float;\n"
                    + "in vec2 vTexCoord;\n"
                    + "uniform sampler2D uTexture;\n"
                    + "out vec4 fragColor;\n"
                    + "void main() {\n"
                    + "  fragColor = texture(uTexture, vTexCoord);\n"
                    + "}\n";

    private static EGLConfig chooseConfig(EGLDisplay display) {
        int[] configAttributes = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] configCount = new int[1];
        if (!EGL14.eglChooseConfig(
                display, configAttributes, 0, configs, 0, 1, configCount, 0)
                || configCount[0] == 0) {
            return null;
        }
        return configs[0];
    }
}
