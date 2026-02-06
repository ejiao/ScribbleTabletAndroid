package com.commonknowledge.scribbletablet

import android.app.Application
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

class ScribbleTabletApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Enable hidden API bypass for Onyx SDK on Android R+
        // This is required for the pen SDK to access low-level input APIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }
}
