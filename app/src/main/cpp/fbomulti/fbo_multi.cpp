#include <jni.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "../common_include_and_log.h"

__thread static GLuint tex_actual;
__thread static GLuint tex_red;
__thread static GLuint tex_green;

__thread static GLuint fbo_red;
__thread static GLuint fbo_green;
__thread static GLuint fbo_actual;

__thread static GLuint fbo_tex_red;
__thread static GLuint fbo_tex_green;
__thread static GLuint fbo_tex_actual;

__thread static ANativeWindow *window= nullptr;
__thread static int vw,vh;

__thread static EGLDisplay common_egl_display = EGL_NO_DISPLAY;
__thread static EGLContext common_egl_context = EGL_NO_CONTEXT;
__thread static EGLSurface common_egl_surface = EGL_NO_SURFACE;
__thread static EGLConfig  me_eglConfig;
__thread static GLuint common_program = 0;
__thread static int tex_w,tex_h;

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

static float common_vertices_pos[] = {
        -1.0f,  1.0f,
        1.0f,  1.0f,
        -1.0f, -1.0f,
        1.0f, -1.0f,
};

static float common_vertices_uv[] = {
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

static void tex_create_sub_(GLuint tex,const void *pixels) {
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, tex_w,tex_h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
}

static void tex_create() {
    int tex_data_size=tex_w*tex_h*4;
    uint32_t *tex_data=new uint32_t[tex_data_size];
    LOGD("tex create,tex_w=%d,tex_h=%d,size=%d\n",tex_w,tex_h,tex_data_size);

    for(int i=0;i<tex_data_size;++i) {
        tex_data[i]=0xff0000ff;
    }
    glGenTextures(1,&tex_red);
    tex_create_sub_(tex_red,tex_data);
    glGenTextures(1,&fbo_tex_red);
    tex_create_sub_(fbo_tex_red, nullptr);

    for(int i=0;i<tex_data_size;++i) {
        tex_data[i]=0xff00ff00;
    }
    glGenTextures(1,&tex_green);
    tex_create_sub_(tex_green,tex_data);
    glGenTextures(1,&fbo_tex_green);
    tex_create_sub_(fbo_tex_green, nullptr);

    for(int i=0;i<tex_data_size;++i) {
        tex_data[i]=0xff00ffff;
    }
    glGenTextures(1,&tex_actual);
    glBindTexture(GL_TEXTURE_2D, tex_actual);
    tex_create_sub_(tex_actual, tex_data);
    glGenTextures(1,&fbo_tex_actual);
    tex_create_sub_(fbo_tex_actual, nullptr);

    delete[] tex_data;
}

static void fbo_create() {
    glGenFramebuffers(1,&fbo_red);
    glGenFramebuffers(1,&fbo_green);
    glGenFramebuffers(1,&fbo_actual);
}

static void fbo_tex_destroy() {
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
    glDeleteFramebuffers(1, &fbo_red);
    glDeleteFramebuffers(1, &fbo_green);
    glDeleteFramebuffers(1, &fbo_actual);
    fbo_red=0;
    fbo_green=0;
    fbo_actual=0;
    glDeleteTextures(1,&tex_red);
    glDeleteTextures(1,&tex_green);
    glDeleteTextures(1,&tex_actual);
    glDeleteTextures(1,&tex_actual);
    tex_red=0;
    tex_green=0;
    tex_actual=0;
    tex_actual=0;
    glDeleteTextures(1,&fbo_tex_red);
    glDeleteTextures(1,&fbo_tex_green);
    glDeleteTextures(1,&fbo_tex_actual);
    glDeleteTextures(1,&fbo_tex_actual);
    fbo_tex_red=0;
    fbo_tex_green=0;
    fbo_tex_actual=0;
    fbo_tex_actual=0;
}

static bool fbo_tex_bind_sub_(GLuint fbo,GLuint tex) {
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    if(0==tex) {
        LOGE("tex is 0,pls check code!\n");
        return false;
    }
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if(status != GL_FRAMEBUFFER_COMPLETE)
    {
        LOGE("check failed\n");
        return false;
    }
    return true;
}

static void fbo_tex_bind() {
    if(!fbo_tex_bind_sub_(fbo_red, fbo_tex_red)) {
        return;
    }
    if(!fbo_tex_bind_sub_(fbo_green, fbo_tex_green)) {
        return;
    }
    if(!fbo_tex_bind_sub_(fbo_actual, fbo_tex_actual)) {
        return;
    }
}

static void fbo_tex_unbind() {
    // fbo_destroy() will do same effect!
}

static void common_clear() {
    glViewport(0,0,tex_w,tex_h);
    glClearColor(1.0,0,0,0.0f);
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
    glVertexAttribPointer(posLoc, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(float), common_vertices_pos);
    glEnableVertexAttribArray(posLoc);
    GLint texLoc = glGetAttribLocation(common_program, "aTexCoord");
    glVertexAttribPointer(texLoc, 2, GL_FLOAT, GL_FALSE, 2 * sizeof(float), common_vertices_uv);
    glEnableVertexAttribArray(texLoc);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

static void fbo_tex_draw_sub_(GLuint fbo,GLuint tex_draw) {
    glBindFramebuffer(GL_FRAMEBUFFER,fbo);
    glBindTexture(GL_TEXTURE_2D, tex_draw);
    common_draw();
}

static void fbo_tex_draw() {
    fbo_tex_draw_sub_(fbo_red,tex_red);
    fbo_tex_draw_sub_(fbo_green,tex_green);
    fbo_tex_draw_sub_(fbo_actual,tex_actual);
}

static void offscreen_draw() {
    fbo_tex_draw();
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, fbo_tex_actual);
    common_draw();
}

static void offscreen_draw_alternating() {
    fbo_tex_draw();
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, fbo_tex_actual);
    common_draw();
    eglSwapBuffers(common_egl_display, common_egl_surface);
    glBindTexture(GL_TEXTURE_2D, fbo_tex_green);
    common_draw();
    eglSwapBuffers(common_egl_display, common_egl_surface);
    glBindTexture(GL_TEXTURE_2D, fbo_tex_red);
    common_draw();
    eglSwapBuffers(common_egl_display, common_egl_surface);
    glBindTexture(GL_TEXTURE_2D, fbo_tex_actual);
    common_draw();
    eglSwapBuffers(common_egl_display, common_egl_surface);
}

