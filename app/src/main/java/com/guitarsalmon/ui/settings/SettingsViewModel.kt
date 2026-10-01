package com.guitarsalmon.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guitarsalmon.audio.AudioEngineState
import com.guitarsalmon.audio.ChordAudioEngine
import com.guitarsalmon.data.PracticeRepository
import com.guitarsalmon.data.Settings
import com.guitarsalmon.data.SettingsRepository
import com.guitarsalmon.domain.NoiseGateCalibration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Progress of an ambient-noise calibration run. */
sealed interface CalibrationState {
    data object Idle : CalibrationState

    /** Mic is open; waiting out the settle period before samples count. */
    data object Settling : CalibrationState

    /**
     * Collecting ambient RMS.
     *
     * @param progress 0..1 through the sampling window.
     * @param liveRms most recent sample, for the level meter in the dialog.
     */
    data class Sampling(val progress: Float, val liveRms: Float) : CalibrationState

    data class Succeeded(val noiseGateRms: Float) : CalibrationState

    data class Failed(val reason: String) : CalibrationState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val practiceRepository: PracticeRepository,
    private val audioEngine: ChordAudioEngine,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings

    private val _dataCleared = MutableStateFlow(false)

    /** Set after a reset so the screen can confirm it happened. */
    val dataCleared: StateFlow<Boolean> = _dataCleared.asStateFlow()

    /** Live level readout, so the user can set the gate against their actual room. */
    val detection = audioEngine.detection

    private val _calibration = MutableStateFlow<CalibrationState>(CalibrationState.Idle)
    val calibration: StateFlow<CalibrationState> = _calibration.asStateFlow()

    private var calibrationJob: Job? = null

    fun setSensitivity(sensitivity: Float) {
        settingsRepository.setSensitivity(sensitivity)
        // Push straight to the running engine so the change is audible immediately.
        audioEngine.setNoiseGate(settingsRepository.settings.value.noiseGateRms)
    }

    fun setShowChromaVisualiser(show: Boolean) {
        settingsRepository.setShowChromaVisualiser(show)
    }

    fun resetAllData() {
        viewModelScope.launch {
            practiceRepository.resetAll()
            _dataCleared.value = true
        }
    }

    fun acknowledgeDataCleared() {
        _dataCleared.value = false
    }

    /**
     * Measures ambient room noise for a few seconds and sets the noise gate just
     * above it. The user should stay quiet and not touch the guitar.
     *
     * Starts the microphone if it is not already running, and stops it afterwards
     * only when this run opened it — so calibrating from Settings does not leave
     * capture going after the dialog closes.
     */
    fun startCalibration() {
        if (calibrationJob?.isActive == true) return

        calibrationJob = viewModelScope.launch {
            val alreadyRunning = audioEngine.state.value == AudioEngineState.Running

            _calibration.value = CalibrationState.Settling

            if (!alreadyRunning) {
                val started = audioEngine.start()
                if (!started) {
                    _calibration.value = CalibrationState.Failed(
                        "Could not open the microphone. Check the permission and try again."
                    )
                    return@launch
                }
            }

            try {
                // Discard the first frames: the stream takes a moment to settle, and
                // the device may briefly AGC-pump when the mic opens.
                delay(SETTLE_MS)
                if (!isActive) return@launch

                val samples = ArrayList<Float>(SAMPLE_CAPACITY)
                val startedAt = System.currentTimeMillis()
                val deadline = startedAt + SAMPLE_MS

                while (isActive && System.currentTimeMillis() < deadline) {
                    val rms = audioEngine.detection.value.rms
                    samples.add(rms)
                    val elapsed = System.currentTimeMillis() - startedAt
                    _calibration.value = CalibrationState.Sampling(
                        progress = (elapsed.toFloat() / SAMPLE_MS).coerceIn(0f, 1f),
                        liveRms = rms,
                    )
                    delay(SAMPLE_TICK_MS)
                }

                if (!isActive) return@launch

                if (samples.size < MIN_SAMPLES) {
                    _calibration.value = CalibrationState.Failed(
                        "Not enough audio samples. Try again in a quieter moment."
                    )
                    return@launch
                }

                val gate = NoiseGateCalibration.gateFromAmbientSamples(samples)
                settingsRepository.setNoiseGate(gate)
                audioEngine.setNoiseGate(gate)
                _calibration.value = CalibrationState.Succeeded(gate)
            } finally {
                if (!alreadyRunning) {
                    audioEngine.stop()
                }
            }
        }
    }

    /** Cancels an in-progress run or clears a finished result from the UI. */
    fun dismissCalibration() {
        calibrationJob?.cancel()
        calibrationJob = null
        _calibration.value = CalibrationState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        calibrationJob?.cancel()
    }

    private companion object {
        /** Time to ignore after opening the mic. */
        const val SETTLE_MS = 400L

        /** How long ambient samples are collected. */
        const val SAMPLE_MS = 2500L

        /** Interval between samples; matches the audio engine's publish rate. */
        const val SAMPLE_TICK_MS = 50L

        const val SAMPLE_CAPACITY = ((SAMPLE_MS / SAMPLE_TICK_MS) + 4).toInt()

        const val MIN_SAMPLES = 10
    }
}
