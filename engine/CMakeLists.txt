cmake_minimum_required(VERSION 3.22)
project(volt_engine LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(VOLT_TESTS "Build the engine unit tests" OFF)

add_library(volt_engine STATIC
  src/params.cpp
  src/voice.cpp
)
target_include_directories(volt_engine PUBLIC src)
if (MSVC)
  target_compile_definitions(volt_engine PUBLIC _USE_MATH_DEFINES=1 NOMINMAX)
  target_compile_options(volt_engine PUBLIC /Zc:__cplusplus /W3)
else()
  # The audio code must be optimised even in debug APKs, or it cannot keep up.
  target_compile_options(volt_engine PRIVATE -O2 -Wall -Wextra)
endif()

if (VOLT_TESTS)
  add_executable(volt_tests
    tests/test_main.cpp
    tests/test_params.cpp
    tests/test_queue.cpp
    tests/test_osc.cpp
    tests/test_adsr.cpp
    tests/test_lfo.cpp
  )
  target_link_libraries(volt_tests PRIVATE volt_engine)
endif()
