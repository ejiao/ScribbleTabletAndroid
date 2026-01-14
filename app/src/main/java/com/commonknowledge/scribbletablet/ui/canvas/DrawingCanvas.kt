package com.commonknowledge.scribbletablet.ui.canvas

import android.graphics.Bitmap
import android.graphics.RectF
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import com.commonknowledge.scribbletablet.data.model.DrawingPath
import com.commonknowledge.scribbletablet.data.model.ToolMode
import com.commonknowledge.scribbletablet.viewmodel.CanvasViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    viewModel: CanvasViewModel,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val view = LocalView.current

    // Set up snapshot callback
    LaunchedEffect(view) {
        viewModel.snapshotCallback = {
            try {
                val bitmap = Bitmap.createBitmap(
                    view.width,
                    view.height,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                view.draw(canvas)
                bitmap
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .onSizeChanged { size ->
                canvasSize = size
                viewModel.viewportRect.value = RectF(0f, 0f, size.width.toFloat(), size.height.toFloat())
            }
            .pointerInteropFilter { event ->
                val mode = viewModel.activeMode.value

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        when (mode) {
                            ToolMode.PERMANENT_INK, ToolMode.MAGIC_INK -> {
                                viewModel.startPath(event.x, event.y, event.pressure)
                            }
                            ToolMode.ERASER -> {
                                viewModel.erasePath(event.x, event.y)
                            }
                            ToolMode.MOVE -> {
                                // Handle selection/movement
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        when (mode) {
                            ToolMode.PERMANENT_INK, ToolMode.MAGIC_INK -> {
                                viewModel.addToPath(event.x, event.y, event.pressure)
                            }
                            ToolMode.ERASER -> {
                                viewModel.erasePath(event.x, event.y)
                            }
                            ToolMode.MOVE -> {
                                // Handle drag
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        when (mode) {
                            ToolMode.PERMANENT_INK, ToolMode.MAGIC_INK -> {
                                viewModel.endPath()
                            }
                            else -> {}
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw grid
            val gridSize = 50f
            val gridColor = Color.LightGray.copy(alpha = 0.3f)

            for (x in 0..size.width.toInt() step gridSize.toInt()) {
                drawLine(
                    color = gridColor,
                    start = Offset(x.toFloat(), 0f),
                    end = Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..size.height.toInt() step gridSize.toInt()) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }

            // Draw permanent paths
            viewModel.permanentPaths.forEach { path ->
                drawPath(path)
            }

            // Draw magic paths with shimmer effect
            viewModel.magicPaths.forEach { path ->
                drawPath(path)
            }

            // Draw current path
            viewModel.currentPath.value?.let { path ->
                drawPath(path)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPath(path: DrawingPath) {
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
