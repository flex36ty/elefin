package com.flex.elefin.ui

import android.content.Context
import android.app.UiModeManager
import android.content.res.Configuration

object DeviceUtils {
    /**
     * Checks if the active device is an Android TV / Google TV.
     */
    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    /**
     * Dynamically generates the device's make and model to report to Jellyfin.
     */
    fun getDeviceName(): String {
        val manufacturer = android.os.Build.MANUFACTURER ?: "Android"
        val model = android.os.Build.MODEL ?: "Device"
        return if (model.lowercase().startsWith(manufacturer.lowercase())) {
            model
        } else {
            "$manufacturer $model"
        }
    }
}
