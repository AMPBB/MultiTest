#include"common_include_and_log.h"

// native-lib.cpp 顶部添加全局渲染资源（仅初始化一次）
__thread static GLuint g_programID = 0;       // 着色器程序ID
__thread static GLuint g_vaoID = 0;           // 顶点数组ID
__thread static GLuint g_vboID = 0;           // 顶点缓冲区ID
__thread static GLint  g_texSamplerLoc = -1;  // 纹理采样器位置
__thread static GLint  g_positionLoc = -1;    // 顶点位置属性位置
__thread static GLint  g_texCoordLoc = -1;    // 纹理坐标属性位置
__thread static int g_screen_width,g_screen_height;
__thread static bool need_draw = true;

// 全屏顶点数据（位置+纹理坐标）：覆盖整个屏幕
__thread static const GLfloat fullScreenVertices[] = {
        // 位置坐标（NDC）  // 纹理坐标
        -1.0f,  1.0f, 0.0f,  0.0f, 1.0f, // 左上
        -1.0f, -1.0f, 0.0f,  0.0f, 0.0f, // 左下
        1.0f,  1.0f, 0.0f,  1.0f, 1.0f, // 右上
        1.0f, -1.0f, 0.0f,  1.0f, 0.0f  // 右下
};

static bool createShader(const char *srcCode,GLenum shaderType,GLuint &shader) {
    shader = glCreateShader(shaderType);
    glShaderSource(shader,1,&srcCode,nullptr);
    glCompileShader(shader);
    GLint compileResult;
    glGetShaderiv(shader,GL_COMPILE_STATUS,&compileResult);
    if(false==compileResult) {
        LOGE("compile 0x%x failed",shaderType);
        glDeleteShader(shader);
        return false;
    }
    LOGI("done");
    return true;
}

// 1. 顶点着色器源码（传递顶点位置和纹理坐标）
__thread const char* vertexShaderSource =
        "#version 300 es\n"
        "layout (location = 0) in vec3 aPos;\n"
        "layout (location = 1) in vec2 aTexCoord;\n"
        "out vec2 TexCoord;\n"
        "void main() {\n"
        "    gl_Position = vec4(aPos, 1.0);\n"
        "    TexCoord = aTexCoord;\n"
        "}\n";

// 2. 片段着色器源码（采样纹理并输出颜色）
__thread const char* fragShaderSource =
        "#version 300 es\n"
        "precision mediump float;\n"
        "in vec2 TexCoord;\n"
        "out vec4 FragColor;\n"
        "uniform sampler2D ourTexture;\n"
        "void main() {\n"
        "    FragColor = texture(ourTexture, TexCoord);\n"
        "}\n";

static bool createProgram(GLuint &program) {
    GLuint vShader,fShader;
    bool vShaderIsSuccess, fShaderIsSuccess;
    vShaderIsSuccess=createShader(vertexShaderSource,GL_VERTEX_SHADER,vShader);
    if(!vShaderIsSuccess) {LOGE("create vShader failed");return false;}
    fShaderIsSuccess=createShader(fragShaderSource,GL_FRAGMENT_SHADER,fShader);
    if(!fShaderIsSuccess) {LOGE("create fShader failed");return false;}
    program=glCreateProgram();
    if(0==program) {
        LOGE("create program failed");
        glDeleteShader(vShader);
        glDeleteShader(fShader);
        return false;
    }
    glAttachShader(program,vShader);print_error_indent;
    glAttachShader(program,fShader);print_error_indent;
    glLinkProgram(program);print_error_indent;
    GLint linked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked) {
        LOGE("program link shader failed");
        glDeleteShader(vShader);
        glDeleteShader(fShader);
        glDeleteProgram(program);
        return false;
    }

    glDeleteShader(vShader);
    glDeleteShader(fShader);
    LOGI("done");
    return true;
}

// 初始化渲染资源（着色器+VAO/VBO）
static void initRenderResources() {
    if(!need_draw) {
        LOGE("if you need draw, please modify need_draw in code!");
        return;
    }
    if (g_programID != 0) return; // 已初始化，直接返回
    // 3. 创建着色器程序
    bool programIsCreated=false;
    programIsCreated= createProgram(g_programID);
    if(!programIsCreated) {
        LOGE("init failed");
        return;
    }

    // 4. 获取着色器变量位置
    g_positionLoc = glGetAttribLocation(g_programID, "aPos");
    g_texCoordLoc = glGetAttribLocation(g_programID, "aTexCoord");
    g_texSamplerLoc = glGetUniformLocation(g_programID, "ourTexture");
    LOGI("着色器变量位置 → aPos=%d, aTexCoord=%d, ourTexture=%d",
         g_positionLoc, g_texCoordLoc, g_texSamplerLoc);

    // 5. 创建VAO/VBO（顶点数组/缓冲区）
    glGenVertexArrays(1, &g_vaoID);
    glGenBuffers(1, &g_vboID);

    glBindVertexArray(g_vaoID);
    glBindBuffer(GL_ARRAY_BUFFER, g_vboID);
    glBufferData(GL_ARRAY_BUFFER, sizeof(fullScreenVertices), fullScreenVertices, GL_STATIC_DRAW);

    // 配置顶点位置属性（location 0）
    glVertexAttribPointer(g_positionLoc, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(GLfloat), (void*)0);
    glEnableVertexAttribArray(g_positionLoc);

    // 配置纹理坐标属性（location 1）
    glVertexAttribPointer(g_texCoordLoc, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(GLfloat), (void*)(3 * sizeof(GLfloat)));
    glEnableVertexAttribArray(g_texCoordLoc);

    // 解绑VAO/VBO
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);

    LOGI("渲染资源初始化完成 → programID=%d, VAO=%d, VBO=%d", g_programID, g_vaoID, g_vboID);
}

