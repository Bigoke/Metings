cmake_minimum_required(VERSION 3.22.1)

# Always build the speech engine fully optimised, also in debug builds.
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)

project(metings_native C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)

# whisper.cpp is checked out here by the build workflow (pinned tag).
add_subdirectory(whisper.cpp)

add_library(metings_whisper SHARED whisper_jni.cpp)
target_include_directories(metings_whisper PRIVATE whisper.cpp/include whisper.cpp/ggml/include)
target_link_libraries(metings_whisper PRIVATE whisper log android)
target_compile_options(metings_whisper PRIVATE -O3)
target_link_options(metings_whisper PRIVATE "-Wl,-z,max-page-size=16384")
