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
    // Read version to ensure recomposition on point additions (works with neverEqualPolicy)
    @Suppress("UNUSED_VARIABLE")
    val pathVersion = viewModel.currentPathVersion.value

    // Read the snapshot state lists directly - they're already observable
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

            // Draw current path
            currentPath?.let { path ->
                drawStrokePathFast(path)
            }
        }
    }
}

/**
 * Draw path with pressure-sensitive width - varies stroke width based on pen pressure.
 * Uses individual segments for variable width rendering.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePathFast(path: DrawingPath) {
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
        // Map pressure (typically 0-1) to stroke width
        val strokeWidth = minWidth + (maxWidth - minWidth) * avgPressure.coerceIn(0f, 1f)

        drawLine(
            color = color,
            start = Offset(prev.x, prev.y),
            end = Offset(curr.x, curr.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

