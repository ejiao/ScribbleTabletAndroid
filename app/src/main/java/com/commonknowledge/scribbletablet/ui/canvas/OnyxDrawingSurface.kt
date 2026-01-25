package com.commonknowledge.scribbletablet.ui.canvas

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.commonknowledge.scribbletablet.data.model.PathPoint
import com.commonknowledge.scribbletablet.data.model.ToolMode
import com.commonknowledge.scribbletablet.viewmodel.CanvasViewModel

/**
 * A SurfaceView for Onyx SDK hardware-accelerated pen drawing.
 *
 * Key insight from Onyx SDK docs:
 * - setRawDrawingRenderEnabled(FALSE) = Direct e-ink hardware rendering (LOWEST latency)
 * - setRawDrawingRenderEnabled(TRUE) = SDK software rendering (higher latency)
 *
 * The SDK renders strokes directly to the e-ink display at hardware level.
 * We just receive callbacks to update our data model.
 * Our Compose rendering is secondary/backup display.
 *
 * Workflow for minimal latency:
 * 1. openRawDrawing() - initialize
 * 2. setRawDrawingRenderEnabled(false) - enable direct e-ink rendering
 * 3. setRawDrawingEnabled(true) - start accepting input
 */
class OnyxDrawingSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private var touchHelper: com.onyx.android.sdk.pen.TouchHelper? = null
    private var callback: Callback? = null
    private var isDrawingEnabled = false
    private var isInitialized = false

    interface Callback {
        fun onBeginDrawing(point: PathPoint)
        fun onDrawingMove(point: PathPoint)
        fun onEndDrawing(point: PathPoint)
        fun onBeginErasing(point: PathPoint)
        fun onErasingMove(point: PathPoint)
        fun onEndErasing(point: PathPoint)
    }

    init {
        // SurfaceView setup for Onyx SDK
        holder.addCallback(this)
        holder.setFormat(PixelFormat.TRANSPARENT)
        setZOrderOnTop(true)

        // Required for touch events
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Initialize TouchHelper when surface is ready
        if (!isInitialized && width > 0 && height > 0) {
            initTouchHelper()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Update limit rect when size changes
        touchHelper?.let { helper ->
            val rect = Rect(0, 0, width, height)
            helper.setLimitRect(rect, emptyList())
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        cleanup()
    }

    private fun initTouchHelper() {
        if (touchHelper != null) return
        if (width <= 0 || height <= 0) {
            android.util.Log.w("OnyxDrawingSurface", "Cannot init TouchHelper - invalid dimensions: ${width}x${height}")
            return
        }

        try {
            // Create handler for main thread callbacks
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

            val rawCallback = object : com.onyx.android.sdk.pen.RawInputCallback() {
                override fun onBeginRawDrawing(b: Boolean, touchPoint: com.onyx.android.sdk.data.note.TouchPoint) {
                    val point = touchPoint.toPathPoint()
                    // Use postAtFrontOfQueue for lowest latency when not on main thread
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        callback?.onBeginDrawing(point)
                    } else {
                        mainHandler.postAtFrontOfQueue { callback?.onBeginDrawing(point) }
                    }
                }

                override fun onRawDrawingTouchPointMoveReceived(touchPoint: com.onyx.android.sdk.data.note.TouchPoint) {
                    val point = touchPoint.toPathPoint()
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        callback?.onDrawingMove(point)
                    } else {
                        mainHandler.postAtFrontOfQueue { callback?.onDrawingMove(point) }
                    }
                }

                override fun onRawDrawingTouchPointListReceived(touchPointList: com.onyx.android.sdk.pen.data.TouchPointList) {
                    val count = touchPointList.size()
                    // Pre-convert all points to avoid work on main thread
                    val points = Array(count) { i -> touchPointList.get(i).toPathPoint() }

                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        points.forEach { callback?.onDrawingMove(it) }
                    } else {
                        mainHandler.postAtFrontOfQueue {
                            points.forEach { callback?.onDrawingMove(it) }
                        }
                    }
                }

                override fun onEndRawDrawing(b: Boolean, touchPoint: com.onyx.android.sdk.data.note.TouchPoint) {
                    val point = touchPoint.toPathPoint()
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        callback?.onEndDrawing(point)
                    } else {
                        mainHandler.postAtFrontOfQueue { callback?.onEndDrawing(point) }
                    }
                }

                override fun onBeginRawErasing(b: Boolean, touchPoint: com.onyx.android.sdk.data.note.TouchPoint) {
                    val point = touchPoint.toPathPoint()
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        callback?.onBeginErasing(point)
                    } else {
                        mainHandler.postAtFrontOfQueue { callback?.onBeginErasing(point) }
                    }
                }

                override fun onRawErasingTouchPointMoveReceived(touchPoint: com.onyx.android.sdk.data.note.TouchPoint) {
                    val point = touchPoint.toPathPoint()
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        callback?.onErasingMove(point)
                    } else {
                        mainHandler.postAtFrontOfQueue { callback?.onErasingMove(point) }
                    }
                }

                override fun onRawErasingTouchPointListReceived(touchPointList: com.onyx.android.sdk.pen.data.TouchPointList) {
                    val count = touchPointList.size()
                    val points = Array(count) { i -> touchPointList.get(i).toPathPoint() }
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        points.forEach { callback?.onErasingMove(it) }
                    } else {
                        mainHandler.postAtFrontOfQueue {
                            points.forEach { callback?.onErasingMove(it) }
                        }
                    }
                }

                override fun onEndRawErasing(b: Boolean, touchPoint: com.onyx.android.sdk.data.note.TouchPoint) {
                    val point = touchPoint.toPathPoint()
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        callback?.onEndErasing(point)
                    } else {
                        mainHandler.postAtFrontOfQueue { callback?.onEndErasing(point) }
                    }
                }
            }

            // Create TouchHelper following the official pattern
            val helper = com.onyx.android.sdk.pen.TouchHelper.create(this, rawCallback)

            // Configure stroke appearance (for SDK rendering if it happens)
            helper.setStrokeWidth(3.0f)
            helper.setStrokeStyle(com.onyx.android.sdk.pen.TouchHelper.STROKE_STYLE_PENCIL)
            helper.setStrokeColor(Color.BLACK)

            // Set the drawing area BEFORE opening raw drawing
            val limitRect = Rect(0, 0, width, height)
            helper.setLimitRect(limitRect, emptyList())
            android.util.Log.d("OnyxDrawingSurface", "Set limit rect: $limitRect")

            // Open raw drawing mode
            helper.openRawDrawing()

            // CRITICAL: setRawDrawingRenderEnabled(false) enables DIRECT e-ink hardware rendering
            // This is counterintuitive but documented in Onyx SDK:
            // - false = Direct e-ink rendering (LOWEST latency, hardware accelerated)
            // - true = SDK software rendering (higher latency)
            try {
                helper.setRawDrawingRenderEnabled(false)
            } catch (e: Exception) {
                // Fallback if method not available
            }

            // Enable raw drawing input
            helper.setRawDrawingEnabled(true)

            touchHelper = helper
            isDrawingEnabled = true
            isInitialized = true

            android.util.Log.d("OnyxDrawingSurface", "TouchHelper initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("OnyxDrawingSurface", "Failed to initialize TouchHelper", e)
        }
    }

    fun setStrokeColor(color: Int) {
        touchHelper?.setStrokeColor(color)
    }

    fun setStrokeWidth(width: Float) {
        touchHelper?.setStrokeWidth(width)
    }

    private fun cleanup() {
        touchHelper?.let { helper ->
            try {
                helper.setRawDrawingEnabled(false)
                helper.closeRawDrawing()
            } catch (e: Exception) {
                android.util.Log.e("OnyxDrawingSurface", "Error during cleanup", e)
            }
        }
        touchHelper = null
        isDrawingEnabled = false
        isInitialized = false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Let TouchHelper handle stylus events
        // Return true for stylus to indicate we're handling it
        // Return false for finger to let it pass through to Compose for panning
        if (event == null) return false

        val toolType = event.getToolType(0)
        return toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanup()
    }

    private fun com.onyx.android.sdk.data.note.TouchPoint.toPathPoint(): PathPoint {
        return PathPoint(this.x, this.y, this.pressure)
    }
}

