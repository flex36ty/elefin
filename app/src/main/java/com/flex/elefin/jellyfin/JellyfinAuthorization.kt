package com.flex.elefin.jellyfin

import com.flex.elefin.BuildConfig
import com.flex.elefin.ui.DeviceUtils

/** Standard Jellyfin authorization, including client identity and optional session token. */
internal fun jellyfinAuthorization(token: String, deviceId: String? = null): String {
    fun quoted(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\r", "").replace("\n", "")
    return "MediaBrowser Client=\"Elefin\", Device=\"${quoted(DeviceUtils.getDeviceName())}\", " +
        "DeviceId=\"${quoted(deviceId?.takeIf { it.isNotBlank() } ?: "elefin")}\", " +
        "Version=\"${BuildConfig.VERSION_NAME}\", Token=\"${quoted(token)}\""
}
