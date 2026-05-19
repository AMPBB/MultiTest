#include <jni.h>
#include <GLES2/gl2.h>
#include <EGL/egl.h>
#include <cstring>
#include <cstdlib>
#include <cmath>

// 对外JNI接口前缀
#define RENDER_JNI_PREFIX Java_pbbadd_opengl_multitest_cube3dmultifbo_Cube3DMultiFboRender_

// 矩阵工具函数
static void matrix_set_identity_m(float* matrix, int offset) {
    memset(matrix + offset, 0, sizeof(float) * 16);
    matrix[offset + 0] = 1.0f;
    matrix[offset + 5] = 1.0f;
    matrix[offset + 10] = 1.0f;
    matrix[offset + 15] = 1.0f;
}

static void matrix_set_rotate_m(float* matrix, int offset, float angle, float x, float y, float z) {
    float a = angle * (float)M_PI / 180.0f;
    float c = cosf(a);
    float s = sinf(a);
    float len = sqrtf(x * x + y * y + z * z);
    x /= len; y /= len; z /= len;
    float xx = x * x, yy = y * y, zz = z * z;
    float xy = x * y, xz = x * z, yz = y * z;
    float xs = x * s, ys = y * s, zs = z * s;
    float one_c = 1.0f - c;

    matrix[offset + 0] = (one_c * xx) + c;
    matrix[offset + 1] = (one_c * xy) - zs;
    matrix[offset + 2] = (one_c * xz) + ys;
    matrix[offset + 3] = 0.0f;

    matrix[offset + 4] = (one_c * xy) + zs;
    matrix[offset + 5] = (one_c * yy) + c;
    matrix[offset + 6] = (one_c * yz) - xs;
    matrix[offset + 7] = 0.0f;

    matrix[offset + 8] = (one_c * xz) - ys;
    matrix[offset + 9] = (one_c * yz) + xs;
    matrix[offset + 10] = (one_c * zz) + c;
    matrix[offset + 11] = 0.0f;

    matrix[offset + 12] = 0.0f;
    matrix[offset + 13] = 0.0f;
    matrix[offset + 14] = 0.0f;
    matrix[offset + 15] = 1.0f;
}

static void matrix_multiply_m(float* result, int result_offset,
                              const float* lhs, int lhs_offset,
                              const float* rhs, int rhs_offset) {
    float tmp[16];
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            tmp[i * 4 + j] =
                    lhs[lhs_offset + i * 4 + 0] * rhs[rhs_offset + 0 * 4 + j] +
                    lhs[lhs_offset + i * 4 + 1] * rhs[rhs_offset + 1 * 4 + j] +
                    lhs[lhs_offset + i * 4 + 2] * rhs[rhs_offset + 2 * 4 + j] +
                    lhs[lhs_offset + i * 4 + 3] * rhs[rhs_offset + 3 * 4 + j];
        }
    }
    memcpy(result + result_offset, tmp, sizeof(float) * 16);
}

static void matrix_set_look_at_m(float* matrix, int offset,
                                 float eyeX, float eyeY, float eyeZ,
                                 float centerX, float centerY, float centerZ,
                                 float upX, float upY, float upZ) {
    float zx = eyeX - centerX;
    float zy = eyeY - centerY;
    float zz = eyeZ - centerZ;
    float len = sqrtf(zx*zx + zy*zy + zz*zz);
    zx /= len; zy /= len; zz /= len;

    float xx = upY * zz - upZ * zy;
    float xy = upZ * zx - upX * zz;
    float xz = upX * zy - upY * zx;
    len = sqrtf(xx*xx + xy*xy + xz*xz);
    xx /= len; xy /= len; xz /= len;

    float yx = zy * xz - zz * xy;
    float yy = zz * xx - zx * xz;
    float yz = zx * xy - zy * xx;

    matrix[offset + 0] = xx;
    matrix[offset + 1] = yx;
    matrix[offset + 2] = zx;
    matrix[offset + 3] = 0.0f;

    matrix[offset + 4] = xy;
    matrix[offset + 5] = yy;
    matrix[offset + 6] = zy;
    matrix[offset + 7] = 0.0f;

    matrix[offset + 8] = xz;
    matrix[offset + 9] = yz;
    matrix[offset + 10] = zz;
    matrix[offset + 11] = 0.0f;

    matrix[offset + 12] = -(xx*eyeX + xy*eyeY + xz*eyeZ);
    matrix[offset + 13] = -(yx*eyeX + yy*eyeY + yz*eyeZ);
    matrix[offset + 14] = -(zx*eyeX + zy*eyeY + zz*eyeZ);
    matrix[offset + 15] = 1.0f;
}

