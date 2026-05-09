#include <jni.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "common_include_and_log.h"

__thread static GLuint origin_fbo_id;
__thread static GLuint origin_tex_id;
__thread static GLuint offscreen_fbo_id;
__thread static GLuint offscreen_tex_id;
__thread static GLuint update_tex_id;
__thread static ANativeWindow *window= nullptr;
__thread static int vw,vh;

__thread static EGLDisplay common_egl_display = EGL_NO_DISPLAY;
__thread static EGLContext common_egl_context = EGL_NO_CONTEXT;
__thread static EGLSurface common_egl_surface = EGL_NO_SURFACE;
__thread static EGLConfig  me_eglConfig;

__thread static GLuint common_program = 0;

static const char* common_vertex_shader = R"(
    attribute vec2 aPos;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        gl_Position = vec4(aPos, 0.0, 1.0);
        vTexCoord = aTexCoord;
    }
)";

static const char* common_fragment_shader = R"(
    precision mediump float;
    varying vec2 vTexCoord;
    uniform sampler2D uTexture;
    void main() {
        gl_FragColor = texture2D(uTexture, vTexCoord);
    }
)";

static float me_vertices_pos[] = {
        -1.0f,  1.0f,
        1.0f,  1.0f,
        -1.0f, -1.0f,
        1.0f, -1.0f,
};

static float me_vertices_uv[] = {
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f,
};

static float me_vertices_uv_flipped[] = {
        0.0f, 1.0f,
        1.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 0.0f,
};

static void offscreen_res_create(int w,int h) {
    glGenFramebuffers(1,&offscreen_fbo_id);
    glGenTextures(1,&offscreen_tex_id);
    glBindTexture(GL_TEXTURE_2D, offscreen_tex_id);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glBindFramebuffer(GL_FRAMEBUFFER, offscreen_fbo_id);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, offscreen_tex_id, 0);
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if(status != GL_FRAMEBUFFER_COMPLETE)
    {
        LOGE("check failed\n");
        glDeleteTextures(1,&offscreen_tex_id);
        glDeleteFramebuffers(1,&offscreen_fbo_id);
        return;
    }
    LOGD("done\n");
}
static void offscreen_res_destroy() {
    glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING,(GLint*)&origin_fbo_id);
    glGetIntegerv(GL_TEXTURE_BINDING_2D, (GLint*)&origin_tex_id);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
    if (offscreen_fbo_id != 0) {
        glDeleteFramebuffers(1, &offscreen_fbo_id);
        offscreen_fbo_id = 0;
    }
    if (offscreen_tex_id != 0) {
        glDeleteTextures(1, &offscreen_tex_id);
        offscreen_tex_id = 0;
    }
    if(origin_fbo_id!=0) {
        glBindFramebuffer(GL_FRAMEBUFFER, origin_fbo_id);
    }
    if(origin_tex_id!=0) {
        glBindTexture(GL_TEXTURE_2D, origin_tex_id);
    }
}

static void common_clear() {
    glClearColor(0.8,0.6,0.0f,0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
}

static void offscreen_clear(int w,int h) {
    vw=w,vh=h;
    glViewport(0,0,vw,vh);
    common_clear();
}

static void common_draw() {
    glUseProgram(common_program);
    GLint posLoc = glGetAttribLocation(common_program, "aPos");
    glVertexAttribPointer(posLoc, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(float), me_vertices_pos);
    glEnableVertexAttribArray(posLoc);
    GLint texLoc = glGetAttribLocation(common_program, "aTexCoord");
    glVertexAttribPointer(texLoc, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(float), me_vertices_uv);
//    glVertexAttribPointer(texLoc, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(float), me_vertices_uv_flipped);
    glEnableVertexAttribArray(texLoc);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

static void offscreen_draw() {
    glBindFramebuffer(GL_FRAMEBUFFER,offscreen_fbo_id);
    glBindTexture(GL_TEXTURE_2D,update_tex_id);
    common_draw();
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D,offscreen_tex_id);
    common_draw();
}

static void offscreen_render() {
    offscreen_draw();
}

static void offscreen_2_screen() {
//    offscreen_clear();
//    eglMakeCurrent(common_egl_display, common_egl_surface, common_egl_surface, common_egl_context);
    eglSwapBuffers(common_egl_display, common_egl_surface);
}

static bool offscreen_render_check(int w,int h) {
    bool res=true;
    if(vw <= 0 || vh <= 0) {
        LOGE("render w or h error,%d-%d\n",w,h);
        res=false;
    }
    if (common_egl_display == EGL_NO_DISPLAY) {
        LOGE("common_egl_display error\n");
        res=false;
    }
    if (common_egl_surface == EGL_NO_SURFACE) {
        LOGE("common_egl_surface error\n");
        res=false;
    }
    if (common_egl_context == EGL_NO_CONTEXT) {
        LOGE("common_egl_context error\n");
        res=false;
    }
    return res;
}

static void offscreen_render_and_to_screen() {
//    eglMakeCurrent(common_egl_display,common_egl_surface,common_egl_surface,common_egl_context);
//    offscreen_res_destroy();
//    offscreen_res_create(vw,vh);
    offscreen_render();
    offscreen_2_screen();
    LOGD("done\n");
}

static void common_program_create() {
    GLuint vs = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vs, 1, &common_vertex_shader, nullptr);
    glCompileShader(vs);
    GLuint fs = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fs, 1, &common_fragment_shader, nullptr);
    glCompileShader(fs);
    common_program = glCreateProgram();
    glAttachShader(common_program, vs);
    glAttachShader(common_program, fs);
    glLinkProgram(common_program);
    glDeleteShader(vs);
    glDeleteShader(fs);
}

