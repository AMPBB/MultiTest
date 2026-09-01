#include <jni.h>

#include <EGL/egl.h>
#include <GLES3/gl3.h>

#include "../common_include_and_log.h"

using FramebufferTexture2DMultisampleExt =
        void (*)(GLenum, GLenum, GLenum, GLuint, GLint, GLsizei);

using RenderbufferStorageMultisampleExt =
        void (*)(GLenum, GLsizei, GLenum, GLsizei, GLsizei);

static FramebufferTexture2DMultisampleExt getFramebufferTexture2DMultisampleExt() {
    return reinterpret_cast<FramebufferTexture2DMultisampleExt>(
            eglGetProcAddress("glFramebufferTexture2DMultisampleEXT"));
}

static RenderbufferStorageMultisampleExt getRenderbufferStorageMultisampleExt() {
    return reinterpret_cast<RenderbufferStorageMultisampleExt>(
            eglGetProcAddress("glRenderbufferStorageMultisampleEXT"));
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_pbbadd_opengl_multitest_texturemultisampleext_TextureMultiSampleExtView_nativeHasFramebufferTexture2DMultisampleEXT(
        JNIEnv *, jclass) {
    return getFramebufferTexture2DMultisampleExt() != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_pbbadd_opengl_multitest_texturemultisampleext_TextureMultiSampleExtView_nativeHasRenderbufferStorageMultisampleEXT(
        JNIEnv *, jclass) {
    return getRenderbufferStorageMultisampleExt() != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_texturemultisampleext_TextureMultiSampleExtView_nativeFramebufferTexture2DMultisampleEXT(
        JNIEnv *, jclass, jint target, jint attachment, jint textarget, jint texture,
        jint level, jint samples) {
    FramebufferTexture2DMultisampleExt function =
            getFramebufferTexture2DMultisampleExt();
    if (function == nullptr) {
        LOGE("glFramebufferTexture2DMultisampleEXT is unavailable");
        return;
    }

    function(
            static_cast<GLenum>(target),
            static_cast<GLenum>(attachment),
            static_cast<GLenum>(textarget),
            static_cast<GLuint>(texture),
            static_cast<GLint>(level),
            static_cast<GLsizei>(samples));
}

extern "C"
JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_texturemultisampleext_TextureMultiSampleExtView_nativeRenderbufferStorageMultisampleEXT(
        JNIEnv *, jclass, jint target, jint samples, jint internalformat, jint width,
        jint height) {
    RenderbufferStorageMultisampleExt function = getRenderbufferStorageMultisampleExt();
    if (function == nullptr) {
        LOGE("glRenderbufferStorageMultisampleEXT is unavailable");
        return;
    }

    function(
            static_cast<GLenum>(target),
            static_cast<GLsizei>(samples),
            static_cast<GLenum>(internalformat),
            static_cast<GLsizei>(width),
            static_cast<GLsizei>(height));
}
