package pbbadd.opengl.multitest.background;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.opengl.EGL14;
import android.opengl.EGL15;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;

public class BackgroundGlLoadService extends Service {
    private static final String TAG = "BackgroundGlLoad";
    private static final String ACTION_START = "pbbadd.opengl.multitest.background.START";
    private static final String ACTION_STOP = "pbbadd.opengl.multitest.background.STOP";
    private static final String EXTRA_TEXTURE_WIDTH = "texture_width";
    private static final String EXTRA_TEXTURE_HEIGHT = "texture_height";
    private static final String EXTRA_BUFFER_BYTES = "buffer_bytes";
    private static final String EXTRA_THREAD_COUNT = "thread_count";
    private static final int RENDER_PASSES_PER_ITERATION = 16;
    private static final int FLOATS_PER_RENDER_VERTEX = 4;
    private static final int RENDER_VERTEX_STRIDE_BYTES = FLOATS_PER_RENDER_VERTEX * 4;

    private static final float[] RENDER_VERTEX_DATA = {
            -1.0f, -1.0f, 0.0f, 0.0f,
             3.0f, -1.0f, 2.0f, 0.0f,
            -1.0f,  3.0f, 0.0f, 2.0f,
    };

    private static final String VERTEX_SHADER =
            "#version 300 es\n" +
                    "layout(location = 0) in vec2 aPosition;\n" +
                    "layout(location = 1) in vec2 aTexCoord;\n" +
                    "out vec2 vTexCoord;\n" +
                    "void main() {\n" +
                    "    gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
                    "    vTexCoord = aTexCoord;\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 300 es\n" +
                    "precision highp float;\n" +
                    "uniform sampler2D uTexture;\n" +
                    "uniform float uPhase;\n" +
                    "in vec2 vTexCoord;\n" +
                    "out vec4 fragColor;\n" +
                    "void main() {\n" +
                    "    vec2 baseUv = fract(vTexCoord);\n" +
                    "    vec4 acc = texture(uTexture, baseUv);\n" +
                    "    for (int i = 0; i < 16; i++) {\n" +
                    "        float fi = float(i);\n" +
                    "        vec2 uv = fract(baseUv * (1.0 + fi * 0.009)\n" +
                    "                + vec2(uPhase * (0.013 + fi * 0.0017),\n" +
                    "                       uPhase * (0.021 - fi * 0.0009)));\n" +
                    "        vec4 s = texture(uTexture, uv);\n" +
                    "        acc.rgb += sin(s.rgb * (fi + 1.0) + uPhase) * 0.025\n" +
                    "                + cos(s.bgr * (fi + 2.0) - uPhase) * 0.015\n" +
                    "                + s.rgb * 0.045;\n" +
                    "        acc.a += s.a * 0.015;\n" +
                    "    }\n" +
                    "    fragColor = vec4(fract(abs(acc.rgb)), 1.0);\n" +
                    "}\n";

    private static volatile Snapshot snapshot = Snapshot.idle("idle");

    private RunState runState;

    public static void startLoad(Context context, int textureWidth, int textureHeight,
            int bufferBytes, int threadCount) {
        Intent intent = new Intent(context, BackgroundGlLoadService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_TEXTURE_WIDTH, textureWidth);
        intent.putExtra(EXTRA_TEXTURE_HEIGHT, textureHeight);
        intent.putExtra(EXTRA_BUFFER_BYTES, bufferBytes);
        intent.putExtra(EXTRA_THREAD_COUNT, threadCount);
        context.startService(intent);
    }

