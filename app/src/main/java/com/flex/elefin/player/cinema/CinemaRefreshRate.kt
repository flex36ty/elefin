package com.flex.elefin.player.cinema

import android.app.Activity
import android.os.Build
import kotlin.math.abs
import kotlin.math.round

internal fun cinemaCadenceMatches(refresh: Double, fps: Double): Boolean {
    if (!fps.isFinite() || !refresh.isFinite() || fps < 10 || fps > 240 || refresh < fps * 0.9998) return false
    val multiple = round(refresh / fps)
    return multiple >= 1 && abs(refresh / fps - multiple) < 0.0002
}

/** Window-local request: never changes the user's global display settings or resolution. */
internal class CinemaRefreshRate(private val activity: Activity) {
    private val original = activity.window.attributes.preferredDisplayModeId
    private var applied = false
    private var attemptedFps: Double? = null

    @Suppress("DEPRECATION")
    fun match(fps: Double?) {
        if (Build.VERSION.SDK_INT < 23 || fps == null || !fps.isFinite() || fps < 10 || fps > 240) return
        if (attemptedFps?.let { abs(it - fps) < 0.01 } == true) return
        attemptedFps = fps
        runCatching {
            val display = activity.windowManager.defaultDisplay
            val current = display.mode
            if (cinemaCadenceMatches(current.refreshRate.toDouble(), fps)) return
            val target = display.supportedModes.filter {
                it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight &&
                    cinemaCadenceMatches(it.refreshRate.toDouble(), fps)
            }.minByOrNull { it.refreshRate } ?: return
            activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = target.modeId }
            applied = true
            android.util.Log.i("CinemaPlayer", "Requested display ${target.refreshRate} Hz for $fps fps")
        }.onFailure { android.util.Log.w("CinemaPlayer", "Refresh-rate matching unavailable", it) }
    }

    fun restore() {
        if (applied) {
            activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = original }
        }
        applied = false
        attemptedFps = null
    }
}