static void matrix_perspective_m(float* matrix, int offset, float fovy, float aspect,
                                 float zNear, float zFar) {
    float f = 1.0f / tanf(fovy * (float)M_PI / 360.0f);
    float range = zNear - zFar;

    memset(matrix + offset, 0, sizeof(float) * 16);
    matrix[offset + 0] = f / aspect;
    matrix[offset + 5] = f;
    matrix[offset + 10] = (zFar + zNear) / range;
    matrix[offset + 11] = -1.0f;
    matrix[offset + 14] = (2 * zFar * zNear) / range;
    matrix[offset + 15] = 0.0f;
}

// ==============================================
// 立方体着色器
// ==============================================
static const char* VERTEX_SHADER =
        "attribute vec3 vPosition;\n"
        "attribute vec4 aColor;\n"
        "uniform mat4 uMVPMatrix;\n"
        "varying vec4 vColor;\n"
        "void main() {\n"
        "  gl_Position = uMVPMatrix * vec4(vPosition, 1.0);\n"
        "  vColor = aColor;\n"
        "}";

static const char* FRAGMENT_SHADER =
        "precision mediump float;\n"
        "varying vec4 vColor;\n"
        "void main() {\n"
        "  gl_FragColor = vColor;\n"
        "}";

// ==============================================
// 纹理显示着色器（专门画FBO到屏幕，修复关键！）
// ==============================================
static const char* SCREEN_VERTEX_SHADER =
        "attribute vec3 vPosition;\n"
        "attribute vec2 aTexCoord;\n"
        "varying vec2 vTexCoord;\n"
        "void main() {\n"
        "    gl_Position = vec4(vPosition, 1.0);\n"
        "    vTexCoord = aTexCoord;\n"
        "}";

static const char* SCREEN_FRAGMENT_SHADER =
        "precision mediump float;\n"
        "varying vec2 vTexCoord;\n"
        "uniform sampler2D uTexture;\n"
        "void main() {\n"
        "    gl_FragColor = texture2D(uTexture, vTexCoord);\n"
        "}";

// ==============================================
// FBO 配置
// ==============================================
#define FBO_COUNT 3
#define FBO_WIDTH 800
#define FBO_HEIGHT 800

static GLuint fbo_ids[FBO_COUNT];
static GLuint fbo_textures[FBO_COUNT];
static GLuint fbo_rbos[FBO_COUNT];

static const int FBO_A = 0;
static const int FBO_B = 1;
static const int FBO_C = 2;

// ==============================================
// 立方体数据（修复：8个顶点对应8组颜色）
// ==============================================
static const float CUBE_VERTICES[] = {
        -0.5f, -0.5f,  0.5f,
        0.5f, -0.5f,  0.5f,
        -0.5f,  0.5f,  0.5f,
        0.5f,  0.5f,  0.5f,
        -0.5f, -0.5f, -0.5f,
        0.5f, -0.5f, -0.5f,
        -0.5f,  0.5f, -0.5f,
        0.5f,  0.5f, -0.5f
};

static const GLshort CUBE_INDICES[] = {
        0,1,2, 2,1,3,    // 前
        4,6,5, 5,6,7,    // 后
        4,0,6, 6,0,2,    // 左
        1,5,3, 3,5,7,    // 右
        2,3,6, 6,3,7,    // 上
        4,5,0, 0,5,1     // 下
};

// 修复：每个立方体 8组颜色，匹配8个顶点
static const float CUBE_COLORS_A[] = {
        1.0f,0.0f,0.0f,1.0f, 1.0f,0.0f,0.0f,1.0f,
        1.0f,0.0f,0.0f,1.0f, 1.0f,0.0f,0.0f,1.0f,
        1.0f,0.0f,0.0f,1.0f, 1.0f,0.0f,0.0f,1.0f,
        1.0f,0.0f,0.0f,1.0f, 1.0f,0.0f,0.0f,1.0f
};

static const float CUBE_COLORS_B[] = {
        0.0f,1.0f,0.0f,1.0f, 0.0f,1.0f,0.0f,1.0f,
        0.0f,1.0f,0.0f,1.0f, 0.0f,1.0f,0.0f,1.0f,
        0.0f,1.0f,0.0f,1.0f, 0.0f,1.0f,0.0f,1.0f,
        0.0f,1.0f,0.0f,1.0f, 0.0f,1.0f,0.0f,1.0f
};

