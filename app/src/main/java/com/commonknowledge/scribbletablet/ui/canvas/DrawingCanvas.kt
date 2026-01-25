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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import android.app.Activity
import com.commonknowledge.scribbletablet.data.model.ToolMode
import com.commonknowledge.scribbletablet.util.OnyxHelper
import com.commonknowledge.scribbletablet.viewmodel.CanvasViewModel
import kotlin.math.sqrt

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

    // Check if this is an Onyx device (pen input handled by OnyxDrawingSurface overlay)
    val isOnyxDevice = remember { OnyxHelper.isOnyxDevice() }

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

                // On Onyx devices, pen input is handled by OnyxDrawingSurface overlay
                // Let the event pass through so the overlay can handle it
                if (isOnyxDevice && (isStylus || isStylusEraser)) {
                    return@pointerInteropFilter false
                }

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
        // Only render the dot grid - strokes are rendered by DrawingOverlay above cards
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(offsetX, offsetY)
                scale(scale, scale, Offset.Zero)
            }) {
                // Draw dot grid (larger for infinite canvas feel)
                val gridSize = 50f
                val dotColor = Color.LightGray.copy(alpha = 0.4f)
                val dotRadius = 2f

                // Calculate visible area
                val startX = (-offsetX / scale - 1000).toInt()
                val endX = ((-offsetX + size.width) / scale + 1000).toInt()
                val startY = (-offsetY / scale - 1000).toInt()
                val endY = ((-offsetY + size.height) / scale + 1000).toInt()

                // Draw dots at grid intersections
                var x = (startX / gridSize.toInt()) * gridSize.toInt()
                while (x < endX) {
                    var y = (startY / gridSize.toInt()) * gridSize.toInt()
                    while (y < endY) {
                        drawCircle(
                            color = dotColor,
                            radius = dotRadius,
                            center = Offset(x.toFloat(), y.toFloat())
                        )
                        y += gridSize.toInt()
                    }
                    x += gridSize.toInt()
                }
            }
        }
    }
}
