package com.guitarsalmon.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.guitarsalmon.ui.theme.CorrectGreen
import kotlin.math.roundToInt

/**
 * Horizontal confidence meter.
 *
 * The bar is colour coded because the absolute number means little to a learner:
 * what they need to know is whether the detector is sure, unsure, or guessing.
 */
@Composable
fun ConfidenceIndicator(
    confidence: Float,
    modifier: Modifier = Modifier,
    label: String = "Confidence",
) {
    val clamped = confidence.coerceIn(0f, 1f)
    val animatedValue by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = 120),
        label = "confidence",
    )
    val targetColor = when {
        clamped >= STRONG -> CorrectGreen
        clamped >= WEAK -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val animatedColor by animateColorAsState(targetColor, label = "confidence-color")

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "${(clamped * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        LinearProgressIndicator(
            progress = { animatedValue },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = animatedColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
    }
}

private const val STRONG = 0.8f
private const val WEAK = 0.55f
