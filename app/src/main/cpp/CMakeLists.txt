cmake_minimum_required(VERSION 3.22.1)
project(volt LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../engine ${CMAKE_CURRENT_BINARY_DIR}/engine)
find_package(oboe REQUIRED CONFIG)

add_library(volt SHARED jni.cpp audio.cpp)
target_compile_options(volt PRIVATE -O2 -Wall)
target_link_libraries(volt PRIVATE volt_engine oboe::oboe log)
