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
import androidx.media3.common.util.UnstableApi
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.screens.JellyfinVideoPlayerScreen
import com.flex.elefin.screens.MPVVideoPlayerScreen

@UnstableApi
class JellyfinVideoPlayerActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_ITEM_ID = "item_id"
        private const val EXTRA_RESUME_POSITION_MS = "resume_position_ms"
        private const val EXTRA_SUBTITLE_STREAM_INDEX = "subtitle_stream_index"
        private const val EXTRA_AUDIO_STREAM_INDEX = "audio_stream_index"

        fun createIntent(
            context: Context,
            itemId: String,
            resumePositionMs: Long = 0L,
            subtitleStreamIndex: Int? = null,
            audioStreamIndex: Int? = null
        ): Intent {
            return Intent(context, JellyfinVideoPlayerActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, itemId)
                putExtra(EXTRA_RESUME_POSITION_MS, resumePositionMs)
                subtitleStreamIndex?.let { putExtra(EXTRA_SUBTITLE_STREAM_INDEX, it) }
                audioStreamIndex?.let { putExtra(EXTRA_AUDIO_STREAM_INDEX, it) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun enableHdrMode() {
        // Enable hardware acceleration for HDR output (CRITICAL - must be first)
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
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

        // Create a minimal item object (details will be fetched in the screen)
        val item = JellyfinItem(
            Id = itemId,
            Name = ""
        )

        val useMpv = settings.isMpvEnabled
        // Force MPVLib initialization by accessing it (only if MPV is enabled)
        val mpvAvailable = if (useMpv) {
            try {
                // Check MPV availability - the library should auto-detect .so files
                val available = `is`.xyz.mpv.MPVLib.isAvailable()
                android.util.Log.d("VideoPlayer", "MPV availability check: $available")
                if (!available) {
                    // If not available, log more details for debugging
                    android.util.Log.w("VideoPlayer", "MPV libraries exist but isAvailable() returned false - this may indicate a library loading issue")
                }
                available
            } catch (e: UnsatisfiedLinkError) {
                android.util.Log.e("VideoPlayer", "MPV native libraries not found (UnsatisfiedLinkError)", e)
                false
            } catch (e: NoClassDefFoundError) {
                android.util.Log.e("VideoPlayer", "MPV classes not found (NoClassDefFoundError)", e)
                false
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayer", "Error checking MPV availability", e)
                false
            }
        } else {
            false
        }
        
        android.util.Log.d("VideoPlayer", "MPV enabled: $useMpv, MPV available: $mpvAvailable")

        setContent {
            JellyfinAppTheme {
                if (useMpv && mpvAvailable) {
                    // Use MPV if explicitly enabled and available
                        MPVVideoPlayerScreen(
                            item = item,
                            apiService = apiService,
                            onBack = {
                                finish()
                            },
                            resumePositionMs = resumePositionMs,
                            subtitleStreamIndex = subtitleStreamIndex
                        )
                } else {
                    // Default to ExoPlayer (when MPV is disabled or not available)
                    if (useMpv && !mpvAvailable) {
                        android.util.Log.w("VideoPlayer", "MPV enabled but libraries not available, falling back to ExoPlayer")
                    }
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
    }

    // Removed onBackPressed - let Compose BackHandler handle it
    // This prevents duplicate finish() calls
    
    // Removed onKeyDown - let PlayerView handle key events directly
    // The PlayerView is configured to be focusable and will handle Enter/OK keys
    // Intercepting here prevents the PlayerView from receiving the events
}



