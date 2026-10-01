package com.guitarsalmon.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User-adjustable settings. */
data class Settings(
    /**
     * Noise gate as an RMS threshold in 0..1. Higher ignores more quiet playing,
     * which helps in a noisy room but misses gentle strumming.
     */
    val noiseGateRms: Float = DEFAULT_NOISE_GATE,
    val showChromaVisualiser: Boolean = true,
) {
    /** Sensitivity as a 0..1 slider value, where 1 is the most sensitive. */
    val sensitivity: Float
        get() = 1f - ((noiseGateRms - MIN_NOISE_GATE) / (MAX_NOISE_GATE - MIN_NOISE_GATE))

    companion object {
        const val MIN_NOISE_GATE = 0.001f
        const val MAX_NOISE_GATE = 0.08f
        const val DEFAULT_NOISE_GATE = 0.01f

        /** Maps a 0..1 sensitivity slider value to an RMS gate threshold. */
        fun noiseGateForSensitivity(sensitivity: Float): Float {
            val clamped = sensitivity.coerceIn(0f, 1f)
            return MAX_NOISE_GATE - clamped * (MAX_NOISE_GATE - MIN_NOISE_GATE)
        }
    }
}

/** Persists [Settings] in SharedPreferences and exposes them as a [StateFlow]. */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun setNoiseGate(rms: Float) {
        val clamped = rms.coerceIn(Settings.MIN_NOISE_GATE, Settings.MAX_NOISE_GATE)
        prefs.edit().putFloat(KEY_NOISE_GATE, clamped).apply()
        _settings.value = _settings.value.copy(noiseGateRms = clamped)
    }

    fun setSensitivity(sensitivity: Float) =
        setNoiseGate(Settings.noiseGateForSensitivity(sensitivity))

    fun setShowChromaVisualiser(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CHROMA, show).apply()
        _settings.value = _settings.value.copy(showChromaVisualiser = show)
    }

    private fun read() = Settings(
        noiseGateRms = prefs.getFloat(KEY_NOISE_GATE, Settings.DEFAULT_NOISE_GATE),
        showChromaVisualiser = prefs.getBoolean(KEY_SHOW_CHROMA, true),
    )

    private companion object {
        const val PREFS_NAME = "guitarsalmon_settings"
        const val KEY_NOISE_GATE = "noise_gate_rms"
        const val KEY_SHOW_CHROMA = "show_chroma"
    }
}
