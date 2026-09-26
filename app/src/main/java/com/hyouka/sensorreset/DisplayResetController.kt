package com.hyouka.sensorreset

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

data class DisplayResetResult(
    val currentPixels: String?,
    val pixelResetStatus: String,
    val contrastResetStatus: String,
    val summary: String,
    val supported: Boolean
)

class DisplayResetController(context: Context) {
    private val displayManager =
        context.applicationContext.getSystemService(DisplayManager::class.java)

    fun inspect(): DisplayResetResult {
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val pixels = display?.mode?.let { mode ->
            mode.physicalWidth.toString() + " × " + mode.physicalHeight.toString()
        }

        return DisplayResetResult(
            currentPixels = pixels,
            pixelResetStatus = "Not available: physical resolution cannot be reset by a normal app.",
            contrastResetStatus = "Not available: Android has no public API for physical panel contrast reset.",
            summary = "No display reset was performed. Android public APIs expose display information, but they do not provide a physical pixel-resolution or panel-contrast reset. Refresh rate and display mode were not changed.",
            supported = false
        )
    }

    fun reset(): DisplayResetResult {
        return inspect().copy(
            summary = "Display reset not working: the requested physical pixel and panel-contrast reset is not exposed to third-party apps through Android public APIs."
        )
    }
}
