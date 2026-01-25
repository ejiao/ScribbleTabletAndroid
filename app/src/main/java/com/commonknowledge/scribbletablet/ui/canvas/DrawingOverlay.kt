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
 * Draw path using simple line segments - fast and minimal smoothing.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePathFast(path: DrawingPath) {
    if (path.points.size < 2) return

    val color = Color(path.color)
    val strokeWidth = path.strokeWidth

    // Draw simple line segments - much faster than bezier curves
    for (i in 1 until path.points.size) {
        val prev = path.points[i - 1]
        val curr = path.points[i]
        drawLine(
            color = color,
            start = Offset(prev.x, prev.y),
            end = Offset(curr.x, curr.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMagicInkShimmerOverlay(
    path: DrawingPath,
    time: Float
) {
    if (path.points.size < 2) return

    val random = Random(path.hashCode())

    // Draw shimmer segments along the path
    val numShimmers = 8
    val baseColor = Color(path.color)

    for (shimmerIndex in 0 until numShimmers) {
        val shimmerSeed = random.nextInt(1000)
        val speed = 0.02f + random.nextFloat() * 0.03f
        val shimmerPosition = ((time * speed + shimmerSeed) % 100f) / 100f

        val pointIndex = (shimmerPosition * (path.points.size - 1)).toInt()
            .coerceIn(0, path.points.size - 1)

        val phase = ((time * 0.1f + shimmerSeed) % 50f)
        val intensity = when {
            phase < 15f -> phase / 15f
            phase < 35f -> 1f
            else -> 1f - (phase - 35f) / 15f
        }.coerceIn(0f, 1f)

        val segmentRadius = 3
        val startIdx = maxOf(0, pointIndex - segmentRadius)
        val endIdx = minOf(path.points.size - 1, pointIndex + segmentRadius)

        if (endIdx > startIdx) {
            val highlightColor = Color(
                red = minOf(1f, baseColor.red + 0.3f * intensity),
                green = minOf(1f, baseColor.green + 0.2f * intensity),
                blue = minOf(1f, baseColor.blue + 0.1f * intensity),
                alpha = 0.6f * intensity
            )

            // Draw shimmer as simple lines
            for (i in startIdx + 1..endIdx) {
                val prev = path.points[i - 1]
                val curr = path.points[i]
                drawLine(
                    color = highlightColor,
                    start = Offset(prev.x, prev.y),
                    end = Offset(curr.x, curr.y),
                    strokeWidth = path.strokeWidth * 0.6f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
