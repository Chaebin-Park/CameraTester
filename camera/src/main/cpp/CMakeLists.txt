cmake_minimum_required(VERSION 3.22.1)

project("camera_native")

# C++17 표준 사용
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 최적화 플래그
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math")

# ARM NEON SIMD 지원 (ARM 아키텍처용)
if(${ANDROID_ABI} STREQUAL "armeabi-v7a" OR ${ANDROID_ABI} STREQUAL "arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon")
    add_definitions(-DUSE_NEON)
endif()

# Native 라이브러리 생성
add_library(
    camera_native
    SHARED
    frame_processor_native.cpp
)

# Android 로그 라이브러리 링크
find_library(log-lib log)

target_link_libraries(
    camera_native
    ${log-lib}
)
