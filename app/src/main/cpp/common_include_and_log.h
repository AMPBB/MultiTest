//
// Created by gr on 2026/1/16.
//

#ifndef OPENGLTEST30_COMMON_INCLUDE_AND_LOG_H
#define OPENGLTEST30_COMMON_INCLUDE_AND_LOG_H

#include <jni.h>
#include <string>
#define EGL_EGLEXT_PROTOTYPES
#include<EGL/egl.h>
#include<EGL/eglext.h>
#include<GLES2/gl2.h>
#include <GLES3/gl3.h>
#include<GLES3/gl3ext.h>
#include <android/log.h>
#include <__random/mersenne_twister_engine.h>
#include <__random/uniform_real_distribution.h>
#include <__random/random_device.h>
#include <unistd.h> // 用于usleep()

// 日志宏定义，方便调试
#define TAG "native"
#define LOGE(fmt,...) __android_log_print(ANDROID_LOG_ERROR, TAG, "%s,%d," fmt, __FILE_NAME__,__LINE__, ##__VA_ARGS__)
#define LOGW(fmt,...) __android_log_print(ANDROID_LOG_WARN, TAG, "%s,%d," fmt, __FILE_NAME__,__LINE__, ##__VA_ARGS__)
#define LOGI(fmt,...) __android_log_print(ANDROID_LOG_INFO, TAG, "%s,%d," fmt, __FILE_NAME__,__LINE__, ##__VA_ARGS__)
#define LOGD(fmt,...) __android_log_print(ANDROID_LOG_DEBUG, TAG, "%s,%d," fmt, __FILE_NAME__,__LINE__, ##__VA_ARGS__)

#define print_error_indent {GLenum err; err = glGetError(); if(GL_NO_ERROR!=err) LOGE("err=0x%x",err);}

#define RANDOM_MIN_SIZE 12    // 整数随机最小值
#define RANDOM_MAX_SIZE 32*1024  // 整数随机最大值

static std::random_device rd;
static std::mt19937 gen(rd());
// 浮点数分布器：固定生成 0.0f ~ 1.0f 浮点数
static std::uniform_real_distribution<float> dis_f(0.0f, 1.0f);
// 整数分布器：生成 [MIN_SIZE, MAX_SIZE] 范围内的整数
static std::uniform_int_distribution<int> dist_size(RANDOM_MIN_SIZE, RANDOM_MAX_SIZE);

static void generateRandomData(GLubyte* data, int size) {
    for (int i = 0; i < size; i++) {
        data[i] = static_cast<GLubyte>(dis_f(gen) * 256.0f);
    }
}

static float getRandomFloat01() {
    return dis_f(gen);
}

static float getRandomFloat(float min, float max) {
    std::uniform_real_distribution<float> dis(min, max);
    return dis(gen);
}

static int getRandomSize() {
    return dist_size(gen);
}

static void msleep(int t) {
    int c=0;
    while(c<t) {
        usleep(1000);
        ++c;
    }
}

static void ssleep(int t) {
    int c=0;
    while(c<t) {
        msleep(1000);
        ++c;
    }
}

#endif //OPENGLTEST30_COMMON_INCLUDE_AND_LOG_H
