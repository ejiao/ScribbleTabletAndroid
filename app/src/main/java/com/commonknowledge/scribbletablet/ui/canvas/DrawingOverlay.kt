package com.commonknowledge.scribbletablet.ui.canvas

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import com.commonknowledge.scribbletablet.data.model.DrawingPath
import com.commonknowledge.scribbletablet.viewmodel.CanvasViewModel
import kotlin.random.Random

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
    val _ = viewModel.currentPathVersion.value

    // Read the snapshot state lists directly - they're already observable
    val permanentPaths = viewModel.permanentPaths
    val magicPaths = viewModel.magicPaths

    // Check if we have any magic ink to animate
    val hasMagicInk = magicPaths.isNotEmpty() || (currentPath?.isMagicInk == true)

    // Shimmer animation - always create but only use when there's magic ink
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTime"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        withTransform({
            translate(offsetX, offsetY)
            scale(scale, scale, Offset.Zero)
        }) {
            // Draw permanent paths (use fast rendering - minimal smoothing)
            permanentPaths.forEach { path ->
                drawStrokePathFast(path)
            }

            // Draw magic paths with shimmer effect
            magicPaths.forEach { path ->
                drawStrokePathFast(path)
                drawMagicInkShimmerOverlay(path, shimmerTime)
            }

            // Draw current path (the one being actively drawn)
            // Use fast line-based rendering for lower latency during active drawing
            currentPath?.let { path ->
                drawStrokePathFast(path)
                // Skip shimmer during active drawing for performance
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMagicInkShimmerOverlay(
    path: DrawingPath,
    time: Float
) {
    if (path.points.size < 4) return

    val random = Random(path.hashCode())
    val baseColor = Color(path.color)

    // Reduced shimmer count for performance
    for (shimmerIndex in 0 until 4) {
        val shimmerSeed = random.nextInt(1000)
        val shimmerPosition = ((time * 0.03f + shimmerSeed) % 100f) / 100f
        val pointIndex = (shimmerPosition * (path.points.size - 1)).toInt()
        val intensity = ((time * 0.1f + shimmerSeed) % 50f).let { phase ->
            when {
                phase < 15f -> phase / 15f
                phase < 35f -> 1f
                else -> 1f - (phase - 35f) / 15f
            }
        }.coerceIn(0f, 1f)

        val startIdx = maxOf(0, pointIndex - 2)
        val endIdx = minOf(path.points.size - 1, pointIndex + 2)

        if (endIdx > startIdx) {
            val highlightColor = Color.White.copy(alpha = 0.5f * intensity)
            drawLine(
                color = highlightColor,
                start = Offset(path.points[startIdx].x, path.points[startIdx].y),
                end = Offset(path.points[endIdx].x, path.points[endIdx].y),
                strokeWidth = path.strokeWidth * 0.5f,
                cap = StrokeCap.Round
            )
        }
    }
}