    public static void stopLoad(Context context) {
        Intent intent = new Intent(context, BackgroundGlLoadService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    public static Snapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopWorkers(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            int textureWidth = intent.getIntExtra(EXTRA_TEXTURE_WIDTH, 1024);
            int textureHeight = intent.getIntExtra(EXTRA_TEXTURE_HEIGHT, 1024);
            int bufferBytes = intent.getIntExtra(EXTRA_BUFFER_BYTES, 4 * 1024 * 1024);
            int threadCount = intent.getIntExtra(EXTRA_THREAD_COUNT, 4);
            startWorkers(textureWidth, textureHeight, bufferBytes, threadCount);
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopWorkers(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private synchronized void startWorkers(
            int textureWidth, int textureHeight, int bufferBytes, int threadCount) {
        stopWorkers(true);

        RunState newRunState = new RunState(textureWidth, textureHeight, bufferBytes, threadCount);
        runState = newRunState;
        newRunState.publish("starting", true);

        for (int i = 0; i < threadCount; i++) {
            BackgroundGlWorker worker = new BackgroundGlWorker(newRunState, i);
            newRunState.workers[i] = worker;
            worker.start();
        }
    }

    private synchronized void stopWorkers(boolean wait) {
        RunState stoppingRunState = runState;
        if (stoppingRunState == null) {
            publish(Snapshot.idle("stopped"));
            return;
        }

        runState = null;
        stoppingRunState.requestStop();
        if (wait) {
            for (BackgroundGlWorker worker : stoppingRunState.workers) {
                if (worker == null) {
                    continue;
                }
                try {
                    worker.join(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        publish(stoppingRunState.snapshot("stopped", false));
    }

    private static void publish(Snapshot newSnapshot) {
        snapshot = newSnapshot;
    }

    public static final class Snapshot {
        public final boolean running;
        public final String status;
        public final int textureWidth;
        public final int textureHeight;
        public final long textureBytes;
        public final int bufferBytes;
        public final int threadCount;
        public final int activeThreadCount;
        public final long iterations;
        public final long glCalls;
        public final long drawCalls;
        public final long elapsedMs;

        private Snapshot(boolean running, String status, int textureWidth, int textureHeight,
                long textureBytes, int bufferBytes, int threadCount, int activeThreadCount,
                long iterations, long glCalls, long drawCalls, long elapsedMs) {
            this.running = running;
            this.status = status;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.textureBytes = textureBytes;
            this.bufferBytes = bufferBytes;
            this.threadCount = threadCount;
            this.activeThreadCount = activeThreadCount;
            this.iterations = iterations;
            this.glCalls = glCalls;
            this.drawCalls = drawCalls;
            this.elapsedMs = elapsedMs;
        }

        static Snapshot idle(String status) {
            return new Snapshot(false, status, 0, 0, 0L, 0, 0, 0,
                    0L, 0L, 0L, 0L);
        }

        public String toDisplayText() {
            if (textureWidth <= 0 || textureHeight <= 0) {
                return status;
            }

            double textureMb = textureBytes / 1024.0 / 1024.0;
            double bufferMb = bufferBytes / 1024.0 / 1024.0;
            double totalDirectMb =
                    (textureBytes + bufferBytes) * (double) threadCount / 1024.0 / 1024.0;
            double renderTargetMb = textureBytes * (double) threadCount / 1024.0 / 1024.0;
            double seconds = elapsedMs / 1000.0;
            double callsPerSecond = seconds > 0.0 ? glCalls / seconds : 0.0;
            double drawsPerSecond = seconds > 0.0 ? drawCalls / seconds : 0.0;
            return String.format(Locale.US,
                    "%s\nthreads: %d active / %d\ntexture per thread: %d x %d, %.2f MB\n"
                            + "buffer per thread: %.2f MB (%d bytes)\n"
                            + "render target per thread: %d x %d, %.2f MB total\n"
                            + "direct memory target: %.2f MB\n"
                            + "render passes per iteration: %d\n"
                            + "iterations: %d, GL calls: %d, %.1f calls/s\n"
                            + "draw calls: %d, %.1f draws/s",
                    status, activeThreadCount, threadCount, textureWidth, textureHeight,
                    textureMb, bufferMb, bufferBytes, textureWidth, textureHeight,
                    renderTargetMb, totalDirectMb, RENDER_PASSES_PER_ITERATION,
                    iterations, glCalls, callsPerSecond, drawCalls, drawsPerSecond);
        }
    }

    private static final class RunState {
        final int textureWidth;
        final int textureHeight;
        final int bufferBytes;
        final int threadCount;
        final long startTimeMs;
        final BackgroundGlWorker[] workers;
        volatile boolean stopRequested;

        RunState(int textureWidth, int textureHeight, int bufferBytes, int threadCount) {
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.bufferBytes = bufferBytes;
            this.threadCount = threadCount;
            this.startTimeMs = SystemClock.elapsedRealtime();
            this.workers = new BackgroundGlWorker[threadCount];
        }

        void requestStop() {
            stopRequested = true;
            for (BackgroundGlWorker worker : workers) {
                if (worker != null) {
                    worker.requestStop();
                }
            }
        }

        void publish(String status, boolean running) {
            BackgroundGlLoadService.publish(snapshot(status, running));
        }

        Snapshot snapshot(String status, boolean running) {
            long totalIterations = 0L;
            int activeCount = 0;
            String workerStatus = null;

            for (BackgroundGlWorker worker : workers) {
                if (worker == null) {
                    continue;
                }
                totalIterations += worker.getIterations();
                if (worker.isWorkerRunning()) {
                    activeCount++;
                } else if (worker.getStatus() != null) {
                    workerStatus = worker.getStatus();
                }
            }

            String displayStatus = workerStatus == null ? status : status + ", " + workerStatus;
            long elapsedMs = Math.max(0L, SystemClock.elapsedRealtime() - startTimeMs);
            long textureBytes = (long) textureWidth * textureHeight * 4L;
            long drawCalls = totalIterations * RENDER_PASSES_PER_ITERATION;
            long glCalls = totalIterations * (4L + RENDER_PASSES_PER_ITERATION);
            return new Snapshot(running, displayStatus, textureWidth, textureHeight, textureBytes,
                    bufferBytes, threadCount, activeCount, totalIterations, glCalls,
                    drawCalls, elapsedMs);
        }
    }

    private static final class BackgroundGlWorker extends Thread {
        private final RunState runState;
        private final int workerIndex;
        private volatile boolean stopRequested;
        private volatile boolean running;
        private volatile long iterations;
        private volatile String status;

        private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
        private EGLContext context = EGL14.EGL_NO_CONTEXT;
        private EGLSurface surface = EGL14.EGL_NO_SURFACE;
        private final int[] textureId = new int[1];
        private final int[] bufferId = new int[1];
        private final int[] renderTextureId = new int[1];
        private final int[] framebufferId = new int[1];
        private final int[] renderVboId = new int[1];
        private int program;
        private int textureUniformLocation;
        private int phaseUniformLocation;

        BackgroundGlWorker(RunState runState, int workerIndex) {
            super("BackgroundGlLoadWorker-" + workerIndex);
            this.runState = runState;
            this.workerIndex = workerIndex;
        }

        void requestStop() {
            stopRequested = true;
            interrupt();
        }

        long getIterations() {
            return iterations;
        }

        boolean isWorkerRunning() {
            return running;
        }

        String getStatus() {
            return status;
        }

        @Override
        public void run() {
            running = true;

            try {
                validateSizes();
                runState.publish("allocating direct buffers", true);
                ByteBuffer textureData = ByteBuffer
                        .allocateDirect((int) ((long) runState.textureWidth
                                * runState.textureHeight * 4L))
                        .order(ByteOrder.nativeOrder());
                ByteBuffer vboData = ByteBuffer
                        .allocateDirect(runState.bufferBytes)
                        .order(ByteOrder.nativeOrder());

                initEgl();
                initGlObjects();
                runState.publish("running with offscreen render", true);

                while (!stopRequested && !runState.stopRequested) {
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId[0]);
                    writeTextureMarkers(textureData, runState.textureWidth,
                            runState.textureHeight, iterations, workerIndex);
                    textureData.position(0);
                    GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                            runState.textureWidth, runState.textureHeight, 0,
                            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, textureData);
                    checkGl("glTexImage2D");

                    writeTextureMarkers(textureData, runState.textureWidth,
                            runState.textureHeight, iterations + 1L, workerIndex);
                    textureData.position(0);
                    GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0,
                            runState.textureWidth, runState.textureHeight,
                            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, textureData);
                    checkGl("glTexSubImage2D");

                    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufferId[0]);
                    writeBufferMarkers(vboData, iterations, workerIndex);
                    vboData.position(0);
                    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, runState.bufferBytes,
                            vboData, GLES30.GL_DYNAMIC_DRAW);
                    checkGl("glBufferData");

                    writeBufferMarkers(vboData, iterations + 1L, workerIndex);
                    vboData.position(0);
                    GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0,
                            runState.bufferBytes, vboData);
                    checkGl("glBufferSubData");

                    renderOffscreen(iterations);

                    GLES30.glFlush();
                    iterations++;
                    if ((iterations & 0x0f) == 0) {
                        runState.publish("running with offscreen render", true);
                    }
                }

                status = "worker " + workerIndex + " stopped";
            } catch (Throwable t) {
                status = "worker " + workerIndex + " error: " + t.getMessage();
                Log.e(TAG, status, t);
            } finally {
                running = false;
                cleanupGl();
                runState.publish(runState.stopRequested ? "stopped" : "running",
                        !runState.stopRequested);
            }
        }

        private void validateSizes() {
            long textureBytes = (long) runState.textureWidth * runState.textureHeight * 4L;
            if (runState.textureWidth <= 0 || runState.textureHeight <= 0) {
                throw new IllegalArgumentException("texture width/height must be > 0");
            }
            if (textureBytes > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("texture data is too large for Java ByteBuffer: "
                        + textureBytes + " bytes");
            }
            if (runState.bufferBytes <= 0) {
                throw new IllegalArgumentException("buffer bytes must be > 0");
            }
        }

        private void initEgl() {
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
            surface = createPbufferSurface(display, config);
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                throwEgl("eglMakeCurrent");
            }
        }

        private EGLConfig chooseConfig(EGLDisplay eglDisplay) {
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
            if (!EGL14.eglChooseConfig(eglDisplay, configAttributes, 0,
                    configs, 0, configs.length, configCount, 0)) {
                throwEgl("eglChooseConfig");
            }
            if (configCount[0] == 0 || configs[0] == null) {
                throw new RuntimeException("no ES3 pbuffer EGLConfig found");
            }
            return configs[0];
        }

        private EGLContext createContext(EGLDisplay eglDisplay, EGLConfig config) {
            int[] contextAttributes = {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                    EGL14.EGL_NONE
            };
            EGLContext eglContext = EGL14.eglCreateContext(eglDisplay, config,
                    EGL14.EGL_NO_CONTEXT, contextAttributes, 0);
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                throwEgl("eglCreateContext");
            }
            return eglContext;
        }

        private EGLSurface createPbufferSurface(EGLDisplay eglDisplay, EGLConfig config) {
            int[] surfaceAttributes = {
                    EGL14.EGL_WIDTH, 1,
                    EGL14.EGL_HEIGHT, 1,
                    EGL14.EGL_NONE
            };
            EGLSurface eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, config,
                    surfaceAttributes, 0);
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                throwEgl("eglCreatePbufferSurface");
            }
            return eglSurface;
        }

        private void initGlObjects() {
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1);

            GLES30.glGenTextures(1, textureId, 0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId[0]);
            setTextureParams();

            GLES30.glGenBuffers(1, bufferId, 0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bufferId[0]);

            program = createProgram();
            textureUniformLocation = GLES30.glGetUniformLocation(program, "uTexture");
            phaseUniformLocation = GLES30.glGetUniformLocation(program, "uPhase");
            if (textureUniformLocation < 0 || phaseUniformLocation < 0) {
                throw new RuntimeException("render shader uniforms not found");
            }

            createRenderTarget();
            createRenderVertexBuffer();
            checkGl("init GL objects");
        }

        private void setTextureParams() {
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER,
                    GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER,
                    GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S,
                    GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T,
                    GLES30.GL_CLAMP_TO_EDGE);
        }

        private void createRenderTarget() {
            GLES30.glGenTextures(1, renderTextureId, 0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, renderTextureId[0]);
            setTextureParams();
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                    runState.textureWidth, runState.textureHeight, 0,
                    GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            checkGl("create render target texture");

            GLES30.glGenFramebuffers(1, framebufferId, 0);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebufferId[0]);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                    GLES30.GL_TEXTURE_2D, renderTextureId[0], 0);
            int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                throw new RuntimeException("FBO incomplete: 0x" + Integer.toHexString(status));
            }
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        }

        private void createRenderVertexBuffer() {
            FloatBuffer vertexBuffer = ByteBuffer
                    .allocateDirect(RENDER_VERTEX_DATA.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            vertexBuffer.put(RENDER_VERTEX_DATA).position(0);

            GLES30.glGenBuffers(1, renderVboId, 0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, renderVboId[0]);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, RENDER_VERTEX_DATA.length * 4,
                    vertexBuffer, GLES30.GL_STATIC_DRAW);
        }

        private void renderOffscreen(long frame) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebufferId[0]);
            GLES30.glViewport(0, 0, runState.textureWidth, runState.textureHeight);
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);
            GLES30.glDisable(GLES30.GL_BLEND);

            GLES30.glUseProgram(program);
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId[0]);
            GLES30.glUniform1i(textureUniformLocation, 0);

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, renderVboId[0]);
            GLES30.glEnableVertexAttribArray(0);
            GLES30.glEnableVertexAttribArray(1);
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false,
                    RENDER_VERTEX_STRIDE_BYTES, 0);
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false,
                    RENDER_VERTEX_STRIDE_BYTES, 2 * 4);

            for (int pass = 0; pass < RENDER_PASSES_PER_ITERATION; pass++) {
                float phase = (float) ((frame + 1L) * 0.013
                        + workerIndex * 0.171 + pass * 0.071);
                GLES30.glUniform1f(phaseUniformLocation, phase);
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
            }
            checkGl("offscreen render");
        }

        private int createProgram() {
            int vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            int renderProgram = GLES30.glCreateProgram();
            GLES30.glAttachShader(renderProgram, vertexShader);
            GLES30.glAttachShader(renderProgram, fragmentShader);
            GLES30.glLinkProgram(renderProgram);

            int[] linked = new int[1];
            GLES30.glGetProgramiv(renderProgram, GLES30.GL_LINK_STATUS, linked, 0);
            GLES30.glDeleteShader(vertexShader);
            GLES30.glDeleteShader(fragmentShader);
            if (linked[0] == 0) {
                String info = GLES30.glGetProgramInfoLog(renderProgram);
                GLES30.glDeleteProgram(renderProgram);
                throw new RuntimeException("render program link failed: " + info);
            }
            return renderProgram;
        }

        private int compileShader(int type, String source) {
            int shader = GLES30.glCreateShader(type);
            GLES30.glShaderSource(shader, source);
            GLES30.glCompileShader(shader);

            int[] compiled = new int[1];
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String info = GLES30.glGetShaderInfoLog(shader);
                GLES30.glDeleteShader(shader);
                throw new RuntimeException("render shader compile failed: " + info);
            }
            return shader;
        }

        private void cleanupGl() {
            if (display != EGL14.EGL_NO_DISPLAY) {
                if (framebufferId[0] != 0) {
                    GLES30.glDeleteFramebuffers(1, framebufferId, 0);
                    framebufferId[0] = 0;
                }
                if (renderTextureId[0] != 0) {
                    GLES30.glDeleteTextures(1, renderTextureId, 0);
                    renderTextureId[0] = 0;
                }
                if (textureId[0] != 0) {
                    GLES30.glDeleteTextures(1, textureId, 0);
                    textureId[0] = 0;
                }
                if (renderVboId[0] != 0) {
                    GLES30.glDeleteBuffers(1, renderVboId, 0);
                    renderVboId[0] = 0;
                }
                if (bufferId[0] != 0) {
                    GLES30.glDeleteBuffers(1, bufferId, 0);
                    bufferId[0] = 0;
                }
                if (program != 0) {
                    GLES30.glDeleteProgram(program);
                    program = 0;
                }
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (surface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(display, surface);
                    surface = EGL14.EGL_NO_SURFACE;
                }
                if (context != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(display, context);
                    context = EGL14.EGL_NO_CONTEXT;
                }
                EGL14.eglTerminate(display);
                display = EGL14.EGL_NO_DISPLAY;
            }
            EGL14.eglReleaseThread();
        }

        private static void writeTextureMarkers(
                ByteBuffer buffer, int width, int height, long frame, int workerIndex) {
            long pixelCount = (long) width * height;
            writeColor(buffer, 0L, frame, workerIndex);
            writeColor(buffer, (pixelCount / 2L) * 4L, frame + 31L, workerIndex);
            writeColor(buffer, (pixelCount - 1L) * 4L, frame + 63L, workerIndex);
        }

        private static void writeColor(ByteBuffer buffer, long offset, long frame, int workerIndex) {
            if (offset < 0L || offset + 3L >= buffer.capacity()) {
                return;
            }
            int index = (int) offset;
            buffer.put(index, (byte) ((frame + workerIndex * 13L) & 0xff));
            buffer.put(index + 1, (byte) ((frame * 3L + workerIndex * 29L) & 0xff));
            buffer.put(index + 2, (byte) ((frame * 7L + workerIndex * 47L) & 0xff));
            buffer.put(index + 3, (byte) 0xff);
        }

        private static void writeBufferMarkers(ByteBuffer buffer, long frame, int workerIndex) {
            writeByte(buffer, 0, frame, workerIndex);
            writeByte(buffer, buffer.capacity() / 2, frame + 17L, workerIndex);
            writeByte(buffer, buffer.capacity() - 1, frame + 41L, workerIndex);
        }

        private static void writeByte(ByteBuffer buffer, int index, long frame, int workerIndex) {
            if (index < 0 || index >= buffer.capacity()) {
                return;
            }
            buffer.put(index, (byte) ((frame + workerIndex * 19L) & 0xff));
        }

        private static void checkGl(String op) {
            int error = GLES30.glGetError();
            if (error != GLES30.GL_NO_ERROR) {
                throw new RuntimeException(op + " GL error 0x" + Integer.toHexString(error));
            }
        }

        private void throwEgl(String op) {
            int error = EGL14.eglGetError();
            throw new RuntimeException(op + " EGL error 0x" + Integer.toHexString(error));
        }
    }
}
