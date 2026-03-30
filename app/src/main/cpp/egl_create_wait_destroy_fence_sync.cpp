#include"common_include_and_log.h"

void fence_sync_test() {
    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major, minor;
    eglInitialize(eglDisplay, &major, &minor);
    EGLSyncKHR sync_fence= nullptr;
    sync_fence = eglCreateSyncKHR(eglDisplay, EGL_SYNC_FENCE_KHR, nullptr);
    EGLBoolean result=false;
    result= eglWaitSyncKHR(eglDisplay, sync_fence, 0);
    if(!result) {
        LOGE("wait failed");
    }
    result=eglDestroySyncKHR(eglDisplay, sync_fence);
    if(!result) {
        LOGE("destroy sync_fence failed");
    }
    LOGD("done");
    eglTerminate(eglDisplay);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_textureview_TextureviewRenderThread_fence_1sync_1test(JNIEnv *env, jobject thiz) {
    fence_sync_test();
}


void fence_sync_dup_test() {
    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major, minor;
    if(!eglInitialize(eglDisplay, &major, &minor)) {
        LOGE("egl function load failed,return!");
        return;
    }

    PFNEGLDUPNATIVEFENCEFDANDROIDPROC my_eglDupNativeFenceFDANDROID = nullptr;
    my_eglDupNativeFenceFDANDROID = (PFNEGLDUPNATIVEFENCEFDANDROIDPROC)eglGetProcAddress("eglDupNativeFenceFDANDROID");

    EGLSyncKHR sync_native_fence= nullptr;
    EGLBoolean result=false;
    sync_native_fence = eglCreateSyncKHR(eglDisplay, EGL_SYNC_NATIVE_FENCE_ANDROID, nullptr);

    EGLint sync_native_fence_fd=-1;
    sync_native_fence_fd=my_eglDupNativeFenceFDANDROID(eglDisplay, sync_native_fence);
    if(sync_native_fence_fd < 0) {
        LOGW("dup failed");
        return;
    }

    EGLint attributes[] = {
            EGL_SYNC_NATIVE_FENCE_FD_ANDROID, sync_native_fence_fd,
            EGL_NONE
    };
    EGLSyncKHR fd_fence_android = eglCreateSyncKHR(eglDisplay, EGL_SYNC_NATIVE_FENCE_ANDROID, attributes);
    if (fd_fence_android == EGL_NO_SYNC_KHR) {
        close(sync_native_fence_fd);
        LOGW("create dup sync_native_fence_fd sync fence failed, wait origin sync");
        result=eglWaitSyncKHR(eglDisplay, sync_native_fence, 0);
        if(!result) {
            LOGE("wait origin sync failed");
        }
        return;
    }
    result=eglWaitSyncKHR(eglDisplay, fd_fence_android, 0);
    if(!result) {
        LOGW("wait dup failed, wait origin sync");
        result=eglWaitSyncKHR(eglDisplay, sync_native_fence, 0);
        if(!result) {
            LOGE("wait origin sync failed");
        }
    }

    result=eglDestroySyncKHR(eglDisplay, fd_fence_android);
    if(!result) {
        LOGE("destroy fd_fence_android failed");
        close(sync_native_fence_fd);
    }

    result=eglDestroySyncKHR(eglDisplay, sync_native_fence);
    if(!result) {
        LOGE("destroy sync_native_fence failed");
    }
    LOGD("done");
    eglTerminate(eglDisplay);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_textureview_TextureviewRenderThread_fence_1sync_1dup_1test(JNIEnv *env, jobject thiz) {
    fence_sync_dup_test();
}
