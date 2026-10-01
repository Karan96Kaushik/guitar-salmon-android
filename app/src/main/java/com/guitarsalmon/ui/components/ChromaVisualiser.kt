package com.guitarsalmon.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Pitch class names in chroma order, matching the native matcher's indexing. */
val PitchClassLabels = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

/**
 * Twelve-bar chroma display, one bar per pitch class.
 *
 * Bar heights are animated rather than snapped to each incoming frame. At 20 Hz the
 * raw values are visibly steppy, and a short tween reads as the chord "settling"
 * without hiding genuine changes.
 */
@Composable
fun ChromaVisualiser(
    chroma: FloatArray,
    modifier: Modifier = Modifier,
    barHeight: Int = 140,
    highlightedPitchClasses: Set<Int> = emptySet(),
) {
    val baseColor = MaterialTheme.colorScheme.secondary
    val peakColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightColor = MaterialTheme.colorScheme.primary

    // One animation per bar; each follows its own value.
    val animated = (0 until CHROMA_SIZE).map { index ->
        animateFloatAsState(
            targetValue = chroma.getOrElse(index) { 0f }.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = ANIMATION_MS),
            label = "chroma-$index",
        ).value
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            animated.forEachIndexed { index, value ->
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight.dp)
                ) {
                    drawChromaBar(
                        value = value,
                        trackColor = trackColor,
                        fillColor = if (index in highlightedPitchClasses) {
                            highlightColor
                        } else {
                            lerp(baseColor, peakColor, value)
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PitchClassLabels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index in highlightedPitchClasses) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Draws one bar: a full-height track with the value filled from the bottom. */
private fun DrawScope.drawChromaBar(value: Float, trackColor: Color, fillColor: Color) {
    val corner = CornerRadius(size.width / 3f)

    drawRoundRect(
        color = trackColor,
        size = size,
        cornerRadius = corner,
    )

    // Always show a sliver so the bar reads as "present but silent" rather than
    // disappearing entirely.
    val filled = (size.height * value).coerceAtLeast(size.width / 2f)
    drawRoundRect(
        color = fillColor,
        topLeft = Offset(0f, size.height - filled),
        size = Size(size.width, filled),
        cornerRadius = corner,
    )
}

private const val CHROMA_SIZE = 12
private const val ANIMATION_MS = 90
