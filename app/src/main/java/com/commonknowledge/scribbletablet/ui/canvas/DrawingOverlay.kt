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
 * Draw path with light smoothing - averages adjacent points for smoother curves
 * while maintaining good performance.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePathFast(path: DrawingPath) {
    if (path.points.size < 2) return

    val linePath = Path()
    val points = path.points
    val first = points[0]
    linePath.moveTo(first.x, first.y)

    if (points.size == 2) {
        linePath.lineTo(points[1].x, points[1].y)
    } else {
        // Light smoothing: use midpoints between consecutive points
        for (i in 1 until points.size - 1) {
            val curr = points[i]
            val next = points[i + 1]
            val midX = (curr.x + next.x) / 2f
            val midY = (curr.y + next.y) / 2f
            linePath.quadraticBezierTo(curr.x, curr.y, midX, midY)
        }
        // Connect to last point
        val last = points.last()
        linePath.lineTo(last.x, last.y)
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

