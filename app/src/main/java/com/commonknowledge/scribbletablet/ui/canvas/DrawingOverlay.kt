package com.commonknowledge.scribbletablet.ui.canvas

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
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

    // IMPORTANT: Read state values at composable level to trigger recomposition
    // Reading inside Canvas draw scope does NOT trigger recomposition!
    val currentPath = viewModel.currentPath.value
    val permanentPaths = viewModel.permanentPaths.toList()
    val magicPaths = viewModel.magicPaths.toList()

    // Shimmer animation for magic ink
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
            // Draw permanent paths
            permanentPaths.forEach { path ->
                drawStrokePath(path)
            }

            // Draw magic paths with shimmer effect
            magicPaths.forEach { path ->
                drawStrokePath(path)
                drawMagicInkShimmerOverlay(path, shimmerTime)
            }

            // Draw current path (the one being actively drawn)
            currentPath?.let { path ->
                drawStrokePath(path)
                if (path.isMagicInk) {
                    drawMagicInkShimmerOverlay(path, shimmerTime)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePath(path: DrawingPath) {
    if (path.points.size < 2) return

    val androidPath = Path()
    val firstPoint = path.points.first()
    androidPath.moveTo(firstPoint.x, firstPoint.y)

    for (i in 1 until path.points.size) {
        val point = path.points[i]
        val prevPoint = path.points[i - 1]

        // Use quadratic bezier for smoother lines
        val midX = (prevPoint.x + point.x) / 2
        val midY = (prevPoint.y + point.y) / 2

        androidPath.quadraticBezierTo(
            prevPoint.x,
            prevPoint.y,
            midX,
            midY
        )
    }

    // Draw the last point
    val lastPoint = path.points.last()
    androidPath.lineTo(lastPoint.x, lastPoint.y)

    drawPath(
        path = androidPath,
        color = Color(path.color),
        style = Stroke(
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
            val shimmerPath = Path()
            shimmerPath.moveTo(path.points[startIdx].x, path.points[startIdx].y)

            for (i in startIdx + 1..endIdx) {
                val point = path.points[i]
                val prevPoint = path.points[i - 1]
                val midX = (prevPoint.x + point.x) / 2
                val midY = (prevPoint.y + point.y) / 2
                shimmerPath.quadraticBezierTo(prevPoint.x, prevPoint.y, midX, midY)
            }
            shimmerPath.lineTo(path.points[endIdx].x, path.points[endIdx].y)

            val highlightColor = Color(
                red = minOf(1f, baseColor.red + 0.3f * intensity),
                green = minOf(1f, baseColor.green + 0.2f * intensity),
                blue = minOf(1f, baseColor.blue + 0.1f * intensity),
                alpha = 0.6f * intensity
            )

            drawPath(
                path = shimmerPath,
                color = highlightColor,
                style = Stroke(
                    width = path.strokeWidth * 0.6f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
