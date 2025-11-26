package `is`.xyz.mpv

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder

class MPVView(context: Context, attrs: AttributeSet?) : BaseMPVView(context, attrs) {
    
    // Store headers to be set during initialization
    var httpHeaders: String? = null
    
    // Store FFmpeg headers to be set before playing (for video and subtitles)
    // These headers apply globally to all HTTP requests via stream-lavf-o
    var ffmpegHeaders: String? = null
    
    // Callback for playback errors (e.g., 404, network errors)
    var onPlaybackError: (() -> Unit)? = null
    
    override fun initOptions() {
        // MPV initialization options - MUST be set before init() for proper video output
        // GPU context setup (critical for video rendering and HDR)
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("gpu-api", "opengl")
        // Use gpu-next VO for better HDR support (falls back to gpu if not available)
        MPVLib.setOptionString("vo", "gpu-next")
        
        // Hardware decoding (most stable on Android TV)
        MPVLib.setOptionString("hwdec", "mediacodec-copy")
        
        // HDR support (critical for HDR10/Dolby Vision content)
        // These are set as OPTIONS (before init) - will be reinforced as PROPERTIES after attachSurface
        MPVLib.setOptionString("hdr-compute-peak", "yes")
        MPVLib.setOptionString("hdr-peak-percentile", "99") // Better peak detection
        MPVLib.setOptionString("target-colorspace-hint", "yes")
        
        // Tone mapping - use "auto" for better HDR handling
        MPVLib.setOptionString("tone-mapping", "auto")
        
        // 10-bit framebuffer format for HDR output
        // This prevents dithering to 8-bit and enables native 10-bit HDR passthrough
        MPVLib.setOptionString("fbo-format", "rgb10_a2")
        
        // Enable native HDR passthrough (critical for HDR output)
        // MUST be set as both option (before init) and property (after attachSurface)
        MPVLib.setOptionString("gpu-hdr", "yes")
        MPVLib.setOptionString("native-hdr", "yes")
        
        // GPU quality settings
        MPVLib.setOptionString("gpu-hq", "yes")
        MPVLib.setOptionString("opengl-es", "yes")
        
        // Subtitle settings
        MPVLib.setOptionString("sub-auto", "fuzzy")
        
        // Disable youtube-dl hook since we're playing direct HTTP streams, not YouTube URLs
        MPVLib.setOptionString("ytdl", "no")
        
        // HTTP headers for FFmpeg will be set via stream-lavf-o before playing
        // Format: stream-lavf-o=headers=Header1: Value1\r\nHeader2: Value2\r\n...
        
        android.util.Log.d(TAG, "✅ MPV initialized with full HDR support: vo=gpu-next, fbo-format=rgb10_a2, gpu-hdr=yes, native-hdr=yes, tone-mapping=auto")
    }

    override fun postInitOptions() {
        // Post-init options
        MPVLib.setOptionString("keep-open", "yes")
        MPVLib.setOptionString("input-vo-keyboard", "yes")
        MPVLib.setOptionString("no-input-default-bindings", "no")
    }

