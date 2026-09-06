cmake_minimum_required(VERSION 3.22.1)

project("llama-bridge" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# --------------------------------------------------------------------------
# Pull in llama.cpp's own source tree at configure time instead of vendoring
# it in this repository — it's a large (100+ MB) upstream C/C++ project with
# its own build system, so fetching a pinned commit keeps this repo's git
# history small while still giving a fully reproducible build (exact same
# commit every time, verified to configure and build during development of
# this feature).
# --------------------------------------------------------------------------
include(FetchContent)
FetchContent_Declare(
        llama_cpp
        GIT_REPOSITORY https://github.com/ggerganov/llama.cpp.git
        GIT_TAG 73a43d1f69345aee8bb186ef4b3172cef892f2e5
        GIT_SHALLOW TRUE
)

# llama.cpp's own CMakeLists only builds its "common" helper library (which
# our JNI bridge needs for chat templating/sampling/tokenization helpers) by
# default when it's the top-level project. Force it on, and force off the
# example/tool/server/test targets we have no use for here, to keep the
# build fast and avoid pulling in dependencies (e.g. an HTTP client) those
# targets need but our bridge never touches.
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama_cpp)

add_library(${CMAKE_PROJECT_NAME} SHARED
        llama_bridge.cpp)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${llama_cpp_SOURCE_DIR}
        ${llama_cpp_SOURCE_DIR}/common
        ${llama_cpp_SOURCE_DIR}/include
        ${llama_cpp_SOURCE_DIR}/ggml/include)

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        llama-common
        android
        log)