// 释放渲染资源
static void releaseRenderResources() {
    if(!need_draw) {
        LOGE("if you need draw, please modify need_draw in code!");
        return;
    }
    if (g_vaoID != 0) {
        glDeleteVertexArrays(1, &g_vaoID);print_error_indent;
        g_vaoID = 0;
    }
    if (g_vboID != 0) {
        glDeleteBuffers(1, &g_vboID);print_error_indent;
        g_vboID = 0;
    }
    if (g_programID != 0) {
        glDeleteProgram(g_programID);print_error_indent;
        g_programID = 0;
    }
    LOGI("done");
}

#define ALPHA_MASK 0xff000000
#define BLUE_MASK  0x00ff0000
#define GREEN_MASK 0x0000ff00
#define RED_MASK   0x000000ff

static void fill_rgba_data_with_color(GLuint* &data,int size,GLuint color) {
    for(int i=0;i<size;++i) {data[i]=color;}
}

static void fill_rgba_data_interval(GLuint* &data, int width, int height,GLuint color) {
    int i,j,k;
    for(i=0;i<height;++i) {
        for(j=0;j<width;++j) {
            data[i*width+j]=color;
        }
        color += 0xff010101;
        color |= ALPHA_MASK;
        if ((color & BLUE_MASK)  == BLUE_MASK)  color &= ~BLUE_MASK;
        if ((color & GREEN_MASK) == GREEN_MASK) color &= ~GREEN_MASK;
        if ((color & RED_MASK)   == RED_MASK)   color &= ~RED_MASK;
    }
}

static void test_glTexImage2D_draw(GLuint textureID) {
    if (g_programID == 0 || g_vaoID == 0) {
        LOGE("渲染资源未初始化，跳过绘制！");
        return;
    }

    glViewport(0, 0, g_screen_width, g_screen_height);print_error_indent;
//    glClearColor(0.0f, 0.6f, 0.0f, 1.0f);
//    glClear(GL_COLOR_BUFFER_BIT);
    glUseProgram(g_programID);print_error_indent;

    glActiveTexture(GL_TEXTURE0);print_error_indent;
    glBindTexture(GL_TEXTURE_2D, textureID);print_error_indent;
    glUniform1i(g_texSamplerLoc, 0);print_error_indent;

    glBindVertexArray(g_vaoID);print_error_indent;
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);print_error_indent;
}

__thread static GLint gl_max_texture_size_supported;
__thread static GLuint texture_id=0;
__thread static int texture_width =512;//11904;
__thread static int texture_height=512;//16384;//32768;//5952;
__thread static int byte_per_pixel=4;
__thread static int texture_buffer_size=0;
__thread static GLuint *texture_buffer= nullptr;
__thread static GLuint *texture_sub_buffer= nullptr;
__thread static bool is_initialized =false;
__thread static bool is_paused =false;

__thread static GLuint *texture_sub_buffer_r= nullptr;
__thread static GLuint *texture_sub_buffer_g= nullptr;
__thread static GLuint *texture_sub_buffer_b= nullptr;
static void test_glTexImage2D_init() {
    if(is_initialized) {
        LOGW("has been initialized");
        return;
    }
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &gl_max_texture_size_supported);print_error_indent;
    if(texture_width>gl_max_texture_size_supported) {
        LOGE("gl_max_texture_size_supported=%d,oversized!",gl_max_texture_size_supported);
        return;
    } else {
        LOGI("gl_max_texture_size_supported=%d",gl_max_texture_size_supported);
    }
    LOGI("texture width=%d,height=%d",texture_width,texture_height);

    initRenderResources();
    glGenTextures(1, &texture_id);print_error_indent;
    glBindTexture(GL_TEXTURE_2D, texture_id);print_error_indent;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);print_error_indent;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);print_error_indent;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);print_error_indent;
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);print_error_indent;

    texture_buffer_size=texture_width*texture_height; //GLint, don't *4
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, texture_width, texture_height, 0, GL_RGBA, GL_UNSIGNED_BYTE,nullptr);print_error_indent;

    texture_sub_buffer=new GLuint[texture_buffer_size]();
    texture_sub_buffer_r=new GLuint[texture_buffer_size]();
    texture_sub_buffer_g=new GLuint[texture_buffer_size]();
    texture_sub_buffer_b=new GLuint[texture_buffer_size]();
    fill_rgba_data_with_color(texture_sub_buffer_r,texture_buffer_size,0xff0000ff);
    fill_rgba_data_with_color(texture_sub_buffer_g,texture_buffer_size,0xff00ff00);
    fill_rgba_data_with_color(texture_sub_buffer_b,texture_buffer_size,0xffff0000);
    is_initialized=true;
}

