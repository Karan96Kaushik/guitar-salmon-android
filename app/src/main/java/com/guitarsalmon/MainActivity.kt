package com.guitarsalmon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.guitarsalmon.ui.GuitarSalmonApp
import com.guitarsalmon.ui.theme.GuitarSalmonTheme
import kotlinx.coroutines.launch

/**
 * The app's single activity.
 *
 * Audio capture is tied to the activity's visible lifecycle: holding the microphone
 * while in the background would be both a privacy problem and a drain, and the
 * analysis is only useful when someone is looking at the screen.
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy { appContainer }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GuitarSalmonTheme {
                GuitarSalmonApp(container = container)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Release the stream rather than just pausing it, so another app can take the
        // microphone while we are backgrounded.
        lifecycleScope.launch { container.audioEngine.stop() }
    }
}
