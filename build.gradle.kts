plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}

/**
 * Builds and runs the host-side C++ DSP tests.
 *
 * They are a plain CMake build for the host rather than an Android target, so they
 * run in seconds without a device or emulator.
 */
tasks.register<Exec>("nativeTest") {
    group = "verification"
    description = "Builds and runs the host-side C++ DSP unit tests."
    workingDir = rootDir
    commandLine("./tools/run_native_tests.sh")
}

