package com.guitarsalmon.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.guitarsalmon.AppContainer
import com.guitarsalmon.ui.live.LiveDetectViewModel
import com.guitarsalmon.ui.practice.PracticeViewModel
import com.guitarsalmon.ui.progress.ProgressViewModel
import com.guitarsalmon.ui.settings.SettingsViewModel

/**
 * Single factory for every ViewModel, wired from the [AppContainer].
 *
 * With four ViewModels and no runtime arguments this is simpler to follow than a DI
 * framework would be.
 */
fun guitarSalmonViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            LiveDetectViewModel(
                audioEngine = container.audioEngine,
                settingsRepository = container.settingsRepository,
            )
        }
        initializer {
            PracticeViewModel(
                audioEngine = container.audioEngine,
                repository = container.practiceRepository,
            )
        }
        initializer {
            ProgressViewModel(repository = container.practiceRepository)
        }
        initializer {
            SettingsViewModel(
                settingsRepository = container.settingsRepository,
                practiceRepository = container.practiceRepository,
                audioEngine = container.audioEngine,
            )
        }
    }
