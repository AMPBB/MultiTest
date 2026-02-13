#include"common_include_and_log.h"

void fence_sync_test() {
    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major, minor;
    eglInitialize(eglDisplay, &major, &minor);
    EGLSyncKHR syncObj = eglCreateSyncKHR(eglDisplay,EGL_SYNC_FENCE_KHR,NULL);
    EGLint wait_result = eglClientWaitSyncKHR(eglDisplay, syncObj, EGL_SYNC_FLUSH_COMMANDS_BIT_KHR, EGL_FOREVER_KHR);
    switch (wait_result) {
        case EGL_CONDITION_SATISFIED_KHR:
            LOGD("EGL_CONDITION_SATISFIED_KHR");
            break;
        case EGL_TIMEOUT_EXPIRED_KHR:
            LOGD("EGL_TIMEOUT_EXPIRED_KHR");
            break;
        case EGL_ALREADY_SIGNALED_NV: //case EGL_ALREADY_SIGNALED_KHR:
            LOGD("EGL_ALREADY_SIGNALED_NV");
            break;
        case EGL_FALSE:
            LOGD("EGL_FALSE");
            break;
        default:
            LOGE("what wait result? 0x%x", wait_result);
            break;
    }
    eglDestroySyncKHR(eglDisplay, syncObj);
    LOGD("done");
//    eglTerminate(eglDisplay);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_textureview_TextureviewRenderThread_fencesynctest(JNIEnv *env, jobject thiz) {
    fence_sync_test();
}