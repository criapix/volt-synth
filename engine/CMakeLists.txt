cmake_minimum_required(VERSION 3.22)
project(volt_engine LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(VOLT_TESTS "Build the engine unit tests" OFF)

add_library(volt_engine STATIC
  src/params.cpp
  src/voice.cpp
  src/engine.cpp
  src/filter.cpp
)
target_include_directories(volt_engine PUBLIC src)
set(VOLT_THIRD_PARTY ${CMAKE_CURRENT_SOURCE_DIR}/../third_party)
target_include_directories(volt_engine SYSTEM PRIVATE
  ${VOLT_THIRD_PARTY}/sst-filters/include
  ${VOLT_THIRD_PARTY}/sst-basic-blocks/include
  ${VOLT_THIRD_PARTY}/simde
)
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
    tests/test_engine.cpp
    tests/test_alloc.cpp
    tests/test_filter.cpp
    tests/test_patch.cpp
    tests/test_arp.cpp
  )
  target_link_libraries(volt_tests PRIVATE volt_engine)
  add_executable(volt_bench tests/bench.cpp)
  target_link_libraries(volt_bench PRIVATE volt_engine)
endif()
