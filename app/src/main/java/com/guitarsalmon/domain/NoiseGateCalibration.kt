package com.guitarsalmon.domain

import com.guitarsalmon.data.Settings
import kotlin.math.max

/**
 * Derives a noise-gate RMS from a short recording of ambient room noise.
 *
 * Pure: given the same samples it always returns the same gate. The UI layer is
 * responsible for collecting samples; this only turns them into a threshold.
 */
object NoiseGateCalibration {

    /**
     * How far above the measured ambient peak the gate sits.
     *
     * Room noise is not perfectly constant: HVAC cycles, footsteps, phone
     * notifications. Sitting the gate a few times above the quiet-room peak keeps
     * those spikes from being mistaken for soft strumming, without climbing so high
     * that a normal quiet chord falls below it.
     */
    const val HEADROOM = 2.5f

    /** Absolute floor so a dead-silent room does not collapse the gate to zero. */
    const val FLOOR_RMS = Settings.MIN_NOISE_GATE

    /**
     * Computes a gate from ambient RMS readings collected while the user stays quiet.
     *
     * Uses the peak rather than the mean: the gate has to clear the *loudest* moment
     * of room noise, not the average. Empty or all-zero input falls back to the
     * default gate.
     */
    fun gateFromAmbientSamples(rmsSamples: List<Float>): Float {
        if (rmsSamples.isEmpty()) return Settings.DEFAULT_NOISE_GATE

        val peak = rmsSamples.maxOrNull() ?: return Settings.DEFAULT_NOISE_GATE
        if (!peak.isFinite() || peak <= 0f) return Settings.DEFAULT_NOISE_GATE

        val proposed = max(peak * HEADROOM, FLOOR_RMS)
        return proposed.coerceIn(Settings.MIN_NOISE_GATE, Settings.MAX_NOISE_GATE)
    }
}
