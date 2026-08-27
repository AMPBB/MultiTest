cmake_minimum_required(VERSION 3.22.1)
project("gles30test")

add_library(
        gles30testdemo
        SHARED
        test_glTexImage2D_onscreen.cpp
        huge_tex_image2d.cpp
        egl_create_wait_destroy_fence_sync.cpp
        resizeableview/resizable_view.cpp
        resizeableview/manual_egl.cpp
        fbomulti/fbo_multi.cpp
        cube3dmultifbo/cube3d_multi_fbo.cpp
        texturemultisampleext/texture_multi_sample_ext.cpp)

find_library(EGL-lib EGL)

find_library(GLES3-lib GLESv3)

find_library(log-lib log)

find_library(android-lib android)

target_link_libraries(
        gles30testdemo
        ${android}
        ${EGL}
        ${EGL-lib}
        ${GLES3-lib}
        ${GLESv3}
        ${log-lib}
        ${android-lib})

if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    set_target_properties(gles30testdemo PROPERTIES
            LINK_FLAGS "-z common-page-size=0x4000 -z max-page-size=0x4000 -Wl,--emit-relocs"
    )
endif()

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wl,-z,relro,-z,now")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,relro,-z,now")
