cmake_minimum_required(VERSION 3.22.1)
project(guitarsalmon LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 99)

# GS_BUILD_ANDROID is set by the Gradle externalNativeBuild invocation. When it is
# OFF we are building the host-side unit tests, which need no Oboe / JNI.
option(GS_BUILD_ANDROID "Build the Android shared library (requires Oboe + JNI)" OFF)

# ---------------------------------------------------------------------------
# Third party: KissFFT (vendored subset -- real FFT only)
# ---------------------------------------------------------------------------
add_library(kissfft STATIC
    third_party/kissfft/kiss_fft.c
    third_party/kissfft/kiss_fftr.c
)
target_include_directories(kissfft PUBLIC third_party/kissfft)
# Use 32-bit floats and keep the library free of its own memory allocator tricks.
target_compile_definitions(kissfft PUBLIC kiss_fft_scalar=float)
if(NOT MSVC)
    target_compile_options(kissfft PRIVATE -Wno-unused-function)
endif()

# ---------------------------------------------------------------------------
# DSP core: platform independent and unit testable
# ---------------------------------------------------------------------------
add_library(gs_dsp STATIC
    dsp/chroma.cpp
    dsp/chord_matcher.cpp
    dsp/chord_detector.cpp
)
target_include_directories(gs_dsp PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(gs_dsp PUBLIC kissfft)
if(NOT MSVC)
    target_compile_options(gs_dsp PRIVATE -Wall -Wextra -ffast-math)
endif()

if(GS_BUILD_ANDROID)
    # -----------------------------------------------------------------------
    # Android shared library: Oboe capture + JNI bridge
    # -----------------------------------------------------------------------
    find_package(oboe REQUIRED CONFIG)

    add_library(guitarsalmon SHARED
        audio/audio_engine.cpp
        jni_bridge.cpp
    )
    target_include_directories(guitarsalmon PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
    target_link_libraries(guitarsalmon PRIVATE gs_dsp oboe::oboe android log)
    target_compile_options(guitarsalmon PRIVATE -Wall -Wextra)
else()
    # -----------------------------------------------------------------------
    # Host unit tests
    # -----------------------------------------------------------------------
    enable_testing()
    add_executable(gs_dsp_tests
        tests/test_main.cpp
        tests/test_chroma.cpp
        tests/test_chord_matcher.cpp
    )
    target_link_libraries(gs_dsp_tests PRIVATE gs_dsp)
    add_test(NAME gs_dsp_tests COMMAND gs_dsp_tests)
endif()
