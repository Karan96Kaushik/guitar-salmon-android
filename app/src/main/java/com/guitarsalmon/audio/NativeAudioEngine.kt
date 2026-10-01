package com.guitarsalmon.audio

/**
 * Thin JNI wrapper around the native Oboe + DSP engine.
 *
 * The native side owns an opaque handle; this class is the only place that talks
 * to it. Results are polled rather than pushed so the DSP thread never has to
 * attach itself to the JVM.
 *
 * Not thread safe: callers must serialise access. [ChordAudioEngine] does that by
 * confining every call to a single coroutine dispatcher.
 */
internal class NativeAudioEngine {

    private var handle: Long = nativeCreate()

    /** Scratch arrays reused across polls so a 20 Hz stream allocates nothing. */
    private val chromaScratch = FloatArray(CHROMA_SIZE)
    private val scalarScratch = FloatArray(2)

    val isValid: Boolean get() = handle != 0L

    /** Display names for every chord template, indexed by the value [poll] returns. */
    val chordNames: List<String> by lazy { nativeChordNames().toList() }

    fun start(): Boolean = if (handle == 0L) false else nativeStart(handle)

    fun stop() {
        if (handle != 0L) nativeStop(handle)
    }

    fun isRunning(): Boolean = handle != 0L && nativeIsRunning(handle)

    /** Sample rate the input stream opened with, or 0 when stopped. */
    fun sampleRate(): Int = if (handle == 0L) 0 else nativeSampleRate(handle)

    /** Sets the RMS level below which the detector reports "no chord". */
    fun setNoiseGate(rms: Float) {
        if (handle != 0L) nativeSetNoiseGate(handle, rms)
    }

    /**
     * Reads the most recent detection.
     *
     * Returns a fresh [ChordDetection]; the underlying float arrays are copied so
     * the caller can hold on to the value safely.
     */
    fun poll(): ChordDetection {
        if (handle == 0L) return ChordDetection.NONE

        val index = nativePoll(handle, chromaScratch, scalarScratch)
        return ChordDetection(
            chordName = chordNames.getOrNull(index),
            confidence = scalarScratch[0],
            rms = scalarScratch[1],
            chroma = chromaScratch.copyOf(),
        )
    }

    /** Releases the native engine. The instance is unusable afterwards. */
    fun release() {
        if (handle != 0L) {
            nativeStop(handle)
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStart(handle: Long): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeIsRunning(handle: Long): Boolean
    private external fun nativeSampleRate(handle: Long): Int
    private external fun nativeSetNoiseGate(handle: Long, rms: Float)
    private external fun nativePoll(handle: Long, chroma: FloatArray, scalars: FloatArray): Int
    private external fun nativeChordNames(): Array<String>

    companion object {
        const val CHROMA_SIZE = 12

        init {
            System.loadLibrary("guitarsalmon")
        }
    }
}
