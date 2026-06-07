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
}
