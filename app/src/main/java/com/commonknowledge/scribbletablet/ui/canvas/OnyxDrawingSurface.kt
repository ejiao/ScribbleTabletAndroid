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
        // Called once at stroke end with all points (zero overhead during drawing)
        fun onStrokeComplete(points: List<PathPoint>)
        // Erasing still needs real-time feedback
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
        android.util.Log.d("OnyxDrawingSurface", "surfaceCreated: width=$width, height=$height, isInitialized=$isInitialized")
        // Initialize TouchHelper when surface is ready
        if (!isInitialized && width > 0 && height > 0) {
            initTouchHelper()
        } else {
            android.util.Log.w("OnyxDrawingSurface", "NOT initializing TouchHelper: isInitialized=$isInitialized, dimensions=${width}x${height}")
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

            val strokeBuffer = java.util.ArrayList<com.onyx.android.sdk.data.note.TouchPoint>(500)
            val eraseBuffer = java.util.ArrayList<com.onyx.android.sdk.data.note.TouchPoint>(500)

            val rawCallback = object : com.onyx.android.sdk.pen.RawInputCallback() {
                override fun onBeginRawDrawing(b: Boolean, touchPoint: com.onyx.android.sdk.data.note.TouchPoint) {
                    android.util.Log.d("OnyxDrawingSurface", "onBeginRawDrawing: x=${touchPoint.x}, y=${touchPoint.y}")
                    strokeBuffer.clear()
                    strokeBuffer.add(touchPoint)
                }

                override fun onRawDrawingTouchPointMoveReceived(touchPoint: com.onyx.android.sdk.data.note.TouchPoint) {
                    strokeBuffer.add(touchPoint)
                }

                override fun onRawDrawingTouchPointListReceived(touchPointList: com.onyx.android.sdk.pen.data.TouchPointList) {
                    val count = touchPointList.size()
                    for (i in 0 until count) {
                        strokeBuffer.add(touchPointList.get(i))
                    }
                }

                override fun onEndRawDrawing(b: Boolean, touchPoint: com.onyx.android.sdk.data.note.TouchPoint) {
                    strokeBuffer.add(touchPoint)
                    android.util.Log.d("OnyxDrawingSurface", "onEndRawDrawing: collected ${strokeBuffer.size} points")

                    val points = ArrayList<PathPoint>(strokeBuffer.size)
                    for (tp in strokeBuffer) {
                        points.add(PathPoint(tp.x, tp.y, tp.pressure))
                    }
                    strokeBuffer.clear()

                    mainHandler.post {
                        callback?.onStrokeComplete(points)
                    }
                }

                override fun onBeginRawErasing(b: Boolean, touchPoint: com.onyx.android.sdk.data.note.TouchPoint) {
                    eraseBuffer.clear()
                    eraseBuffer.add(touchPoint)
                    val point = PathPoint(touchPoint.x, touchPoint.y, touchPoint.pressure)
                    mainHandler.post { callback?.onBeginErasing(point) }
                }

                override fun onRawErasingTouchPointMoveReceived(touchPoint: com.onyx.android.sdk.data.note.TouchPoint) {
                    eraseBuffer.add(touchPoint)
                    val point = PathPoint(touchPoint.x, touchPoint.y, touchPoint.pressure)
                    mainHandler.post { callback?.onErasingMove(point) }
                }

                override fun onRawErasingTouchPointListReceived(touchPointList: com.onyx.android.sdk.pen.data.TouchPointList) {
                    val count = touchPointList.size()
                    for (i in 0 until count) {
                        val tp = touchPointList.get(i)
                        eraseBuffer.add(tp)
                        val point = PathPoint(tp.x, tp.y, tp.pressure)
                        mainHandler.post { callback?.onErasingMove(point) }
                    }
                }

                override fun onEndRawErasing(b: Boolean, touchPoint: com.onyx.android.sdk.data.note.TouchPoint) {
                    eraseBuffer.clear()
                    val point = PathPoint(touchPoint.x, touchPoint.y, touchPoint.pressure)
                    mainHandler.post { callback?.onEndErasing(point) }
                }
            }

            // Create TouchHelper
            val helper = com.onyx.android.sdk.pen.TouchHelper.create(this, rawCallback)

            // Configure stroke appearance - use BLUE to confirm SDK rendering
            helper.setStrokeWidth(3.0f)
            helper.setStrokeStyle(com.onyx.android.sdk.pen.TouchHelper.STROKE_STYLE_PENCIL)
            helper.setStrokeColor(Color.BLUE)

            // Set the drawing area
            val limitRect = Rect(0, 0, width, height)
            helper.setLimitRect(limitRect, emptyList())
            android.util.Log.d("OnyxDrawingSurface", "Set limit rect: $limitRect")

            // CREATIVE APPROACH: Try to disable raw input reader and use standard Android events
            // This might let us use TouchHelper for rendering without needing /dev/input access
            try {
                val setRawInputMethod = helper.javaClass.getMethod("setRawInputReaderEnable", Boolean::class.javaPrimitiveType)
                setRawInputMethod.invoke(helper, false)
                android.util.Log.d("OnyxDrawingSurface", "Disabled raw input reader - using Android events")
            } catch (e: Exception) {
                android.util.Log.w("OnyxDrawingSurface", "Could not disable raw input reader: ${e.message}")
            }

            // Open raw drawing mode
            helper.openRawDrawing()

            // Enable SDK rendering
            try {
                helper.setRawDrawingRenderEnabled(true)
                android.util.Log.d("OnyxDrawingSurface", "setRawDrawingRenderEnabled(true) - SDK will render strokes")
            } catch (e: Exception) {
                android.util.Log.e("OnyxDrawingSurface", "setRawDrawingRenderEnabled failed", e)
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

    /**
     * Feed a standard Android MotionEvent to TouchHelper.
     * This is our creative workaround - we handle input ourselves and ask SDK to render.
     */
    fun feedMotionEvent(event: MotionEvent): Boolean {
        val helper = touchHelper ?: return false

        try {
            // Try to call onTouchEvent on TouchHelper
            val onTouchMethod = helper.javaClass.getMethod("onTouchEvent", MotionEvent::class.java)
            return onTouchMethod.invoke(helper, event) as? Boolean ?: false
        } catch (e: NoSuchMethodException) {
            // Try alternative method name
            try {
                val dispatchMethod = helper.javaClass.getMethod("dispatchTouchEvent", MotionEvent::class.java)
                return dispatchMethod.invoke(helper, event) as? Boolean ?: false
            } catch (e2: Exception) {
                return false
            }
        } catch (e: Exception) {
            return false
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
        // Only consume stylus events if TouchHelper is properly initialized and working
        // Otherwise let events pass through to Compose for fallback handling
        if (event == null) return false
        if (touchHelper == null || !isInitialized) return false

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
 * Compose wrapper using AndroidView with a SurfaceView for Onyx SDK.
 * The SDK requires a SurfaceView to receive pen input and render strokes.
 */
@Composable
fun OnyxDrawingSurface(
    viewModel: CanvasViewModel,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    // Check if Onyx SDK is available
    val isOnyxDevice = remember {
        try {
            Class.forName("com.onyx.android.sdk.pen.TouchHelper")
            val manufacturer = android.os.Build.MANUFACTURER?.lowercase() ?: ""
            val brand = android.os.Build.BRAND?.lowercase() ?: ""
            val isOnyx = manufacturer.contains("onyx") || brand.contains("onyx") || brand.contains("boox")
            android.util.Log.d("OnyxDrawingSurface", "isOnyxDevice=$isOnyx (manufacturer=$manufacturer, brand=$brand)")
            isOnyx
        } catch (e: ClassNotFoundException) {
            android.util.Log.d("OnyxDrawingSurface", "Onyx SDK TouchHelper class NOT found")
            false
        }
    }

    if (!isOnyxDevice || !isVisible) {
        return
    }

    // Use AndroidView to embed our SurfaceView
    AndroidView(
        modifier = modifier,
        factory = { context ->
            OnyxDrawingSurfaceView(context).apply {
                setCallback(object : OnyxDrawingSurfaceView.Callback {
                    override fun onStrokeComplete(points: List<PathPoint>) {
                        if (points.size >= 2) {
                            val scale = viewModel.canvasScale.value
                            val offsetX = viewModel.canvasOffsetX.value
                            val offsetY = viewModel.canvasOffsetY.value
                            val mode = viewModel.activeMode.value

                            val canvasPoints = points.map { p ->
                                PathPoint(
                                    (p.x - offsetX) / scale,
                                    (p.y - offsetY) / scale,
                                    p.pressure
                                )
                            }
                            viewModel.addCompleteStroke(canvasPoints, mode)
                        }
                    }

                    override fun onBeginErasing(point: PathPoint) {
                        val scale = viewModel.canvasScale.value
                        val offsetX = viewModel.canvasOffsetX.value
                        val offsetY = viewModel.canvasOffsetY.value
                        viewModel.startErase()
                        viewModel.erasePath((point.x - offsetX) / scale, (point.y - offsetY) / scale)
                    }

                    override fun onErasingMove(point: PathPoint) {
                        val scale = viewModel.canvasScale.value
                        val offsetX = viewModel.canvasOffsetX.value
                        val offsetY = viewModel.canvasOffsetY.value
                        viewModel.erasePath((point.x - offsetX) / scale, (point.y - offsetY) / scale)
                    }

                    override fun onEndErasing(point: PathPoint) {
                        viewModel.endErase()
                    }
                })
            }
        }
    )
}
