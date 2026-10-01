package com.guitarsalmon.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guitarsalmon.audio.AudioEngineState
import com.guitarsalmon.ui.components.ChromaVisualiser
import com.guitarsalmon.ui.components.ConfidenceIndicator
import com.guitarsalmon.ui.components.MicPermissionCard
import com.guitarsalmon.ui.components.MicPermissionController
import com.guitarsalmon.ui.theme.ChordDisplayStyle

/**
 * Live Detect: a large chord readout, a confidence meter and the chroma bars.
 */
@Composable
fun LiveDetectScreen(
    viewModel: LiveDetectViewModel,
    micPermission: MicPermissionController,
    modifier: Modifier = Modifier,
) {
    val detection by viewModel.detection.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val isRunning = engineState == AudioEngineState.Running

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!micPermission.isGranted) {
            MicPermissionCard(controller = micPermission)
            Spacer(Modifier.height(20.dp))
        }

        ChordReadout(
            chordName = detection.chordName,
            isRunning = isRunning,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))

        ConfidenceIndicator(
            confidence = detection.confidence,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        AnimatedVisibility(visible = settings.showChromaVisualiser) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Pitch classes",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    ChromaVisualiser(chroma = detection.chroma)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        StartStopButton(
            isRunning = isRunning,
            enabled = micPermission.isGranted,
            onClick = viewModel::toggle,
        )

        if (engineState == AudioEngineState.Failed) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Could not open the microphone. Another app may be using it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ChordReadout(
    chordName: String?,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    // A gentle fade when the chord changes, so the eye registers the transition
    // without the layout jumping around.
    val alpha by animateFloatAsState(
        targetValue = if (chordName != null) 1f else 0.35f,
        animationSpec = tween(180),
        label = "chord-alpha",
    )

    Box(
        modifier = modifier.height(140.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = chordName ?: if (isRunning) "–" else "Ready",
                style = ChordDisplayStyle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { this.alpha = alpha },
            )
            Text(
                text = when {
                    !isRunning -> "Tap listen, then strum a chord"
                    chordName == null -> "Listening…"
                    else -> "Detected"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StartStopButton(
    isRunning: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = if (isRunning) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = null,
        )
        Spacer(Modifier.height(0.dp))
        Text(
            text = if (isRunning) "  Stop listening" else "  Start listening",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
