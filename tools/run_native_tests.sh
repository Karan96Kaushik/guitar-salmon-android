#!/usr/bin/env bash
# Configures and runs the host-side DSP unit tests.
#
# These tests cover the platform independent parts of the pipeline (chroma
# folding, chord template matching, the detector's gating and hysteresis), so
# they need neither a device nor the Android NDK.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${ROOT}/build/native-tests"

# Prefer a cmake on PATH, otherwise fall back to the one shipped with the SDK.
if command -v cmake >/dev/null 2>&1; then
    CMAKE=cmake
else
    SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
    CMAKE="$(find "${SDK}/cmake" -maxdepth 3 -name cmake -type f -perm -u+x 2>/dev/null | sort | tail -1)"
    if [[ -z "${CMAKE}" ]]; then
        echo "error: cmake not found on PATH or in ${SDK}/cmake" >&2
        exit 1
    fi
fi

"${CMAKE}" -S "${ROOT}/cpp" -B "${BUILD_DIR}" -DGS_BUILD_ANDROID=OFF -DCMAKE_BUILD_TYPE=Release
"${CMAKE}" --build "${BUILD_DIR}" --parallel
"${BUILD_DIR}/gs_dsp_tests"