    override fun observeProperties() {
        // Observe important properties including VO configuration
        MPVLib.addObserver(object : MPVLib.EventObserver {
            override fun eventProperty(property: String) {
                Log.d(TAG, "Property changed: $property")
            }

            override fun eventProperty(property: String, value: Long) {
                when (property) {
                    "vo-configured" -> {
                        Log.d(TAG, "VO configured = ${value == 1L} (value=$value)")
                        if (value == 0L) {
                            Log.w(TAG, "⚠️ VO is NOT configured - video will not work!")
                        } else {
                            Log.d(TAG, "✅ VO is configured - video decoder should work")
                        }
                    }
                    else -> {
                        Log.d(TAG, "Property changed: $property = $value")
                    }
                }
            }

            override fun eventProperty(property: String, value: Boolean) {
                Log.d(TAG, "Property changed: $property = $value")
            }

            override fun eventProperty(property: String, value: String) {
                when (property) {
                    "vo" -> {
                        Log.d(TAG, "Video output changed: $value")
                    }
                    else -> {
                        Log.d(TAG, "Property changed: $property = $value")
                    }
                }
            }

            override fun eventProperty(property: String, value: Double) {
                Log.d(TAG, "Property changed: $property = $value")
            }

            override fun event(eventId: Int) {
                when (eventId) {
                    MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                        // Check if playback failed by checking if duration is 0 (file didn't load properly)
                        val duration = try {
                            MPVLib.getPropertyDouble("duration") ?: 0.0
                        } catch (e: Exception) {
                            0.0
                        }
                        val timePos = try {
                            MPVLib.getPropertyDouble("time-pos") ?: 0.0
                        } catch (e: Exception) {
                            0.0
                        }
                        
                        // If file ended immediately (duration 0 or very short playback), it's likely an error
                        if (duration == 0.0 || (duration > 0 && timePos < 1.0)) {
                            Log.w(TAG, "Playback ended prematurely (duration=$duration, position=$timePos) - likely error")
                            // Notify listener about playback failure
                            onPlaybackError?.invoke()
                        } else {
                            Log.d(TAG, "Playback ended normally")
                        }
                    }
                    MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                        Log.d(TAG, "File loaded successfully")
                    }
                    MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN -> {
                        Log.d(TAG, "MPV shutdown")
                    }
                }
            }
        })
        
        // Observe VO configuration status
        try {
            MPVLib.observeProperty("vo-configured", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            Log.d(TAG, "Started observing vo-configured property")
        } catch (e: Exception) {
            Log.w(TAG, "Could not observe vo-configured property", e)
        }
    }

    fun play(url: String, resumePosition: Double = 0.0) {
        try {
            // Set FFmpeg headers via stream-lavf-o before playing
            // This must be done before loading the file so FFmpeg uses the headers
            ffmpegHeaders?.let { headers ->
                try {
                    // Set stream-lavf-o option with headers in the format FFmpeg expects
                    // Format: stream-lavf-o=headers=Header1: Value1\r\nHeader2: Value2\r\n...
                    val optionValue = "headers=$headers"
                    MPVLib.setOptionString("stream-lavf-o", optionValue)
                    android.util.Log.d(TAG, "Set stream-lavf-o headers before playback")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error setting stream-lavf-o headers", e)
                }
            }
            
            // Set resume position before loading file
            if (resumePosition > 0) {
                MPVLib.setPropertyDouble("time-pos", resumePosition)
            }
            
            // Use playFile which will handle surface readiness
            // If surface is not ready, it will queue the URL
            playFile(url)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing file", e)
        }
    }

    fun pause() {
        try {
            MPVLib.setPropertyBoolean("pause", true)
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing", e)
        }
    }

    fun resume() {
        try {
            MPVLib.setPropertyBoolean("pause", false)
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming", e)
        }
    }

    fun seekTo(position: Double) {
        try {
            MPVLib.setPropertyDouble("time-pos", position)
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking", e)
        }
    }

    fun setSubtitleTrack(index: Int) {
        try {
            MPVLib.setPropertyInt("sid", index)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting subtitle track", e)
        }
    }

    fun setSubtitleUrl(url: String, title: String? = null) {
        try {
            // MPV's sub-add command loads external subtitle files
            // MPV uses the same HTTP client settings (stream-lavf-o headers) for subtitle fetches
            // Since we set ffmpegHeaders before play(), they apply to subtitle URLs automatically
            
            // Add subtitle file to MPV
            // Format: sub-add <url> [flags] [title]
            // The "select" flag makes MPV automatically select and display the subtitle
            // We can call this before or after playback starts - MPV will load it when ready
            val command = if (title != null) {
                // Include title for better identification in MPV's subtitle list
                arrayOf("sub-add", url, "select", title)
            } else {
                arrayOf("sub-add", url, "select")
            }
            
            MPVLib.command(command)
            Log.d(TAG, "✅ Subtitle added: $url${if (title != null) " (title: $title)" else ""}")
            
            // Verify subtitle was added by checking subtitle list
            try {
                val subCount = MPVLib.getPropertyInt("track-list/count") ?: 0
                Log.d(TAG, "Current track list count: $subCount (includes subtitle tracks)")
            } catch (e: Exception) {
                Log.w(TAG, "Could not get track list count", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding subtitle", e)
        }
    }

    fun getCurrentPosition(): Double {
        if (!initialized) {
            Log.w(TAG, "getCurrentPosition called but MPV not initialized or destroyed")
            return 0.0
        }
        return try {
            MPVLib.getPropertyDouble("time-pos") ?: 0.0
        } catch (e: Exception) {
            // Don't log errors if MPV is being destroyed - this is expected
            if (initialized) {
                Log.e(TAG, "Error getting position", e)
            }
            0.0
        }
    }

    fun getDuration(): Double {
        if (!initialized) {
            Log.w(TAG, "getDuration called but MPV not initialized or destroyed")
            return 0.0
        }
        return try {
            val duration = MPVLib.getPropertyDouble("duration")
            if (duration == null) {
                Log.w(TAG, "getDuration returned null (MPV may be destroyed)")
                return 0.0
            }
            duration
        } catch (e: Exception) {
            // Don't log if MPV is being destroyed - this is expected
            if (initialized) {
                Log.e(TAG, "Error getting duration", e)
            }
            0.0
        }
    }

    fun isPaused(): Boolean {
        if (!initialized) {
            Log.w(TAG, "isPaused called but MPV not initialized")
            return false
        }
        return try {
            MPVLib.getPropertyBoolean("pause") ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error getting pause state", e)
            false
        }
    }

    fun setAudioTrack(index: Int) {
        try {
            // Set audio track by ID (aid property)
            MPVLib.setPropertyInt("aid", index)
            Log.d(TAG, "Set audio track to: $index")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting audio track", e)
        }
    }

    fun getCurrentAudioTrack(): Int? {
        return try {
            MPVLib.getPropertyInt("aid")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current audio track", e)
            null
        }
    }

    fun getTrackListCount(): Int {
        return try {
            MPVLib.getPropertyInt("track-list/count") ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error getting track list count", e)
            0
        }
    }

    fun getTrackTitle(index: Int): String? {
        return try {
            MPVLib.getPropertyString("track-list/$index/title")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting track title for index $index", e)
            null
        }
    }

    fun getTrackType(index: Int): String? {
        return try {
            MPVLib.getPropertyString("track-list/$index/type")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting track type for index $index", e)
            null
        }
    }

    fun getTrackLang(index: Int): String? {
        return try {
            MPVLib.getPropertyString("track-list/$index/lang")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting track language for index $index", e)
            null
        }
    }

    fun getTrackId(index: Int): Int? {
        return try {
            MPVLib.getPropertyInt("track-list/$index/id")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting track ID for index $index", e)
            null
        }
    }

    fun getTrackCodec(index: Int): String? {
        return try {
            MPVLib.getPropertyString("track-list/$index/codec")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting track codec for index $index", e)
            null
        }
    }

    companion object {
        private const val TAG = "MPVView"
    }
}

