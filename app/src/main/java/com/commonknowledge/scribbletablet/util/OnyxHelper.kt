package com.commonknowledge.scribbletablet.util

import android.os.Build
import android.view.View

/**
 * Helper for detecting Onyx/Boox e-ink devices and controlling e-ink display modes.
 *
 * The Onyx SDK has two main components:
 * 1. TouchHelper (raw pen input) - Requires system app privileges, won't work for third-party apps
 * 2. EpdController (display refresh) - Can be used by third-party apps for faster screen updates
 *
 * We use EpdController to enable DW (Direct Waveform) mode during drawing for faster e-ink refresh.
 */
object OnyxHelper {

    private var isOnyxDeviceCached: Boolean? = null
    private var epdControllerAvailable: Boolean? = null

    /**
     * Check if the current device is an Onyx/BOOX e-ink tablet.
     */
    fun isOnyxDevice(): Boolean {
        if (isOnyxDeviceCached != null) {
            return isOnyxDeviceCached!!
        }

        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        val model = Build.MODEL?.lowercase() ?: ""

        // Check for Onyx/BOOX devices
        val isOnyx = manufacturer.contains("onyx") ||
                brand.contains("onyx") ||
                brand.contains("boox") ||
                model.contains("boox") ||
                model.contains("onyx")

        // Also check if the SDK classes are available
        val sdkAvailable = try {
            Class.forName("com.onyx.android.sdk.pen.TouchHelper")
            true
        } catch (e: ClassNotFoundException) {
            false
        }

        isOnyxDeviceCached = isOnyx && sdkAvailable
        android.util.Log.d("OnyxHelper", "Device check: manufacturer=$manufacturer, brand=$brand, model=$model, isOnyx=$isOnyx, sdkAvailable=$sdkAvailable")
        return isOnyxDeviceCached!!
    }

    /**
     * Enable fast e-ink refresh mode for a view.
     * Uses HAND_WRITING_REPAINT_MODE for optimal stylus drawing performance.
     * Call this when starting to draw for lower latency screen updates.
     */
    fun enableFastRefresh(view: View) {
        if (!isOnyxDevice()) return

        try {
            val epdController = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
            val updateModeClass = Class.forName("com.onyx.android.sdk.api.device.epd.UpdateMode")

            // Try HAND_WRITING_REPAINT_MODE first (best for stylus), then DU as fallback
            val mode = try {
                updateModeClass.getField("HAND_WRITING_REPAINT_MODE").get(null)
            } catch (e: NoSuchFieldException) {
                try {
                    updateModeClass.getField("DU").get(null)
                } catch (e2: NoSuchFieldException) {
                    updateModeClass.getField("GU_FAST").get(null)
                }
            }

            // Call EpdController.setViewDefaultUpdateMode(view, mode)
            val setMethod = epdController.getMethod("setViewDefaultUpdateMode", View::class.java, updateModeClass)
            setMethod.invoke(null, view, mode)

            android.util.Log.d("OnyxHelper", "Enabled fast e-ink refresh mode: $mode")
        } catch (e: Exception) {
            android.util.Log.w("OnyxHelper", "Failed to enable fast refresh: ${e.message}")
        }
    }

    /**
     * Reset view to normal e-ink refresh mode.
     * Call this when done drawing to restore normal display quality.
     */
    fun resetRefresh(view: View) {
        if (!isOnyxDevice()) return

        try {
            val epdController = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")

            // Call EpdController.resetViewUpdateMode(view)
            val resetMethod = epdController.getMethod("resetViewUpdateMode", View::class.java)
            resetMethod.invoke(null, view)

            android.util.Log.d("OnyxHelper", "Reset e-ink refresh mode")
        } catch (e: Exception) {
            android.util.Log.w("OnyxHelper", "Failed to reset refresh: ${e.message}")
        }
    }

    /**
     * Enable system-wide fast refresh mode for drawing.
     * This affects all views and provides the fastest e-ink response.
     */
    fun enableSystemFastRefresh() {
        if (!isOnyxDevice()) return

        try {
            val epdController = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
            val updateModeClass = Class.forName("com.onyx.android.sdk.api.device.epd.UpdateMode")
            val updateSchemeClass = Class.forName("com.onyx.android.sdk.api.device.epd.UpdateScheme")

            // Try HAND_WRITING_REPAINT_MODE first, then DU as fallback
            val mode = try {
                updateModeClass.getField("HAND_WRITING_REPAINT_MODE").get(null)
            } catch (e: NoSuchFieldException) {
                updateModeClass.getField("DU").get(null)
            }
            val queueMerge = updateSchemeClass.getField("QUEUE_AND_MERGE").get(null)

            // Call setSystemUpdateModeAndScheme
            val setMethod = epdController.getMethod(
                "setSystemUpdateModeAndScheme",
                updateModeClass,
                updateSchemeClass,
                Int::class.java
            )
            setMethod.invoke(null, mode, queueMerge, Int.MAX_VALUE)

            android.util.Log.d("OnyxHelper", "Enabled system-wide fast refresh mode")
        } catch (e: Exception) {
            android.util.Log.w("OnyxHelper", "Failed to enable system fast refresh: ${e.message}")
        }
    }

