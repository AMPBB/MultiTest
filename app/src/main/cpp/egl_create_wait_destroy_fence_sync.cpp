#include"common_include_and_log.h"

void fence_sync_test() {
    EGLDisplay eglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLint major, minor;
    eglInitialize(eglDisplay, &major, &minor);
    EGLSyncKHR syncObj= nullptr;
    syncObj = eglCreateSyncKHR(eglDisplay,EGL_SYNC_FENCE_KHR,nullptr);
    EGLBoolean wait_result = eglWaitSyncKHR(eglDisplay,syncObj,0);
    if(!wait_result) {
        LOGE("wait failed");
    }
    eglDestroySyncKHR(eglDisplay, syncObj);
    LOGD("done");
//    eglTerminate(eglDisplay);
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

    EGLSyncKHR sync_fence;
    EGLBoolean wait_result;
    sync_fence = eglCreateSyncKHR(eglDisplay, EGL_SYNC_NATIVE_FENCE_ANDROID, nullptr);

    EGLint fd= my_eglDupNativeFenceFDANDROID(eglDisplay, sync_fence);
    if(fd<0) {
        LOGW("dup failed");
        return;
    }

    EGLint attributes[] = {
            EGL_SYNC_NATIVE_FENCE_FD_ANDROID, fd,
            EGL_NONE
    };
    EGLSyncKHR sync_fence_fd = eglCreateSyncKHR(eglDisplay, EGL_SYNC_NATIVE_FENCE_ANDROID, attributes);
    if (sync_fence_fd == EGL_NO_SYNC_KHR) {
        close(fd);
        LOGW("create dup fd sync fence failed, wait origin sync");
        wait_result=eglWaitSyncKHR(eglDisplay, sync_fence, 0);
        if(!wait_result) {
            LOGE("wait origin sync failed");
        }
        return;
    }
    wait_result=eglWaitSyncKHR(eglDisplay, sync_fence_fd, 0);
    if(!wait_result) {
        LOGW("wait dup failed, wait origin sync");
        wait_result=eglWaitSyncKHR(eglDisplay, sync_fence, 0);
        if(!wait_result) {
            LOGE("wait origin sync failed");
        }
    }

    eglDestroySyncKHR(eglDisplay, sync_fence);
//    close(fd);
    LOGD("done");
    eglTerminate(eglDisplay);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_textureview_TextureviewRenderThread_fence_1sync_1dup_1test(JNIEnv *env, jobject thiz) {
    fence_sync_dup_test();
}
