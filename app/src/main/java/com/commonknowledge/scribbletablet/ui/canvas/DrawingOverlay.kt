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
 * Draw path using a single batched path - fastest rendering.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePathFast(path: DrawingPath) {
    if (path.points.size < 2) return

    // Use a single Path object for batched rendering (faster than individual drawLine calls)
    val linePath = Path()
    val first = path.points[0]
    linePath.moveTo(first.x, first.y)

    for (i in 1 until path.points.size) {
        linePath.lineTo(path.points[i].x, path.points[i].y)
    }

    drawPath(
        path = linePath,
        color = Color(path.color),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = path.strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

