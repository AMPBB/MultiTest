#include <jni.h>

#include <EGL/egl.h>
#include <GLES2/gl2.h>

#include "../common_include_and_log.h"

using FramebufferTexture2DMultisampleExt =
        void (*)(GLenum, GLenum, GLenum, GLuint, GLint, GLsizei);

static FramebufferTexture2DMultisampleExt getFramebufferTexture2DMultisampleExt() {
    return reinterpret_cast<FramebufferTexture2DMultisampleExt>(
            eglGetProcAddress("glFramebufferTexture2DMultisampleEXT"));
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_pbbadd_opengl_multitest_texturemultisampleext_TextureMultiSampleExtView_nativeHasFramebufferTexture2DMultisampleEXT(
        JNIEnv *, jclass) {
    return getFramebufferTexture2DMultisampleExt() != nullptr ? JNI_TRUE : JNI_FALSE;
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
