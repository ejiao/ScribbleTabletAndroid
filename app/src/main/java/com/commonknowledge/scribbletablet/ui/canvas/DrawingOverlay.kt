package com.commonknowledge.scribbletablet.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import com.commonknowledge.scribbletablet.data.model.DrawingPath
import com.commonknowledge.scribbletablet.viewmodel.CanvasViewModel

/**
 * Overlay that renders drawing paths above cards.
 * This is a visual-only layer - touch handling is done by DrawingCanvas.
 */
@Composable
fun DrawingOverlay(
    viewModel: CanvasViewModel,
    modifier: Modifier = Modifier
) {
    val scale = viewModel.canvasScale.value
    val offsetX = viewModel.canvasOffsetX.value
    val offsetY = viewModel.canvasOffsetY.value

    // Read state values at composable level to trigger recomposition
    val currentPath = viewModel.currentPath.value
    val permanentPaths = viewModel.permanentPaths
    val magicPaths = viewModel.magicPaths

    Canvas(modifier = modifier.fillMaxSize()) {
        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, Offset.Zero)
        }) {
            // Draw permanent paths
            permanentPaths.forEach { path ->
                drawStrokePathFast(path)
            }

            // Draw magic paths
            magicPaths.forEach { path ->
                drawStrokePathFast(path)
            }

            // Draw current path with predictive extension for lower perceived latency
            currentPath?.let { path ->
                drawStrokePathFast(path, predictive = true)
            }
        }
    }
}

/**
 * Draw path with pressure-sensitive width and predictive extension.
 * Varies stroke width based on pen pressure and extends slightly in direction of movement.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePathFast(
    path: DrawingPath,
    predictive: Boolean = false
) {
    if (path.points.size < 2) return

    val points = path.points
    val color = Color(path.color)
    val baseWidth = path.strokeWidth
    val minWidth = baseWidth * 0.3f
    val maxWidth = baseWidth * 1.5f

    // Draw pressure-sensitive segments
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]

        // Interpolate pressure between points for smooth width transitions
        val avgPressure = (prev.pressure + curr.pressure) / 2f
        val strokeWidth = minWidth + (maxWidth - minWidth) * avgPressure.coerceIn(0f, 1f)

        drawLine(
            color = color,
            start = Offset(prev.x, prev.y),
            end = Offset(curr.x, curr.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }

    // Predictive extension: draw slightly ahead based on velocity
    if (predictive && points.size >= 3) {
        val n = points.size
        val p1 = points[n - 2]
        val p2 = points[n - 1]

        // Calculate velocity
        val vx = p2.x - p1.x
        val vy = p2.y - p1.y

        // Extend 30% ahead
        val predictX = p2.x + vx * 0.3f
        val predictY = p2.y + vy * 0.3f
        val strokeWidth = minWidth + (maxWidth - minWidth) * p2.pressure.coerceIn(0f, 1f)

        drawLine(
            color = color,
            start = Offset(p2.x, p2.y),
            end = Offset(predictX, predictY),
            strokeWidth = strokeWidth * 0.8f, // Slightly thinner for prediction
            cap = StrokeCap.Round
        )
    }
}

