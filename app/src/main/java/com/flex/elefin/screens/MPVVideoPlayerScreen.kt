package com.flex.elefin.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.player.mpv.MpvUrlSelector
import com.flex.elefin.player.mpv.MPVHolder
import `is`.xyz.mpv.MPVView
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import android.media.MediaCodecList
import android.media.MediaCodecInfo
import okhttp3.OkHttpClient
import okhttp3.Request

// MPV singleton is managed by MPVHolder - no need for global state tracking here

@Composable
fun MPVVideoPlayerScreen(
    item: JellyfinItem,
    apiService: JellyfinApiService,
    onBack: () -> Unit = {},
    resumePositionMs: Long = 0L,
    subtitleStreamIndex: Int? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mediaUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var mpvViewRef by remember { mutableStateOf<MPVView?>(null) }
    var initialized by remember { mutableStateOf(false) }  // Tracks if playback is initialized
    var progressReportingJob by remember { mutableStateOf<Job?>(null) }
    var subtitleHeaders by remember { mutableStateOf<String?>(null) }
    var overlayVisible by remember { mutableStateOf(false) }
    var urlFallbackAttempt by remember { mutableStateOf(0) }
    var playbackFailed by remember { mutableStateOf(false) }
    var overlayTimeoutJob by remember { mutableStateOf<Job?>(null) }
    
    // ⭐ SUBTITLE OVERLAY WORKAROUND FOR MPV-ANDROID ⭐
    // MPV can't render subtitles on SurfaceView properly on Android TV
    // So we extract sub-text from MPV and render it ourselves in Compose
    var currentSubtitleText by remember { mutableStateOf<String?>(null) }

    // Track itemDetails for video codec detection and subtitle URL generation
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    
    // Title overlay visibility - show initially, hide after 10 seconds (like ExoPlayer)
    var titleOverlayVisible by remember { mutableStateOf(true) }
    
    // Hide title overlay after 10 seconds
    LaunchedEffect(Unit) {
        delay(10000) // 10 seconds
        titleOverlayVisible = false
    }
    
    // ⭐ Poll MPV for current subtitle text - ONLY after file is loaded!
    LaunchedEffect(MPVHolder.ready) {
        if (MPVHolder.ready) {
            Log.d("MPVPlayer", "✅ MPV ready, starting subtitle text polling")
            
            while (MPVHolder.ready) {
                try {
                    val subText = `is`.xyz.mpv.MPVLib.getPropertyString("sub-text")
                    currentSubtitleText = if (!subText.isNullOrBlank()) subText else null
                } catch (e: Exception) {
                    // Property not available - no subtitle selected or not ready yet
                    // This is normal behavior, not an error
                    currentSubtitleText = null
                }
                delay(100) // Poll 10 times per second
            }
        }
    }
    
    // Hide title overlay when controls are shown
    LaunchedEffect(overlayVisible) {
        if (overlayVisible) {
            titleOverlayVisible = false
        }
    }
    
    // Request focus for key events on Android TV
    val focusRequester = remember { FocusRequester() }
    
    // Auto-hide overlay after timeout
    val overlayTimeoutMs = 5000L
    
    fun showControlsTemporarily() {
        Log.d("MPVPlayer", "showControlsTemporarily() called")
        overlayTimeoutJob?.cancel()
        overlayVisible = true
        Log.d("MPVPlayer", "✅ Overlay visibility set to: $overlayVisible")
        overlayTimeoutJob = scope.launch {
            delay(overlayTimeoutMs)
            overlayVisible = false
            Log.d("MPVPlayer", "⏰ Overlay auto-hidden after timeout")
        }
    }
    
    /**
     * ⭐ CRITICAL FIX FOR MPV SUBTITLE BUG:
     * MPV does NOT send HTTP headers for sub-add commands (known MPV bug on all platforms).
     * 
     * Solution: Download subtitle locally with proper authentication headers,
     * then load from cache. This bypasses MPV's header bug entirely.
     * 
     * @param subtitleUrl The Jellyfin subtitle stream URL
     * @param apiKey The Jellyfin API key for authentication
     * @param deviceId The device ID for client identification
     * @return Local file path if successful, null if failed
     */
    suspend fun downloadSubtitleToLocal(
        context: android.content.Context,
        subtitleUrl: String,
        apiKey: String,
        deviceId: String
    ): File? {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(subtitleUrl)
                    .header("User-Agent", "Elefin/1.0 (MPV)")
                    .header("X-Emby-Token", apiKey)
                    .header(
                        "X-Emby-Authorization",
                        "MediaBrowser Client=\"Elefin\", Device=\"AndroidTV\", DeviceId=\"$deviceId\", Version=\"1.0.0\", Token=\"$apiKey\""
                    )
                    .header("Accept", "*/*")
                    .build()

                Log.d("MPVPlayer", "📥 Downloading subtitle from Jellyfin with auth headers...")
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    Log.e("MPVPlayer", "❌ Subtitle download failed: HTTP ${response.code}")
                    return@withContext null
                }

                // Determine file extension from URL or content-type
                val extension = when {
                    subtitleUrl.contains(".srt", ignoreCase = true) -> "srt"
                    subtitleUrl.contains(".ass", ignoreCase = true) -> "ass"
                    subtitleUrl.contains(".vtt", ignoreCase = true) -> "vtt"
                    subtitleUrl.contains(".ssa", ignoreCase = true) -> "ssa"
                    response.header("Content-Type")?.contains("subrip", ignoreCase = true) == true -> "srt"
                    response.header("Content-Type")?.contains("webvtt", ignoreCase = true) == true -> "vtt"
                    response.header("Content-Type")?.contains("ass", ignoreCase = true) == true -> "ass"
                    else -> "srt" // Default to SRT
                }

                // Save to cache with timestamp to avoid conflicts
                val file = File(context.cacheDir, "mpv_subtitle_${System.currentTimeMillis()}.$extension")
                file.outputStream().use { out ->
                    response.body?.byteStream()?.copyTo(out)
                }
                
                Log.d("MPVPlayer", "✅ Subtitle downloaded successfully: ${file.absolutePath} (${file.length()} bytes)")
                file
            } catch (e: Exception) {
                Log.e("MPVPlayer", "❌ Exception downloading subtitle: ${e.message}", e)
                null
            }
        }
    }
    
    // Fetch item details and prepare video URL
    LaunchedEffect(item.Id, apiService, subtitleStreamIndex) {
        withContext(Dispatchers.IO) {
            try {
                // Get full item details with MediaSources (includes UserData with PositionTicks for resume position)
                val details = apiService.getItemDetails(item.Id)
                itemDetails = details
                if (details != null) {
                    // Get resume position from server (UserData.PositionTicks) - will be used when initializing MPV
                    val serverResumePositionTicks = details.UserData?.PositionTicks ?: 0L
                    val serverResumePositionMs = if (serverResumePositionTicks > 0) {
                        serverResumePositionTicks / 10_000
                    } else {
                        0L
                    }
                    
                    if (serverResumePositionMs > 0) {
                        Log.d("MPVPlayer", "✅ Resume position available from server: ${serverResumePositionMs}ms (${serverResumePositionMs / 1000.0}s)")
                    } else {
                        Log.d("MPVPlayer", "No resume position from server (PositionTicks: ${serverResumePositionTicks})")
                    }
                    
                    // Get the first media source
                    val mediaSource = details.MediaSources?.firstOrNull()
                    val mediaSourceId = mediaSource?.Id

                    // Step 1: Detect HDR & HEVC
                    val videoStream = details.MediaSources?.firstOrNull()?.MediaStreams
                        ?.firstOrNull { it.Type == "Video" }
                    val videoCodec = videoStream?.Codec ?: ""
                    val width = videoStream?.Width ?: 0
                    val height = videoStream?.Height ?: 0
                    
                    // Detect HEVC/H.265
                    val isHevc = videoCodec.contains("hevc", ignoreCase = true) ||
                                 videoCodec.contains("h265", ignoreCase = true) ||
                                 videoCodec.contains("h.265", ignoreCase = true)
                    
                    // Detect HDR - check for HDR indicators in codec string or 4K resolution (common for HDR)
                    // Jellyfin may include HDR metadata in codec or we can infer from resolution
                    val isHdr = videoCodec.contains("hdr", ignoreCase = true) ||
                                videoCodec.contains("bt2020", ignoreCase = true) ||
                                videoCodec.contains("bt.2020", ignoreCase = true) ||
                                (isHevc && (width >= 3840 || height >= 2160)) // 4K HEVC is often HDR
                    
                    // Step 2: Check if device can decode HEVC
                    val deviceSupportsHevc = try {
                        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
                        codecList.codecInfos.any { codecInfo ->
                            codecInfo.supportedTypes.contains("video/hevc")
                        }
                    } catch (e: Exception) {
                        Log.w("MPVPlayer", "Error checking HEVC support, assuming supported: ${e.message}")
                        true // Assume supported if check fails
                    }
                    
                    Log.d("MPVPlayer", "Video detection: codec=$videoCodec, isHEVC=$isHevc, isHDR=$isHdr, resolution=${width}x${height}, deviceSupportsHEVC=$deviceSupportsHevc")
                    
                    val serverBaseUrl = apiService.serverBaseUrl.let { if (it.endsWith("/")) it.removeSuffix("/") else it }
                    val deviceId = apiService.getJellyfinConfig()?.deviceId ?: ""
                    
                    val selector = MpvUrlSelector(
                        server = serverBaseUrl,
                        userId = apiService.getUserId(),
                        accessToken = apiService.apiKey,
                        deviceId = deviceId
                    )
                    
                    // Step 3: ALWAYS use DIRECT PLAY for all content (HDR and SDR)
                    // MPV can direct play everything - no need for transcoding or HLS
                    // This avoids 500 errors and preserves quality
                    Log.d("MPVPlayer", "✅ Forcing DIRECT PLAY for all content (MPV handles everything)")
                    if (isHdr && isHevc && deviceSupportsHevc) {
                        Log.d("MPVPlayer", "  → HDR+HEVC+Hardware support detected")
                    } else if (isHevc && deviceSupportsHevc) {
                        Log.d("MPVPlayer", "  → HEVC+Hardware support detected")
                    } else {
                        Log.d("MPVPlayer", "  → Standard content (MPV will handle decoding)")
                    }
                    
                    val result = selector.buildDirectPlay(item.Id, mediaSourceId) // Always use static=true direct play
                    
                    mediaUrl = result.url
                    subtitleHeaders = result.headers
                    
                    Log.d("MPVPlayer", "Selected MPV URL (Direct Play): ${result.url} ${if (isHdr) "[HDR]" else "[SDR]"} ${if (isHevc) "[HEVC]" else ""}")
                    isLoading = false
                } else {
                    Log.e("MPVPlayer", "Failed to fetch item details")
                    isLoading = false
                }
            } catch (e: Exception) {
                Log.e("MPVPlayer", "Error preparing video", e)
                isLoading = false
            }
        }
    }

    // Start playback when media URL is ready
    // ⭐ CRITICAL: Track if MPV has been configured to prevent reconfig loop
    var mpvConfigured by remember { mutableStateOf(false) }
    
    LaunchedEffect(mediaUrl, mpvViewRef, itemDetails) {
        if (mediaUrl != null && mpvViewRef != null) {
            withContext(Dispatchers.Main) {
                try {
                    val mpvView = mpvViewRef ?: return@withContext
                    
                    // ⭐ CRITICAL: Only configure MPV ONCE per session
                    // Re-configuring causes VO reset and MPV discards loadfile commands
                    if (mpvConfigured) {
                        Log.d("MPVPlayer", "MPV already configured, skipping reconfig (prevents VO reset)")
                        return@withContext
                    }
                    
                    Log.d("MPVPlayer", "Configuring MPV for playback (ONCE)")
                    
                    // ⭐ CRITICAL: Configure MPV for subtitle visibility on Android TV
                    // This is the DEFINITIVE configuration for NVIDIA Shield, Chromecast, Fire TV
                    // Ensures subtitles ALWAYS render in the GPU layer with video
                    try {
                        // ----- VIDEO OUTPUT (CRITICAL FOR SUBTITLE VISIBILITY) -----
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "vo", "gpu"))
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "gpu-api", "opengl"))
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "gpu-context", "android"))
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "opengl-es", "yes"))
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "profile", "gpu-hq"))
                        
                        // ----- CRITICAL: FORCE SUBTITLE BLENDING IN GPU LAYER -----
                        // This ensures subtitles are composited WITH the video, not on a separate plane
                        // Without this, subtitles render but remain invisible on Android TV
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "blend-subtitles", "video"))
                        
                        // ----- HARDWARE DECODING -----
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "hwdec", "mediacodec-copy"))
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "hwdec-preload", "yes"))
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "hwdec-codecs", "all"))
                        
                        // ----- TEXT SUBTITLE SETTINGS (SRT, ASS, VTT) -----
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "sub-scale-by-window", "yes"))
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "sub-ass", "yes"))
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "sub-use-margins", "yes"))
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "sub-pos", "95"))
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "sub-ass-vsfilter-aspect-compat", "no"))
                        
                        // ----- SUBTITLE CUSTOMIZATION FROM SETTINGS -----
                        val settings = AppSettings(context)
                        
                        // Text size (30-100, default 55)
                        val textSize = settings.subtitleTextSize
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "sub-font-size", textSize.toString()))
                        
                        // Text color (ARGB format)
                        val textColor = settings.subtitleTextColor
                        val textColorHex = String.format("#%08X", textColor)
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "sub-color", textColorHex))
                        
                        // Background color and transparency
                        if (settings.subtitleBgTransparent) {
                            // Transparent background
                            `is`.xyz.mpv.MPVLib.command(arrayOf("set", "sub-back-color", "#00000000"))
                        } else {
                            // Opaque background with custom color
                            val bgColor = settings.subtitleBgColor
                            val bgColorHex = String.format("#%08X", bgColor)
                            `is`.xyz.mpv.MPVLib.command(arrayOf("set", "sub-back-color", bgColorHex))
                        }
                        
                        Log.d("MPVPlayer", "✅ Applied subtitle customization: size=$textSize, textColor=$textColorHex, bgTransparent=${settings.subtitleBgTransparent}")
                        
                        // ----- PGS/SUP/BITMAP SUBTITLE FIX (CRITICAL FOR BLU-RAY) -----
                        // PGS subtitles are bitmap images from Blu-ray discs
                        // Without these settings, they render off-screen or at wrong scale
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "stretch-image-subs-to-screen", "yes"))
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "image-subs-video-resolution", "no"))
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "sub-scale", "2.4"))
                        `is`.xyz.mpv.MPVLib.command(arrayOf("set", "sub-ass-force-style", "PlayResY=1080,PlayResX=1920"))
                        
                        Log.d("MPVPlayer", "✅ MPV configured for Android TV (Shield-optimized + PGS fix)")
                        
                        // Mark as configured to prevent reconfig loop
                        mpvConfigured = true
                        
                        // ⭐ REMOVED: Don't query current-vo before file-loaded!
                        // Will verify rendering mode after file-loaded event in the coroutine below
                        Log.d("MPVPlayer", "✅ MPV configured ONCE, will not reconfigure (prevents VO reset)")
                    } catch (e: Exception) {
                        Log.e("MPVPlayer", "❌ Error configuring MPV: ${e.message}", e)
                    }

                    // Use headers from MpvUrlSelector result (includes all required headers)
                    // Add X-Emby-Client-Capabilities header to tell Jellyfin what codecs MPV supports
                    val clientCapabilities = "PlayableMediaTypes=Audio,Video;SupportsH264=true;SupportsHevc=true;SupportsAv1=true;SupportsMkv=true;SupportsAac=true;SupportsOpus=true;SupportsAc3=true;SupportsEac3=true;SupportsTrueHD=true;SupportsDts=true;SupportsDca=true;SupportsDtshd=true;MaxStreamingBitrate=140000000;MaxStaticBitrate=140000000"
                    
                    // Combine headers from selector with client capabilities
                    val ffmpegHeaders = buildString {
                        append(subtitleHeaders ?: "")
                        append("X-Emby-Client-Capabilities: $clientCapabilities\r\n")
                    }
                    
                    // Store headers in MPVView for BOTH video AND subtitle requests
                    // ffmpegHeaders: Used for stream-lavf-o (video playback)
                    // httpHeaders: Used for http-header-fields (subtitle requests via sub-add)
                    mpvView.ffmpegHeaders = ffmpegHeaders
                    mpvView.httpHeaders = ffmpegHeaders // ⭐ CRITICAL: Also set httpHeaders for subtitles!
                    Log.d("MPVPlayer", "Stored FFmpeg headers in MPVView for video AND subtitle requests")
                    Log.d("MPVPlayer", "Headers: $ffmpegHeaders")
                    
                    // ⚠️ DISABLED: Subtitle-related MPV properties removed
                    // Subtitle loading breaks playback - will be re-enabled when we implement
                    // subtitle pre-download to local cache
                    Log.d("MPVPlayer", "MPV subtitle loading disabled to prevent playback issues")
                    
                    // Get effective resume position from itemDetails (fetched from server)
                    val effectiveResumePositionMs = itemDetails?.UserData?.PositionTicks?.let { ticks ->
                        if (ticks > 0) ticks / 10_000 else 0L
                    } ?: resumePositionMs
                    
                    // Convert resume position from ms to seconds
                    val resumePositionSeconds = if (effectiveResumePositionMs > 0) {
                        effectiveResumePositionMs / 1000.0
                    } else {
                        0.0
                    }

                    // ⭐ CRITICAL: Reset for new video to prevent double-load crash
                    MPVHolder.prepareForNewVideo()
                    
                    // Play the video first - BaseMPVView.playFile() will handle waiting for surface
                    // It stores the filePath and plays it when surface is ready
                    if (resumePositionSeconds > 0) {
                        Log.d("MPVPlayer", "✅ Starting playback with resume position from server: ${resumePositionSeconds}s (${effectiveResumePositionMs}ms) - BaseMPVView will handle surface attachment")
                    } else {
                        Log.d("MPVPlayer", "Starting playback from beginning - BaseMPVView will handle surface attachment")
                    }
                    // Set up error callback for automatic fallback
                    val mediaSourceForFallback = itemDetails?.MediaSources?.firstOrNull()
                    val mediaSourceIdForFallback = mediaSourceForFallback?.Id ?: item.Id
                    
                    mpvView.onPlaybackError = {
                        scope.launch(Dispatchers.IO) {
                            Log.w("MPVPlayer", "Playback error detected, attempting fallback")
                            val serverBaseUrl = apiService.serverBaseUrl.let { if (it.endsWith("/")) it.removeSuffix("/") else it }
                            val deviceId = apiService.getJellyfinConfig()?.deviceId ?: ""
                            
                            val selector = MpvUrlSelector(
                                server = serverBaseUrl,
                                userId = apiService.getUserId(),
                                accessToken = apiService.apiKey,
                                deviceId = deviceId
                            )
                            
                            // Always avoid HLS/transcoding - MPV can direct play everything
                            // Fallback order: DirectPlay Stream -> DirectPlay Original (NEVER HLS/Transcode)
                            Log.w("MPVPlayer", "Playback error - trying direct play fallbacks (avoiding HLS/transcoding)")
                            val fallbackStrategies = listOf(
                                { selector.buildDirectPlay(item.Id, mediaSourceIdForFallback) },
                                { selector.buildDirectPlayOriginal(item.Id, mediaSourceIdForFallback) }
                                // No HLS or transcode fallback - MPV should handle direct play
                            )
                            
                            if (urlFallbackAttempt + 1 < fallbackStrategies.size) {
                                val nextAttempt = urlFallbackAttempt + 1
                                Log.w("MPVPlayer", "Playback failed, trying fallback ${nextAttempt + 1}/${fallbackStrategies.size}")
                                
                                val urlBuilder = fallbackStrategies[nextAttempt]
                                val result = urlBuilder()
                                
                                withContext(Dispatchers.Main) {
                                    urlFallbackAttempt = nextAttempt
                                    mediaUrl = result.url
                                    subtitleHeaders = result.headers
                                    
                                    mpvViewRef?.let { view ->
                                        // ⭐ CRITICAL: Reset for fallback URL attempt
                                        view.resetForNextVideo()
                                        MPVHolder.prepareForNewVideo()
                                        
                                        view.ffmpegHeaders = subtitleHeaders ?: ""
                                        view.httpHeaders = subtitleHeaders ?: "" // ⭐ Also set for subtitles!
                                        view.play(result.url, 0.0) // Start from beginning on fallback
                                        Log.d("MPVPlayer", "✅ Reset hasLoadedOnce flag and retrying with fallback URL: ${result.url}")
                                    }
                                }
                            } else {
                                Log.e("MPVPlayer", "All URL strategies failed, cannot play media")
                                withContext(Dispatchers.Main) {
                                    playbackFailed = true
                                }
                            }
                        }
                    }
                    
                    // Start playback with resume position (if resumable)
                    mpvView.play(mediaUrl!!, resumePositionSeconds)
                    if (resumePositionSeconds > 0) {
                        Log.d("MPVPlayer", "✅ Resuming playback from ${resumePositionSeconds}s")
                    }
                    
                    // Post-playback verification will happen AFTER file-loaded event in the subtitle loading coroutine
                    
                    // ⭐ CRITICAL: Wait for MPV file-loaded event (event-based, not polling!)
                    // Accessing MPV properties before file-loaded causes SIGSEGV crashes
                    scope.launch {
                        Log.d("MPVPlayer", "Waiting for MPV file-loaded event (MPVHolder.ready)...")
                        
                        var waitAttempts = 0
                        while (!MPVHolder.ready && waitAttempts < 20) { // Wait up to 10 seconds
                            delay(500)
                            waitAttempts++
                            if (waitAttempts % 4 == 0) {
                                Log.d("MPVPlayer", "Still waiting for file-loaded event... (${waitAttempts * 500}ms)")
                            }
                        }
                        
                        if (!MPVHolder.ready) {
                            Log.e("MPVPlayer", "❌ MPV never became ready - playback failed")
                            return@launch
                        }
                        
                        Log.d("MPVPlayer", "✅ MPV ready (file-loaded event received), safe to query properties and load subtitles")
                        
                        // Verify rendering mode now that MPV is ready
                        try {
                            val voActual = `is`.xyz.mpv.MPVLib.getPropertyString("vo")
                            val currentVo = `is`.xyz.mpv.MPVLib.getPropertyString("current-vo")
                            val hwdecActual = `is`.xyz.mpv.MPVLib.getPropertyString("hwdec")
                            
                            Log.d("MPVPlayer", "🔍 RENDERING MODE (after file-loaded):")
                            Log.d("MPVPlayer", "   vo = $voActual, current-vo = $currentVo, hwdec = $hwdecActual")
                            
                            if (currentVo == "gpu" || currentVo == "gpu-next") {
                                Log.d("MPVPlayer", "✅ GPU rendering confirmed - subtitles will be visible")
                            } else {
                                Log.w("MPVPlayer", "⚠️ current-vo = $currentVo (expected: gpu or gpu-next)")
                            }
                        } catch (e: Exception) {
                            Log.w("MPVPlayer", "Could not verify rendering mode: ${e.message}")
                        }

                        // ------------------------------
                        // ⭐ SUBTITLE LOADING ⭐
                        // ------------------------------
                        val mediaSource = itemDetails?.MediaSources?.firstOrNull()
                        val mediaSourceId = mediaSource?.Id ?: item.Id

                        val subtitleStreams = mediaSource?.MediaStreams
                            ?.filter { it.Type == "Subtitle" }
                            ?.sortedBy { it.Index ?: 0 }
                            ?: emptyList()

                        Log.d("MPVPlayer", "Found ${subtitleStreams.size} subtitle stream(s)")
                        Log.d("MPVPlayer", "MediaSourceId = $mediaSourceId")

                        // Determine which subtitle to load
                        val settings = AppSettings(context)
                        val selectedIndex = subtitleStreamIndex ?: settings.getSubtitlePreference(item.Id)
                        val selectedStream = subtitleStreams.find { it.Index == selectedIndex }

                        if (selectedStream != null) {
                            Log.d("MPVPlayer", "➡ Selected subtitle: ${selectedStream.DisplayTitle} (Index=${selectedStream.Index})")
                            Log.d("MPVPlayer", "   IsExternal=${selectedStream.IsExternal}")
                            Log.d("MPVPlayer", "   SupportsExternalStream=${selectedStream.SupportsExternalStream}")
                            
                            mpvViewRef?.let { mpv ->
                                // ⭐ Download subtitle to local cache, then load via file:// URL
                                // This bypasses MPV's http-header-fields bug
                                scope.launch {
                                    try {
                                        val localPath = com.flex.elefin.player.mpv.MPVSubtitleDownloader.downloadSubtitle(
                                            context = context,
                                            apiService = apiService,
                                            itemId = item.Id,
                                            mediaSourceId = mediaSourceId,
                                            stream = selectedStream
                                        )
                                        
                                        if (localPath != null) {
                                            withContext(Dispatchers.Main) {
                                                Log.d("MPVPlayer", "✅ Loading subtitle from local file: $localPath")
                                                MPVLib.command(arrayOf("sub-add", localPath, "select", selectedStream.DisplayTitle ?: "Subtitle"))
                                            }
                                        } else {
                                            Log.e("MPVPlayer", "❌ Failed to download subtitle, disabling")
                                            withContext(Dispatchers.Main) {
                                                MPVLib.command(arrayOf("sub-select", "no"))
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MPVPlayer", "Error loading subtitle", e)
                                        withContext(Dispatchers.Main) {
                                            MPVLib.command(arrayOf("sub-select", "no"))
                                        }
                                    }
                                }
                            }
                        } else {
                            Log.d("MPVPlayer", "No subtitle selected (selectedIndex=$selectedIndex)")
                        }
                    }
                    
                    initialized = true
                    playbackFailed = false
                    Log.d("MPVPlayer", "MPV player initialized and playback started")
                } catch (e: Exception) {
                    Log.e("MPVPlayer", "Error initializing MPV player", e)
                }
            }
        }
    }

    // Report playback progress periodically when initialized
    LaunchedEffect(initialized, mpvViewRef) {
        if (initialized && mpvViewRef != null) {
            progressReportingJob?.cancel()
            progressReportingJob = scope.launch(Dispatchers.IO) {
                while (initialized && mpvViewRef != null) {
                    delay(5000) // Report every 5 seconds
                    try {
                        // Check if MPV is still initialized before calling methods
                        if (mpvViewRef == null) break
                        val currentPositionSeconds = mpvViewRef?.getCurrentPosition() ?: 0.0
                        val isPaused = mpvViewRef?.isPaused() ?: false
                        // Report progress if position is valid (even when paused, so Jellyfin saves the position)
                        if (currentPositionSeconds > 0) {
                            // Convert seconds to ticks: 1 second = 10,000,000 ticks
                            val positionTicks = (currentPositionSeconds * 10_000_000).toLong()
                            apiService.reportPlaybackProgress(
                                itemId = item.Id,
                                positionTicks = positionTicks,
                                isPaused = isPaused
                            )
                            Log.d("MPVPlayer", "Reported playback progress: ${currentPositionSeconds}s (paused: $isPaused)")
                        }
                    } catch (e: Exception) {
                        Log.w("MPVPlayer", "Error reporting playback progress", e)
                        // If we get an error, MPV might be destroyed, break the loop
                        break
                    }
                }
            }
        } else {
            progressReportingJob?.cancel()
            progressReportingJob = null
        }
    }

    // Cleanup MPV on dispose
    DisposableEffect(Unit) {
        onDispose {
            Log.d("MPVPlayer", "Releasing MPV player")
            progressReportingJob?.cancel()
            progressReportingJob = null
            overlayTimeoutJob?.cancel()
            
            // ⭐ CRITICAL: MPV singleton - just stop playback, DON'T destroy!
            val itemId = item.Id
            
            // Get position BEFORE stopping
            var currentPositionSeconds = 0.0
            var duration = 0.0
            try {
                mpvViewRef?.let { view ->
                    currentPositionSeconds = view.getCurrentPosition() ?: 0.0
                    duration = view.getDuration() ?: 0.0
                    Log.d("MPVPlayer", "Got position before stopping: $currentPositionSeconds, duration: $duration")
                }
            } catch (e: Exception) {
                Log.w("MPVPlayer", "Error getting position", e)
            }
            
            // ⭐ CRITICAL: DO NOT call stop or destroy - use loadfile replace for next video!
            // Stopping playback here breaks the VO pipeline and causes crashes on next load
            // Just pause playback to reduce resource usage
            try {
                `is`.xyz.mpv.MPVLib.command(arrayOf("set", "pause", "yes"))
                MPVHolder.prepareForNewVideo()  // Reset flags only, no stop/destroy
                Log.d("MPVPlayer", "MPV playback paused (singleton retained, VO pipeline preserved)")
            } catch (e: Exception) {
                Log.w("MPVPlayer", "Error pausing playback", e)
            }
            
            // Report final position (on background thread)
            if (currentPositionSeconds > 0) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val positionTicks = (currentPositionSeconds * 10_000_000).toLong()
                        apiService.reportPlaybackStopped(itemId, positionTicks)
                        
                        // Only mark as watched if video was actually completed (watched at least 90% or within last 5 seconds)
                        val isComplete = duration > 0 && (
                            currentPositionSeconds >= duration - 5 || // Within last 5 seconds
                            currentPositionSeconds >= duration * 0.90 // Or watched 90% of video
                        )
                        if (isComplete) {
                            apiService.markAsWatched(itemId)
                            Log.d("MPVPlayer", "Marked item as watched (completed ${(currentPositionSeconds * 100 / duration).toInt()}%)")
                        } else {
                            Log.d("MPVPlayer", "Playback stopped early (${(currentPositionSeconds * 100 / duration).toInt()}%), not marking as watched")
                        }
                        Log.d("MPVPlayer", "Reported final playback position on dispose")
                    } catch (e: Exception) {
                        Log.w("MPVPlayer", "Error reporting final playback position", e)
                    }
                }
            }
        }
    }

    // Request focus when initialized to capture key events
    LaunchedEffect(initialized) {
        if (initialized) {
            delay(500) // Small delay to ensure view is ready
            focusRequester.requestFocus()
            Log.d("MPVPlayer", "Focus requested for key events")
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Handle key events for showing overlay controls (TV app pattern - same as ExoPlayer)
                val nativeKeyCode = event.nativeKeyEvent?.keyCode
                
                // Handle both KeyDown and KeyUp events
                // For TV remotes, KeyUp is more reliable for button presses
                if (event.type == KeyEventType.KeyUp) {
                    // Check by native key code first (more reliable for TV remotes)
                    val isEnterKey = nativeKeyCode == 23 || // KEYCODE_DPAD_CENTER
                                    nativeKeyCode == 66 || // KEYCODE_ENTER
                                    event.key == Key.DirectionCenter ||
                                    event.key == Key.Enter ||
                                    event.key == Key.NumPadEnter
                    
                    if (isEnterKey) {
                        if (overlayVisible) {
                            // ⚠️ FIX: Don't consume Enter/OK when overlay is visible!
                            // Let the overlay buttons handle the click event
                            // The overlay's IconButtons have their own onClick handlers
                            Log.d("MPVPlayer", "Enter/OK pressed with overlay visible - letting overlay handle it")
                            return@onPreviewKeyEvent false  // DON'T consume - let overlay buttons handle it!
                        } else {
                            // If overlay is hidden, show it temporarily
                            showControlsTemporarily()
                            Log.d("MPVPlayer", "Enter/OK pressed - showing controls")
                            return@onPreviewKeyEvent true  // consume event
                        }
                    }
                    
                    // When overlay is visible, let directional keys pass through to overlay buttons
                    // But don't intercept them - let overlay handle navigation
                    // IMPORTANT: Reset timeout on ANY key press to keep controls visible during navigation
                    if (overlayVisible) {
                        val isDirectionalKey = event.key == Key.DirectionUp ||
                                               event.key == Key.DirectionDown ||
                                               event.key == Key.DirectionLeft ||
                                               event.key == Key.DirectionRight ||
                                               nativeKeyCode == 19 || // KEYCODE_DPAD_UP
                                               nativeKeyCode == 20 || // KEYCODE_DPAD_DOWN
                                               nativeKeyCode == 21 || // KEYCODE_DPAD_LEFT
                                               nativeKeyCode == 22    // KEYCODE_DPAD_RIGHT
                        
                        if (isDirectionalKey) {
                            // ⭐ CRITICAL FIX: Reset timeout when user navigates controls!
                            // This prevents controls from disappearing while user is actively using them
                            overlayTimeoutJob?.cancel()
                            overlayTimeoutJob = scope.launch {
                                delay(overlayTimeoutMs)
                                overlayVisible = false
                                Log.d("MPVPlayer", "⏰ Overlay auto-hidden after timeout")
                            }
                            
                            // Don't consume - let overlay handle navigation
                            return@onPreviewKeyEvent false
                        }
                    }
                    
                    // Handle left/right for seeking (like ExoPlayer) - only when overlay is hidden
                    if (!overlayVisible) {
                        val isLeftKey = event.key == Key.DirectionLeft || nativeKeyCode == 21 // KEYCODE_DPAD_LEFT
                        val isRightKey = event.key == Key.DirectionRight || nativeKeyCode == 22 // KEYCODE_DPAD_RIGHT
                        
                        if (isLeftKey) {
                            // Seek backward 15 seconds (don't show controller)
                            scope.launch(Dispatchers.Main) {
                                try {
                                    val currentPos = mpvViewRef?.getCurrentPosition() ?: 0.0
                                    val seekTo = (currentPos - 15.0).coerceAtLeast(0.0)
                                    mpvViewRef?.seekTo(seekTo)
                                    Log.d("MPVPlayer", "Left pressed - seeking backward to ${seekTo}s")
                                } catch (e: Exception) {
                                    Log.w("MPVPlayer", "Error seeking backward", e)
                                }
                            }
                            return@onPreviewKeyEvent true  // Consume event
                        }
                        
                        if (isRightKey) {
                            // Seek forward 15 seconds (don't show controller)
                            scope.launch(Dispatchers.Main) {
                                try {
                                    val currentPos = mpvViewRef?.getCurrentPosition() ?: 0.0
                                    val duration = mpvViewRef?.getDuration() ?: 0.0
                                    val seekTo = if (duration > 0) {
                                        (currentPos + 15.0).coerceAtMost(duration)
                                    } else {
                                        currentPos + 15.0
                                    }
                                    mpvViewRef?.seekTo(seekTo)
                                    Log.d("MPVPlayer", "Right pressed - seeking forward to ${seekTo}s")
                                } catch (e: Exception) {
                                    Log.w("MPVPlayer", "Error seeking forward", e)
                                }
                            }
                            return@onPreviewKeyEvent true  // Consume event
                        }
                    }
                    
                    // Handle Menu key to show/hide overlay
                    if (event.key == Key.Menu || nativeKeyCode == 82) { // KEYCODE_MENU
                        if (overlayVisible) {
                            overlayVisible = false
                        } else {
                            showControlsTemporarily()
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                
                false  // don't consume other events
            }
    ) {
        // ⭐ CRITICAL: Always keep AndroidView in composition to prevent surface destruction!
        // If we conditionally show/hide AndroidView based on mediaUrl, the surface gets destroyed
        // between videos, causing crashes in MPV's VO pipeline
        AndroidView(
            factory = { ctx ->
                // ⭐ CRITICAL: Use singleton MPVView instance
                // Creating multiple MPVView instances causes crashes!
                val mpv = MPVHolder.getOrCreateMPV(ctx)
                mpvViewRef = mpv
                
                mpv.apply {
                    // ⭐ CRITICAL: Stop MPV BEFORE surface initialization
                    // This must be done before surface attach/GPU config
                    stopPlayback()
                    
                    // Ensure view is visible and properly configured
                    visibility = android.view.View.VISIBLE
                    // Prevent view from consuming key events - we handle them in Compose
                    isFocusable = false
                    isFocusableInTouchMode = false
                    
                    Log.d("MPVPlayer", "Using MPV singleton instance (stopped before surface attach)")
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Ensure the view is visible and attached to window
                // This is critical for SurfaceView to create its surface
                view.visibility = android.view.View.VISIBLE
                view.requestLayout()
                
                // Force the holder to create the surface by getting it
                // SurfaceView creates the surface when:
                // 1. View is attached to window
                // 2. View is visible
                // 3. View has non-zero size
                // 4. holder.surface is accessed or callback is set
                try {
                    val surface = view.holder.surface
                    if (surface != null && surface.isValid) {
                        Log.d("MPVPlayer", "Surface is valid in update callback")
                        // If surface exists but surfaceReady is false, the callback might not have fired yet
                        // The callback should fire soon, but we can check
                    } else {
                        Log.d("MPVPlayer", "Surface not yet created, waiting for surfaceCreated callback")
                    }
                } catch (e: Exception) {
                    Log.w("MPVPlayer", "Error checking surface in update", e)
                }
            },
            onRelease = { view ->
                // ⚠️ DO NOTHING on release - we want to keep the surface alive!
                // The singleton MPVView should persist across video changes
                Log.d("MPVPlayer", "AndroidView onRelease called (no-op to preserve surface)")
            }
        )
        
        // Show loading overlay on top of MPV view if needed
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Loading indicator could go here
            }
        }
        
        // Detect HDR status for overlay indicator
        val isHDRContent = remember(itemDetails) {
            val videoStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                ?.firstOrNull { it.Type == "Video" }
            val isHEVC = videoStream?.Codec?.contains("hevc", ignoreCase = true) == true ||
                         videoStream?.Codec?.contains("h265", ignoreCase = true) == true
            val width = videoStream?.Width ?: 0
            val height = videoStream?.Height ?: 0
            val is4KOrHigher = width >= 3840 || height >= 2160
            isHEVC && is4KOrHigher
        }
        
        // Overlay UI - matching ExoPlayer's controller style
        if (overlayVisible && initialized && mpvViewRef != null) {
            MPVPlayerOverlay(
                mpvView = mpvViewRef,
                visible = overlayVisible,
                onClose = { 
                    overlayVisible = false
                    Log.d("MPVPlayer", "Overlay closed")
                },
                item = itemDetails ?: item,
                apiService = apiService,
                onSubtitleSelected = { subtitleIndex ->
                    // Handle subtitle selection from settings menu
                    scope.launch {
                        try {
                            val mediaSourceId = itemDetails?.MediaSources?.firstOrNull()?.Id ?: item.Id
                            if (subtitleIndex != null) {
                                val subtitleStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                    ?.find { it.Type == "Subtitle" && it.Index == subtitleIndex }
                                
                                if (subtitleStream != null && mpvViewRef != null) {
                                    Log.d("MPVPlayer", "User selected subtitle from settings: ${subtitleStream.DisplayTitle}")
                                    
                                    // ⭐ Download subtitle to local cache, then load via file:// URL
                                    scope.launch {
                                        try {
                                            val localPath = com.flex.elefin.player.mpv.MPVSubtitleDownloader.downloadSubtitle(
                                                context = context,
                                                apiService = apiService,
                                                itemId = item.Id,
                                                mediaSourceId = mediaSourceId,
                                                stream = subtitleStream
                                            )
                                            
                                            if (localPath != null) {
                                                withContext(Dispatchers.Main) {
                                                    Log.d("MPVPlayer", "✅ Loading subtitle from local file: $localPath")
                                                    // Remove any existing subtitles first
                                                    MPVLib.command(arrayOf("sub-remove"))
                                                    // Add new subtitle
                                                    MPVLib.command(arrayOf("sub-add", localPath, "select", subtitleStream.DisplayTitle ?: "Subtitle"))
                                                }
                                            } else {
                                                Log.e("MPVPlayer", "❌ Failed to download subtitle")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("MPVPlayer", "Error loading subtitle", e)
                                        }
                                    }
                                    
                                    // Save preference
                                    AppSettings(context).setSubtitlePreference(item.Id, subtitleIndex)
                                } else if (subtitleIndex == null && mpvViewRef != null) {
                                    // User disabled subtitles
                                    Log.d("MPVPlayer", "Disabling subtitles")
                                    MPVLib.command(arrayOf("sub-select", "no"))
                                    AppSettings(context).setSubtitlePreference(item.Id, null)
                                } else {
                                    Log.e("MPVPlayer", "Subtitle stream not found for index $subtitleIndex")
                                }
                            } else {
                                // Disable subtitles
                                MPVLib.command(arrayOf("sub-select", "no"))
                                Log.d("MPVPlayer", "Subtitles disabled")
                            }
                        } catch (e: Exception) {
                            Log.e("MPVPlayer", "Error loading subtitle from settings", e)
                        }
                    }
                },
                isHDR = isHDRContent
            )
        }
        
        // Title overlay (shown when controls are hidden, like ExoPlayer)
        val displayName = itemDetails?.Name ?: item.Name
        val showTitleWhenHidden = !overlayVisible && initialized && displayName.isNotEmpty()
        
        if (showTitleWhenHidden && titleOverlayVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 32.dp, top = 32.dp)
            ) {
                Column {
                    Text(
                        text = displayName,
                        style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    // HDR indicator
                    if (isHDRContent) {
                        Box(
                            modifier = Modifier
                                .background(Color.Black, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            androidx.tv.material3.Text(
                                text = "HDR",
                                style = androidx.tv.material3.MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
        
        // ⭐ CUSTOM SUBTITLE OVERLAY (WORKAROUND FOR MPV-ANDROID RENDERING BUG) ⭐
        // MPV renders subtitles internally but they don't appear on SurfaceView on Android TV
        // This Compose overlay extracts sub-text from MPV and displays it ourselves
        if (!currentSubtitleText.isNullOrBlank()) {
            val settings = AppSettings(context)
            val subTextSize = settings.subtitleTextSize.sp
            val subTextColor = Color(settings.subtitleTextColor.toLong())
            val isTransparent = settings.subtitleBgTransparent
            val subBackColor = if (isTransparent) {
                Color.Transparent
            } else {
                Color(settings.subtitleBgColor.toLong()).copy(alpha = 0.7f)
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 80.dp, start = 48.dp, end = 48.dp)
            ) {
                androidx.tv.material3.Text(
                    text = currentSubtitleText!!,
                    style = androidx.tv.material3.MaterialTheme.typography.titleLarge.copy(
                        fontSize = subTextSize,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black,
                            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                            blurRadius = 4f
                        )
                    ),
                    color = subTextColor,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            subBackColor,
                            androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

