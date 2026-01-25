package com.commonknowledge.scribbletablet.ui.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.os.Build
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.commonknowledge.scribbletablet.data.model.PathPoint
import com.commonknowledge.scribbletablet.util.OnyxHelper

/**
 * A SurfaceView for ultra-low-latency real-time stroke rendering.
 *
 * SurfaceView renders on a separate surface that can be updated independently
 * of the main UI thread, providing the lowest possible latency for drawing.
 *
 * Only renders the current in-progress stroke. Completed strokes are rendered
 * by the Compose DrawingOverlay.
 */
class RealtimeStrokeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private val strokePaint = Paint().apply {
        isAntiAlias = false // Disable for speed
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
    }

    // Current stroke points (in canvas coordinates, not screen coordinates)
    @Volatile private var currentPoints: List<PathPoint>? = null
    @Volatile private var strokeColor: Int = Color.BLACK
    @Volatile private var baseStrokeWidth: Float = 3f

    // Canvas transform
    @Volatile private var canvasScale: Float = 1f
    @Volatile private var canvasOffsetX: Float = 0f
    @Volatile private var canvasOffsetY: Float = 0f

    private var surfaceReady = false

    init {
        holder.addCallback(this)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        // Enable fast e-ink refresh for this surface
        OnyxHelper.enableFastRefresh(this)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Redraw on size change
        drawStroke()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
    }

    fun setTransform(scale: Float, offsetX: Float, offsetY: Float) {
        canvasScale = scale
        canvasOffsetX = offsetX
        canvasOffsetY = offsetY
    }

    fun setStrokeStyle(color: Int, width: Float) {
        strokeColor = color
        baseStrokeWidth = width
    }

    fun updateCurrentStroke(points: List<PathPoint>?) {
        currentPoints = points
        drawStroke()
    }

    fun clearCurrentStroke() {
        currentPoints = null
        drawStroke()
    }

    /**
     * Direct point rendering - draws a single segment directly to surface.
     * This is the fastest path - minimal operations per point.
     */
    fun drawPointDirect(fromX: Float, fromY: Float, toX: Float, toY: Float, pressure: Float) {
        if (!surfaceReady) return

        val canvas: Canvas?
        try {
            canvas = holder.lockCanvas()
        } catch (e: Exception) {
            return
        }
        if (canvas == null) return

        try {
            canvas.save()
            canvas.translate(canvasOffsetX, canvasOffsetY)
            canvas.scale(canvasScale, canvasScale)

            strokePaint.color = strokeColor
            val minWidth = baseStrokeWidth * 0.3f
            val maxWidth = baseStrokeWidth * 1.5f
            strokePaint.strokeWidth = minWidth + (maxWidth - minWidth) * pressure.coerceIn(0f, 1f)

            canvas.drawLine(fromX, fromY, toX, toY, strokePaint)
            canvas.restore()
        } finally {
            try {
                holder.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                // Surface may have been destroyed
            }
        }
    }

    private fun drawStroke() {
        if (!surfaceReady) return

        val canvas: Canvas?
        try {
            canvas = holder.lockCanvas()
        } catch (e: Exception) {
            return
        }

        if (canvas == null) return

        try {
            // Clear with transparent
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            val points = currentPoints
            if (points == null || points.size < 2) {
                return
            }

            canvas.save()
            canvas.translate(canvasOffsetX, canvasOffsetY)
            canvas.scale(canvasScale, canvasScale)

            strokePaint.color = strokeColor
            val minWidth = baseStrokeWidth * 0.3f
            val maxWidth = baseStrokeWidth * 1.5f

            // Draw pressure-sensitive line segments (no predictive extension)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]

                val avgPressure = (prev.pressure + curr.pressure) / 2f
                strokePaint.strokeWidth = minWidth + (maxWidth - minWidth) * avgPressure.coerceIn(0f, 1f)

                canvas.drawLine(prev.x, prev.y, curr.x, curr.y, strokePaint)
            }

            canvas.restore()
        } finally {
            try {
                holder.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                // Surface may have been destroyed
            }
        }
    }
}
