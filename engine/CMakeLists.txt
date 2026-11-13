cmake_minimum_required(VERSION 3.22)
project(volt_engine LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(VOLT_TESTS "Build the engine unit tests" OFF)

set(MSFA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../third_party/Synth_Dexed/src)
add_library(msfa STATIC
  ${MSFA_DIR}/dx7note.cpp
  ${MSFA_DIR}/env.cpp
  ${MSFA_DIR}/exp2.cpp
  ${MSFA_DIR}/fm_core.cpp
  ${MSFA_DIR}/fm_op_kernel.cpp
  ${MSFA_DIR}/freqlut.cpp
  ${MSFA_DIR}/lfo.cpp
  ${MSFA_DIR}/pitchenv.cpp
  ${MSFA_DIR}/porta.cpp
  ${MSFA_DIR}/sin.cpp
  ${MSFA_DIR}/EngineMsfa.cpp
)
target_include_directories(msfa SYSTEM PUBLIC ${MSFA_DIR})
if (MSVC)
  target_compile_definitions(msfa PUBLIC _USE_MATH_DEFINES=1 NOMINMAX)
  target_compile_options(msfa PRIVATE /W0 /utf-8)
else()
  target_compile_options(msfa PRIVATE -O2 -w)
endif()

add_library(volt_engine STATIC
  src/params.cpp
  src/voice.cpp
  src/engine.cpp
  src/filter.cpp
  src/fx/fx_sst.cpp
  src/fx/fx_chain.cpp
)
target_include_directories(volt_engine PUBLIC src)
target_link_libraries(volt_engine PUBLIC msfa)
target_compile_definitions(volt_engine PRIVATE FMT_HEADER_ONLY=1)
set(VOLT_THIRD_PARTY ${CMAKE_CURRENT_SOURCE_DIR}/../third_party)
target_include_directories(volt_engine SYSTEM PRIVATE
  ${VOLT_THIRD_PARTY}/sst-filters/include
  ${VOLT_THIRD_PARTY}/sst-effects/include
  ${VOLT_THIRD_PARTY}/fmt/include
  ${VOLT_THIRD_PARTY}/sst-basic-blocks/include
  ${VOLT_THIRD_PARTY}/simde
)
if (MSVC)
  target_compile_definitions(volt_engine PUBLIC _USE_MATH_DEFINES=1 NOMINMAX)
  target_compile_options(volt_engine PUBLIC /Zc:__cplusplus /W3 /utf-8)
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
    tests/test_fx.cpp
    tests/test_presets.cpp
    tests/test_governor.cpp
    tests/test_msfa.cpp
  )
  target_link_libraries(volt_tests PRIVATE volt_engine)
  set(VOLT_PRESETS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/assets/presets/factory)
  target_compile_definitions(volt_tests PRIVATE VOLT_PRESETS_DIR="${VOLT_PRESETS_DIR}")
  add_executable(volt_render tests/render.cpp)
  target_link_libraries(volt_render PRIVATE volt_engine)
  target_compile_definitions(volt_render PRIVATE VOLT_PRESETS_DIR="${VOLT_PRESETS_DIR}")
  add_executable(volt_bench tests/bench.cpp)
  target_link_libraries(volt_bench PRIVATE volt_engine)
  add_executable(fx_dump tests/fx_dump.cpp)
  target_link_libraries(fx_dump PRIVATE volt_engine)
  target_compile_definitions(fx_dump PRIVATE FMT_HEADER_ONLY=1)
  target_include_directories(fx_dump SYSTEM PRIVATE
    ${VOLT_THIRD_PARTY}/sst-effects/include ${VOLT_THIRD_PARTY}/fmt/include ${VOLT_THIRD_PARTY}/sst-filters/include
    ${VOLT_THIRD_PARTY}/sst-basic-blocks/include ${VOLT_THIRD_PARTY}/simde)
endif()
