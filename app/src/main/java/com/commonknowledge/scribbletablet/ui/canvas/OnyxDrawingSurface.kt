package com.commonknowledge.scribbletablet.ui.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.viewinterop.AndroidView
import com.commonknowledge.scribbletablet.data.model.PathPoint
import com.commonknowledge.scribbletablet.data.model.ToolMode
import com.commonknowledge.scribbletablet.viewmodel.CanvasViewModel
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList

/**
 * SurfaceView for Onyx SDK hardware-accelerated pen drawing.
 * Based on the official OnyxAndroidDemo ScribbleTouchHelperDemoActivity pattern.
 */
class OnyxDrawingSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "OnyxDrawingSurface"
        private const val STROKE_WIDTH = 3.0f
    }

    private var touchHelper: TouchHelper? = null
    private var callback: Callback? = null
    private var isInitialized = false

    interface Callback {
        fun onBeginDrawing(point: PathPoint)
        fun onStrokeComplete(points: List<PathPoint>)
        fun onBeginErasing(point: PathPoint)
        fun onErasingMove(point: PathPoint)
        fun onEndErasing(point: PathPoint)
    }

    // Buffer for stroke points - cleared on each new stroke
    private val strokeBuffer = ArrayList<TouchPoint>(500)

    private val rawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint) {
            Log.d(TAG, "onBeginRawDrawing: x=${touchPoint.x}, y=${touchPoint.y}")
            strokeBuffer.clear()
            strokeBuffer.add(touchPoint)
            // Notify callback so it can capture canvas offset/scale at stroke start
            callback?.onBeginDrawing(PathPoint(touchPoint.x, touchPoint.y, touchPoint.pressure))
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint) {
            strokeBuffer.add(touchPoint)
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList) {
            val count = touchPointList.size()
            for (i in 0 until count) {
                strokeBuffer.add(touchPointList.get(i))
            }
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint) {
            strokeBuffer.add(touchPoint)
            Log.d(TAG, "onEndRawDrawing: collected ${strokeBuffer.size} points")

            // Convert to PathPoints and notify callback
            val points = strokeBuffer.map { tp ->
                PathPoint(tp.x, tp.y, tp.pressure)
            }
            strokeBuffer.clear()

            callback?.onStrokeComplete(points)
        }

        override fun onBeginRawErasing(b: Boolean, touchPoint: TouchPoint) {
            Log.d(TAG, "onBeginRawErasing")
            callback?.onBeginErasing(PathPoint(touchPoint.x, touchPoint.y, touchPoint.pressure))
        }

        override fun onRawErasingTouchPointMoveReceived(touchPoint: TouchPoint) {
            callback?.onErasingMove(PathPoint(touchPoint.x, touchPoint.y, touchPoint.pressure))
        }

        override fun onRawErasingTouchPointListReceived(touchPointList: TouchPointList) {
            val count = touchPointList.size()
            for (i in 0 until count) {
                val tp = touchPointList.get(i)
                callback?.onErasingMove(PathPoint(tp.x, tp.y, tp.pressure))
            }
        }

        override fun onEndRawErasing(b: Boolean, touchPoint: TouchPoint) {
            Log.d(TAG, "onEndRawErasing")
            callback?.onEndErasing(PathPoint(touchPoint.x, touchPoint.y, touchPoint.pressure))
        }
    }

    init {
        holder.addCallback(this)
        // Don't set focusable - we only want stylus input, not finger touches
        isFocusable = false
        isFocusableInTouchMode = false
        isClickable = false
    }

    private fun isStylus(event: MotionEvent): Boolean {
        val toolType = event.getToolType(0)
        val isStylusTool = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        val isStylusSource = event.isFromSource(InputDevice.SOURCE_STYLUS) ||
                event.isFromSource(InputDevice.SOURCE_BLUETOOTH_STYLUS)
        val result = isStylusTool || isStylusSource
        Log.d(TAG, "isStylus: toolType=$toolType, isStylusTool=$isStylusTool, isStylusSource=$isStylusSource, result=$result")
        return result
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Only dispatch stylus events - let finger touches fall through to views below
        val stylus = isStylus(event)
        Log.d(TAG, "dispatchTouchEvent: action=${event.action}, isStylus=$stylus")
        return if (stylus) {
            super.dispatchTouchEvent(event)
        } else {
            // Return false to not consume - parent should handle
            false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only consume stylus input
        val stylus = isStylus(event)
        Log.d(TAG, "onTouchEvent: action=${event.action}, isStylus=$stylus")
        return if (stylus) {
            super.onTouchEvent(event)
        } else {
            false
        }
    }

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceCreated")
        cleanSurfaceView()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged: ${width}x${height}")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed")
        cleanup()
    }

    /**
     * Initialize TouchHelper after view is laid out.
     * Must be called after the view has valid dimensions.
     */
    fun initializeTouchHelper() {
        if (isInitialized || width <= 0 || height <= 0) {
            Log.w(TAG, "Cannot initialize: isInitialized=$isInitialized, dimensions=${width}x${height}")
            return
        }

        try {
            Log.d(TAG, "Creating TouchHelper...")

            // Create TouchHelper following the official demo pattern
            val helper = TouchHelper.create(this, rawInputCallback)

            // Set drawing area
            val limitRect = Rect(0, 0, width, height)
            Log.d(TAG, "Setting limit rect: $limitRect")

            helper.setStrokeWidth(STROKE_WIDTH)
                .setLimitRect(limitRect, emptyList())
                .openRawDrawing()

            // Use fountain pen style for best results
            helper.setStrokeStyle(TouchHelper.STROKE_STYLE_FOUNTAIN)

            // Enable SDK rendering - strokes are drawn directly to e-ink
            helper.setRawDrawingRenderEnabled(true)

            // Start accepting pen input
            helper.setRawDrawingEnabled(true)

            // Enable finger touch passthrough so taps can reach the Compose UI below
            helper.enableFingerTouch(true)

            touchHelper = helper
            isInitialized = true
            Log.d(TAG, "TouchHelper initialized successfully!")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TouchHelper", e)
        }
    }

    fun setStrokeColor(color: Int) {
        touchHelper?.setStrokeColor(color)
    }

    fun setStrokeWidth(width: Float) {
        touchHelper?.setStrokeWidth(width)
    }

    fun pauseDrawing() {
        touchHelper?.setRawDrawingEnabled(false)
    }

    fun resumeDrawing() {
        touchHelper?.setRawDrawingEnabled(true)
    }

    private fun cleanSurfaceView(): Boolean {
        val canvas = holder.lockCanvas() ?: return false
        canvas.drawColor(Color.WHITE)
        holder.unlockCanvasAndPost(canvas)
        return true
    }

    private fun cleanup() {
        touchHelper?.let { helper ->
            try {
                helper.setRawDrawingEnabled(false)
                helper.closeRawDrawing()
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }
        touchHelper = null
        isInitialized = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanup()
    }
}

