//
// Created by gr on 2026/4/25.
//
#include <jni.h>
#include <GLES2/gl2.h>
#include <cstring>
#include "resizable_view_shader_utils.h"

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
Java_pbbadd_opengl_multitest_resizableview_ResizeableView_nativeOnSurfaceCreated(JNIEnv* env, jobject thiz,jint pw,jint ph) {
    // 1. 创建并编译着色器
    program = createProgram(vertexShader, fragmentShader);
    glUseProgram(program);

    // 2. 生成纹理 ID
    glGenTextures(1, &textureId);
    glBindTexture(GL_TEXTURE_2D, textureId);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA,
                 pw,  // 固定宽度
                 ph,  // 固定高度
                 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);

    // 纹理参数
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
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
    glClear(GL_COLOR_BUFFER_BIT);
    glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

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