static void offscreen_draw_fbo() {
    fbo_tex_draw();
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_red);
    eglSwapBuffers(common_egl_display, common_egl_surface);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_green);
    eglSwapBuffers(common_egl_display, common_egl_surface);
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_actual);
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

static void offscreen_2_screen() {
    offscreen_draw();
    eglSwapBuffers(common_egl_display, common_egl_surface);
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
    LOGD("egl init,vw=%d,vh=%d\n",vw,vh);
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
    glViewport(0, 0, tex_w, tex_h);
    tex_create();
    fbo_create();
    fbo_tex_bind();
}

static void common_egl_deinit() {
    eglMakeCurrent(common_egl_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    fbo_tex_unbind();
    fbo_tex_destroy();
    common_clear();
    if (common_egl_surface != EGL_NO_SURFACE) {
        eglDestroySurface(common_egl_display, common_egl_surface);
        common_egl_surface = EGL_NO_SURFACE;
    }
    common_program_destroy();
}

static void common_update_tex_image_2d(GLbyte *pixels, int w, int h) {
    glBindTexture(GL_TEXTURE_2D, tex_actual);
    glTexSubImage2D(GL_TEXTURE_2D,0,0,0,tex_w,tex_h,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
//    glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,tex_w,tex_h,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_fbomulti_FboMultiView_setTexSize(JNIEnv *env, jobject thiz, jint w,jint h)
{
    tex_w=w;
    tex_h=h;
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_fbomulti_FboMultiView_eglinit(JNIEnv *env, jobject thiz, jobject jsurface)
{
    window = ANativeWindow_fromSurface(env, jsurface);
    common_egl_init();
//    ANativeWindow_release(window);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_fbomulti_FboMultiView_egldeinit(JNIEnv *env, jobject thiz)
{
    common_egl_deinit();
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_fbomulti_FboMultiView_updateTexture(JNIEnv *env, jobject thiz, jbyteArray pixels, jint w, jint h)
{
    int size=w*h*4;
    auto *update_pixels=new uint8_t[size];
    env->GetByteArrayRegion(pixels, 0, size, (jbyte*)update_pixels);
    LOGD("w=%d,h=%d,size=%d\n",w,h,size);
    common_update_tex_image_2d((GLbyte*)update_pixels,w,h);
    delete[] update_pixels;
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_fbomulti_FboMultiView_render(JNIEnv *env, jobject thiz,jint w,jint h)
{
    if(offscreen_render_check(w,h)) {
        offscreen_2_screen();
//        offscreen_draw_alternating();
//        offscreen_draw_fbo();
    }
}
