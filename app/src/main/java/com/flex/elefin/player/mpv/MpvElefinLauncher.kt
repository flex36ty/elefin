package com.flex.elefin.player.mpv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.flex.elefin.jellyfin.JellyfinConfig

/**
 * Helper to launch the mpv-elefin external player.
 * 
 * mpv-elefin is a separate app optimized for Android TV that provides:
 * - YouTube TV-style controls
 * - Hardware-accelerated video decoding via MPV
 * - Automatic progress reporting to Jellyfin
 * - Resume playback support
 */
object MpvElefinLauncher {
    private const val TAG = "MpvElefinLauncher"
    private const val MPV_ELEFIN_PACKAGE = "com.flex.mpvelefin"
    private const val MPV_ELEFIN_ACTIVITY = "com.flex.mpvelefin.MpvPlayerActivity"
    
    /**
     * Check if mpv-elefin is installed.
     */
    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(MPV_ELEFIN_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Build a direct stream URL for Jellyfin.
     */
    fun buildStreamUrl(
        serverUrl: String,
        itemId: String,
        accessToken: String,
        mediaSourceId: String? = null,
        startTimeTicks: Long? = null
    ): String {
        val baseUrl = serverUrl.removeSuffix("/")
        return buildString {
            append("$baseUrl/Videos/$itemId/stream?")
            if (startTimeTicks != null && startTimeTicks > 0) {
                append("static=false")
                append("&startTimeTicks=$startTimeTicks")
            } else {
                append("static=true")
            }
            append("&api_key=$accessToken")
            append("&mediaSourceId=${mediaSourceId ?: itemId}")
            append("&enableAutoStreamCopy=true")
            append("&allowVideoStreamCopy=true")
            append("&allowAudioStreamCopy=true")
        }
    }

    /**
     * Build HTTP headers for Jellyfin authentication.
     */
    fun buildHeaders(
        accessToken: String,
        deviceId: String,
        clientName: String = "Elefin",
        version: String = "1.1.10"
    ): String = buildString {
        append("User-Agent: $clientName/MPV\r\n")
        append("Authorization: MediaBrowser Token=\"$accessToken\"\r\n")
        append("X-Emby-Authorization: MediaBrowser ")
        append("Client=\"$clientName\", Device=\"AndroidTV\", DeviceId=\"$deviceId\", ")
        append("Token=\"$accessToken\", Version=\"$version\"\r\n")
        append("Accept: */*\r\n")
    }

    /**
     * Launch mpv-elefin to play a video.
     * 
     * @param context Android context
     * @param itemId Jellyfin item ID
     * @param title Video title
     * @param resumePositionMs Resume position in milliseconds
     * @param config Jellyfin configuration
     * @return true if launched successfully, false otherwise
     */
    fun play(
        context: Context,
        itemId: String,
        title: String,
        resumePositionMs: Long = 0L,
        config: JellyfinConfig
    ): Boolean {
        if (!isInstalled(context)) {
            Log.w(TAG, "mpv-elefin is not installed")
            return false
        }

        val serverUrl = config.serverUrl.removeSuffix("/")
        val accessToken = config.accessToken ?: ""
        val userId = config.userId ?: ""
        val deviceId = config.deviceId

        // Convert resume position to ticks for server-side seeking
        val resumeTicks = if (resumePositionMs > 0) resumePositionMs * 10_000L else null

        // Build stream URL
        val url = buildStreamUrl(
            serverUrl = serverUrl,
            itemId = itemId,
            accessToken = accessToken,
            startTimeTicks = resumeTicks
        )

        // Build headers
        val headers = buildHeaders(
            accessToken = accessToken,
            deviceId = deviceId
        )

        Log.d(TAG, "Launching mpv-elefin with URL: $url")
        if (resumePositionMs > 0) {
            Log.d(TAG, "Resume position: ${resumePositionMs}ms")
        }

        return try {
            val intent = Intent().apply {
                setClassName(MPV_ELEFIN_PACKAGE, MPV_ELEFIN_ACTIVITY)
                putExtra("url", url)
                putExtra("headers", headers)
                putExtra("title", title)
                putExtra("item_id", itemId)
                putExtra("resume_ms", resumePositionMs)
                putExtra("server_url", serverUrl)
                putExtra("access_token", accessToken)
                putExtra("user_id", userId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch mpv-elefin", e)
            false
        }
    }
}

