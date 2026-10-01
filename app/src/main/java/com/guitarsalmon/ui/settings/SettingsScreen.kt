package com.guitarsalmon.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guitarsalmon.data.Settings
import com.guitarsalmon.ui.components.MicPermissionController
import com.guitarsalmon.ui.components.MicPermissionState
import com.guitarsalmon.ui.theme.CorrectGreen
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    micPermission: MicPermissionController,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val detection by viewModel.detection.collectAsStateWithLifecycle()
    val dataCleared by viewModel.dataCleared.collectAsStateWithLifecycle()
    val calibration by viewModel.calibration.collectAsStateWithLifecycle()
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        SensitivitySection(
            settings = settings,
            currentRms = detection.rms,
            onSensitivityChange = viewModel::setSensitivity,
            onCalibrate = {
                when {
                    micPermission.isGranted -> viewModel.startCalibration()
                    micPermission.state == MicPermissionState.PermanentlyDenied ->
                        micPermission.openSettings()
                    else -> micPermission.request()
                }
            },
            calibrateEnabled = calibration !is CalibrationState.Settling &&
                calibration !is CalibrationState.Sampling,
        )

        if (!micPermission.isGranted) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (micPermission.state) {
                    MicPermissionState.PermanentlyDenied ->
                        "Microphone access is off. Enable it in Android settings to calibrate."
                    else ->
                        "Calibration needs the microphone. Allow access when prompted."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Chroma visualiser", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Show the twelve pitch class bars",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.showChromaVisualiser,
                onCheckedChange = viewModel::setShowChromaVisualiser,
            )
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text("Practice data", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Deletes every session and chord attempt stored on this device. " +
                "This cannot be undone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { showResetDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Text("Reset all data")
        }

        if (dataCleared) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Practice data cleared.",
                style = MaterialTheme.typography.bodyMedium,
                color = CorrectGreen,
            )
        }
    }

    when (val state = calibration) {
        CalibrationState.Idle -> Unit
        is CalibrationState.Settling,
        is CalibrationState.Sampling,
        -> CalibrationInProgressDialog(
            state = state,
            onCancel = viewModel::dismissCalibration,
        )
        is CalibrationState.Succeeded -> CalibrationResultDialog(
            title = "Calibration complete",
            body = "Noise gate set to sit above your room's ambient level " +
                "(threshold ${formatGate(state.noiseGateRms)}). " +
                "You can still fine-tune with the sensitivity slider.",
            onDismiss = viewModel::dismissCalibration,
        )
        is CalibrationState.Failed -> CalibrationResultDialog(
            title = "Calibration failed",
            body = state.reason,
            onDismiss = viewModel::dismissCalibration,
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all data?") },
            text = {
                Text("Every session and chord attempt will be deleted. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAllData()
                        showResetDialog = false
                    }
                ) {
                    Text("Delete everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Sensitivity slider with a live level meter and one-tap ambient calibration.
 *
 * The meter is what makes the setting usable: the number alone means nothing, but
 * seeing your own playing sit above or below the threshold makes the right value
 * obvious. Calibration measures the room while you stay quiet and places the gate
 * just above that ambient peak.
 */
@Composable
private fun SensitivitySection(
    settings: Settings,
    currentRms: Float,
    onSensitivityChange: (Float) -> Unit,
    onCalibrate: () -> Unit,
    calibrateEnabled: Boolean,
) {
    Text("Sensitivity", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        text = "How quiet a sound still counts as playing. Turn it down if background " +
            "noise is being detected as chords; turn it up if gentle strumming is missed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    Slider(
        value = settings.sensitivity,
        onValueChange = onSensitivityChange,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Less sensitive", style = MaterialTheme.typography.labelSmall)
        Text("More sensitive", style = MaterialTheme.typography.labelSmall)
    }

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = onCalibrate,
        enabled = calibrateEnabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Calibrate automatically")
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Stay quiet for a few seconds. The app listens to your room and sets " +
            "the gate just above the ambient noise.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(16.dp))

    Card {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Input level", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = if (currentRms >= settings.noiseGateRms) "above gate" else "below gate",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (currentRms >= settings.noiseGateRms) {
                        CorrectGreen
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            // Scaled against the top of the gate range so the bar has useful travel.
            LinearProgressIndicator(
                progress = { (currentRms / Settings.MAX_NOISE_GATE).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Start listening on the Live tab to see your level here.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalibrationInProgressDialog(
    state: CalibrationState,
    onCancel: () -> Unit,
) {
    val progress = when (state) {
        is CalibrationState.Sampling -> state.progress
        else -> 0f
    }
    val liveRms = when (state) {
        is CalibrationState.Sampling -> state.liveRms
        else -> 0f
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Calibrating…") },
        text = {
            Column {
                Text(
                    text = if (state is CalibrationState.Settling) {
                        "Opening the microphone. Stay quiet and do not touch the guitar."
                    } else {
                        "Measuring room noise. Stay quiet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    strokeCap = StrokeCap.Round,
                )
                if (state is CalibrationState.Sampling) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Ambient level ${formatGate(liveRms)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}

@Composable
private fun CalibrationResultDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

/** Formats an RMS gate as a short percentage of the adjustable range. */
private fun formatGate(rms: Float): String {
    val pct = ((rms / Settings.MAX_NOISE_GATE).coerceIn(0f, 1f) * 100f).roundToInt()
    return "$pct%"
}