static const float CUBE_COLORS_C[] = {
        0.0f,0.0f,1.0f,1.0f, 0.0f,0.0f,1.0f,1.0f,
        0.0f,0.0f,1.0f,1.0f, 0.0f,0.0f,1.0f,1.0f,
        0.0f,0.0f,1.0f,1.0f, 0.0f,0.0f,1.0f,1.0f,
        0.0f,0.0f,1.0f,1.0f, 0.0f,0.0f,1.0f,1.0f
};

static float VIEW_MATRIX_A[16];
static float VIEW_MATRIX_B[16];
static float VIEW_MATRIX_C[16];

static float MVP_MATRIX_A[16];
static float MVP_MATRIX_B[16];
static float MVP_MATRIX_C[16];
static float PROJ_MATRIX[16];
static float ROT_ANGLE = 0.0f;

// 两个着色器程序：立方体 + 纹理显示
static GLuint cube_program = 0;
static GLint a_position = -1;
static GLint a_color = -1;
static GLint u_mvp_matrix = -1;

static GLuint screen_program = 0;
static GLint a_screen_pos = -1;
static GLint a_screen_tex = -1;
static GLint u_texture = -1;

// ==============================================
// 工具函数
// ==============================================
static GLuint load_shader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);

    GLint compiled;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        glDeleteShader(shader);
        return 0;
    }
    return shader;
}

// 初始化立方体着色器
static void init_cube_shader() {
    GLuint vertex = load_shader(GL_VERTEX_SHADER, VERTEX_SHADER);
    GLuint fragment = load_shader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

    cube_program = glCreateProgram();
    glAttachShader(cube_program, vertex);
    glAttachShader(cube_program, fragment);
    glLinkProgram(cube_program);

    a_position = glGetAttribLocation(cube_program, "vPosition");
    a_color = glGetAttribLocation(cube_program, "aColor");
    u_mvp_matrix = glGetAttribLocation(cube_program, "uMVPMatrix");
}

// 初始化屏幕纹理着色器（关键修复）
static void init_screen_shader() {
    GLuint vertex = load_shader(GL_VERTEX_SHADER, SCREEN_VERTEX_SHADER);
    GLuint fragment = load_shader(GL_FRAGMENT_SHADER, SCREEN_FRAGMENT_SHADER);

    screen_program = glCreateProgram();
    glAttachShader(screen_program, vertex);
    glAttachShader(screen_program, fragment);
    glLinkProgram(screen_program);

    a_screen_pos = glGetAttribLocation(screen_program, "vPosition");
    a_screen_tex = glGetAttribLocation(screen_program, "aTexCoord");
    u_texture = glGetUniformLocation(screen_program, "uTexture");
}

// ==============================================
// 初始化 FBO
// ==============================================
static void init_fbos() {
    glGenFramebuffers(FBO_COUNT, fbo_ids);
    glGenTextures(FBO_COUNT, fbo_textures);
    glGenRenderbuffers(FBO_COUNT, fbo_rbos);

    for (int i = 0; i < FBO_COUNT; i++) {
        glBindTexture(GL_TEXTURE_2D, fbo_textures[i]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, FBO_WIDTH, FBO_HEIGHT,
                     0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glBindRenderbuffer(GL_RENDERBUFFER, fbo_rbos[i]);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, FBO_WIDTH, FBO_HEIGHT);

        glBindFramebuffer(GL_FRAMEBUFFER, fbo_ids[i]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                               GL_TEXTURE_2D, fbo_textures[i], 0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                                  GL_RENDERBUFFER, fbo_rbos[i]);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
}

static void init_matrices() {
    matrix_perspective_m(PROJ_MATRIX, 0, 45.0f, 1.0f, 0.1f, 100.0f);
    matrix_set_look_at_m(VIEW_MATRIX_A, 0, 0,0,2.5f, 0,0,0, 0,1,0);
    matrix_set_look_at_m(VIEW_MATRIX_B, 0, 0,0,3.5f, 0,0,0, 0,1,0);
    matrix_set_look_at_m(VIEW_MATRIX_C, 0, 0,0,4.5f, 0,0,0, 0,1,0);
}

// ==============================================
// 绘制立方体到FBO
// ==============================================
static void draw_cube_to_fbo(int fbo_index, const float* mvp_matrix, const float* colors) {
    glBindFramebuffer(GL_FRAMEBUFFER, fbo_ids[fbo_index]);
    glViewport(0, 0, FBO_WIDTH, FBO_HEIGHT);
    glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

    glEnable(GL_DEPTH_TEST);
    glEnable(GL_CULL_FACE);

    glUseProgram(cube_program);

    glEnableVertexAttribArray(a_position);
    glVertexAttribPointer(a_position, 3, GL_FLOAT, GL_FALSE, 0, CUBE_VERTICES);

    glEnableVertexAttribArray(a_color);
    glVertexAttribPointer(a_color, 4, GL_FLOAT, GL_FALSE, 0, colors);

    glUniformMatrix4fv(u_mvp_matrix, 1, GL_FALSE, mvp_matrix);
    glDrawElements(GL_TRIANGLES, 36, GL_UNSIGNED_SHORT, CUBE_INDICES);

    glDisableVertexAttribArray(a_position);
    glDisableVertexAttribArray(a_color);
}

// ==============================================
// 正确绘制FBO到屏幕（专用着色器）
// ==============================================
static void draw_fbo_c_to_screen(int texture_id) {
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, FBO_WIDTH, FBO_HEIGHT);
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glDisable(GL_DEPTH_TEST);

    glUseProgram(screen_program);

    const float vertices[] = { -1.0f,  1.0f, 0.0f,
                               -1.0f, -1.0f, 0.0f,
                               1.0f, -1.0f, 0.0f,
                               1.0f,  1.0f, 0.0f };

    const float texCoords[] = { 0.0f, 1.0f,
                                0.0f, 0.0f,
                                1.0f, 0.0f,
                                1.0f, 1.0f };

    const GLushort indices[] = { 0,1,2, 0,2,3 };

    glEnableVertexAttribArray(a_screen_pos);
    glVertexAttribPointer(a_screen_pos, 3, GL_FLOAT, GL_FALSE, 0, vertices);

    glEnableVertexAttribArray(a_screen_tex);
    glVertexAttribPointer(a_screen_tex, 2, GL_FLOAT, GL_FALSE, 0, texCoords);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, texture_id);
    glUniform1i(u_texture, 0);

    glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_SHORT, indices);

    glDisableVertexAttribArray(a_screen_pos);
    glDisableVertexAttribArray(a_screen_tex);
}