    /**
     * Reset system-wide refresh mode to normal.
     */
    fun resetSystemRefresh() {
        if (!isOnyxDevice()) return

        try {
            val epdController = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")

            // Call clearSystemUpdateModeAndScheme()
            val clearMethod = epdController.getMethod("clearSystemUpdateModeAndScheme")
            clearMethod.invoke(null)

            android.util.Log.d("OnyxHelper", "Reset system-wide refresh mode")
        } catch (e: Exception) {
            android.util.Log.w("OnyxHelper", "Failed to reset system refresh: ${e.message}")
        }
    }

    /**
     * Request a partial screen refresh for a specific rectangle.
     * This can help e-ink update faster by only refreshing changed pixels.
     */
    fun refreshRect(view: View, left: Int, top: Int, right: Int, bottom: Int) {
        if (!isOnyxDevice()) return

        try {
            val epdController = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
            val updateModeClass = Class.forName("com.onyx.android.sdk.api.device.epd.UpdateMode")

            // Get the fast update mode
            val mode = try {
                updateModeClass.getField("DU").get(null)
            } catch (e: NoSuchFieldException) {
                updateModeClass.getField("GU_FAST").get(null)
            }

            // Try to find a method for partial refresh
            // Method 1: invalidate with rect
            try {
                val invalidateMethod = epdController.getMethod(
                    "invalidate",
                    View::class.java,
                    updateModeClass,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                invalidateMethod.invoke(null, view, mode, left, top, right, bottom)
                return
            } catch (e: NoSuchMethodException) {
                // Try next method
            }

            // Method 2: repaintEveryThing with rect (some SDK versions)
            try {
                val repaintMethod = epdController.getMethod(
                    "repaintEveryThing",
                    updateModeClass,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                repaintMethod.invoke(null, mode, left, top, right, bottom)
                return
            } catch (e: NoSuchMethodException) {
                // Fall back to full view refresh
            }

        } catch (e: Exception) {
            // Silently fail - partial refresh is an optimization
        }
    }

    /**
     * Request fastest possible e-ink refresh mode for drawing.
     * Tries HAND_WRITING_REPAINT_MODE first (designed for pen), then falls back to DU.
     */
    fun refreshA2(view: View) {
        if (!isOnyxDevice()) return

        try {
            val epdController = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
            val updateModeClass = Class.forName("com.onyx.android.sdk.api.device.epd.UpdateMode")

            // Try HAND_WRITING_REPAINT_MODE first (optimized for pen), then DU (fast monochrome)
            val mode = try {
                updateModeClass.getField("HAND_WRITING_REPAINT_MODE").get(null)
            } catch (e: NoSuchFieldException) {
                try {
                    updateModeClass.getField("DU").get(null)
                } catch (e2: NoSuchFieldException) {
                    updateModeClass.getField("GU_FAST").get(null)
                }
            }

            val setMethod = epdController.getMethod("setViewDefaultUpdateMode", View::class.java, updateModeClass)
            setMethod.invoke(null, view, mode)
            android.util.Log.d("OnyxHelper", "Enabled fast pen refresh mode: $mode")
        } catch (e: Exception) {
            android.util.Log.w("OnyxHelper", "Failed to enable fast refresh mode: ${e.message}")
        }
    }

    /**
     * Try to use Onyx's direct pen rendering API if available.
     * This bypasses Android's drawing pipeline for lowest latency.
     */
    fun tryDirectPenRendering(view: View, x: Float, y: Float, pressure: Float, strokeWidth: Float): Boolean {
        if (!isOnyxDevice()) return false

        try {
            // Try to find and use the direct scribble API
            val scribbleClass = Class.forName("com.onyx.android.sdk.scribble.NeoStylusScribble")

            // Check if there's a static draw method
            val drawMethod = scribbleClass.getMethod(
                "drawPoint",
                View::class.java,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )
            drawMethod.invoke(null, view, x, y, pressure, strokeWidth)
            return true
        } catch (e: ClassNotFoundException) {
            // NeoStylusScribble not available
        } catch (e: NoSuchMethodException) {
            // Method doesn't exist
        } catch (e: Exception) {
            android.util.Log.w("OnyxHelper", "Direct pen rendering failed: ${e.message}")
        }
        return false
    }
}
