package com.guitarsalmon.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guitarsalmon.audio.AudioEngineState
import com.guitarsalmon.audio.ChordAudioEngine
import com.guitarsalmon.audio.ChordDetection
import com.guitarsalmon.data.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Drives the Live Detect screen. */
class LiveDetectViewModel(
    private val audioEngine: ChordAudioEngine,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val detection: StateFlow<ChordDetection> = audioEngine.detection
    val engineState: StateFlow<AudioEngineState> = audioEngine.state
    val settings = settingsRepository.settings

    fun start() {
        viewModelScope.launch { audioEngine.start() }
    }

    fun stop() {
        viewModelScope.launch { audioEngine.stop() }
    }

    fun toggle() {
        viewModelScope.launch {
            if (engineState.value == AudioEngineState.Running) {
                audioEngine.stop()
            } else {
                audioEngine.start()
            }
        }
    }
}