// ==============================================
// JNI 接口
// ==============================================
extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_cube3dmultifbo_Cube3DMultiFboRender_onSurfaceCreated(JNIEnv*,jclass) {
    glEnable(GL_DEPTH_TEST);
    glEnable(GL_CULL_FACE);

    init_cube_shader();
    init_screen_shader();
    init_fbos();
    init_matrices();
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_cube3dmultifbo_Cube3DMultiFboRender_onSurfaceChanged(JNIEnv*,jclass, jint width, jint height) {
    glViewport(0, 0, width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_cube3dmultifbo_Cube3DMultiFboRender_onDrawFrame(JNIEnv*, jclass) {
    ROT_ANGLE += 0.5f;
    if (ROT_ANGLE >= 360) ROT_ANGLE = 0;

    float rot[16], model[16];

    // A 红
    matrix_set_identity_m(model,0);
    matrix_set_rotate_m(rot,0, ROT_ANGLE,1,1,1);
    matrix_multiply_m(model,0, rot,0, model,0);
    matrix_multiply_m(MVP_MATRIX_A,0, VIEW_MATRIX_A,0, model,0);
    matrix_multiply_m(MVP_MATRIX_A,0, PROJ_MATRIX,0, MVP_MATRIX_A,0);
    draw_cube_to_fbo(FBO_A, MVP_MATRIX_A, CUBE_COLORS_A);

    // B 绿
    matrix_set_identity_m(model,0);
    matrix_set_rotate_m(rot,0, ROT_ANGLE,1,1,1);
    matrix_multiply_m(model,0, rot,0, model,0);
    matrix_multiply_m(MVP_MATRIX_B,0, VIEW_MATRIX_B,0, model,0);
    matrix_multiply_m(MVP_MATRIX_B,0, PROJ_MATRIX,0, MVP_MATRIX_B,0);
    draw_cube_to_fbo(FBO_B, MVP_MATRIX_B, CUBE_COLORS_B);

    // C 蓝
    matrix_set_identity_m(model,0);
    matrix_set_rotate_m(rot,0, ROT_ANGLE,1,1,1);
    matrix_multiply_m(model,0, rot,0, model,0);
    matrix_multiply_m(MVP_MATRIX_C,0, VIEW_MATRIX_C,0, model,0);
    matrix_multiply_m(MVP_MATRIX_C,0, PROJ_MATRIX,0, MVP_MATRIX_C,0);
    draw_cube_to_fbo(FBO_C, MVP_MATRIX_C, CUBE_COLORS_C);

    // 最终上屏
    draw_fbo_c_to_screen(fbo_textures[FBO_C]);
}