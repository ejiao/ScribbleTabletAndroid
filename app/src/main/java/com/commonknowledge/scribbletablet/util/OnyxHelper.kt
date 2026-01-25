package com.commonknowledge.scribbletablet.util

import android.os.Build

/**
 * Helper for detecting Onyx/Boox e-ink devices.
 */
object OnyxHelper {

    private var isOnyxDeviceCached: Boolean? = null

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
}
