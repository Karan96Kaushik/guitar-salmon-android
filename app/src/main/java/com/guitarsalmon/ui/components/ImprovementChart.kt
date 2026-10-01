package com.guitarsalmon.ui.components

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.guitarsalmon.domain.DailyProgressPoint
import java.time.format.DateTimeFormatter

/**
 * Line chart of average time-to-hit per day, drawn with Compose Canvas.
 *
 * The y-axis is inverted relative to the usual "up is more" convention: the metric
 * is a time, so faster -- and therefore better -- is higher on the chart. That
 * makes improvement read as a line going up.
 */
@Composable
fun ImprovementChart(
    points: List<DailyProgressPoint>,
    modifier: Modifier = Modifier,
    chartHeight: Int = 160,
) {
    if (points.size < 2) {
        Text(
            text = if (points.isEmpty()) {
                "Practise on two different days to see your improvement here."
            } else {
                "One more day of practice and your trend will appear here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val pointColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    val values = points.map { it.averageTimeToHitMs.toFloat() }

    // Pad the range so the line never sits exactly on the top or bottom edge.
    val minValue = values.min()
    val maxValue = values.max()
    val span = (maxValue - minValue).takeIf { it > 0f } ?: 1f
    val low = minValue - span * 0.15f
    val high = maxValue + span * 0.15f

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight.dp)
        ) {
            val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width

            // Faster is higher, so map the smallest time to the top of the plot.
            fun yFor(value: Float): Float {
                val fraction = ((value - low) / (high - low)).coerceIn(0f, 1f)
                return fraction * size.height
            }

            // Horizontal guides at quarter intervals.
            repeat(5) { i ->
                val y = size.height * i / 4f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)),
                )
            }

            val path = Path()
            values.forEachIndexed { index, value ->
                val x = index * stepX
                val y = yFor(value)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = lineColor, style = Stroke(width = 4f))

            values.forEachIndexed { index, value ->
                drawCircle(
                    color = pointColor,
                    radius = 6f,
                    center = Offset(index * stepX, yFor(value)),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = points.first().date.format(DAY_FORMAT),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "faster is higher",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = points.last().date.format(DAY_FORMAT),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
