package com.flex.elefin

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.media3.common.util.UnstableApi
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.player.mpv.MpvTvPlayerActivity
import com.flex.elefin.player.mpv.MpvUrlBuilder
import com.flex.elefin.screens.JellyfinVideoPlayerScreen
import `is`.xyz.mpv.MPVLib

@UnstableApi
class JellyfinVideoPlayerActivity : ComponentActivity() {
    
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }
    
    companion object {
        private const val EXTRA_ITEM_ID = "item_id"
        private const val EXTRA_ITEM_NAME = "item_name"
        private const val EXTRA_RESUME_POSITION_MS = "resume_position_ms"
        private const val EXTRA_SUBTITLE_STREAM_INDEX = "subtitle_stream_index"
        private const val EXTRA_AUDIO_STREAM_INDEX = "audio_stream_index"

        fun createIntent(
            context: Context,
            itemId: String,
            resumePositionMs: Long = 0L,
            subtitleStreamIndex: Int? = null,
            audioStreamIndex: Int? = null,
            itemName: String? = null
        ): Intent {
            return Intent(context, JellyfinVideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, itemId)
                putExtra(EXTRA_RESUME_POSITION_MS, resumePositionMs)
                subtitleStreamIndex?.let { putExtra(EXTRA_SUBTITLE_STREAM_INDEX, it) }
                audioStreamIndex?.let { putExtra(EXTRA_AUDIO_STREAM_INDEX, it) }
                itemName?.let { putExtra(EXTRA_ITEM_NAME, it) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun enableHdrMode() {
        // Enable hardware acceleration for HDR output (CRITICAL - must be first)
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        // Keep screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        android.util.Log.d("VideoPlayer", "✅ Hardware acceleration enabled for HDR support")
        
        // Request HDR mode on the window (Android 13+)
        // Note: preferredHdrModes is not available in public API, but setting the Surface format
        // to RGBA_1010102 in BaseMPVView.surfaceCreated() is what actually enables HDR output.
        // The hardware acceleration flag above ensures the window can support HDR rendering.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.util.Log.d("VideoPlayer", "✅ Android 13+ detected - HDR support enabled via Surface format (RGBA_1010102)")
        }
        
        // Log HDR capabilities for debugging
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val display = displayManager.getDisplay(0)
                if (display != null) {
                    val hdrCapabilities = display.hdrCapabilities
                    if (hdrCapabilities != null) {
                        val supportedTypes = hdrCapabilities.supportedHdrTypes
                        android.util.Log.d("VideoPlayer", "✅ Display HDR capabilities: ${supportedTypes?.joinToString()}")
                        if (supportedTypes != null && supportedTypes.isNotEmpty()) {
                            android.util.Log.d("VideoPlayer", "✅ Display supports HDR types: ${supportedTypes.contentToString()}")
                        } else {
                            android.util.Log.w("VideoPlayer", "⚠️ Display does not support HDR")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("VideoPlayer", "Could not check HDR capabilities", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable HDR mode BEFORE creating MPVView
        // This ensures the window is configured for HDR output
        enableHdrMode()
        
        // Request focus so key events work on Android TV
        window.decorView.requestFocus()

        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
        val itemName = intent.getStringExtra(EXTRA_ITEM_NAME) ?: ""
        val resumePositionMs = intent.getLongExtra(EXTRA_RESUME_POSITION_MS, 0L)
        val subtitleStreamIndex = if (intent.hasExtra(EXTRA_SUBTITLE_STREAM_INDEX)) {
            intent.getIntExtra(EXTRA_SUBTITLE_STREAM_INDEX, -1).takeIf { it >= 0 }
        } else null
        val audioStreamIndex = if (intent.hasExtra(EXTRA_AUDIO_STREAM_INDEX)) {
            intent.getIntExtra(EXTRA_AUDIO_STREAM_INDEX, -1).takeIf { it >= 0 }
        } else null

        // Get Jellyfin configuration and API service
        val config = JellyfinConfig(this)
        val settings = AppSettings(this)
        val apiService = if (config.isConfigured()) {
            JellyfinApiService(
                baseUrl = config.serverUrl,
                accessToken = config.accessToken,
                userId = config.userId,
                config = config
            )
        } else {
            finish()
            return
        }

        // Check if MPV is enabled in settings
        if (settings.isMpvEnabled) {
            val serverUrl = config.serverUrl.removeSuffix("/")
            val accessToken = config.accessToken ?: ""
            
            android.util.Log.d("VideoPlayer", "MPV player enabled - launching embedded MpvTvPlayerActivity")
            
            lifecycleScope.launch {
                var finalUrl: String
                var extraSubtitleUrl: String? = null
                val headers = "X-Emby-Token: $accessToken" // Basic header needed

                // Fetch PlaybackInfo to check for transcoding needs and subtitle details
                val playbackInfo = apiService.getPlaybackInfo(
                    itemId = itemId,
                    mediaSourceId = itemId, // Assuming 1:1 for initial lookup
                    subtitleStreamIndex = subtitleStreamIndex
                )
                
                val mediaSource = playbackInfo?.MediaSources?.firstOrNull()
                val videoStream = mediaSource?.MediaStreams?.firstOrNull { it.Type == "Video" }
                val videoCodec = videoStream?.Codec?.lowercase() ?: ""
                
                // Check user transcoding settings
                val enforceTranscoding = settings.serverTranscodingEnabled && (
                    (settings.transcodeAV1 && (videoCodec.contains("av1") || videoCodec.contains("av01"))) ||
                    (settings.transcodeHEVC && (videoCodec.contains("hevc") || videoCodec.contains("h265")))
                )

                if (enforceTranscoding) {
                    android.util.Log.d("VideoPlayer", "🔄 Enforcing Transcoding (Codec: $videoCodec)")
                    // Use the TranscodingUrl from PlaybackInfo (includes burned subs if requested)
                    val transcodeUrl = mediaSource?.TranscodingUrl
                    if (transcodeUrl != null) {
                         finalUrl = if (transcodeUrl.startsWith("http")) transcodeUrl else "$serverUrl$transcodeUrl"
                         android.util.Log.d("VideoPlayer", "🔥 Using Transcoding URL (Burn-in active): $finalUrl")
                    } else {
                         android.util.Log.w("VideoPlayer", "⚠️ Transcoding enforced but no URL. Fallback to Direct.")
                         finalUrl = MpvUrlBuilder.buildStreamUrl(serverUrl, itemId, accessToken)
                    }
                } else {
                    // Direct Play Mode (Soft Subs)
                    finalUrl = MpvUrlBuilder.buildStreamUrl(serverUrl, itemId, accessToken)
                    android.util.Log.d("VideoPlayer", "▶️ Direct Play (Video Copy)")

                    // If subtitles selected, handle as Soft Subs (copy/extract)
                    if (subtitleStreamIndex != null) {
                        // Find the selected stream to get details (Codec, etc)
                        val subStream = mediaSource?.MediaStreams?.find { it.Type == "Subtitle" && it.Index == subtitleStreamIndex }
                        if (subStream != null) {
                             // Build soft subtitle URL
                             // Use standard extraction URL which MPV can stream
                             extraSubtitleUrl = apiService.buildJellyfinSubtitleUrl(
                                 itemId = itemId,
                                 mediaSourceId = itemId,
                                 streamIndex = subtitleStreamIndex,
                                 isExternal = subStream.IsExternal == true,
                                 codec = subStream.Codec,
                                 path = subStream.Path
                             )
                             android.util.Log.d("VideoPlayer", "📝 Soft Subtitle URL: $extraSubtitleUrl")
                        }
                    }
                }

                android.util.Log.d("VideoPlayer", "MPV Final URL: $finalUrl")
                
                // Prioritize local cached subtitle (if any), otherwise use remote soft-sub URL
                val cachedSubtitlePath = subtitleStreamIndex?.let { streamIndex ->
                    com.flex.elefin.player.SubtitleDownloader.getCachedSubtitle(itemId, streamIndex)
                }
                
                // Final subtitle source: Cached Local > Remote Soft Sub > None
                val subtitleSource = cachedSubtitlePath ?: extraSubtitleUrl
                if (subtitleSource != null) {
                    android.util.Log.d("VideoPlayer", "Using Subtitle Source: $subtitleSource")
                }
                
                val intent = MpvTvPlayerActivity.createIntent(
                    context = this@JellyfinVideoPlayerActivity,
                    url = finalUrl,
                    headers = headers,
                    title = itemName,
                    itemId = itemId,
                    resumePositionMs = resumePositionMs
                )
                
                // Pass selected streams
                subtitleStreamIndex?.let { intent.putExtra("subtitle_stream_index", it) }
                audioStreamIndex?.let { intent.putExtra("audio_stream_index", it) }
                
                // Pass the subtitle file/URL
                if (subtitleSource != null) {
                    intent.putExtra("subtitle_file", subtitleSource)
                }

                startActivity(intent)
                finish()
            }
            return
        }

        // Create a minimal item object (details will be fetched in the screen)
        val item = JellyfinItem(
            Id = itemId,
            Name = itemName
        )

        setContent {
            JellyfinAppTheme {
                // Use ExoPlayer with FFmpeg for comprehensive codec support
                JellyfinVideoPlayerScreen(
                    item = item,
                    apiService = apiService,
                    onBack = {
                        finish()
                    },
                    resumePositionMs = resumePositionMs,
                    subtitleStreamIndex = subtitleStreamIndex,
                    audioStreamIndex = audioStreamIndex
                )
            }
        }
    }

    // Removed onBackPressed - let Compose BackHandler handle it
    // This prevents duplicate finish() calls
    
    // Removed onKeyDown - let PlayerView handle key events directly
    // The PlayerView is configured to be focusable and will handle Enter/OK keys
    // Intercepting here prevents the PlayerView from receiving the events
}



