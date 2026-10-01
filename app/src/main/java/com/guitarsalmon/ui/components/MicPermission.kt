package com.guitarsalmon.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Whether RECORD_AUDIO has been granted. */
fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/** State of the microphone permission as far as the UI needs to care. */
enum class MicPermissionState { Granted, NotRequested, Denied, PermanentlyDenied }

/**
 * Remembers the microphone permission state and gives back a launcher for
 * requesting it.
 *
 * "Permanently denied" is inferred the way the platform intends: after a denial,
 * if the system no longer wants to show a rationale, the user has chosen "don't ask
 * again" and only the system settings screen can change it.
 */
@Composable
fun rememberMicPermission(): MicPermissionController {
    val context = LocalContext.current
    var state by remember {
        mutableStateOf(
            if (context.hasMicPermission()) MicPermissionState.Granted
            else MicPermissionState.NotRequested
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        state = when {
            granted -> MicPermissionState.Granted
            shouldShowRationale(context) -> MicPermissionState.Denied
            else -> MicPermissionState.PermanentlyDenied
        }
    }

    return remember(state) {
        MicPermissionController(
            state = state,
            request = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
            refresh = {
                if (context.hasMicPermission()) state = MicPermissionState.Granted
            },
            openSettings = { context.openAppSettings() },
        )
    }
}

class MicPermissionController(
    val state: MicPermissionState,
    val request: () -> Unit,
    val refresh: () -> Unit,
    val openSettings: () -> Unit,
) {
    val isGranted: Boolean get() = state == MicPermissionState.Granted
}

/**
 * Card explaining why the microphone is needed, with the appropriate action for the
 * current permission state.
 */
@Composable
fun MicPermissionCard(
    controller: MicPermissionController,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Microphone access",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "GuitarSalmon listens to your guitar to work out which chord " +
                    "you are playing. Audio is analysed on your device as it arrives " +
                    "and is never recorded, saved or sent anywhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            if (controller.state == MicPermissionState.PermanentlyDenied) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Microphone access is turned off for this app, so it has to " +
                        "be re-enabled in Android settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (controller.state == MicPermissionState.PermanentlyDenied) {
                    Button(onClick = controller.openSettings) { Text("Open settings") }
                    TextButton(onClick = controller.refresh) { Text("I've enabled it") }
                } else {
                    Button(onClick = controller.request) { Text("Allow microphone") }
                }
            }
        }
    }
}

private fun shouldShowRationale(context: Context): Boolean {
    val activity = context.findActivity() ?: return false
    return activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
}

/** Walks up the ContextWrapper chain, since LocalContext is not always the Activity. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    )
}
