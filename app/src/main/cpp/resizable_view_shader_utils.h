//
// Created by gr on 2026/4/25.
//

#ifndef MULTITEST_RESIZABLE_VIEW_SHADER_UTILS_H
#define MULTITEST_RESIZABLE_VIEW_SHADER_UTILS_H

#endif //MULTITEST_RESIZABLE_VIEW_SHADER_UTILS_H
#include <GLES2/gl2.h>
#include <android/log.h>

#define LOG_TAG "OpenGLUtils"
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

inline GLuint createShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);

    GLint success;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
    if (!success) {
        char log[1024];
        glGetShaderInfoLog(shader, 1024, nullptr, log);
        ALOGD("Shader 编译失败: %s", log);
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

inline GLuint createProgram(const char* vertexSource, const char* fragmentSource) {
    GLuint vertex = createShader(GL_VERTEX_SHADER, vertexSource);
    GLuint fragment = createShader(GL_FRAGMENT_SHADER, fragmentSource);

    GLuint program = glCreateProgram();
    glAttachShader(program, vertex);
    glAttachShader(program, fragment);
    glLinkProgram(program);

    GLint success;
    glGetProgramiv(program, GL_LINK_STATUS, &success);
    if (!success) {
        char log[1024];
        glGetProgramInfoLog(program, 1024, nullptr, log);
        ALOGD("Program 链接失败: %s", log);
        glDeleteProgram(program);
        return 0;
    }

    glDeleteShader(vertex);
    glDeleteShader(fragment);
    return program;
}