/**
 * Compose wrapper for OnyxDrawingSurface.
 */
@OptIn(ExperimentalComposeUiApi::class)
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
            Log.d("OnyxDrawingSurface", "isOnyxDevice=$isOnyx (manufacturer=$manufacturer, brand=$brand)")
            isOnyx
        } catch (e: ClassNotFoundException) {
            Log.d("OnyxDrawingSurface", "Onyx SDK TouchHelper class NOT found")
            false
        }
    }

    if (!isOnyxDevice || !isVisible) {
        return
    }

    // Filter to only pass stylus events to the AndroidView, letting finger touches through
    val stylusOnlyModifier = modifier.pointerInteropFilter { event ->
        val toolType = event.getToolType(0)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        // Return true to consume stylus events (pass to AndroidView), false to let finger events through
        isStylus
    }

    AndroidView(
        modifier = stylusOnlyModifier,
        factory = { context ->
            OnyxDrawingSurfaceView(context).apply {
                // Capture canvas transform at stroke start to avoid position drift during panning
                var strokeStartScale = 1f
                var strokeStartOffsetX = 0f
                var strokeStartOffsetY = 0f
                var strokeStartMode = ToolMode.PERMANENT_INK

                setCallback(object : OnyxDrawingSurfaceView.Callback {
                    override fun onBeginDrawing(point: PathPoint) {
                        // Capture canvas offset/scale immediately when stroke starts
                        strokeStartScale = viewModel.canvasScale.value
                        strokeStartOffsetX = viewModel.canvasOffsetX.value
                        strokeStartOffsetY = viewModel.canvasOffsetY.value
                        strokeStartMode = viewModel.activeMode.value
                    }

                    override fun onStrokeComplete(points: List<PathPoint>) {
                        if (points.size >= 2) {
                            // Use the offset/scale captured at stroke START, not current values
                            // This prevents position drift if user pans during stroke
                            val canvasPoints = points.map { p ->
                                PathPoint(
                                    (p.x - strokeStartOffsetX) / strokeStartScale,
                                    (p.y - strokeStartOffsetY) / strokeStartScale,
                                    p.pressure
                                )
                            }
                            viewModel.addCompleteStroke(canvasPoints, strokeStartMode)
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

                // Initialize after layout
                addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(
                        v: View, left: Int, top: Int, right: Int, bottom: Int,
                        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                    ) {
                        if (right - left > 0 && bottom - top > 0) {
                            initializeTouchHelper()
                            removeOnLayoutChangeListener(this)
                        }
                    }
                })
            }
        }
    )
}
