package com.flex.elefin.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.flex.elefin.player.mpv.MpvUrlSelector
import `is`.xyz.mpv.MPVView
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File

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
    var initialized by remember { mutableStateOf(false) }
    var progressReportingJob by remember { mutableStateOf<Job?>(null) }
    var subtitleHeaders by remember { mutableStateOf<String?>(null) }
    var overlayVisible by remember { mutableStateOf(false) }
    var urlFallbackAttempt by remember { mutableStateOf(0) }
    var playbackFailed by remember { mutableStateOf(false) }
    var overlayTimeoutJob by remember { mutableStateOf<Job?>(null) }

    // Track itemDetails for video codec detection and subtitle URL generation
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    
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
    
    // Fetch item details and prepare video URL
    LaunchedEffect(item.Id, apiService, subtitleStreamIndex) {
        withContext(Dispatchers.IO) {
            try {
                // Get full item details with MediaSources
                val details = apiService.getItemDetails(item.Id)
                itemDetails = details
                if (details != null) {
                    // Get the first media source
                    val mediaSource = details.MediaSources?.firstOrNull()
                    val mediaSourceId = mediaSource?.Id

                    // Check if content is HDR/HEVC by examining video codec
                    val videoStream = details.MediaSources?.firstOrNull()?.MediaStreams
                        ?.firstOrNull { it.Type == "Video" }
                    val videoCodec = videoStream?.Codec
                    val isHevc = videoCodec?.lowercase()?.contains("hevc") == true || 
                                 videoCodec?.lowercase()?.contains("h265") == true ||
                                 videoCodec?.lowercase()?.contains("h.265") == true
                    
                    if (isHevc) {
                        Log.d("MPVPlayer", "HEVC/HDR content detected (codec=$videoCodec) - will prioritize direct play to preserve HDR")
                    }

                    // Use MpvUrlSelector to intelligently select the best playback URL
                    // For HDR/HEVC: Direct Play > Direct Stream (copy) > HLS
                    // For SDR: Direct Play > MP4 Transcode > HLS
                    // This preserves HDR and avoids Jellyfin's transcoder crashes
                    val serverBaseUrl = apiService.serverBaseUrl.let { if (it.endsWith("/")) it.removeSuffix("/") else it }
                    
                    // Get device ID from config for proper authentication
                    val deviceId = apiService.getJellyfinConfig()?.deviceId ?: ""
                    
                    // Create URL selector instance
                    val selector = MpvUrlSelector(
                        server = serverBaseUrl,
                        userId = apiService.getUserId(),
                        accessToken = apiService.apiKey,
                        deviceId = deviceId
                    )
                    
                    // Use DirectPlay Stream (more reliable than /original which may not exist)
                    // The /original endpoint can return 404 for some media items
                    // Fallback will automatically try other strategies if this fails
                    val result = selector.buildDirectPlay(item.Id, mediaSourceId)
                    
                    mediaUrl = result.url
                    
                    // Store headers for MPV (will be set in ffmpegHeaders)
                    subtitleHeaders = result.headers
                    
                    Log.d("MPVPlayer", "Selected MPV URL (DirectPlay Stream): ${result.url} ${if (isHevc) "[HDR/HEVC]" else "[SDR]"}")
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

    // Initialize MPV view when media URL is ready
    LaunchedEffect(mediaUrl, mpvViewRef, itemDetails) {
        if (mediaUrl != null && mpvViewRef != null && !initialized) {
            withContext(Dispatchers.Main) {
                try {
                    val mpvView = mpvViewRef ?: return@withContext
                    
                    Log.d("MPVPlayer", "Starting MPV initialization")

                    // Initialize MPV with config and cache directories
                    val configDir = File(context.filesDir, "mpv_config").apply {
                        if (!exists()) mkdirs()
                        
                        // Copy mpv.conf from assets to config directory if it doesn't exist
                        val mpvConfFile = File(this, "mpv.conf")
                        if (!mpvConfFile.exists()) {
                            try {
                                context.assets.open("mpv.conf").use { input ->
                                    mpvConfFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                Log.d("MPVPlayer", "✅ Copied mpv.conf from assets to config directory")
                            } catch (e: Exception) {
                                Log.w("MPVPlayer", "Could not copy mpv.conf from assets", e)
                            }
                        }
                    }
                    val cacheDir = context.cacheDir

                    val initSuccess = mpvView.initialize(configDir.absolutePath, cacheDir.absolutePath)
                    if (!initSuccess) {
                        Log.e("MPVPlayer", "Failed to initialize MPV, libraries not available")
                        throw Exception("MPV libraries not available")
                    }

                    // Use headers from MpvUrlSelector result (includes all required headers)
                    // Add X-Emby-Client-Capabilities header to tell Jellyfin what codecs MPV supports
                    val clientCapabilities = "PlayableMediaTypes=Audio,Video;SupportsH264=true;SupportsHevc=true;SupportsAv1=true;SupportsMkv=true;SupportsAac=true;SupportsOpus=true;SupportsAc3=true;SupportsEac3=true;SupportsTrueHD=true;SupportsDts=true;SupportsDca=true;SupportsDtshd=true;MaxStreamingBitrate=140000000;MaxStaticBitrate=140000000"
                    
                    // Combine headers from selector with client capabilities
                    val ffmpegHeaders = buildString {
                        append(subtitleHeaders ?: "")
                        append("X-Emby-Client-Capabilities: $clientCapabilities\r\n")
                    }
                    
                    // Store headers in MPVView - they'll be set via stream-lavf-o before playback
                    mpvView.ffmpegHeaders = ffmpegHeaders
                    Log.d("MPVPlayer", "Stored FFmpeg headers in MPVView from MpvUrlSelector")
                    
                    // Convert resume position from ms to seconds
                    val resumePositionSeconds = if (resumePositionMs > 0) {
                        resumePositionMs / 1000.0
                    } else {
                        0.0
                    }

                    // Play the video first - BaseMPVView.playFile() will handle waiting for surface
                    // It stores the filePath and plays it when surface is ready
                    if (resumePositionSeconds > 0) {
                        Log.d("MPVPlayer", "Starting playback with resume position: ${resumePositionSeconds}s - BaseMPVView will handle surface attachment")
                    } else {
                        Log.d("MPVPlayer", "Starting playback from beginning - BaseMPVView will handle surface attachment")
                    }
                    // Set up error callback for automatic fallback
                    val mediaSourceForFallback = itemDetails?.MediaSources?.firstOrNull()
                    val mediaSourceIdForFallback = mediaSourceForFallback?.Id ?: item.Id
                    
                    mpvView.onPlaybackError = {
                        scope.launch(Dispatchers.IO) {
                            val serverBaseUrl = apiService.serverBaseUrl.let { if (it.endsWith("/")) it.removeSuffix("/") else it }
                            val deviceId = apiService.getJellyfinConfig()?.deviceId ?: ""
                            
                            val selector = MpvUrlSelector(
                                server = serverBaseUrl,
                                userId = apiService.getUserId(),
                                accessToken = apiService.apiKey,
                                deviceId = deviceId
                            )
                            
                            // Fallback order: DirectPlay Stream -> DirectPlay Original -> HLS -> Transcode
                            val fallbackStrategies = listOf(
                                { selector.buildDirectPlay(item.Id, mediaSourceIdForFallback) },
                                { selector.buildDirectPlayOriginal(item.Id, mediaSourceIdForFallback) },
                                { selector.buildHls(item.Id, mediaSourceIdForFallback) },
                                { selector.buildTranscodeHls(item.Id, mediaSourceIdForFallback) }
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
                                        view.ffmpegHeaders = subtitleHeaders ?: ""
                                        view.play(result.url, 0.0) // Start from beginning on fallback
                                        Log.d("MPVPlayer", "Retrying with fallback URL: ${result.url}")
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
                    
                    mpvView.play(mediaUrl!!, resumePositionSeconds)
                    
                    // Load subtitles only if explicitly specified (subtitleStreamIndex is provided)
                    // Load subtitles AFTER starting playback with a delay to ensure MPV is ready
                    if (subtitleStreamIndex != null) {
                        // Delay subtitle loading to ensure MPV playback has started
                        scope.launch {
                            delay(1000) // Wait 1 second for MPV to start playback
                            
                            val mediaSource = itemDetails?.MediaSources?.firstOrNull()
                            val mediaSourceId = mediaSource?.Id ?: item.Id
                            
                            // Get subtitle streams from media source
                            val subtitleStreams = mediaSource?.MediaStreams?.filter { it.Type == "Subtitle" } ?: emptyList()
                            Log.d("MPVPlayer", "Found ${subtitleStreams.size} subtitle stream(s) available")
                            
                            // Load the explicitly specified subtitle track
                            val subtitleIndexToLoad = subtitleStreamIndex
                            val subtitleStream = subtitleStreams.find { it.Index == subtitleIndexToLoad }
                            
                            if (subtitleStream != null) {
                                try {
                                    val subtitleUrl = apiService.getSubtitleUrl(item.Id, mediaSourceId, subtitleIndexToLoad)
                                    val subtitleTitle = subtitleStream.DisplayTitle 
                                        ?: subtitleStream.Language 
                                        ?: "Unknown"
                                    Log.d("MPVPlayer", "Loading subtitle track ${subtitleIndexToLoad} (${subtitleTitle}): $subtitleUrl")
                                    
                                    // Add subtitle URL to MPV - this loads the external subtitle file
                                    // MPV will use the same headers from stream-lavf-o for authentication
                                    mpvViewRef?.setSubtitleUrl(subtitleUrl, subtitleTitle)
                                    Log.d("MPVPlayer", "✅ Subtitle loaded: track ${subtitleIndexToLoad} (${subtitleTitle})")
                                } catch (e: Exception) {
                                    Log.e("MPVPlayer", "Error loading subtitle track ${subtitleIndexToLoad}", e)
                                }
                            } else {
                                Log.w("MPVPlayer", "Subtitle track ${subtitleIndexToLoad} not found in available streams")
                            }
                        }
                    } else {
                        Log.d("MPVPlayer", "No subtitle index provided, skipping auto-load")
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
            
            // Get references BEFORE destroying MPV
            val viewToDestroy = mpvViewRef
            val wasInitialized = initialized
            val itemId = item.Id
            
            // Destroy MPV FIRST to prevent race conditions
            try {
                if (wasInitialized && viewToDestroy != null) {
                    // Get position BEFORE destroying (while MPV is still valid)
                    var currentPositionSeconds = 0.0
                    var duration = 0.0
                    try {
                        // Access MPV methods - they have null checks internally
                        currentPositionSeconds = viewToDestroy.getCurrentPosition() ?: 0.0
                        duration = viewToDestroy.getDuration() ?: 0.0
                        Log.d("MPVPlayer", "Got position before destroy: $currentPositionSeconds, duration: $duration")
                    } catch (e: Exception) {
                        Log.w("MPVPlayer", "Error getting position before destroy (MPV may already be destroyed)", e)
                    }
                    
                    // Destroy MPV
                    viewToDestroy.destroy()
                    Log.d("MPVPlayer", "MPV destroyed in DisposableEffect")
                    
                    // Report final position AFTER destroying (on background thread)
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
            } catch (e: Exception) {
                Log.e("MPVPlayer", "Error destroying MPV player", e)
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
                            // If overlay is visible, toggle play/pause (like ExoPlayer)
                            scope.launch(Dispatchers.Main) {
                                try {
                                    val isPaused = mpvViewRef?.isPaused() ?: false
                                    if (isPaused) {
                                        mpvViewRef?.resume()
                                        Log.d("MPVPlayer", "Enter/OK pressed - resuming playback")
                                    } else {
                                        mpvViewRef?.pause()
                                        Log.d("MPVPlayer", "Enter/OK pressed - pausing playback")
                                    }
                                } catch (e: Exception) {
                                    Log.w("MPVPlayer", "Error toggling play/pause", e)
                                }
                            }
                            return@onPreviewKeyEvent true  // consume event
                        } else {
                            // If overlay is hidden, show it temporarily
                            showControlsTemporarily()
                            Log.d("MPVPlayer", "Enter/OK pressed - showing controls")
                            return@onPreviewKeyEvent true  // consume event
                        }
                    }
                    
                    // When overlay is visible, let directional keys pass through to overlay buttons
                    // But don't intercept them - let overlay handle navigation
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
                }
                
                false  // don't consume other events
            }
    ) {
        when {
            mediaUrl != null -> {
                AndroidView(
                    factory = { ctx ->
                        MPVView(ctx, null).apply {
                            mpvViewRef = this
                            // Ensure view is visible and properly configured
                            visibility = android.view.View.VISIBLE
                            // Prevent view from consuming key events - we handle them in Compose
                            isFocusable = false
                            isFocusableInTouchMode = false
                            // BaseMPVView already handles surface callbacks internally
                            // The surface will be attached automatically when created
                            Log.d("MPVPlayer", "MPVView created in AndroidView factory (focus disabled)")
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
                        // Cleanup if needed
                        Log.d("MPVPlayer", "AndroidView released")
                    }
                )
            }
            isLoading -> {
                // Loading indicator could go here
            }
        }
        
        // Overlay UI - simple overlay matching ExoPlayer's controller style
        if (overlayVisible && initialized && mpvViewRef != null) {
            MPVPlayerOverlay(
                mpvView = mpvViewRef,
                visible = overlayVisible,
                onClose = { 
                    overlayVisible = false
                    Log.d("MPVPlayer", "Overlay closed")
                }
            )
        }
    }
}

