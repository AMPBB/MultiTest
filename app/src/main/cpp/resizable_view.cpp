//
// Created by gr on 2026/4/25.
//
#include <jni.h>
#include <GLES2/gl2.h>
#include <cstring>
#include <EGL/egl.h>
#include "resizable_view_shader_utils.h"
#include <android/native_window_jni.h>

static EGLDisplay eglDisplay = EGL_NO_DISPLAY;
static EGLContext eglContext = EGL_NO_CONTEXT;
static EGLSurface eglSurface = EGL_NO_SURFACE;
static EGLConfig  eglConfig = NULL;

#define FIXED_TEXTURE_SIZE 512
// 着色器和纹理 ID
GLuint program;
GLuint textureId;
// 图片像素数据
jbyte* imagePixels = nullptr;
int imageWidth = 0, imageHeight = 0;

// 顶点着色器
const char* vertexShader = R"(
    attribute vec2 aPos;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        gl_Position = vec4(aPos, 0.0, 1.0);
        vTexCoord = aTexCoord;
    }
)";

// 片段着色器
const char* fragmentShader = R"(
    precision mediump float;
    varying vec2 vTexCoord;
    uniform sampler2D uTexture;
    void main() {
        gl_FragColor = texture2D(uTexture, vTexCoord);
    }
)";

// 全屏顶点坐标（覆盖整个 View）
float vertices[] = {
        -1.0f,  1.0f,  0.0f, 0.0f, // 左上
        1.0f,  1.0f,  1.0f, 0.0f, // 右上
        -1.0f, -1.0f,  0.0f, 1.0f, // 左下
        1.0f, -1.0f,  1.0f, 1.0f  // 右下
};

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ResizeableView_recreateSurface(
        JNIEnv* env, jobject thiz, jobject surface)
{
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (eglSurface != EGL_NO_SURFACE) {
        eglDestroySurface(eglDisplay, eglSurface);
        eglSurface = EGL_NO_SURFACE;
    }
    eglSurface = eglCreateWindowSurface(eglDisplay, eglConfig, window, nullptr);
    eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA,
                 FIXED_TEXTURE_SIZE, FIXED_TEXTURE_SIZE,
                 0, GL_RGBA, GL_UNSIGNED_BYTE, imagePixels);
    glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glUseProgram(program);
    GLint posLoc = glGetAttribLocation(program, "aPos");
    glVertexAttribPointer(posLoc, 2, GL_FLOAT, GL_FALSE, 4*sizeof(float), vertices);
    glEnableVertexAttribArray(posLoc);
    GLint texLoc = glGetAttribLocation(program, "aTexCoord");
    glVertexAttribPointer(texLoc, 2, GL_FLOAT, GL_FALSE, 4*sizeof(float), vertices+2);
    glEnableVertexAttribArray(texLoc);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    eglSwapBuffers(eglDisplay, eglSurface);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ResizeableView_nativeOnSurfaceCreated(
        JNIEnv* env, jobject thiz, jint pw, jint ph)
{
    // 获取 display
    eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(eglDisplay, 0, 0);

    // 选择 config
    const EGLint attribs[] = {
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_DEPTH_SIZE, 16,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
            EGL_NONE
    };
    EGLint numConfigs;
    eglChooseConfig(eglDisplay, attribs, &eglConfig, 1, &numConfigs);

    // 创建上下文
    const EGLint ctxAttribs[] = {
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL_NONE
    };
    eglContext = eglCreateContext(eglDisplay, eglConfig, EGL_NO_CONTEXT, ctxAttribs);

    // ------------------------
    // 你的着色器、纹理创建逻辑不变
    // ------------------------
    program = createProgram(vertexShader, fragmentShader);
    glGenTextures(1, &textureId);
    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA,
                 FIXED_TEXTURE_SIZE, FIXED_TEXTURE_SIZE,
                 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ResizeableView_nativeSetTextureData(JNIEnv* env, jobject thiz,jbyteArray pixels, jint width, jint height) {
    // 保存图片数据
    imageWidth = width;
    imageHeight = height;
    int pixelSize = width * height * 4;
    imagePixels = new jbyte[pixelSize];
    env->GetByteArrayRegion(pixels, 0, pixelSize, imagePixels);

    // 绑定纹理
    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexSubImage2D(GL_TEXTURE_2D, 0,
                    0, 0,
                    width,
                    height,
                    GL_RGBA, GL_UNSIGNED_BYTE, imagePixels);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ResizeableView_glteximage2d(JNIEnv* env, jobject thiz,jbyteArray pixels, jint width, jint height) {
    // 保存图片数据
    imageWidth = width;
    imageHeight = height;
    int pixelSize = width * height * 4;
    imagePixels = new jbyte[pixelSize];
    env->GetByteArrayRegion(pixels, 0, pixelSize, imagePixels);

    // 绑定纹理
    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexSubImage2D(GL_TEXTURE_2D, 0,
                    0, 0,
                    width,
                    height,
                    GL_RGBA, GL_UNSIGNED_BYTE, imagePixels);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ResizeableView_nativeOnSurfaceChanged(JNIEnv* env, jobject thiz, jint width, jint height) {
    glViewport(0, 0, width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ResizeableView_nativeOnSurfaceChangedAndUpdate(JNIEnv* env, jobject thiz, jint vw, jint vh,jbyteArray pixels,jint pw,jint ph) {
    glViewport(0, 0, vw, vh);
    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexSubImage2D(GL_TEXTURE_2D, 0,
                    0, 0,
                    pw,
                    ph,
                    GL_RGBA, GL_UNSIGNED_BYTE, imagePixels);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ResizeableView_nativeOnDrawFrame(JNIEnv* env, jobject thiz) {
    glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    // 设置顶点数据
    GLint posLoc = glGetAttribLocation(program, "aPos");
    glVertexAttribPointer(posLoc, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), vertices);
    glEnableVertexAttribArray(posLoc);

    GLint texLoc = glGetAttribLocation(program, "aTexCoord");
    glVertexAttribPointer(texLoc, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), vertices + 2);
    glEnableVertexAttribArray(texLoc);

    // 绘制
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_resizableview_ResizeableView_onDrawOnlyTriangle(JNIEnv* env, jobject thiz) {
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}