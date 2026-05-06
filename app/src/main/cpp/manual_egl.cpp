#include <jni.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "common_include_and_log.h"

static EGLDisplay me_eglDisplay = EGL_NO_DISPLAY;
static EGLContext me_eglContext = EGL_NO_CONTEXT;
static EGLSurface me_eglSurface = EGL_NO_SURFACE;
static EGLConfig  me_eglConfig;

static GLuint me_program = 0;
static uint8_t* me_imagePixels = nullptr;
// FBO 离屏渲染变量
static GLuint me_fboId = 0;
static GLuint me_fboTextureId      = 0; // FBO离屏纹理
static GLuint me_originalTextureId = 0; // 你的图片纹理（真正的图）

// ==========================
// ✅ 修复：正确的着色器
// ==========================
static const char* me_vertexShader = R"(
    attribute vec2 aPos;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        gl_Position = vec4(aPos, 0.0, 1.0);
        vTexCoord = aTexCoord;
    }
)";

static const char* me_fragmentShader = R"(
    precision mediump float;
    varying vec2 vTexCoord;
    uniform sampler2D uTexture;
    void main() {
        gl_FragColor = texture2D(uTexture, vTexCoord);  // ✅ 修复！
    }
)";

// 顶点坐标（不变，负责画到屏幕哪里）
static float me_vertices_pos[] = {
        -1.0f,  1.0f,
        1.0f,  1.0f,
        -1.0f, -1.0f,
        1.0f, -1.0f,
};

// 纹理坐标 → 正常图片（不使用FBO时）
static float me_vertices_uv[] = {
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
};

// 纹理坐标 → FBO 必须翻转（解决倒置！）
static float me_vertices_uv_flipped[] = {
        0.0f, 1.0f,  // ← 翻转
        1.0f, 1.0f,  // ← 翻转
        0.0f, 0.0f,
        1.0f, 0.0f,
};

static void me_createFBO(int w,int h)
{
    // 1. 创建 FBO
    glGenFramebuffers(1, &me_fboId);
    // 2. 创建 FBO 绑定的颜色纹理
    glGenTextures(1, &me_fboTextureId);

    // 配置离线纹理
    glBindTexture(GL_TEXTURE_2D, me_fboTextureId);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA,
                 w, h,
                 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    // 3. 绑定 FBO，把纹理挂载到 FBO 颜色附件
    glBindFramebuffer(GL_FRAMEBUFFER, me_fboId);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                           GL_TEXTURE_2D, me_fboTextureId, 0);

    // 4. 校验 FBO 完整性（必加，防止配置错）
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if(status != GL_FRAMEBUFFER_COMPLETE)
    {
        // FBO 初始化失败
    }

    // 解绑，切回屏幕默认帧缓冲 0
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
}

// 安全释放旧的 FBO 资源
static void me_releaseFBO() {
    // 一定要先解绑！
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    // 删除 FBO
    if (me_fboId != 0) {
        glDeleteFramebuffers(1, &me_fboId);
        me_fboId = 0;
    }

    // 删除 FBO 附着的纹理
    if (me_fboTextureId != 0) {
        glDeleteTextures(1, &me_fboTextureId);
        me_fboTextureId = 0;
    }
}

