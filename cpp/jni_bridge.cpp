// JNI surface for com.guitarsalmon.audio.NativeAudioEngine.
//
// The engine is handed to Kotlin as an opaque long handle. Kotlin polls
// nativePoll() at ~20 Hz rather than having native code call up into the JVM,
// which keeps the DSP thread free of any JNI attachment cost.

#include <jni.h>

#include <new>

#include "audio/audio_engine.h"
#include "dsp/chord_matcher.h"

namespace {

gs::AudioEngine* engineFrom(jlong handle) {
    return reinterpret_cast<gs::AudioEngine*>(handle);
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_guitarsalmon_audio_NativeAudioEngine_nativeCreate(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new (std::nothrow) gs::AudioEngine());
}

JNIEXPORT void JNICALL
Java_com_guitarsalmon_audio_NativeAudioEngine_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    delete engineFrom(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_guitarsalmon_audio_NativeAudioEngine_nativeStart(JNIEnv*, jobject, jlong handle) {
    gs::AudioEngine* engine = engineFrom(handle);
    if (engine == nullptr) return JNI_FALSE;
    return engine->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_guitarsalmon_audio_NativeAudioEngine_nativeStop(JNIEnv*, jobject, jlong handle) {
    gs::AudioEngine* engine = engineFrom(handle);
    if (engine != nullptr) engine->stop();
}

JNIEXPORT jboolean JNICALL
Java_com_guitarsalmon_audio_NativeAudioEngine_nativeIsRunning(JNIEnv*, jobject, jlong handle) {
    gs::AudioEngine* engine = engineFrom(handle);
    return (engine != nullptr && engine->isRunning()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_guitarsalmon_audio_NativeAudioEngine_nativeSampleRate(JNIEnv*, jobject, jlong handle) {
    gs::AudioEngine* engine = engineFrom(handle);
    return engine == nullptr ? 0 : engine->sampleRate();
}

JNIEXPORT void JNICALL
Java_com_guitarsalmon_audio_NativeAudioEngine_nativeSetNoiseGate(JNIEnv*,
                                                                jobject,
                                                                jlong handle,
                                                                jfloat rms) {
    gs::AudioEngine* engine = engineFrom(handle);
    if (engine != nullptr) engine->setNoiseGateRms(rms);
}

/// Copies the latest detection into caller-supplied arrays and returns the chord
/// index (-1 for "no chord").
///
/// `chromaOut` must hold 12 floats and `scalarsOut` 2: [confidence, rms]. Reusing
/// caller arrays avoids allocating a new object on every one of the ~20 polls per
/// second.
JNIEXPORT jint JNICALL
Java_com_guitarsalmon_audio_NativeAudioEngine_nativePoll(JNIEnv* env,
                                                        jobject,
                                                        jlong handle,
                                                        jfloatArray chromaOut,
                                                        jfloatArray scalarsOut) {
    gs::AudioEngine* engine = engineFrom(handle);
    if (engine == nullptr) return -1;

    const gs::DetectionResult result = engine->latestResult();

    if (chromaOut != nullptr && env->GetArrayLength(chromaOut) >= gs::kNumPitchClasses) {
        env->SetFloatArrayRegion(chromaOut, 0, gs::kNumPitchClasses, result.chroma);
    }
    if (scalarsOut != nullptr && env->GetArrayLength(scalarsOut) >= 2) {
        const jfloat scalars[2] = {result.confidence, result.rms};
        env->SetFloatArrayRegion(scalarsOut, 0, 2, scalars);
    }

    return result.chordIndex;
}

/// All chord display names, indexed by the value nativePoll() returns. Fetched
/// once so native stays the single source of truth for chord naming.
JNIEXPORT jobjectArray JNICALL
Java_com_guitarsalmon_audio_NativeAudioEngine_nativeChordNames(JNIEnv* env, jobject) {
    const auto& templates = gs::chordTemplates();
    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) return nullptr;

    jobjectArray names =
        env->NewObjectArray(static_cast<jsize>(templates.size()), stringClass, nullptr);
    if (names == nullptr) return nullptr;

    for (jsize i = 0; i < static_cast<jsize>(templates.size()); ++i) {
        jstring name = env->NewStringUTF(templates[static_cast<std::size_t>(i)].name.c_str());
        env->SetObjectArrayElement(names, i, name);
        // Release the local reference straight away: the default local reference
        // table is small and we create 84 of these in one call.
        env->DeleteLocalRef(name);
    }
    return names;
}

}  // extern "C"
