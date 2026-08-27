package pbbadd.opengl.multitest.texturemultisampleext;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
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
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                LogFrameBindingOnce.logMainFramebuffer0();
                GLES20.glViewport(0, 0, viewWidth, viewHeight);
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                drawOffscreenTexture(blitProgram, sharedOffscreenTexture);
                GLES20.glFinish();

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

            GLES20.glDeleteProgram(blitProgram);
            GLES20.glFinish();
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

            String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
            boolean extensionSupported =
                    extensions != null && extensions.contains(REQUIRED_EXTENSION);
            boolean procSupported = nativeHasFramebufferTexture2DMultisampleEXT();
            int sceneProgram = createProgram(SCENE_VERTEX_SHADER, SCENE_FRAGMENT_SHADER);
            if (sceneProgram == 0) {
                publishResult("MultisampleGlThread scene shader initialization failed");
                return;
            }

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            int offscreenTexture = textures[0];
            createTexture(offscreenTexture);

            int[] fbos = new int[1];
            GLES20.glGenFramebuffers(1, fbos, 0);
            int offscreenFbo = fbos[0];
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, offscreenFbo);
            Log.i(TAG, "MultisampleGlThread: glBindFramebuffer(offscreenFbo="
                    + offscreenFbo + ")");

            boolean usingExtension = attachOffscreenTexture(offscreenTexture);

            int fboStatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            boolean fboComplete = fboStatus == GLES20.GL_FRAMEBUFFER_COMPLETE;
            Log.i(TAG, "MultisampleGlThread: glCheckFramebufferStatus=0x"
                    + Integer.toHexString(fboStatus));

            synchronized (frameLock) {
                sharedOffscreenTexture = offscreenTexture;
                frameLock.notifyAll();
            }

            String result = String.format(Locale.US,
                    "GL_EXT_multisampled_render_to_texture: %s\n"
                            + "native glFramebufferTexture2DMultisampleEXT: %s\n"
                            + "offscreen texture: %dx%d RGBA\n"
                            + "requested samples: %d\n"
                            + "FBO mode: %s\n"
                            + "FBO status: %s\n"
                            + "contexts: shared EGLContext\n\n"
                            + "MultisampleGlThread:\n"
                            + "  glBindFramebuffer(offscreenFbo)\n"
                            + "  glFramebufferTexture2DMultisampleEXT\n"
                            + "  glDrawArrays(scene)\n"
                            + "  glFinish()\n\n"
                            + "MainGlThread:\n"
                            + "  glBindFramebuffer(0)\n"
                            + "  draw offscreenTexture\n"
                            + "  glFinish()\n"
                            + "  eglSwapBuffers(windowSurface)",
                    extensionSupported ? "yes" : "no",
                    procSupported ? "yes" : "no",
                    OFFSCREEN_WIDTH,
                    OFFSCREEN_HEIGHT,
                    REQUESTED_SAMPLES,
                    usingExtension ? "EXT multisampled texture" : "regular texture FBO fallback",
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
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, offscreenFbo);
                LogFrameBindingOnce.logMultisampleFramebuffer(offscreenFbo);
                attachOffscreenTexture(offscreenTexture);
                GLES20.glViewport(0, 0, OFFSCREEN_WIDTH, OFFSCREEN_HEIGHT);
                GLES20.glClearColor(0.04f, 0.05f, 0.09f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                drawScene(sceneProgram, angle);
                GLES20.glFinish();

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

            GLES20.glDeleteFramebuffers(1, new int[]{offscreenFbo}, 0);
            GLES20.glDeleteTextures(1, new int[]{offscreenTexture}, 0);
            GLES20.glDeleteProgram(sceneProgram);
            GLES20.glFinish();
        } finally {
            synchronized (frameLock) {
                frameLock.notifyAll();
            }
            egl.release();
            Log.i(TAG, "MultisampleGlThread stopped");
        }
    }

    private void createTexture(int texture) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        Log.i(TAG, "MultisampleGlThread: glTexImage2D(texture=" + texture
                + ", width=" + OFFSCREEN_WIDTH
                + ", height=" + OFFSCREEN_HEIGHT + ")");
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                OFFSCREEN_WIDTH,
                OFFSCREEN_HEIGHT,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null);
    }

    private boolean attachOffscreenTexture(int texture) {
        if (extensionSupported && procSupported) {
            Log.i(TAG, "MultisampleGlThread: glFramebufferTexture2DMultisampleEXT("
                    + "texture=" + texture
                    + ", samples=" + REQUESTED_SAMPLES + ")");
            nativeFramebufferTexture2DMultisampleEXT(
                    GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D,
                    texture,
                    0,
                    REQUESTED_SAMPLES);
            int error = GLES20.glGetError();
            if (error == GLES20.GL_NO_ERROR) {
                return true;
            }
            Log.e(TAG, "MultisampleGlThread extension call failed: "
                    + errorToString(error));
        }

        Log.i(TAG, "MultisampleGlThread: glFramebufferTexture2D(texture=" + texture + ")");
        GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                texture,
                0);
        return false;
    }

    private void drawScene(int program, float angle) {
        GLES20.glUseProgram(program);

        int positionLocation = GLES20.glGetAttribLocation(program, "aPosition");
        int colorLocation = GLES20.glGetAttribLocation(program, "aColor");
        int angleLocation = GLES20.glGetUniformLocation(program, "uAngle");

        scenePositions.position(0);
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false,
                0, scenePositions);
        GLES20.glEnableVertexAttribArray(positionLocation);

        sceneColors.position(0);
        GLES20.glVertexAttribPointer(colorLocation, 4, GLES20.GL_FLOAT, false,
                0, sceneColors);
        GLES20.glEnableVertexAttribArray(colorLocation);

        GLES20.glUniform1f(angleLocation, angle);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);

        GLES20.glDisableVertexAttribArray(positionLocation);
        GLES20.glDisableVertexAttribArray(colorLocation);
    }

    private void drawOffscreenTexture(int program, int texture) {
        GLES20.glUseProgram(program);

        int positionLocation = GLES20.glGetAttribLocation(program, "aPosition");
        int texCoordLocation = GLES20.glGetAttribLocation(program, "aTexCoord");
        int textureLocation = GLES20.glGetUniformLocation(program, "uTexture");

        blitPositions.position(0);
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false,
                0, blitPositions);
        GLES20.glEnableVertexAttribArray(positionLocation);

        blitTexCoords.position(0);
        GLES20.glVertexAttribPointer(texCoordLocation, 2, GLES20.GL_FLOAT, false,
                0, blitTexCoords);
        GLES20.glEnableVertexAttribArray(texCoordLocation);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(textureLocation, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(positionLocation);
        GLES20.glDisableVertexAttribArray(texCoordLocation);
    }

    private int createProgram(String vertexSource, String fragmentSource) {
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

    private int compileShader(int type, String source) {
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
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
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
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
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

    private static native void nativeFramebufferTexture2DMultisampleEXT(
            int target,
            int attachment,
            int textarget,
            int texture,
            int level,
            int samples);

    private static final String SCENE_VERTEX_SHADER =
            "attribute vec2 aPosition;\n"
                    + "attribute vec4 aColor;\n"
                    + "uniform float uAngle;\n"
                    + "varying vec4 vColor;\n"
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
            "precision mediump float;\n"
                    + "varying vec4 vColor;\n"
                    + "void main() {\n"
                    + "  gl_FragColor = vColor;\n"
                    + "}\n";

    private static final String BLIT_VERTEX_SHADER =
            "attribute vec2 aPosition;\n"
                    + "attribute vec2 aTexCoord;\n"
                    + "varying vec2 vTexCoord;\n"
                    + "void main() {\n"
                    + "  gl_Position = vec4(aPosition, 0.0, 1.0);\n"
                    + "  vTexCoord = aTexCoord;\n"
                    + "}\n";

    private static final String BLIT_FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "varying vec2 vTexCoord;\n"
                    + "uniform sampler2D uTexture;\n"
                    + "void main() {\n"
                    + "  gl_FragColor = texture2D(uTexture, vTexCoord);\n"
                    + "}\n";

    private static EGLConfig chooseConfig(EGLDisplay display) {
        int[] configAttributes = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
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
