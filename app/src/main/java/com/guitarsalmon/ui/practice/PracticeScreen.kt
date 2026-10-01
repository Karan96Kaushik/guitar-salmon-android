package com.guitarsalmon.ui.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guitarsalmon.ui.components.ChromaVisualiser
import com.guitarsalmon.ui.components.MicPermissionCard
import com.guitarsalmon.ui.components.MicPermissionController
import com.guitarsalmon.ui.theme.CorrectGreen

/**
 * Practice mode: pick a sequence, hold each chord until it registers, advance.
 */
@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    micPermission: MicPermissionController,
    showChroma: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (!micPermission.isGranted) {
            MicPermissionCard(controller = micPermission)
            Spacer(Modifier.height(20.dp))
        }

        if (!state.isRunning) {
            SequencePicker(
                presets = viewModel.presets,
                singleChords = viewModel.singleChords,
                selectedName = state.sequence.name,
                onSelectPreset = viewModel::selectSequence,
                onSelectChord = viewModel::selectSingleChord,
            )
            Spacer(Modifier.height(20.dp))
        }

        TargetDisplay(state = state, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))

        SequenceStrip(
            chords = state.sequence.chords,
            position = state.position,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = if (state.isRunning) viewModel::stop else viewModel::start,
                enabled = micPermission.isGranted,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (state.isRunning) "Stop" else "Start practice")
            }
            if (state.isRunning) {
                OutlinedButton(onClick = viewModel::skipCurrent) { Text("Skip") }
            }
        }

        if (state.isComplete && !state.isRunning) {
            Spacer(Modifier.height(20.dp))
            CompletionCard(state = state)
        }

        if (state.outcomes.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            AttemptLog(outcomes = state.outcomes)
        }

        if (showChroma && state.isRunning) {
            Spacer(Modifier.height(24.dp))
            ChromaVisualiser(chroma = state.detection.chroma, barHeight = 90)
        }
    }
}

/** Big target chord with a ring that fills as the chord is held. */
@Composable
private fun TargetDisplay(state: PracticeUiState, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = state.holdProgress,
        animationSpec = tween(80),
        label = "hold",
    )
    val ringColor by animateColorAsState(
        targetValue = if (state.isOnTarget) CorrectGreen else MaterialTheme.colorScheme.primary,
        label = "ring",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val stroke = 14f
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (progress > 0f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.targetChord ?: if (state.isComplete) "Done" else "–",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        !state.isRunning -> "Target"
                        state.isOnTarget -> "Hold it…"
                        state.detection.chordName != null -> "Heard ${state.detection.chordName}"
                        else -> "Listening…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Horizontal strip of the sequence showing which chord is current. */
@Composable
private fun SequenceStrip(chords: List<String>, position: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        chords.forEachIndexed { index, chord ->
            val done = index < position
            val current = index == position
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(chord) },
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = when {
                        done -> CorrectGreen.copy(alpha = 0.25f)
                        current -> MaterialTheme.colorScheme.primaryContainer
                        else -> Color.Transparent
                    },
                    disabledLabelColor = when {
                        current -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            )
        }
    }
}

@Composable
private fun SequencePicker(
    presets: List<com.guitarsalmon.domain.PracticeSequence>,
    singleChords: List<String>,
    selectedName: String,
    onSelectPreset: (com.guitarsalmon.domain.PracticeSequence) -> Unit,
    onSelectChord: (String) -> Unit,
) {
    Column {
        Text("Practise a sequence", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets) { preset ->
                FilterChip(
                    selected = preset.name == selectedName,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset.name) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Or drill one chord", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(singleChords) { chord ->
                FilterChip(
                    selected = chord == selectedName,
                    onClick = { onSelectChord(chord) },
                    label = { Text(chord) },
                )
            }
        }
    }
}

@Composable
private fun CompletionCard(state: PracticeUiState) {
    val times = state.outcomes.mapNotNull { it.timeToHitMs }
    val average = if (times.isEmpty()) null else times.average().toLong()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Sequence complete",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("${state.hits} of ${state.total} chords hit")
                    if (average != null) append(" · average ${formatMs(average)}")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun AttemptLog(outcomes: List<AttemptOutcome>) {
    Column {
        Text("This run", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        outcomes.takeLast(MAX_LOG_ROWS).forEach { outcome ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = outcome.chord,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = outcome.timeToHitMs?.let { formatMs(it) } ?: "skipped",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (outcome.success) CorrectGreen else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String =
    if (ms < 1000) "$ms ms" else String.format("%.1f s", ms / 1000.0)

private const val MAX_LOG_ROWS = 8