static void common_program_destroy() {
    glDeleteProgram(common_program);
}

static void common_egl_init() {
    vw = ANativeWindow_getWidth(window);
    vh = ANativeWindow_getHeight(window);
    common_egl_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(common_egl_display, nullptr, nullptr);
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
    eglChooseConfig(common_egl_display, configAttrs, &me_eglConfig, 1, &numConfig);
    EGLint ctxAttrs[] = {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL_NONE
    };
    common_egl_context = eglCreateContext(common_egl_display, me_eglConfig, EGL_NO_CONTEXT, ctxAttrs);
    common_egl_surface = eglCreateWindowSurface(common_egl_display, me_eglConfig, window, nullptr);
    eglMakeCurrent(common_egl_display, common_egl_surface, common_egl_surface, common_egl_context);
    common_program_create();

    glViewport(0, 0, vw, vh);
    offscreen_res_create(vw,vh);
    glGenTextures(1,&update_tex_id);
    glBindTexture(GL_TEXTURE_2D, update_tex_id);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    int size=vw*vh*4;
    auto *update_pixels=new uint8_t[size];
    LOGD("vw=%d,vh=%d,size=%d\n",vw,vh,size);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, vw, vh, 0, GL_RGBA, GL_UNSIGNED_BYTE, update_pixels);
    delete[] update_pixels;
}

static void common_egl_deinit() {
    eglMakeCurrent(common_egl_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    offscreen_res_destroy();
    common_clear();
    if (common_egl_surface != EGL_NO_SURFACE) {
        eglDestroySurface(common_egl_display, common_egl_surface);
        common_egl_surface = EGL_NO_SURFACE;
    }

    common_program_destroy();
    glDeleteTextures(1,&update_tex_id);
}

static void common_update_tex_image_2d(GLbyte *pixels, int w, int h) {
    glBindTexture(GL_TEXTURE_2D,update_tex_id);
    glTexSubImage2D(GL_TEXTURE_2D,0,0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
//    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ManualEGLView_eglinit(JNIEnv *env, jobject thiz, jobject jsurface)
{
    window = ANativeWindow_fromSurface(env, jsurface);
    common_egl_init();
//    ANativeWindow_release(window);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ManualEGLView_egldeinit(JNIEnv *env, jobject thiz)
{
    common_egl_deinit();
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ManualEGLView_updateTexture(JNIEnv *env, jobject thiz, jbyteArray pixels, jint w, jint h)
{
    int size=w*h*4;
    auto *update_pixels=new uint8_t[size];
    env->GetByteArrayRegion(pixels, 0, size, (jbyte*)update_pixels);
    LOGD("w=%d,h=%d,size=%d\n",w,h,size);
    common_update_tex_image_2d((GLbyte*)update_pixels,w,h);
    delete[] update_pixels;
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ManualEGLView_render(JNIEnv *env, jobject thiz,jint w,jint h)
{
    if(offscreen_render_check(w,h)) {
        offscreen_render_and_to_screen();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ManualEGLView_resize(
        JNIEnv *env, jobject thiz, jobject jsurface)
{
    if(window!= nullptr) {
        ANativeWindow_release(window);
    }
    window = ANativeWindow_fromSurface(env, jsurface);
    vw = ANativeWindow_getWidth(window);
    vh = ANativeWindow_getHeight(window);
    eglMakeCurrent(common_egl_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    offscreen_res_destroy();
    common_clear();

    if (common_egl_surface != EGL_NO_SURFACE) {
        eglDestroySurface(common_egl_display, common_egl_surface);
        common_egl_surface = EGL_NO_SURFACE;
    }

    common_egl_surface = eglCreateWindowSurface(
            common_egl_display,
            me_eglConfig,
            window,
            nullptr
    );

    eglMakeCurrent(common_egl_display, common_egl_surface, common_egl_surface, common_egl_context);
    offscreen_res_create(vw,vh);
    glViewport(0,0,vw,vh);
//    ANativeWindow_release(window);
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