// 先渲染到 FBO 离屏
static void me_renderToFBO(int screenW, int screenH)
{
    glBindFramebuffer(GL_FRAMEBUFFER, me_fboId);
    glViewport(0, 0, screenW, screenH);
    glClearColor(0.8f, 0.6f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(me_program);

    // ✅ 必须绑定原始图片！！！
    glBindTexture(GL_TEXTURE_2D, me_originalTextureId);

    // 顶点
    GLint posLoc = glGetAttribLocation(me_program, "aPos");
    glVertexAttribPointer(posLoc, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(float), me_vertices_pos);
    glEnableVertexAttribArray(posLoc);

    // 正常UV
    GLint texLoc = glGetAttribLocation(me_program, "aTexCoord");
    glVertexAttribPointer(texLoc, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(float), me_vertices_uv);
    glEnableVertexAttribArray(texLoc);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
}

// 把 FBO 离线纹理 绘制到手机屏幕
static void me_drawFBOToScreen(int screenW, int screenH)
{
    glViewport(0, 0, screenW, screenH);
    glClearColor(0.6f, 0.8f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(me_program);

    // ✅ 这里才绑 FBO 纹理（用来显示）
    glBindTexture(GL_TEXTURE_2D, me_fboTextureId);

    // 顶点
    GLint posLoc = glGetAttribLocation(me_program, "aPos");
    glVertexAttribPointer(posLoc, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(float), me_vertices_pos);
    glEnableVertexAttribArray(posLoc);

    // ✅ FBO 必须翻转
    GLint texLoc = glGetAttribLocation(me_program, "aTexCoord");
    glVertexAttribPointer(texLoc, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(float), me_vertices_uv_flipped);
    glEnableVertexAttribArray(texLoc);

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    eglSwapBuffers(me_eglDisplay, me_eglSurface);
}

static void me_clear() {
    glClearColor(1.0f, 0.8f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
}

static void me_renderInternal(int screenW, int screenH)
{
    me_releaseFBO();
    me_createFBO(screenW, screenH);

    glViewport(0, 0, screenW, screenH);
    glClearColor(0.5f,0.5f,0.0f,0.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    me_renderToFBO(screenW, screenH);
    me_drawFBOToScreen(screenW, screenH);
}

static void me_createShader() {
    GLuint vs = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vs, 1, &me_vertexShader, nullptr);
    glCompileShader(vs);

    GLuint fs = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fs, 1, &me_fragmentShader, nullptr);
    glCompileShader(fs);

    me_program = glCreateProgram();
    glAttachShader(me_program, vs);
    glAttachShader(me_program, fs);
    glLinkProgram(me_program);

    glDeleteShader(vs);
    glDeleteShader(fs);
}

static void me_createTexture() {
    glGenTextures(1, &me_originalTextureId);
    glBindTexture(GL_TEXTURE_2D, me_originalTextureId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ManualEGLView_eglInit(
        JNIEnv *env, jobject thiz, jobject jsurface)
{
    ANativeWindow *window = ANativeWindow_fromSurface(env, jsurface);
    // ✅ 强制设置视口
    int w = ANativeWindow_getWidth(window);
    int h = ANativeWindow_getHeight(window);

    me_eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(me_eglDisplay, nullptr, nullptr);

    EGLint configAttrs[] = {
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 16,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_NONE
    };

    EGLint numConfig;
    eglChooseConfig(me_eglDisplay, configAttrs, &me_eglConfig, 1, &numConfig);

    EGLint ctxAttrs[] = {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL_NONE
    };
    me_eglContext = eglCreateContext(me_eglDisplay, me_eglConfig, EGL_NO_CONTEXT, ctxAttrs);
    me_eglSurface = eglCreateWindowSurface(me_eglDisplay, me_eglConfig, window, nullptr);
    eglMakeCurrent(me_eglDisplay, me_eglSurface, me_eglSurface, me_eglContext);

    me_createShader();
    me_createTexture();

    glViewport(0, 0, w, h);
    me_createFBO(w,h);   // 初始化FBO
    ANativeWindow_release(window);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ManualEGLView_updateTexture(
        JNIEnv *env, jobject thiz, jbyteArray pixels, jint width, jint height)
{
    int size = width * height * 4;
    me_imagePixels = new uint8_t[size];
    env->GetByteArrayRegion(pixels, 0, size, (jbyte*)me_imagePixels);

    glBindTexture(GL_TEXTURE_2D, me_originalTextureId);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, me_imagePixels);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ManualEGLView_render(
        JNIEnv *env, jobject thiz,jint w,jint h)
{
    if(w <= 0 || h <= 0) {
        LOGE("pbbadd,render w or h error,%d-%d\n",w,h);
    }
    // ✅ 必须加！子线程渲染必须绑定上下文
    if (me_eglDisplay == EGL_NO_DISPLAY) {
        LOGE("pbbadd,me_eglDisplay error\n");
        return;
    }
    if (me_eglSurface == EGL_NO_SURFACE) {
        LOGE("pbbadd,me_eglSurface error\n");
        return;
    }
    if (me_eglContext == EGL_NO_CONTEXT) {
        LOGE("pbbadd,me_eglContext error\n");
        return;
    }
    me_renderInternal(w,h);
    LOGD("pbbadd,render done\n");
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ManualEGLView_recreateSurface(
        JNIEnv *env, jobject thiz, jobject jsurface)
{
    ANativeWindow *newWindow = ANativeWindow_fromSurface(env, jsurface);
    // ✅ 修复视口
    int w = ANativeWindow_getWidth(newWindow);
    int h = ANativeWindow_getHeight(newWindow);

    eglMakeCurrent(me_eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    me_releaseFBO();
    me_clear();

    // 2. 销毁旧surface
    if (me_eglSurface != EGL_NO_SURFACE) {
        eglDestroySurface(me_eglDisplay, me_eglSurface);
        me_eglSurface = EGL_NO_SURFACE;
    }

    // 3. 创建新surface
    me_eglSurface = eglCreateWindowSurface(
            me_eglDisplay,
            me_eglConfig,
            newWindow,
            nullptr
    );

    // 4. 绑定新surface
    eglMakeCurrent(me_eglDisplay, me_eglSurface, me_eglSurface, me_eglContext);

//    me_createFBO(w,h);
//    glViewport(0, 0, w, h);

    // 绘制
//    me_renderInternal(w,h);
    ANativeWindow_release(newWindow);
}

static EGLDisplay mme_eglDisplay = EGL_NO_DISPLAY;
static EGLContext mme_eglContext = EGL_NO_CONTEXT;
static EGLSurface mme_eglSurface = EGL_NO_SURFACE;
static EGLConfig  mme_eglConfig;

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ManualEGLView_eglinitanother(
        JNIEnv *env, jobject thiz, jobject jsurface)
{
    ANativeWindow *window = ANativeWindow_fromSurface(env, jsurface);
    int w = ANativeWindow_getWidth(window);
    int h = ANativeWindow_getHeight(window);

    mme_eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(mme_eglDisplay, nullptr, nullptr);

    EGLint configAttrs[] = {
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 16,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_NONE
    };

    EGLint numConfig;
    eglChooseConfig(mme_eglDisplay, configAttrs, &mme_eglConfig, 1, &numConfig);

    EGLint ctxAttrs[] = {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL_NONE
    };
    mme_eglContext = eglCreateContext(mme_eglDisplay, mme_eglConfig, EGL_NO_CONTEXT, ctxAttrs);
    mme_eglSurface = eglCreateWindowSurface(mme_eglDisplay, mme_eglConfig, window, nullptr);
    eglMakeCurrent(mme_eglDisplay, mme_eglSurface, mme_eglSurface, mme_eglContext);

    glViewport(0, 0, w, h);
    glClearColor(1.0f,0.0f,0.0f,0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    ANativeWindow_release(window);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ManualEGLView_makeanothercontext(
        JNIEnv *env, jobject thiz) {
    eglMakeCurrent(mme_eglDisplay, mme_eglSurface, mme_eglSurface, mme_eglContext);
}