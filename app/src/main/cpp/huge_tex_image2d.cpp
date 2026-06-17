#include "common_include_and_log.h"

#include <algorithm>
#include <cstdint>
#include <cstdarg>
#include <limits>
#include <new>
#include <string>

namespace {

thread_local GLuint g_texture_id = 0;
thread_local int g_texture_width = 0;
thread_local int g_texture_height = 0;
thread_local uint32_t *g_texture_buffer = nullptr;
thread_local size_t g_texture_pixel_count = 0;
thread_local int g_frame_index = 0;
thread_local std::string g_last_error;

static void set_last_error(const char *fmt, ...) {
    char buffer[512];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);
    g_last_error = buffer;
    LOGE("%s", buffer);
}

static void clear_last_error() {
    g_last_error.clear();
}

static bool check_gl_error(const char *op) {
    GLenum err = glGetError();
    if (err == GL_NO_ERROR) {
        return true;
    }
    set_last_error("%s failed: 0x%x", op, err);
    return false;
}

static uint32_t make_rgba(uint8_t red, uint8_t green, uint8_t blue, uint8_t alpha) {
    return (static_cast<uint32_t>(alpha) << 24) |
           (static_cast<uint32_t>(blue) << 16) |
           (static_cast<uint32_t>(green) << 8) |
           static_cast<uint32_t>(red);
}

static int positive_mod_256(int64_t value) {
    int result = static_cast<int>(value % 256);
    return result < 0 ? result + 256 : result;
}

static void fill_texture_data(int frame_index) {
    for (int gl_y = 0; gl_y < g_texture_height; gl_y++) {
        int row_from_top = g_texture_height - 1 - gl_y;
        int base = positive_mod_256((static_cast<int64_t>(row_from_top) - frame_index * 4LL) * 3LL);
        uint32_t color = make_rgba(
                static_cast<uint8_t>(base),
                static_cast<uint8_t>((base + 85) & 0xff),
                static_cast<uint8_t>((base + 170) & 0xff),
                0xff);

        uint32_t *row = g_texture_buffer + static_cast<size_t>(gl_y) * g_texture_width;
        std::fill(row, row + g_texture_width, color);
    }
}

static void release_texture() {
    if (g_texture_id != 0) {
        glBindTexture(GL_TEXTURE_2D, 0);
        glDeleteTextures(1, &g_texture_id);
        g_texture_id = 0;
    }

    delete[] g_texture_buffer;
    g_texture_buffer = nullptr;
    g_texture_width = 0;
    g_texture_height = 0;
    g_texture_pixel_count = 0;
    g_frame_index = 0;
}

} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_pbbadd_opengl_multitest_hugeteximage2d_HugeTexView_nativeCreateTexture(
        JNIEnv *env, jclass clazz, jint width, jint height) {
    (void) env;
    (void) clazz;

    release_texture();
    clear_last_error();

    if (width <= 0 || height <= 0) {
        set_last_error("texture size must be > 0");
        return 0;
    }

    GLint max_texture_size = 0;
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &max_texture_size);
    if (!check_gl_error("glGetIntegerv(GL_MAX_TEXTURE_SIZE)")) {
        return 0;
    }
    if (width > max_texture_size || height > max_texture_size) {
        set_last_error("size %d x %d exceeds GL_MAX_TEXTURE_SIZE %d",
                       width, height, max_texture_size);
        return 0;
    }

    uint64_t pixel_count64 = static_cast<uint64_t>(width) * static_cast<uint64_t>(height);
    uint64_t byte_size64 = pixel_count64 * sizeof(uint32_t);
    if (pixel_count64 > static_cast<uint64_t>(std::numeric_limits<size_t>::max() / sizeof(uint32_t))) {
        set_last_error("texture data is too large: %llu bytes",
                       static_cast<unsigned long long>(byte_size64));
        return 0;
    }

    size_t pixel_count = static_cast<size_t>(pixel_count64);
    g_texture_buffer = new (std::nothrow) uint32_t[pixel_count];
    if (g_texture_buffer == nullptr) {
        set_last_error("native allocate texture data failed: %.2f MB",
                       byte_size64 / 1024.0 / 1024.0);
        return 0;
    }

    g_texture_width = width;
    g_texture_height = height;
    g_texture_pixel_count = pixel_count;
    g_frame_index = 0;
    fill_texture_data(g_frame_index);

    GLuint texture_id = 0;
    glGenTextures(1, &texture_id);
    if (!check_gl_error("glGenTextures") || texture_id == 0) {
        release_texture();
        return 0;
    }

    g_texture_id = texture_id;
    glBindTexture(GL_TEXTURE_2D, g_texture_id);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA,
                 g_texture_width, g_texture_height, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, g_texture_buffer);
    if (!check_gl_error("glTexImage2D")) {
        release_texture();
        return 0;
    }

    g_frame_index = 1;
    LOGI("native huge texture created: %d x %d, %.2f MB, id=%u",
         g_texture_width, g_texture_height, byte_size64 / 1024.0 / 1024.0, g_texture_id);
    return static_cast<jint>(g_texture_id);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_pbbadd_opengl_multitest_hugeteximage2d_HugeTexView_nativeUpdateTexture(
        JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;

    clear_last_error();

    if (g_texture_id == 0 || g_texture_buffer == nullptr) {
        set_last_error("texture is not created");
        return JNI_FALSE;
    }

    fill_texture_data(g_frame_index);
    glBindTexture(GL_TEXTURE_2D, g_texture_id);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0,
                    g_texture_width, g_texture_height,
                    GL_RGBA, GL_UNSIGNED_BYTE, g_texture_buffer);
    if (!check_gl_error("glTexSubImage2D")) {
        return JNI_FALSE;
    }

    g_frame_index++;
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_hugeteximage2d_HugeTexView_nativeDestroyTexture(
        JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;

    release_texture();
    clear_last_error();
}

extern "C" JNIEXPORT jstring JNICALL
Java_pbbadd_opengl_multitest_hugeteximage2d_HugeTexView_nativeGetLastError(
        JNIEnv *env, jclass clazz) {
    (void) clazz;

    if (g_last_error.empty()) {
        return env->NewStringUTF("native texture error");
    }
    return env->NewStringUTF(g_last_error.c_str());
}
