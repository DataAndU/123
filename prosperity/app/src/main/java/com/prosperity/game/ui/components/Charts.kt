package com.prosperity.game.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** A no-dependency line chart: plots a list of doubles across the full width/height it's given. */
@Composable
fun LineChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    showRangeLabels: Boolean = true
) {
    Column(modifier) {
        if (values.size < 2) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text("Not enough history yet", style = MaterialTheme.typography.bodySmall)
            }
            return@Column
        }
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).takeIf { it > 0.0001 } ?: 1.0

        if (showRangeLabels) {
            Row(Modifier.fillMaxWidth()) {
                Text(formatCompact(maxV), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val stepX = size.width / (values.size - 1)
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = index * stepX
                val y = size.height - ((value - minV) / range * size.height).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 4f))
            val zeroLineY = size.height - ((0.0 - minV) / range * size.height).toFloat()
            if (minV < 0 && maxV > 0) {
                drawLine(Color.Gray.copy(alpha = 0.4f), Offset(0f, zeroLineY), Offset(size.width, zeroLineY), strokeWidth = 1.5f)
            }
        }
        if (showRangeLabels) {
            Row(Modifier.fillMaxWidth()) {
                Text(formatCompact(minV), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A tiny inline chart with no axis labels, for use inside a list row. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, lineColor: Color = MaterialTheme.colorScheme.primary) {
    if (values.size < 2) return
    val minV = values.min()
    val maxV = values.max()
    val range = (maxV - minV).takeIf { it > 0.0001 } ?: 1.0
    Canvas(modifier = modifier.fillMaxSize()) {
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - minV) / range * size.height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 3f))
    }
}

/** Side-by-side bars comparing two values (e.g. income vs. expenses). */
@Composable
fun ComparisonBars(leftLabel: String, leftValue: Double, rightLabel: String, rightValue: Double, modifier: Modifier = Modifier) {
    val maxValue = maxOf(leftValue, rightValue, 1.0)
    Column(modifier.fillMaxWidth()) {
        BarRow(leftLabel, leftValue, maxValue, MaterialTheme.colorScheme.primary)
        BarRow(rightLabel, rightValue, maxValue, MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun BarRow(label: String, value: Double, maxValue: Double, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.3f))
        Box(Modifier.weight(0.55f).height(14.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val fraction = (value / maxValue).coerceIn(0.0, 1.0)
                drawRect(color, size = size.copy(width = size.width * fraction.toFloat()))
            }
        }
        Text(formatCompact(value), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.3f))
    }
}

private fun formatCompact(value: Double): String {
    val abs = kotlin.math.abs(value)
    return when {
        abs >= 1_000_000 -> "$${"%.1f".format(value / 1_000_000)}M"
        abs >= 1_000 -> "$${"%.1f".format(value / 1_000)}k"
        else -> "$${"%.0f".format(value)}"
    }
}
