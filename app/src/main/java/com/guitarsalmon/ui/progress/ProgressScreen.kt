package com.guitarsalmon.ui.progress

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guitarsalmon.domain.ChordPerformance
import com.guitarsalmon.ui.components.ImprovementChart
import com.guitarsalmon.ui.theme.CorrectGreen
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Progress: totals, streak, per-chord accuracy and the improvement trend. */
@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel,
    modifier: Modifier = Modifier,
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (!summary.hasData) {
            Text(
                text = "No practice recorded yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Run a practice session and your totals, streak and per-chord " +
                    "accuracy will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Practice time",
                value = formatDuration(summary.totalPracticeMs),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Streak",
                value = if (summary.streakDays == 1) "1 day" else "${summary.streakDays} days",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Sessions",
                value = summary.sessionCount.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = "Accuracy",
                value = "${(summary.overallAccuracy * 100).roundToInt()}%",
                modifier = Modifier.weight(1f),
            )
        }

        if (summary.chordsToWorkOn.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            ChordsToWorkOnCard(summary.chordsToWorkOn)
        }

        Spacer(Modifier.height(24.dp))
        Text("Improvement", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Average time to find a chord, by day",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        ImprovementChart(points = summary.dailyProgress, modifier = Modifier.fillMaxWidth())

        if (summary.performances.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Text("Per chord", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            summary.performances.forEach { performance ->
                ChordRow(performance)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ChordsToWorkOnCard(chords: List<ChordPerformance>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Chords to work on",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            chords.forEach { chord ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = chord.chord,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "${(chord.accuracy * 100).roundToInt()}% of " +
                            "${chord.attempts} attempts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChordRow(performance: ChordPerformance) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = performance.chord,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = buildString {
                    append("${(performance.accuracy * 100).roundToInt()}%")
                    performance.averageTimeToHitMs?.let { append(" · ${formatMs(it)}") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { performance.accuracy },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = if (performance.accuracy >= GOOD_ACCURACY) {
                CorrectGreen
            } else {
                MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
    }
}

private fun formatMs(ms: Long): String =
    if (ms < 1000) "$ms ms" else String.format("%.1f s", ms / 1000.0)

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${TimeUnit.MILLISECONDS.toSeconds(millis)}s"
    }
}

private const val GOOD_ACCURACY = 0.8f
