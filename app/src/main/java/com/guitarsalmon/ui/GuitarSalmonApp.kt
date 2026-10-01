package com.guitarsalmon.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guitarsalmon.AppContainer
import com.guitarsalmon.ui.components.rememberMicPermission
import com.guitarsalmon.ui.live.LiveDetectScreen
import com.guitarsalmon.ui.live.LiveDetectViewModel
import com.guitarsalmon.ui.practice.PracticeScreen
import com.guitarsalmon.ui.practice.PracticeViewModel
import com.guitarsalmon.ui.progress.ProgressScreen
import com.guitarsalmon.ui.progress.ProgressViewModel
import com.guitarsalmon.ui.settings.SettingsScreen
import com.guitarsalmon.ui.settings.SettingsViewModel

/** The app's four destinations. */
enum class Destination(val label: String, val icon: ImageVector) {
    Live("Live", Icons.Filled.GraphicEq),
    Practice("Practice", Icons.Filled.Timer),
    Progress("Progress", Icons.AutoMirrored.Filled.ShowChart),
    Settings("Settings", Icons.Filled.Settings),
}

/**
 * Root composable.
 *
 * Navigation is a single saved enum rather than a nav graph: with four flat,
 * sibling destinations and no arguments or deep links, a NavHost would add a
 * dependency and indirection for nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuitarSalmonApp(container: AppContainer) {
    val factory = guitarSalmonViewModelFactory(container)
    var destination by rememberSaveable { mutableStateOf(Destination.Live) }
    val micPermission = rememberMicPermission()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(topBarTitle(destination)) })
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = destination == entry,
                        onClick = { destination = entry },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { insets ->
        val contentModifier = Modifier.padding(insets)

        when (destination) {
            Destination.Live -> LiveDetectScreen(
                viewModel = viewModel<LiveDetectViewModel>(factory = factory),
                micPermission = micPermission,
                modifier = contentModifier,
            )

            Destination.Practice -> PracticeScreen(
                viewModel = viewModel<PracticeViewModel>(factory = factory),
                micPermission = micPermission,
                showChroma = settings.showChromaVisualiser,
                modifier = contentModifier,
            )

            Destination.Progress -> ProgressScreen(
                viewModel = viewModel<ProgressViewModel>(factory = factory),
                modifier = contentModifier,
            )

            Destination.Settings -> SettingsScreen(
                viewModel = viewModel<SettingsViewModel>(factory = factory),
                micPermission = micPermission,
                modifier = contentModifier,
            )
        }
    }
}

private fun topBarTitle(destination: Destination): String = when (destination) {
    Destination.Live -> "GuitarSalmon"
    Destination.Practice -> "Practice"
    Destination.Progress -> "Your progress"
    Destination.Settings -> "Settings"
}