__thread static GLuint color=0;
static void test_glTexSubImage2D_update() {
    if(!is_initialized) {
        LOGE("is_initialized == false");
        return;
    }
    if(is_paused) {
        LOGI("is paused");
        return;
    }
    fill_rgba_data_interval(texture_sub_buffer,texture_width,texture_height,color);
    color += 0xff010101;
    color |= ALPHA_MASK;
    if ((color & BLUE_MASK)  == BLUE_MASK)  color &= ~BLUE_MASK;
    if ((color & GREEN_MASK) == GREEN_MASK) color &= ~GREEN_MASK;
    if ((color & RED_MASK)   == RED_MASK)   color &= ~RED_MASK;

    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, texture_width, texture_height, GL_RGBA, GL_UNSIGNED_BYTE, texture_sub_buffer);print_error_indent;
    test_glTexImage2D_draw(texture_id);print_error_indent;
}

static void test_glTexSubImage2D_update_re_malloc() {
    if(!is_initialized) {
        LOGE("is_initialized == false");
        return;
    }
    if(is_paused) {
        LOGI("is paused");
        return;
    }

    delete[] texture_sub_buffer;
    texture_sub_buffer= nullptr;
    texture_sub_buffer=new GLuint[texture_buffer_size]();
    if(texture_sub_buffer==nullptr) {
        LOGE("re malloc failed");
        return;
    }

    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, texture_width, texture_height, GL_RGBA, GL_UNSIGNED_BYTE, texture_sub_buffer);print_error_indent;
    test_glTexImage2D_draw(texture_id);print_error_indent;
}

__thread static int update_fast_index=0;
static void test_glTexSubImage2D_update_fast() {
    if(!is_initialized) {
        LOGE("is_initialized == false");
        return;
    }
    if(is_paused) {
        LOGI("is paused");
        return;
    }

    GLuint *data= nullptr;
    if(0==update_fast_index) {
        data=texture_sub_buffer_r;
    } else if(1==update_fast_index) {
        data=texture_sub_buffer_g;
    } else {
        data=texture_sub_buffer_b;
    }
    ++update_fast_index;
    update_fast_index=update_fast_index%3;

    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, texture_width, texture_height, GL_RGBA, GL_UNSIGNED_BYTE, data);print_error_indent;
    test_glTexImage2D_draw(texture_id);print_error_indent;
}

static void test_glTexImage2D_deinit() {
    if(!is_initialized) {
        LOGE("is_initialized == false");
        return;
    }
    delete[] texture_sub_buffer;
    texture_sub_buffer= nullptr;
    delete[] texture_sub_buffer_r;
    texture_sub_buffer_r= nullptr;
    delete[] texture_sub_buffer_g;
    texture_sub_buffer_g= nullptr;
    delete[] texture_sub_buffer_b;
    texture_sub_buffer_b= nullptr;

    glBindTexture(GL_TEXTURE_2D, 0);print_error_indent;
    glDeleteTextures(1, &texture_id);print_error_indent;
    releaseRenderResources();
    is_initialized=false;
    LOGW("resource released");
}

static void set_view_port(int width,int height) {
    g_screen_width=width;
    g_screen_height=height;
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_teximage2d_TexImage2DRendererOnScreen_TestViewport(JNIEnv *env, jobject thiz, jint width, jint height) {
    glViewport(0,0,width,height);
    set_view_port(width,height);
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_teximage2d_TexImage2DRendererOnScreen_TestglTexImage2DSetSize(JNIEnv *env, jobject thiz, jint width, jint height) {
    texture_width=width;
    texture_height=height;
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_teximage2d_TexImage2DRendererOnScreen_TestglTexImage2DPause(JNIEnv *env, jobject thiz) {
    is_paused=true;
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_teximage2d_TexImage2DRendererOnScreen_TestglTexImage2DResume(JNIEnv *env, jobject thiz) {
    is_paused=false;
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_teximage2d_TexImage2DRendererOnScreen_TestglTexImage2DInit(JNIEnv *env, jobject thiz) {
    test_glTexImage2D_init();
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_teximage2d_TexImage2DRendererOnScreen_TestglTexSubImage2DUpdate(JNIEnv *env, jobject thiz) {
//    test_glTexSubImage2D_update();
//    test_glTexSubImage2D_update_re_malloc();
    test_glTexSubImage2D_update_fast();
}

extern "C" JNIEXPORT void JNICALL
Java_pbbadd_opengl_multitest_teximage2d_TexImage2DRendererOnScreen_TestglTexImage2DDeinit(JNIEnv *env, jobject thiz) {
    test_glTexImage2D_deinit();
}