/**
 * Compose wrapper for the Onyx drawing surface.
 */
@Composable
fun OnyxDrawingSurface(
    viewModel: CanvasViewModel,
    modifier: Modifier = Modifier
) {
    // Check if Onyx SDK is available
    val isOnyxDevice = remember {
        try {
            Class.forName("com.onyx.android.sdk.pen.TouchHelper")
            val manufacturer = android.os.Build.MANUFACTURER?.lowercase() ?: ""
            val brand = android.os.Build.BRAND?.lowercase() ?: ""
            val isOnyx = manufacturer.contains("onyx") || brand.contains("onyx") || brand.contains("boox")
            android.util.Log.d("OnyxDrawingSurface", "isOnyxDevice: $isOnyx (manufacturer=$manufacturer, brand=$brand)")
            isOnyx
        } catch (e: ClassNotFoundException) {
            android.util.Log.d("OnyxDrawingSurface", "Onyx SDK not available")
            false
        }
    }

    if (!isOnyxDevice) {
        return
    }

    val activeMode = viewModel.activeMode.value
    var surfaceView by remember { mutableStateOf<OnyxDrawingSurfaceView?>(null) }

    // Update stroke color when mode changes
    LaunchedEffect(activeMode, surfaceView) {
        surfaceView?.let { view ->
            val color = when (activeMode) {
                ToolMode.MAGIC_INK -> 0xFF4CAF50.toInt()
                else -> 0xFF000000.toInt()
            }
            view.setStrokeColor(color)
        }
    }

    AndroidView(
        factory = { context ->
            OnyxDrawingSurfaceView(context).also { view ->
                surfaceView = view

                view.setCallback(object : OnyxDrawingSurfaceView.Callback {
                    override fun onBeginDrawing(point: PathPoint) {
                        val mode = viewModel.activeMode.value
                        val scale = viewModel.canvasScale.value
                        val offsetX = viewModel.canvasOffsetX.value
                        val offsetY = viewModel.canvasOffsetY.value

                        val canvasX = (point.x - offsetX) / scale
                        val canvasY = (point.y - offsetY) / scale

                        when (mode) {
                            ToolMode.PERMANENT_INK, ToolMode.MAGIC_INK -> {
                                viewModel.startPath(canvasX, canvasY, point.pressure)
                            }
                            ToolMode.ERASER -> {
                                viewModel.startErase()
                                viewModel.erasePath(canvasX, canvasY)
                            }
                            else -> {}
                        }
                    }

                    override fun onDrawingMove(point: PathPoint) {
                        val mode = viewModel.activeMode.value
                        val scale = viewModel.canvasScale.value
                        val offsetX = viewModel.canvasOffsetX.value
                        val offsetY = viewModel.canvasOffsetY.value

                        val canvasX = (point.x - offsetX) / scale
                        val canvasY = (point.y - offsetY) / scale

                        when (mode) {
                            ToolMode.PERMANENT_INK, ToolMode.MAGIC_INK -> {
                                viewModel.addToPath(canvasX, canvasY, point.pressure)
                            }
                            ToolMode.ERASER -> {
                                viewModel.erasePath(canvasX, canvasY)
                            }
                            else -> {}
                        }
                    }

                    override fun onEndDrawing(point: PathPoint) {
                        val mode = viewModel.activeMode.value
                        when (mode) {
                            ToolMode.PERMANENT_INK, ToolMode.MAGIC_INK -> {
                                viewModel.endPath()
                            }
                            ToolMode.ERASER -> {
                                viewModel.endErase()
                            }
                            else -> {}
                        }
                    }

                    override fun onBeginErasing(point: PathPoint) {
                        val scale = viewModel.canvasScale.value
                        val offsetX = viewModel.canvasOffsetX.value
                        val offsetY = viewModel.canvasOffsetY.value

                        val canvasX = (point.x - offsetX) / scale
                        val canvasY = (point.y - offsetY) / scale

                        viewModel.startErase()
                        viewModel.erasePath(canvasX, canvasY)
                    }

                    override fun onErasingMove(point: PathPoint) {
                        val scale = viewModel.canvasScale.value
                        val offsetX = viewModel.canvasOffsetX.value
                        val offsetY = viewModel.canvasOffsetY.value

                        val canvasX = (point.x - offsetX) / scale
                        val canvasY = (point.y - offsetY) / scale

                        viewModel.erasePath(canvasX, canvasY)
                    }

                    override fun onEndErasing(point: PathPoint) {
                        viewModel.endErase()
                    }
                })
            }
        },
        modifier = modifier
    )

    DisposableEffect(Unit) {
        onDispose {
            // Cleanup is handled in onDetachedFromWindow
        }
    }
}
