package com.commonknowledge.scribbletablet.ui.canvas

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PixelCopy
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import android.app.Activity
import com.commonknowledge.scribbletablet.data.model.DrawingPath
import com.commonknowledge.scribbletablet.data.model.ToolMode
import com.commonknowledge.scribbletablet.viewmodel.CanvasViewModel
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.sqrt
import kotlin.random.Random

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    viewModel: CanvasViewModel,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val view = LocalView.current
    val context = LocalContext.current
    val activity = context as? Activity

    // Pan and zoom state - use ViewModel state so cards can access it
    var scale by viewModel.canvasScale
    var offsetX by viewModel.canvasOffsetX
    var offsetY by viewModel.canvasOffsetY

    // Track if we're in a multi-touch gesture
    var isDrawing by remember { mutableStateOf(false) }
    var isErasing by remember { mutableStateOf(false) }

    // For pinch-to-zoom
    var lastPinchDistance by remember { mutableStateOf(0f) }
    var lastPinchCenterX by remember { mutableStateOf(0f) }
    var lastPinchCenterY by remember { mutableStateOf(0f) }
    var pinchStartScale by remember { mutableStateOf(1f) }

    // For single finger pan
    var lastTouchX by remember { mutableStateOf(0f) }
    var lastTouchY by remember { mutableStateOf(0f) }
    var isPanning by remember { mutableStateOf(false) }

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

    // Keep references updated
    val currentView by rememberUpdatedState(view)
    val currentActivity by rememberUpdatedState(activity)

    // Set up snapshot callback using PixelCopy for reliable capture of Compose views
    SideEffect {
        viewModel.snapshotCallback = {
            try {
                val v = currentView
                val act = currentActivity
                val window = act?.window

                val width = v.width
                val height = v.height
                android.util.Log.d("DrawingCanvas", "Snapshot attempt: view=$v, window=$window, dimensions=${width}x${height}")

                if (width > 0 && height > 0 && window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Use PixelCopy for hardware-accelerated views (like Compose)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                    // Get view location on screen
                    val locationOnScreen = IntArray(2)
                    v.getLocationOnScreen(locationOnScreen)
                    val rect = Rect(
                        locationOnScreen[0],
                        locationOnScreen[1],
                        locationOnScreen[0] + width,
                        locationOnScreen[1] + height
                    )

                    // Synchronous wrapper for PixelCopy
                    var copyResult: Int = PixelCopy.ERROR_UNKNOWN
                    val latch = java.util.concurrent.CountDownLatch(1)

                    // Use a background handler thread to avoid deadlock
                    val handlerThread = android.os.HandlerThread("PixelCopyThread")
                    handlerThread.start()
                    val handler = Handler(handlerThread.looper)

                    PixelCopy.request(
                        window,
                        rect,
                        bitmap,
                        { result ->
                            copyResult = result
                            latch.countDown()
                        },
                        handler
                    )

                    // Wait for completion (with timeout)
                    val completed = latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                    handlerThread.quitSafely()

                    if (completed && copyResult == PixelCopy.SUCCESS) {
                        android.util.Log.d("DrawingCanvas", "PixelCopy success")
                        bitmap
                    } else {
                        android.util.Log.e("DrawingCanvas", "PixelCopy failed: completed=$completed, result=$copyResult")
                        // Fallback to traditional method
                        val fallbackBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(fallbackBitmap)
                        v.draw(canvas)
                        fallbackBitmap
                    }
                } else if (width > 0 && height > 0) {
                    // Fallback for older devices or missing window
                    android.util.Log.d("DrawingCanvas", "Using fallback capture method")
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    v.draw(canvas)
                    bitmap
                } else {
                    android.util.Log.e("DrawingCanvas", "Invalid view dimensions: ${width}x${height}")
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("DrawingCanvas", "Snapshot error: ${e.message}", e)
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
                val actionMasked = event.actionMasked
                val pointerCount = event.pointerCount

                // Check if this is a stylus using multiple methods
                // Method 1: Check tool type
                val toolType = event.getToolType(0)
                val isToolTypeStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS
                val isStylusEraser = toolType == MotionEvent.TOOL_TYPE_ERASER

                // Method 2: Check input source (some devices like DC-1 may use this)
                val source = event.source
                val isSourceStylus = (source and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS

                // Consider it a stylus if either method detects it
                val isStylus = isToolTypeStylus || isSourceStylus
                val isFinger = toolType == MotionEvent.TOOL_TYPE_FINGER && !isSourceStylus

                // Transform touch coordinates to canvas coordinates
                val touchX = (event.x - offsetX) / scale
                val touchY = (event.y - offsetY) / scale

                when (actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // Stylus always draws, finger pans
                        if (isStylus || isStylusEraser) {
                            isPanning = false
                            when {
                                isStylusEraser || mode == ToolMode.ERASER -> {
                                    isErasing = true
                                    isDrawing = false
                                    viewModel.startErase()
                                    viewModel.erasePath(touchX, touchY)
                                }
                                mode == ToolMode.PERMANENT_INK || mode == ToolMode.MAGIC_INK -> {
                                    isDrawing = true
                                    isErasing = false
                                    viewModel.startPath(touchX, touchY, event.pressure)
                                }
                            }
                            true
                        } else {
                            // Finger - start panning
                            isPanning = true
                            isDrawing = false
                            lastTouchX = event.x
                            lastTouchY = event.y
                            true
                        }
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        // Second finger down - cancel drawing/erasing, start pinch zoom
                        if (isDrawing) {
                            viewModel.endPath()
                            isDrawing = false
                        }
                        if (isErasing) {
                            viewModel.endErase()
                            isErasing = false
                        }
                        isPanning = false

                        if (pointerCount >= 2) {
                            // Calculate initial pinch distance and center
                            val dx = event.getX(1) - event.getX(0)
                            val dy = event.getY(1) - event.getY(0)
                            lastPinchDistance = sqrt(dx * dx + dy * dy)
                            lastPinchCenterX = (event.getX(0) + event.getX(1)) / 2
                            lastPinchCenterY = (event.getY(0) + event.getY(1)) / 2
                            pinchStartScale = scale
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        when {
                            (isStylus || isStylusEraser) && isErasing -> {
                                viewModel.erasePath(touchX, touchY)
                                true
                            }
                            (isStylus || isStylusEraser) && isDrawing -> {
                                when {
                                    mode == ToolMode.PERMANENT_INK || mode == ToolMode.MAGIC_INK -> {
                                        viewModel.addToPath(touchX, touchY, event.pressure)
                                    }
                                }
                                true
                            }
                            pointerCount >= 2 -> {
                                // Pinch to zoom
                                val dx = event.getX(1) - event.getX(0)
                                val dy = event.getY(1) - event.getY(0)
                                val newDistance = sqrt(dx * dx + dy * dy)

                                if (lastPinchDistance > 0) {
                                    val zoomFactor = newDistance / lastPinchDistance
                                    val newScale = (scale * zoomFactor).coerceIn(0.25f, 4f)

                                    // Calculate pinch center in screen coordinates
                                    val pinchCenterX = (event.getX(0) + event.getX(1)) / 2
                                    val pinchCenterY = (event.getY(0) + event.getY(1)) / 2

                                    // Calculate the point in canvas coordinates (before zoom)
                                    val canvasPointX = (pinchCenterX - offsetX) / scale
                                    val canvasPointY = (pinchCenterY - offsetY) / scale

                                    // Calculate new offset to keep pinch center stationary
                                    // newOffset = canvasPoint * newScale - pinchCenter
                                    val newOffsetX = canvasPointX * newScale - pinchCenterX
                                    val newOffsetY = canvasPointY * newScale - pinchCenterY

                                    // Also handle pan during pinch
                                    val panDeltaX = pinchCenterX - lastPinchCenterX
                                    val panDeltaY = pinchCenterY - lastPinchCenterY

                                    scale = newScale
                                    offsetX = -newOffsetX + panDeltaX
                                    offsetY = -newOffsetY + panDeltaY

                                    lastPinchCenterX = pinchCenterX
                                    lastPinchCenterY = pinchCenterY
                                }
                                lastPinchDistance = newDistance
                                true
                            }
                            isPanning && pointerCount == 1 -> {
                                // Single finger pan
                                val deltaX = event.x - lastTouchX
                                val deltaY = event.y - lastTouchY
                                offsetX += deltaX
                                offsetY += deltaY
                                lastTouchX = event.x
                                lastTouchY = event.y
                                true
                            }
                            else -> false
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDrawing) {
                            when (mode) {
                                ToolMode.PERMANENT_INK, ToolMode.MAGIC_INK -> {
                                    viewModel.endPath()
                                }
                                else -> {}
                            }
                            isDrawing = false
                        }
                        if (isErasing) {
                            viewModel.endErase()
                            isErasing = false
                        }
                        isPanning = false
                        lastPinchDistance = 0f
                        true
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        // Reset pinch state when a finger is lifted
                        if (pointerCount <= 2) {
                            lastPinchDistance = 0f
                            // If one finger remains, switch to panning
                            if (pointerCount == 2) {
                                val remainingIndex = if (event.actionIndex == 0) 1 else 0
                                lastTouchX = event.getX(remainingIndex)
                                lastTouchY = event.getY(remainingIndex)
                                isPanning = true
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(offsetX, offsetY)
                scale(scale, scale, Offset.Zero)
            }) {
                // Draw grid (larger for infinite canvas feel)
                val gridSize = 50f
                val gridColor = Color.LightGray.copy(alpha = 0.3f)

                // Calculate visible area
                val startX = (-offsetX / scale - 1000).toInt()
                val endX = ((-offsetX + size.width) / scale + 1000).toInt()
                val startY = (-offsetY / scale - 1000).toInt()
                val endY = ((-offsetY + size.height) / scale + 1000).toInt()

                // Draw vertical grid lines
                var x = (startX / gridSize.toInt()) * gridSize.toInt()
                while (x < endX) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x.toFloat(), startY.toFloat()),
                        end = Offset(x.toFloat(), endY.toFloat()),
                        strokeWidth = 1f / scale
                    )
                    x += gridSize.toInt()
                }

                // Draw horizontal grid lines
                var y = (startY / gridSize.toInt()) * gridSize.toInt()
                while (y < endY) {
                    drawLine(
                        color = gridColor,
                        start = Offset(startX.toFloat(), y.toFloat()),
                        end = Offset(endX.toFloat(), y.toFloat()),
                        strokeWidth = 1f / scale
                    )
                    y += gridSize.toInt()
                }

                // Draw permanent paths
                viewModel.permanentPaths.forEach { path ->
                    drawPath(path, scale)
                }

                // Draw magic paths with shimmer effect
                viewModel.magicPaths.forEach { path ->
                    drawPath(path, scale)
                    drawMagicInkShimmer(path, shimmerTime, scale)
                }

                // Draw current path
                viewModel.currentPath.value?.let { path ->
                    drawPath(path, scale)
                    if (path.isMagicInk) {
                        drawMagicInkShimmer(path, shimmerTime, scale)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPath(path: DrawingPath, scale: Float = 1f) {
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMagicInkShimmer(
    path: DrawingPath,
    time: Float,
    scale: Float
) {
    if (path.points.size < 2) return

    val random = Random(path.hashCode())

    // Draw shimmer segments along the path - these are bright spots that travel along the stroke
    val numShimmers = 8
    val baseColor = Color(path.color)

    for (shimmerIndex in 0 until numShimmers) {
        // Each shimmer travels along the path at different speeds/offsets
        val shimmerSeed = random.nextInt(1000)
        val speed = 0.02f + random.nextFloat() * 0.03f
        val shimmerPosition = ((time * speed + shimmerSeed) % 100f) / 100f

        // Find the point along the path for this shimmer
        val pointIndex = (shimmerPosition * (path.points.size - 1)).toInt()
            .coerceIn(0, path.points.size - 1)

        // Shimmer intensity varies with time
        val phase = ((time * 0.1f + shimmerSeed) % 50f)
        val intensity = when {
            phase < 15f -> phase / 15f
            phase < 35f -> 1f
            else -> 1f - (phase - 35f) / 15f
        }.coerceIn(0f, 1f)

        // Draw highlight as a brighter segment on top of the existing stroke
        // Use a small segment around the shimmer point
        val segmentRadius = 3 // points before and after
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

            // Bright highlight color (lighter green/yellow tint)
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
