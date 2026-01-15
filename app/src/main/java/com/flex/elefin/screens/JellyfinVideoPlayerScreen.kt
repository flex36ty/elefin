package com.flex.elefin.screens

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.common.ParserException
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.ui.PlayerView
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.MediaStream
import com.flex.elefin.jellyfin.SkipMarkers
import com.flex.elefin.player.SubtitleMapper
import com.flex.elefin.player.GLVideoSurfaceView
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.material.icons.Icons
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import androidx.tv.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.media3.common.TrackSelectionOverride
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Warning

// Picture mode / aspect ratio options
enum class AspectMode(val label: String) {
    FIT("Fit"),              // Natural letterbox - fits video in screen with black bars
    FILL("Fill"),            // Crop to fill screen - removes black bars by cropping
    LETTERBOX("16:9"),       // Force 16:9 letterbox - maintains aspect ratio in 16:9 frame
    CINEMA("Cinema"),        // Cinema scope 2.39:1 - movie theater style with wide black bars
    STRETCH("Stretch"),      // Stretch both axes - distorts to fill screen
    ORIGINAL("Original");    // Display at native resolution without scaling

    fun next(): AspectMode {
        val modes = values()
        return modes[(ordinal + 1) % modes.size]
    }
}

@UnstableApi
@Composable
fun JellyfinVideoPlayerScreen(
    item: JellyfinItem,
    apiService: JellyfinApiService,
    onBack: () -> Unit = {},
    resumePositionMs: Long = 0L,
    subtitleStreamIndex: Int? = null,
    audioStreamIndex: Int? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { com.flex.elefin.jellyfin.AppSettings(context) }
    
    // Track itemDetails state for codec detection (will be populated by LaunchedEffect)
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    
    // GL Enhancement settings
    // IMPORTANT: GL mode causes black screen with AV1 on devices without hardware AV1 decoder
    // Shield TV has NO hardware AV1 - software decode + GL surface = black screen
    // 
    // Since Jellyfin often returns Codec=null, we CANNOT rely on metadata detection.
    // Instead, we detect AV1 at RUNTIME from ExoPlayer's track info and force safe mode.
    val glSettingEnabled = remember { settings.useGLEnhancements }
    
    // Track if AV1 was detected at runtime (will be set by ExoPlayer listener)
    var runtimeAV1Detected by remember { mutableStateOf(false) }
    
    // Show error dialog for AV1 decoding failure
    var showAV1Error by remember { mutableStateOf(false) }
    
    // Auto-transcode fallback state
    val autoTranscodeOnError = remember { settings.autoTranscodeOnError }
    var hasTriedTranscodeFallback by remember { mutableStateOf(false) }
    var isUsingTranscodeFallback by remember { mutableStateOf(false) }
    
    // MPV fallback state
    val fallbackToMpv = remember { settings.fallbackToMpv }
    var hasTriedMpvFallback by remember { mutableStateOf(false) }
    val isMpvInstalled = remember { com.flex.elefin.player.mpv.MpvElefinLauncher.isInstalled(context) }
    
    // Helper function to launch MPV as fallback
    val jellyfinConfig = remember { com.flex.elefin.jellyfin.JellyfinConfig(context) }
    val launchMpvFallback: () -> Unit = {
        Log.d("JellyfinPlayer", "🎬 Launching MPV player as fallback...")
        
        if (jellyfinConfig.isConfigured()) {
            // Try to get cached subtitle if one was selected
            val subtitlePath = subtitleStreamIndex?.let { streamIndex ->
                com.flex.elefin.player.SubtitleDownloader.getCachedSubtitle(item.Id, streamIndex)
            }
            if (subtitlePath != null) {
                Log.d("JellyfinPlayer", "🎬 Found cached subtitle for MPV: $subtitlePath")
            }
            
            val success = com.flex.elefin.player.mpv.MpvElefinLauncher.play(
                context = context,
                itemId = item.Id,
                title = item.Name ?: "Video",
                resumePositionMs = resumePositionMs,
                config = jellyfinConfig,
                subtitleFilePath = subtitlePath
            )
            
            if (success) {
                Log.d("JellyfinPlayer", "✅ MPV launched successfully - closing ExoPlayer")
                // Go back since we're switching to MPV
                onBack()
            } else {
                Log.e("JellyfinPlayer", "❌ Failed to launch MPV")
                showAV1Error = true
            }
        } else {
            Log.e("JellyfinPlayer", "❌ No Jellyfin config available for MPV fallback")
            showAV1Error = true
        }
    }
    
    // Start with user's GL setting, but will be overridden if AV1 detected at runtime
    // Note: The actual enforcement happens in the player listener below
    val useGLEnhancements = remember { 
        Log.d("JellyfinPlayer", "🎨 GL enhancements mode: $glSettingEnabled (will be disabled if AV1 detected at runtime)")
        glSettingEnabled 
    }
    val enableFakeHDR = remember { settings.enableFakeHDR }
    val enableSharpening = remember { settings.enableSharpening }
    val hdrStrength = remember { settings.hdrStrength }
    val sharpenStrength = remember { settings.sharpenStrength }
    
    // New video enhancement settings
    val enableDenoise = remember { settings.enableDenoise }
    val denoiseStrength = remember { settings.denoiseStrength }
    val enableDeband = remember { settings.enableDeband }
    val debandStrength = remember { settings.debandStrength }
    val enableFXAA = remember { settings.enableFXAA }
    val videoBrightness = remember { settings.videoBrightness }
    val videoContrast = remember { settings.videoContrast }
    val videoSaturation = remember { settings.videoSaturation }
    val videoColorTemperature = remember { settings.videoColorTemperature }
    
    // Load stored audio preference if not provided
    val storedAudioPreference = remember(item.Id) {
        if (audioStreamIndex == null) {
            val pref = settings.getAudioPreference(item.Id)
            Log.d("JellyfinPlayer", "Loaded stored audio preference for ${item.Id}: $pref")
            pref
        } else {
            Log.d("JellyfinPlayer", "Using provided audioStreamIndex: $audioStreamIndex")
            audioStreamIndex
        }
    }
    // Create player with enhanced codec support and LoadControl configured based on settings
    // Enable extension renderers (including FFmpeg) and decoder fallback
    // FFmpeg supports: DTS, DTS-HD, TrueHD, AC3, E-AC3, FLAC, ALAC, Vorbis, Opus
    val renderersFactory = remember {
        DefaultRenderersFactory(context).apply {
            // PREFER extension renderers (FFmpeg) over platform decoders for better compatibility
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
            Log.d("JellyfinPlayer", "🎬 ExoPlayer initialized with FFmpeg extension support")
            Log.d("JellyfinPlayer", "   Extension renderer mode: PREFER, Decoder fallback: ENABLED")
        }
    }
    
    // Configure track selector with better track selection
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
                    // Subtitle preferences - disable ALL auto-selection but allow manual control
                    .setSelectUndeterminedTextLanguage(false)  // Don't auto-select unknown language subs
                    .setDisabledTextTrackSelectionFlags(C.SELECTION_FLAG_FORCED or C.SELECTION_FLAG_DEFAULT)  // Disable forced AND default auto-selection
                    // ❌ DO NOT use setTrackTypeDisabled - it prevents "None" from working in ExoPlayer UI
                    // Only select subtitles explicitly chosen by user or saved preference
                    .setPreferredTextLanguage(null)  // No auto language preference
                    .setPreferredTextRoleFlags(0)  // No role-based auto-selection
                    .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_FORCED or C.SELECTION_FLAG_DEFAULT)  // Ignore forced/default flags completely
            )
        }
    }
    
    // Configure audio attributes for media playback
    val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.CONTENT_TYPE_MOVIE)
        .build()
    
    val player = remember {
        // Configure LoadControl to prevent OOM on high-bitrate content (especially HLS H.265)
        val loadControl = if (settings.minimalBuffer4K) {
            // "Minimal" but robust buffering
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    2000,   // minBufferMs - 2 seconds
                    50000,  // maxBufferMs - 50 seconds
                    1000,   // bufferForPlaybackMs - 1 second
                    2000    // bufferForPlaybackAfterRebufferMs - 2 seconds
                )
                .setTargetBufferBytes(50 * 1024 * 1024) // 50MB max buffer (reduced from 100MB to prevent OOM)
                .build()
        } else {
            // Robust buffering for high bitrate content
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    10000,  // minBufferMs - 10 seconds
                    120000, // maxBufferMs - 120 seconds
                    5000,   // bufferForPlaybackMs - 5 seconds
                    10000   // bufferForPlaybackAfterRebufferMs - 10 seconds
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setTargetBufferBytes(128 * 1024 * 1024) // 128MB max buffer (reduced to prevent OOM)
                .build()
        }
            
            ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setAudioAttributes(audioAttributes, true)
                .setLoadControl(loadControl)
                .build()
                .also { 
                if (settings.minimalBuffer4K) {
                    Log.d("JellyfinPlayer", "Created player with minimal buffering (50MB limit) for 4K content")
        } else {
                    Log.d("JellyfinPlayer", "Created player with standard buffering (250MB limit)")
                }
                    Log.d("JellyfinPlayer", "Extension renderer mode: PREFER, Decoder fallback: enabled")
        }
    }
    
    // Lifecycle Observer to pause playback when app goes to background (Home button)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                Log.d("JellyfinPlayer", "Lifecycle PAUSE/STOP detected. Pausing player.")
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    val glSurfaceViewRef = remember { mutableStateOf<GLVideoSurfaceView?>(null) }
    var mediaUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var playerInitialized by remember { mutableStateOf(false) }
    var progressReportingJob by remember { mutableStateOf<Job?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    // itemDetails is declared earlier for codec detection
    var hasSeekedToResume by remember { mutableStateOf(false) } // Track if we've already seeked to resume position
    var hasRetriedWithoutRange by remember { mutableStateOf(false) } // Track if we've retried without range requests for 416 errors
    var hasRetriedWithHls by remember { mutableStateOf(false) } // Track if we've retried with HLS for parser errors
    var currentMediaSource by remember { mutableStateOf<MediaSource?>(null) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var settingsMenuInitialLevel by remember { mutableStateOf("main") } // Track which submenu to open to
    var showControls by remember { mutableStateOf(false) } // Custom Compose controls overlay
    var currentPosition by remember { mutableStateOf(0L) } // Current playback position in ms
    var duration by remember { mutableStateOf(0L) } // Total duration in ms
    var currentSubtitleIndex by remember { mutableStateOf<Int?>(subtitleStreamIndex) }
    var lastSelectedSubtitleIndex by remember { mutableStateOf<Int?>(subtitleStreamIndex) } // Track last selected subtitle from controller
    var hasAppliedInitialSubtitlePreference by remember { mutableStateOf(false) } // Track if we've applied the saved preference once
    var hasRegisteredTracks by remember { mutableStateOf(false) } // Track if we've registered ExoPlayer tracks with SubtitleMapper
    var currentAudioIndex by remember { mutableStateOf<Int?>(storedAudioPreference) }
    var lastSelectedAudioIndex by remember { mutableStateOf<Int?>(storedAudioPreference) } // Track last selected audio from controller
    var is4KContent by remember { mutableStateOf(false) } // Track if current content is 4K
    // Store subtitle streams list for composite key registration in onTracksChanged
    var jellyfinSubtitleStreams by remember { mutableStateOf<List<MediaStream>>(emptyList()) }
    // Downloaded subtitles from OpenSubtitles
    var downloadedSubtitles by remember { mutableStateOf<List<com.flex.elefin.subtitles.DownloadedSubtitle>>(emptyList()) }
    var nextEpisodeId by remember { mutableStateOf<String?>(null) } // Next episode ID for autoplay
    var nextEpisodeDetails by remember { mutableStateOf<JellyfinItem?>(null) } // Next episode details
    var currentAspectMode by remember { mutableStateOf(AspectMode.FIT) } // Picture mode / aspect ratio
    
    // ===================================================================================
    // CLEAN AUTOPLAY STATE - Single source of truth
    // ===================================================================================
    var showNextUpOverlay by remember { mutableStateOf(false) }
    var autoplayCountdown by remember { mutableStateOf(settings.autoplayCountdownSeconds) }
    var autoplayCancelled by remember { mutableStateOf(false) }
    var isAutoPlayingNext by remember { mutableStateOf(false) } // Guard against double triggers
    var countdownJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    // ===================================================================================
    // AUTOPLAY HELPER FUNCTION - Single exit point for starting next episode
    // Based on Jellyfin Android TV approach: Stop current player FIRST, then start new activity
    // ===================================================================================
    val startNextEpisode: () -> Unit = startNextEpisode@{
        val nextEp = nextEpisodeDetails
        if (isAutoPlayingNext || nextEp == null) {
            Log.d("JellyfinPlayer", "🎬 Ignoring autoplay trigger (already playing=$isAutoPlayingNext, nextEp=${nextEp?.Name})")
            return@startNextEpisode
        }
        
        val activity = context as? android.app.Activity
        if (activity == null || activity.isFinishing) {
            Log.e("JellyfinPlayer", "🎬 ERROR: Activity is null or finishing")
            return@startNextEpisode
        }
        
        isAutoPlayingNext = true
        showNextUpOverlay = false
        countdownJob?.cancel()
        countdownJob = null
        
        Log.d("JellyfinPlayer", "🎬 ===== STARTING NEXT EPISODE (Jellyfin TV approach) =====")
        Log.d("JellyfinPlayer", "🎬 Next: ${nextEp.Name} (ID: ${nextEp.Id})")
        
        // Step 1: Cancel progress reporting
        progressReportingJob?.cancel()
        progressReportingJob = null
        Log.d("JellyfinPlayer", "🎬 Step 1: Progress reporting cancelled")
        
        // Step 2: Stop and release current player COMPLETELY (like Jellyfin TV does)
        val currentPositionMs = try { player.currentPosition } catch (e: Exception) { 0L }
        val positionTicks = currentPositionMs * 10_000L
        try {
            player.stop()
            player.release()
            Log.d("JellyfinPlayer", "🎬 Step 2: Player stopped and released")
        } catch (e: Exception) {
            Log.w("JellyfinPlayer", "🎬 Step 2: Error stopping player", e)
        }
        
        // Step 3: Report playback stopped (fire and forget)
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                apiService.reportPlaybackStopped(item.Id, positionTicks)
                apiService.markAsWatched(item.Id)
                Log.d("JellyfinPlayer", "🎬 Step 3: Reported playback stopped")
            } catch (e: Exception) {
                Log.w("JellyfinPlayer", "🎬 Step 3: Error reporting", e)
            }
        }
        
        // Step 4: Finish current activity FIRST (player is already released)
        // This is critical - we must finish before starting new activity to avoid conflicts
        Log.d("JellyfinPlayer", "🎬 Step 4: Finishing current activity FIRST...")
        activity.finish()
        
        // Step 5: Create and start intent for next episode
        // Use Handler to post the startActivity after finish() has been processed
        val intent = com.flex.elefin.JellyfinVideoPlayerActivity.createIntent(
            context = activity.applicationContext, // Use applicationContext since activity is finishing
            itemId = nextEp.Id,
            resumePositionMs = 0L,
            subtitleStreamIndex = null,
            audioStreamIndex = null
        )
        // FLAG_ACTIVITY_NEW_TASK is required when starting from applicationContext
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        
        Log.d("JellyfinPlayer", "🎬 Step 5: Starting new activity...")
        activity.applicationContext.startActivity(intent)
        
        Log.d("JellyfinPlayer", "🎬 ===== AUTOPLAY COMPLETE =====")
    }
    
    // ===================================================================================
    // AUTOPLAY COUNTDOWN FUNCTION - Owns the transition, fires exactly once
    // Must be defined right after startNextEpisode so it can reference it
    // ===================================================================================
    val startAutoplayCountdown: () -> Unit = {
        countdownJob?.cancel()
        
        countdownJob = scope.launch {
            val seconds = settings.autoplayCountdownSeconds
            Log.d("JellyfinPlayer", "⏱️ Starting ${seconds}s countdown...")
            
            for (i in seconds downTo 1) {
                // Check BEFORE updating state - exit early if conditions changed
                if (autoplayCancelled || isAutoPlayingNext) {
                    Log.d("JellyfinPlayer", "⛔ Countdown aborted (cancelled=$autoplayCancelled, autoplaying=$isAutoPlayingNext)")
                    showNextUpOverlay = false
                    return@launch
                }
                
                autoplayCountdown = i
                Log.d("JellyfinPlayer", "⏱️ Countdown: $i")
                delay(1_000)
            }
            
            // Final check before triggering
            if (autoplayCancelled || isAutoPlayingNext) {
                Log.d("JellyfinPlayer", "⛔ Countdown finished but conditions changed, not starting")
                return@launch
            }
            
            // 🔥 COUNTDOWN FINISHED - START NEXT EPISODE
            autoplayCountdown = 0
            Log.d("JellyfinPlayer", "⏱️ Countdown finished — starting next episode!")
            startNextEpisode()
        }
    }
    
    // Skip intro/credits state
    var skipMarkers by remember { mutableStateOf(SkipMarkers()) }
    var showSkipIntroButton by remember { mutableStateOf(false) }
    var showSkipCreditsButton by remember { mutableStateOf(false) }
    val skipIntroEnabled = remember { settings.skipIntroEnabled }
    val skipCreditsEnabled = remember { settings.skipCreditsEnabled }

    // Load downloaded subtitles from OpenSubtitles
    LaunchedEffect(item.Id) {
        downloadedSubtitles = com.flex.elefin.subtitles.OpenSubtitlesApi.getDownloadedSubtitles(context, item.Id)
        Log.d("JellyfinPlayer", "📁 Loaded ${downloadedSubtitles.size} downloaded subtitle(s) for item ${item.Id}")
    }
    
    // Fetch skip markers for intro/credits (only for episodes)
    LaunchedEffect(item.Id, apiService) {
        if (item.Type == "Episode" && (skipIntroEnabled || skipCreditsEnabled)) {
            withContext(Dispatchers.IO) {
                try {
                    val markers = apiService.getMediaSegments(item.Id)
                    skipMarkers = markers
                    Log.d("JellyfinPlayer", "Skip markers loaded: intro=${markers.introStartMs}-${markers.introEndMs}ms, credits=${markers.creditsStartMs}ms")
                } catch (e: Exception) {
                    Log.d("JellyfinPlayer", "Skip markers not available: ${e.message}")
                }
            }
        }
    }
    
    // Fetch item details and prepare video URL
    LaunchedEffect(item.Id, apiService, subtitleStreamIndex) {
        withContext(Dispatchers.IO) {
            try {
                // Get full item details with MediaSources
                val details = apiService.getItemDetails(item.Id)
                if (details != null) {
                    itemDetails = details
                    // Get the first media source
                    val mediaSource = details.MediaSources?.firstOrNull()
                    val mediaSourceId = mediaSource?.Id

                    // Detect if video is HDR/high-quality (HEVC codec and high resolution typically indicates HDR)
                    val videoStream = mediaSource?.MediaStreams?.firstOrNull { it.Type == "Video" }
                    val isHEVC = videoStream?.Codec?.contains("hevc", ignoreCase = true) == true ||
                                 videoStream?.Codec?.contains("h265", ignoreCase = true) == true
                    val width = videoStream?.Width ?: 0
                    val height = videoStream?.Height ?: 0
                    val is4KOrHigher = width >= 3840 || height >= 2160
                    val isHDROrHighQuality = isHEVC && is4KOrHigher
                    
                    // Store 4K status for buffering control
                    is4KContent = is4KOrHigher
                    
                    // Detect if audio codec is unsupported by Android (requires transcoding)
                    val audioStream = mediaSource?.MediaStreams?.firstOrNull { it.Type == "Audio" }
                    val audioCodec = audioStream?.Codec?.lowercase() ?: ""
                    // TrueHD, DTS-HD, and other lossless/high-end audio codecs aren't supported by Android AudioTrack
                    val isUnsupportedAudio = audioCodec.contains("truehd", ignoreCase = true) ||
                                            audioCodec.contains("dts-hd", ignoreCase = true) ||
                                            audioCodec.contains("dtshd", ignoreCase = true) ||
                                            audioCodec.contains("dtsx", ignoreCase = true) ||
                                            audioCodec.contains("atmos", ignoreCase = true) && audioCodec.contains("truehd", ignoreCase = true)
                    
                    // Check if AAC to AC3 transcoding is enabled
                    val shouldTranscodeAacToAc3 = settings.transcodeAacToAc3 && audioCodec.contains("aac", ignoreCase = true)
                    val needsAudioTranscoding = isUnsupportedAudio || shouldTranscodeAacToAc3
                    val targetAudioCodec = if (shouldTranscodeAacToAc3) "ac3" else null
                    
                    if (isHDROrHighQuality) {
                        Log.d("JellyfinPlayer", "HDR/high-quality video detected (${videoStream?.Codec}, ${width}x${height}) - requesting full quality")
                    }
                    if (isUnsupportedAudio) {
                        Log.d("JellyfinPlayer", "Unsupported audio codec detected (${audioStream?.Codec}) - will transcode audio while preserving video quality")
                    }
                    if (shouldTranscodeAacToAc3) {
                        Log.d("JellyfinPlayer", "AAC to AC3 transcoding enabled - transcoding audio from AAC to AC3 for universal device compatibility")
                    }
                    
                    // USER SELECTED SUBTITLE OVERRIDE
                    // Check if user explicitly selected a subtitle (from series/movie page or Jellyfin UI)
                    val selectedSubtitleStream = if (subtitleStreamIndex != null) {
                        details?.MediaSources?.firstOrNull()?.MediaStreams
                            ?.find { it.Type == "Subtitle" && it.Index == subtitleStreamIndex }
                    } else {
                        null
                    }
                    
                    // HLS (master.m3u8) does NOT support external subtitles via SubtitleConfiguration!
                    // This is a known Media3 limitation - ExoPlayer ignores SubtitleConfiguration for HLS streams
                    // Solution: If user selected an EXTERNAL subtitle, force direct streaming (no HLS)
                    val userSelectedExternalSubtitle = selectedSubtitleStream?.IsExternal == true
                    val forceDirectStreamForSubtitles = userSelectedExternalSubtitle && needsAudioTranscoding
                    
                    if (userSelectedExternalSubtitle) {
                        Log.d("JellyfinPlayer", "📌 USER SELECTED EXTERNAL SUBTITLE")
                        Log.d("JellyfinPlayer", "   Selected: ${selectedSubtitleStream?.DisplayTitle ?: selectedSubtitleStream?.Language}")
                        Log.d("JellyfinPlayer", "   Index: ${selectedSubtitleStream?.Index}, IsExternal: ${selectedSubtitleStream?.IsExternal}")
                        
                        if (needsAudioTranscoding) {
                            Log.w("JellyfinPlayer", "⚠️ SUBTITLE PRIORITY MODE ACTIVATED")
                            Log.w("JellyfinPlayer", "   External subtitle selected + audio transcoding needed")
                            Log.w("JellyfinPlayer", "   Disabling HLS transcoding to use direct streaming")
                            Log.w("JellyfinPlayer", "   WHY: HLS playlists do NOT include external subtitles")
                            Log.w("JellyfinPlayer", "   WHY: ExoPlayer ignores SubtitleConfiguration for HLS (Media3 limitation)")
                            Log.w("JellyfinPlayer", "   RESULT: Selected subtitle will work, audio codec may not be optimal")
                            Log.w("JellyfinPlayer", "   ALTERNATIVE: Use MPV player for both subtitle + audio transcoding support")
                        } else {
                            Log.d("JellyfinPlayer", "   ✅ Direct streaming - external subtitle will load successfully")
                        }
                    }

                    // Generate video playback URL
                    // If user selected external subtitle, disable HLS transcoding to force direct streaming
                    val effectiveNeedsTranscoding = if (forceDirectStreamForSubtitles) false else needsAudioTranscoding
                    val effectiveAudioCodec = if (forceDirectStreamForSubtitles) null else targetAudioCodec
                    
                    // Check if server-side transcoding is enabled
                    val serverTranscodingEnabled = settings.serverTranscodingEnabled
                    val transcodeAV1Setting = settings.transcodeAV1
                    val transcodeHEVCSetting = settings.transcodeHEVC
                    val transcodeTargetCodec = settings.transcodeTargetCodec
                    val transcodeMaxBitrate = settings.transcodeMaxBitrateMbps
                    
                    // Detect video codec for transcoding decision
                    val videoCodecName = videoStream?.Codec?.lowercase() ?: ""
                    val isAV1Video = videoCodecName.contains("av1") || videoCodecName.contains("av01")
                    val isHEVCVideo = videoCodecName.contains("hevc") || videoCodecName.contains("h265") || videoCodecName.contains("h.265")
                    
                    // Determine if we should request server-side transcoding
                    val shouldRequestTranscoding = serverTranscodingEnabled && (
                        (transcodeAV1Setting && isAV1Video) ||
                        (transcodeHEVCSetting && isHEVCVideo)
                    )
                    
                    val videoUrl = if (shouldRequestTranscoding && !forceDirectStreamForSubtitles) {
                        Log.d("JellyfinPlayer", "🔄 SERVER-SIDE TRANSCODING ENABLED")
                        Log.d("JellyfinPlayer", "   Source codec: $videoCodecName")
                        Log.d("JellyfinPlayer", "   Target codec: $transcodeTargetCodec @ ${transcodeMaxBitrate}Mbps")
                        Log.d("JellyfinPlayer", "   Reason: ${if (isAV1Video) "AV1" else "HEVC"} transcoding requested")
                        
                        apiService.getTranscodedVideoUrl(
                            itemId = item.Id,
                            mediaSourceId = mediaSourceId,
                            subtitleStreamIndex = null,
                            targetVideoCodec = transcodeTargetCodec,
                            maxBitrateMbps = transcodeMaxBitrate,
                            audioCodec = "aac"
                        )
                    } else {
                        apiService.getVideoPlaybackUrl(
                            itemId = item.Id,
                            mediaSourceId = mediaSourceId,
                            subtitleStreamIndex = null,
                            preserveQuality = isHDROrHighQuality,
                            transcodeAudio = effectiveNeedsTranscoding, // Disabled if subtitles exist
                            audioCodec = effectiveAudioCodec
                        )
                    }
                    Log.d("JellyfinPlayer", "Video URL: $videoUrl")
                    mediaUrl = videoUrl
                    
                    // Check for next episode if this is an episode
                    // Use the simpler StartIndex approach: /Shows/{seriesId}/Episodes?StartIndex={currentIndex + 1}&Limit=1
                    if (details.Type == "Episode") {
                        Log.d("JellyfinPlayer", "Episode detected. NextEpisodeId from API: ${details.NextEpisodeId}")
                        Log.d("JellyfinPlayer", "Episode info: SeriesId=${details.SeriesId}, Season=${details.ParentIndexNumber}, Episode=${details.IndexNumber}")
                        
                        // Try to get next episode ID from API response first
                        var foundNextEpisode: JellyfinItem? = null
                        
                        if (details.NextEpisodeId != null) {
                            // API provided NextEpisodeId, fetch the episode
                            val nextDetails = apiService.getItemDetails(details.NextEpisodeId)
                            if (nextDetails != null) {
                                foundNextEpisode = nextDetails
                                Log.d("JellyfinPlayer", "✅ Found next episode via NextEpisodeId: ${nextDetails.Name}")
                            }
                        }
                        
                        // If NextEpisodeId is not available, find next episode in same season first
                        if (foundNextEpisode == null && details.SeriesId != null && details.IndexNumber != null && details.ParentIndexNumber != null) {
                            Log.d("JellyfinPlayer", "NextEpisodeId not available, finding next episode in same season...")
                            Log.d("JellyfinPlayer", "Current: S${details.ParentIndexNumber}E${details.IndexNumber}")
                            try {
                                // First, get the seasons to find the current season's ID
                                    val seasons = apiService.getSeasons(details.SeriesId)
                                    val currentSeason = seasons.firstOrNull { it.IndexNumber == details.ParentIndexNumber }
                                
                                if (currentSeason != null) {
                                    Log.d("JellyfinPlayer", "Found current season: ${currentSeason.Name} (ID: ${currentSeason.Id})")
                                    
                                    // Try to get next episode in the SAME season
                                    foundNextEpisode = apiService.getNextEpisodeInSeason(
                                        seriesId = details.SeriesId,
                                        seasonId = currentSeason.Id,
                                        currentEpisodeIndex = details.IndexNumber,
                                        currentSeasonNumber = details.ParentIndexNumber
                                    )
                                    
                                    if (foundNextEpisode != null) {
                                        Log.d("JellyfinPlayer", "✅ Found next episode in same season: S${foundNextEpisode.ParentIndexNumber}E${foundNextEpisode.IndexNumber} - ${foundNextEpisode.Name}")
                                    } else {
                                        // No more episodes in current season, try next season's first episode
                                        Log.d("JellyfinPlayer", "No more episodes in S${details.ParentIndexNumber}, checking next season...")
                                    val nextSeason = seasons.firstOrNull { 
                                            it.IndexNumber == details.ParentIndexNumber + 1 
                                    }
                                    
                                    if (nextSeason != null) {
                                            Log.d("JellyfinPlayer", "Found next season: ${nextSeason.Name} (ID: ${nextSeason.Id})")
                                            // Get first episode of next season (episode index 0, looking for episode 1)
                                            foundNextEpisode = apiService.getNextEpisodeInSeason(
                                                seriesId = details.SeriesId,
                                                seasonId = nextSeason.Id,
                                                currentEpisodeIndex = 0, // Looking for episode 1
                                                currentSeasonNumber = nextSeason.IndexNumber ?: (details.ParentIndexNumber + 1)
                                            )
                                            
                                            // If that didn't work, try getting all episodes from next season
                                            if (foundNextEpisode == null) {
                                                val nextSeasonEpisodes = apiService.getEpisodes(details.SeriesId, nextSeason.Id)
                                                foundNextEpisode = nextSeasonEpisodes.firstOrNull()
                                        }
                                        
                                        if (foundNextEpisode != null) {
                                                Log.d("JellyfinPlayer", "✅ Found first episode of next season: S${foundNextEpisode.ParentIndexNumber}E${foundNextEpisode.IndexNumber} - ${foundNextEpisode.Name}")
                                        }
                                        } else {
                                            Log.d("JellyfinPlayer", "No next season found (this is the last episode of the series)")
                                    }
                                    }
                                } else {
                                    Log.e("JellyfinPlayer", "Could not find current season with IndexNumber=${details.ParentIndexNumber}")
                                }
                            } catch (e: Exception) {
                                Log.e("JellyfinPlayer", "Error finding next episode", e)
                                e.printStackTrace()
                            }
                        }
                        
                        if (foundNextEpisode != null) {
                            nextEpisodeId = foundNextEpisode.Id
                            nextEpisodeDetails = foundNextEpisode
                            Log.d("JellyfinPlayer", "✅✅✅ Next episode resolved: ${foundNextEpisode.Name}, ID: ${foundNextEpisode.Id}")
                            Log.d("JellyfinPlayer", "✅ Next episode IndexNumber: ${foundNextEpisode.IndexNumber}, Season: ${foundNextEpisode.ParentIndexNumber}")
                        } else {
                            Log.d("JellyfinPlayer", "No next episode found (this might be the last episode)")
                        }
                    }
                    
                    isLoading = false
                } else {
                    Log.e("JellyfinPlayer", "Failed to fetch item details")
                    isLoading = false
                }
            } catch (e: Exception) {
                Log.e("JellyfinPlayer", "Error preparing video", e)
                isLoading = false
            }
        }
    }

    // Initialize player when media URL is ready
    LaunchedEffect(mediaUrl) {
        if (mediaUrl != null && !playerInitialized) {
            withContext(Dispatchers.Main) {
                try {
                    
                    // Get authentication headers
                    val headers = apiService.getVideoRequestHeaders()

                    // Create HTTP data source factory with headers
                    // For 416 errors, we'll retry with range requests disabled
                    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent("Jellyfin Android TV")
                        .setAllowCrossProtocolRedirects(true)
                    
                    // Set headers using setDefaultRequestProperties
                    val headersMap = headers.toMutableMap()
                    httpDataSourceFactory.setDefaultRequestProperties(headersMap)
                    

                    // Create data source factory
                    val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

                    // Store mediaUrl in local variable for smart cast
                    val currentMediaUrl = mediaUrl ?: return@withContext
                    
                    // Detect if URL is HLS (ends with .m3u8 or contains master.m3u8)
                    val isHlsUrl = currentMediaUrl.contains(".m3u8", ignoreCase = true)

                    // Load ALL external subtitles into MediaItem (Jellyfin AndroidTV approach)
                    // This allows ExoPlayer to show the subtitle button and let users switch between them
                    val mediaItem = if (itemDetails != null) {
                        try {
                            val mediaSourceIdForSubtitle = itemDetails?.MediaSources?.firstOrNull()?.Id ?: item.Id
                            
                            // Get ALL subtitle streams from Jellyfin
                            val allSubtitleStreams = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                ?.filter { it.Type == "Subtitle" && it.Index != null }
                                ?: emptyList()
                            
                            Log.d("JellyfinPlayer", "Found ${allSubtitleStreams.size} subtitle stream(s) from Jellyfin")
                            
                            // Store subtitle streams for composite key registration in onTracksChanged
                            jellyfinSubtitleStreams = allSubtitleStreams
                            
                            // Reset SubtitleMapper for new playback session
                            com.flex.elefin.player.SubtitleMapper.reset()
                            
                            // Create SubtitleConfiguration for each subtitle using SubtitleMapper
                            // Uses COMPOSITE KEY approach (production-safe, used by Plex/Emby/Jellyfin TV)
                            val subtitleConfigurations = allSubtitleStreams.map { stream ->
                                try {
                                    val subtitleIndex = stream.Index ?: return@map null
                                    val subtitleUrl = apiService.buildJellyfinSubtitleUrl(
                                        itemId = item.Id,
                                        mediaSourceId = mediaSourceIdForSubtitle,
                                        streamIndex = subtitleIndex,
                                        isExternal = stream.IsExternal == true,
                                        codec = stream.Codec,
                                        path = stream.Path
                                    )
                                    
                                    Log.d("JellyfinPlayer", "Adding subtitle ${stream.Index}: ${stream.DisplayTitle ?: stream.Language} (${stream.Codec}) - IsExternal=${stream.IsExternal}")
                                    
                                    // Use SubtitleMapper to create configuration with position tracking
                                    // ⚠️ CRITICAL: Use actual Jellyfin index, NOT sequential position!
                                    // This ensures SubtitleMapper correctly maps Jellyfin index → ExoPlayer track
                                    com.flex.elefin.player.SubtitleMapper.buildSubtitleConfiguration(
                                        context = context,
                                        apiService = apiService,
                                        itemId = item.Id,
                                        mediaSourceId = mediaSourceIdForSubtitle ?: item.Id,
                                        stream = stream,
                                        positionIndex = subtitleIndex  // Use actual JF index, not sequential!
                                    )
                                } catch (e: Exception) {
                                    Log.w("JellyfinPlayer", "Failed to create subtitle config for index ${stream.Index}: ${e.message}")
                                    null
                                }
                            }.filterNotNull()
                            
                            Log.d("JellyfinPlayer", "Successfully created ${subtitleConfigurations.size} Jellyfin subtitle configuration(s)")
                            subtitleConfigurations.forEachIndexed { index, config ->
                                Log.d("JellyfinPlayer", "  [$index] ${config.uri}")
                                Log.d("JellyfinPlayer", "       Lang: ${config.language}, MIME: ${config.mimeType}, Label: ${config.label}")
                            }
                            
                            // ⭐ ADD DOWNLOADED OPENSUBTITLES (if any exist for this item)
                            val downloadedSubtitles = com.flex.elefin.subtitles.OpenSubtitlesApi.getDownloadedSubtitles(context, item.Id)
                            val downloadedSubtitleConfigs = downloadedSubtitles.mapNotNull { downloadedSub ->
                                try {
                                    Log.d("JellyfinPlayer", "📁 Adding downloaded subtitle: ${downloadedSub.fileName}")
                                    com.flex.elefin.player.SubtitleMapper.buildLocalSubtitleConfiguration(
                                        filePath = downloadedSub.filePath,
                                        language = downloadedSub.language,
                                        label = "${com.flex.elefin.subtitles.SubtitleLanguages.getDisplayName(downloadedSub.language)} (Downloaded)"
                                    )
                                } catch (e: Exception) {
                                    Log.w("JellyfinPlayer", "Failed to add downloaded subtitle ${downloadedSub.fileName}: ${e.message}")
                                    null
                                }
                            }
                            
                            if (downloadedSubtitleConfigs.isNotEmpty()) {
                                Log.d("JellyfinPlayer", "✅ Added ${downloadedSubtitleConfigs.size} downloaded subtitle(s) from OpenSubtitles")
                            }
                            
                            // Combine Jellyfin subtitles + downloaded OpenSubtitles
                            val allSubtitleConfigs = subtitleConfigurations + downloadedSubtitleConfigs
                            Log.d("JellyfinPlayer", "Total subtitle configurations: ${allSubtitleConfigs.size} (${subtitleConfigurations.size} Jellyfin + ${downloadedSubtitleConfigs.size} downloaded)")
                            
                            // Create MediaItem with ALL subtitle configurations
                            if (allSubtitleConfigs.isNotEmpty()) {
                                MediaItem.Builder()
                                    .setUri(Uri.parse(currentMediaUrl))
                                    .setSubtitleConfigurations(allSubtitleConfigs)
                                    .build().also {
                                        Log.d("JellyfinPlayer", "✅ MediaItem created with ${allSubtitleConfigs.size} subtitle configuration(s)")
                                    }
                            } else {
                                Log.d("JellyfinPlayer", "No valid subtitle configurations - creating MediaItem without subtitles")
                                MediaItem.fromUri(Uri.parse(currentMediaUrl))
                            }
                        } catch (e: Exception) {
                            Log.e("JellyfinPlayer", "❌ Error creating MediaItem with subtitles: ${e.message}", e)
                            Log.e("JellyfinPlayer", "   Playing video without subtitles")
                            MediaItem.fromUri(Uri.parse(currentMediaUrl))
                        }
                    } else {
                        Log.d("JellyfinPlayer", "No item details - creating MediaItem without subtitles")
                        MediaItem.fromUri(Uri.parse(currentMediaUrl))
                    }
                    
                    // Create media source from MediaItem - use DefaultMediaSourceFactory for proper subtitle support
                    // DefaultMediaSourceFactory automatically detects the media type and handles subtitles
                    val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                    val mediaSource: MediaSource = mediaSourceFactory.createMediaSource(mediaItem)
                    Log.d("JellyfinPlayer", "Created MediaSource using DefaultMediaSourceFactory")

                    // Set media source
                    player.setMediaSource(mediaSource)

                    // Store media source for potential retry
                    currentMediaSource = mediaSource

                    // Handle player lifecycle
                    player.addListener(object : Player.Listener {
                        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                            Log.d("JellyfinPlayer", "📺 Video size changed: ${videoSize.width}x${videoSize.height}")
                            
                            // Update GL surface with video dimensions for proper aspect ratio
                            if (videoSize.width > 0 && videoSize.height > 0) {
                                glSurfaceViewRef.value?.setVideoSize(videoSize.width, videoSize.height)
                            }
                            
                            // Also check codec from videoFormat when size changes
                            val format = player.videoFormat
                            val codec = format?.codecs ?: format?.sampleMimeType ?: "unknown"
                            Log.d("JellyfinPlayer", "📺 Video format: codec=$codec, mime=${format?.sampleMimeType}")
                            
                            if (videoSize.width == 0 || videoSize.height == 0) {
                                Log.w("JellyfinPlayer", "⚠️ Video size is 0x0 - video may not be rendering!")
                                
                                // If AV1 was detected and we have black screen, this confirms the issue
                                if (runtimeAV1Detected) {
                                    Log.e("JellyfinPlayer", "❌ CONFIRMED: AV1 + current surface = no video output")
                                    Log.e("JellyfinPlayer", "❌ Solution: Disable GL Enhancements in Settings")
                                }
                            }
                        }
                        
                        override fun onRenderedFirstFrame() {
                            Log.d("JellyfinPlayer", "🎬 First video frame rendered!")
                            if (runtimeAV1Detected) {
                                Log.d("JellyfinPlayer", "✅ AV1 video is rendering successfully!")
                            }
                        }
                        
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            Log.e("JellyfinPlayer", "Player error: ${error.message}", error)
                            Log.e("JellyfinPlayer", "Error type: ${error.errorCode}, Cause: ${error.cause?.javaClass?.simpleName}")
                            
                            // Check for 10-bit AV1 specific error from libgav1
                            val causeMessage = error.cause?.message ?: ""
                            val is10BitAV1Error = causeMessage.contains("High bit depth") && 
                                                  causeMessage.contains("not supported with YUV surface")
                            
                            // Check if we should try auto-transcode fallback
                            val isDecoderError = is10BitAV1Error || 
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                            
                            if (isDecoderError && autoTranscodeOnError && !hasTriedTranscodeFallback && !isUsingTranscodeFallback) {
                                Log.w("JellyfinPlayer", "")
                                Log.w("JellyfinPlayer", "🔄🔄🔄 AUTO-TRANSCODE FALLBACK TRIGGERED 🔄🔄🔄")
                                Log.w("JellyfinPlayer", "")
                                Log.w("JellyfinPlayer", "⚠️ Direct play failed - attempting server-side transcoding...")
                                Log.w("JellyfinPlayer", "   Error: ${error.message}")
                                Log.w("JellyfinPlayer", "")
                                
                                hasTriedTranscodeFallback = true
                                
                                // Retry with transcoded stream
                                scope.launch(Dispatchers.Main) {
                                    try {
                                        player.stop()
                                        player.clearMediaItems()
                                        
                                        // Get transcoding settings
                                        val transcodeTargetCodec = settings.transcodeTargetCodec
                                        val transcodeMaxBitrate = settings.transcodeMaxBitrateMbps
                                        
                                        // Get the transcoded URL
                                        val transcodedUrl = apiService.getTranscodedVideoUrl(
                                            itemId = item.Id,
                                            mediaSourceId = itemDetails?.MediaSources?.firstOrNull()?.Id,
                                            subtitleStreamIndex = null,
                                            targetVideoCodec = transcodeTargetCodec,
                                            maxBitrateMbps = transcodeMaxBitrate,
                                            audioCodec = "aac"
                                        )
                                        
                                        Log.d("JellyfinPlayer", "🔄 Transcoded URL: $transcodedUrl")
                                        
                                        // Create HLS media source for transcoded stream
                                        val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                                            .createMediaSource(MediaItem.fromUri(Uri.parse(transcodedUrl)))
                                        
                                        player.setMediaSource(hlsMediaSource)
                                        player.prepare()
                                        player.play()
                                        
                                        isUsingTranscodeFallback = true
                                        Log.d("JellyfinPlayer", "✅ Switched to server transcoding: $transcodeTargetCodec @ ${transcodeMaxBitrate}Mbps")
                                        
                                        // Report playback start for transcode fallback
                                        scope.launch(Dispatchers.IO) {
                                            apiService.reportPlaybackStart(item.Id, 0)
                                        }
                                        
                                    } catch (e: Exception) {
                                        Log.e("JellyfinPlayer", "❌ Failed to switch to transcoding: ${e.message}", e)
                                        
                                        // Try MPV fallback if transcoding failed
                                        if (fallbackToMpv && isMpvInstalled && !hasTriedMpvFallback) {
                                            Log.w("JellyfinPlayer", "🎬 Transcoding failed - trying MPV fallback...")
                                            hasTriedMpvFallback = true
                                            launchMpvFallback()
                                        } else {
                                            showAV1Error = true
                                        }
                                    }
                                }
                                // Don't show error dialog - we're retrying with transcoding
                            } else if (isDecoderError && fallbackToMpv && isMpvInstalled && !hasTriedMpvFallback) {
                                // Transcoding is disabled, try MPV fallback
                                Log.w("JellyfinPlayer", "")
                                Log.w("JellyfinPlayer", "🎬🎬🎬 MPV FALLBACK TRIGGERED 🎬🎬🎬")
                                Log.w("JellyfinPlayer", "")
                                Log.w("JellyfinPlayer", "⚠️ ExoPlayer failed and transcoding is disabled")
                                Log.w("JellyfinPlayer", "⚠️ Launching MPV player as fallback...")
                                Log.w("JellyfinPlayer", "")
                                
                                hasTriedMpvFallback = true
                                launchMpvFallback()
                            } else if (is10BitAV1Error) {
                                Log.e("JellyfinPlayer", "")
                                Log.e("JellyfinPlayer", "❌❌❌ 10-BIT AV1 DECODING ERROR ❌❌❌")
                                Log.e("JellyfinPlayer", "")
                                Log.e("JellyfinPlayer", "⚠️ libgav1 cannot output 10-bit video to standard surface")
                                Log.e("JellyfinPlayer", "⚠️ This is a known limitation of the AV1 software decoder")
                                Log.e("JellyfinPlayer", "")
                                Log.e("JellyfinPlayer", "✅ SOLUTIONS:")
                                Log.e("JellyfinPlayer", "   1. Enable MPV Player: Settings → Playback → Use MPV Player")
                                Log.e("JellyfinPlayer", "      MPV uses dav1d which handles 10-bit AV1 natively")
                                Log.e("JellyfinPlayer", "   2. Server Transcoding: Configure Jellyfin to transcode AV1")
                                Log.e("JellyfinPlayer", "      Dashboard → Playback → Transcoding → AV1 → H.264/H.265")
                                Log.e("JellyfinPlayer", "")
                                
                                // Show user-friendly error to user
                                showAV1Error = true
                            }
                            // Check for other decoder errors
                            else if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                                error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED) {
                                Log.e("JellyfinPlayer", "❌ VIDEO DECODER ERROR: Device may not support this video codec!")
                                Log.e("JellyfinPlayer", "   Consider enabling server-side transcoding for this content")
                                
                                if (runtimeAV1Detected) {
                                    Log.e("JellyfinPlayer", "❌ AV1 DECODER FAILURE!")
                                    Log.e("JellyfinPlayer", "   Enable MPV Player or configure Jellyfin to transcode AV1 → H.264/H.265")
                                    showAV1Error = true
                                }
                            }
                            
                            // Check for subtitle-specific errors
                            if (error.cause is ParserException || error.message?.contains("subtitle", ignoreCase = true) == true) {
                                Log.e("JellyfinPlayer", "❌ SUBTITLE LOAD ERROR: This might be why external subtitles aren't appearing!")
                                Log.e("JellyfinPlayer", "   Error details: ${error.cause?.message ?: "Unknown"}")
                            }
                            
                            // Check if it's an HTTP 416 error (Range Not Satisfiable)
                            if (error.cause is HttpDataSource.InvalidResponseCodeException) {
                                val httpError = error.cause as HttpDataSource.InvalidResponseCodeException
                                if (httpError.responseCode == 416 && !hasRetriedWithoutRange && mediaUrl != null) {
                                    Log.w("JellyfinPlayer", "HTTP 416 error detected. Retrying without range requests...")
                                    scope.launch(Dispatchers.Main) {
                                        try {
                                            // Stop current playback
                                            player.stop()
                                            player.clearMediaItems()
                                            
                                            // Create a DataSource wrapper factory that removes range requests to avoid 416 errors
                                            val noRangeDataSourceFactory = object : DataSource.Factory {
                                                private val baseHttpFactory = DefaultHttpDataSource.Factory()
                                                    .setUserAgent("Jellyfin Android TV")
                                                    .setAllowCrossProtocolRedirects(true)
                                                    .setDefaultRequestProperties(headers.toMutableMap())
                                                
                                                private val baseFactory = DefaultDataSource.Factory(context, baseHttpFactory)
                                                
                                                override fun createDataSource(): DataSource {
                                                    val baseDataSource = baseFactory.createDataSource()
                                                    
                                                    // Return a wrapper that modifies DataSpecs to remove range information
                                                    return object : DataSource {
                                                        override fun open(dataSpec: DataSpec): Long {
                                                            // Remove range information to avoid 416 errors
                                                            // If DataSpec has position or length set, remove them to request entire file
                                                            val modifiedDataSpec = if (dataSpec.position != 0L || dataSpec.length > 0) {
                                                                // Create a new DataSpec without range request (request entire file)
                                                                DataSpec.Builder()
                                                                    .setUri(dataSpec.uri)
                                                                    .setHttpMethod(dataSpec.httpMethod)
                                                                    .setHttpRequestHeaders(dataSpec.httpRequestHeaders)
                                                                    .setKey(dataSpec.key)
                                                                    .setFlags(dataSpec.flags)
                                                                    .setPosition(0L) // Start from beginning
                                                                    .setLength(C.LENGTH_UNSET.toLong()) // Request entire file
                                                                    .build()
                                                            } else {
                                                                dataSpec
                                                            }
                                                            return baseDataSource.open(modifiedDataSpec)
                                                        }
                                                        
                                                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                                                            return baseDataSource.read(buffer, offset, length)
                                                        }
                                                        
                                                        override fun getUri(): android.net.Uri? {
                                                            return baseDataSource.uri
                                                        }
                                                        
                                                        override fun close() {
                                                            baseDataSource.close()
                                                        }
                                                        
                                                        override fun addTransferListener(transferListener: TransferListener) {
                                                            baseDataSource.addTransferListener(transferListener)
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            // Use the custom DataSource factory that removes range requests
                                            val retryDataSourceFactory = noRangeDataSourceFactory
                                            
                                            // Recreate MediaItem
                                            val retryMediaItem = if (subtitleStreamIndex != null && itemDetails != null) {
                                                try {
                                                    val mediaSourceIdForSubtitle = itemDetails?.MediaSources?.firstOrNull()?.Id ?: item.Id
                                                    val subtitleStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                                        ?.find { it.Type == "Subtitle" && it.Index == subtitleStreamIndex }
                                                    val subtitleUrl = apiService.buildJellyfinSubtitleUrl(
                                                        itemId = item.Id,
                                                        mediaSourceId = mediaSourceIdForSubtitle,
                                                        streamIndex = subtitleStreamIndex!!,
                                                        isExternal = subtitleStream?.IsExternal == true,
                                                        codec = subtitleStream?.Codec,
                                                        path = subtitleStream?.Path
                                                    )
                                                    val subtitleLanguage = subtitleStream?.Language
                                                    val subtitleMimeType = MimeTypes.TEXT_VTT
                                                    
                                                    MediaItem.Builder()
                                                        .setUri(Uri.parse(mediaUrl))
                                                        .setSubtitleConfigurations(
                                                            listOf(
                                                                MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                                                                    .setMimeType(subtitleMimeType)
                                                                    .setLanguage(subtitleLanguage)
                                                                    .build()
                                                            )
                                                        )
                                                        .build()
                                                } catch (e: Exception) {
                                                    MediaItem.fromUri(Uri.parse(mediaUrl))
                                                }
                                            } else {
                                                MediaItem.fromUri(Uri.parse(mediaUrl))
                                            }
                                            
                                            // Create new media source with the DataSource factory
                                            val retryMediaSource = ProgressiveMediaSource.Factory(retryDataSourceFactory as DataSource.Factory)
                                                .createMediaSource(retryMediaItem)
                                            
                                            // Set new media source and prepare
                                            player.setMediaSource(retryMediaSource)
                                            player.prepare()
                                            player.playWhenReady = true
                                            
                                            // Mark that we've retried
                                            hasRetriedWithoutRange = true
                                            hasSeekedToResume = false // Reset resume seek
                                            playerInitialized = true // Mark as initialized after retry
                                            
                                            Log.d("JellyfinPlayer", "Retried playback without range requests")
                                            
                                            // Report playback start for retry
                                            scope.launch(Dispatchers.IO) {
                                                apiService.reportPlaybackStart(item.Id, 0)
                                            }
                                        } catch (e: Exception) {
                                            Log.e("JellyfinPlayer", "Error retrying playback without range requests", e)
                                        }
                                    }
                                    return // Don't log as fatal error, we're handling it
                                }
                            }
                            
                            // Check if it's a parser error (malformed file) - fallback to MP4 transcoding
                            if (error.cause is ParserException && !hasRetriedWithHls && mediaUrl != null && itemDetails != null) {
                                Log.w("JellyfinPlayer", "Parser error detected (malformed file). Falling back to MP4 transcoding...")
                                scope.launch(Dispatchers.Main) {
                                    try {
                                        // Stop current playback
                                        player.stop()
                                        player.clearMediaItems()
                                        
                                        // Get media source ID
                                        val mediaSource = itemDetails?.MediaSources?.firstOrNull()
                                        val mediaSourceId = mediaSource?.Id ?: item.Id
                                        
                                        // Generate MP4 transcoding URL (server will transcode to MP4)
                                        val base = if (apiService.serverBaseUrl.endsWith("/")) apiService.serverBaseUrl else "${apiService.serverBaseUrl}/"
                                        val mp4Url = "${base}Videos/${item.Id}/stream.mp4?VideoCodec=h264&AudioCodec=aac&mediaSourceId=$mediaSourceId&api_key=${apiService.apiKey}"
                                        
                                        // Create media source with transcoded MP4
                                        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                                            .setUserAgent("Jellyfin Android TV")
                                            .setAllowCrossProtocolRedirects(true)
                                            .setDefaultRequestProperties(apiService.getVideoRequestHeaders().toMutableMap())
                                        
                                        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
                                        
                                        // Create MediaItem
                                        val transcodedMediaItem = if (subtitleStreamIndex != null && itemDetails != null) {
                                            try {
                                                val subtitleStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                                    ?.find { it.Type == "Subtitle" && it.Index == subtitleStreamIndex }
                                                
                                                val subtitleConfig = if (subtitleStream != null) {
                                                    Log.d("JellyfinPlayer", "🔄 Fallback: Adding subtitle ${subtitleStream.DisplayTitle}")
                                                    com.flex.elefin.player.SubtitleMapper.buildSubtitleConfiguration(
                                                        context = context,
                                                        apiService = apiService,
                                                        itemId = item.Id,
                                                        mediaSourceId = mediaSourceId, // Use the ID resolved above
                                                        stream = subtitleStream,
                                                        positionIndex = subtitleStream.Index ?: 0
                                                    )
                                                } else null
                                                
                                                val builder = MediaItem.Builder().setUri(Uri.parse(mp4Url))
                                                if (subtitleConfig != null) {
                                                    builder.setSubtitleConfigurations(listOf(subtitleConfig))
                                                }
                                                builder.build()
                                            } catch (e: Exception) {
                                                Log.e("JellyfinPlayer", "Error adding subtitle to fallback media item", e)
                                                MediaItem.fromUri(Uri.parse(mp4Url))
                                            }
                                        } else {
                                            MediaItem.fromUri(Uri.parse(mp4Url))
                                        }
                                        
                                        val transcodedMediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                                            .createMediaSource(transcodedMediaItem)
                                        
                                        // Set new media source and prepare
                                        player.setMediaSource(transcodedMediaSource)
                                        player.prepare()
                                        player.playWhenReady = true
                                        
                                        // Mark that we've retried
                                        hasRetriedWithHls = true
                                        hasSeekedToResume = false // Reset resume seek
                                        playerInitialized = true
                                        
                                        // Update mediaUrl for reference
                                        mediaUrl = mp4Url
                                        
                                        // Report playback start for HLS retry
                                        scope.launch(Dispatchers.IO) {
                                            apiService.reportPlaybackStart(item.Id, 0)
                                        }
                                        
                                        Log.d("JellyfinPlayer", "Retried playback with MP4 transcoding")
                                    } catch (e: Exception) {
                                        Log.e("JellyfinPlayer", "Error falling back to MP4 transcoding", e)
                                    }
                                }
                                return // Don't log as fatal error, we're handling it
                            }
                            
                            // For other errors, log and let the player handle it normally
                            Log.e("JellyfinPlayer", "Unhandled player error", error)
                        }

                        override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                            isPlaying = isPlayingNow
                        }

                        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                            // ============================================================
                            // RUNTIME AV1 DETECTION - This is the ONLY reliable way to detect AV1
                            // Jellyfin metadata often returns Codec=null, so we must detect from ExoPlayer tracks
                            // ============================================================
                            val videoTrack = tracks.groups
                                .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
                                .firstOrNull()
                            
                            if (videoTrack != null && videoTrack.length > 0) {
                                val videoFormat = videoTrack.mediaTrackGroup.getFormat(0)
                                val detectedMime = videoFormat.sampleMimeType ?: ""
                                val detectedCodecs = videoFormat.codecs ?: ""
                                
                                Log.d("JellyfinPlayer", "🔍 Runtime codec detection: mime=$detectedMime, codecs=$detectedCodecs")
                                
                                // Check for AV1 in MIME type or codecs string
                                val isAV1 = detectedMime.contains("av01", ignoreCase = true) || 
                                           detectedMime.contains("av1", ignoreCase = true) ||
                                           detectedCodecs.contains("av01", ignoreCase = true) ||
                                           detectedCodecs.contains("av1", ignoreCase = true)
                                
                                if (isAV1) {
                                    // Check bit depth from colorInfo (if available)
                                    val colorInfo = videoFormat.colorInfo
                                    val bitDepth = colorInfo?.lumaBitdepth ?: 8
                                    Log.e("JellyfinPlayer", "⚠️ RUNTIME AV1 DETECTED! mime=$detectedMime, codecs=$detectedCodecs, bitDepth=$bitDepth")
                                    Log.e("JellyfinPlayer", "   ColorInfo: $colorInfo")
                                    
                                    // AV1 with 10-bit will fail with libgav1 on standard surface
                                    // We'll detect this at error time and show the dialog
                                    Log.e("JellyfinPlayer", "")
                                    Log.e("JellyfinPlayer", "⚠️ AV1 DETECTED - May require special handling")
                                    Log.e("JellyfinPlayer", "⚠️ If video shows black screen, it's likely 10-bit AV1")
                                    Log.e("JellyfinPlayer", "⚠️ libgav1 cannot output 10-bit to standard SurfaceView")
                                    Log.e("JellyfinPlayer", "")
                                    Log.e("JellyfinPlayer", "✅ SOLUTIONS:")
                                    Log.e("JellyfinPlayer", "   1. Enable MPV Player in Settings → Playback → Use MPV Player")
                                    Log.e("JellyfinPlayer", "      MPV handles 10-bit AV1 natively via dav1d")
                                    Log.e("JellyfinPlayer", "   2. Configure Jellyfin server to transcode AV1 → H.264/H.265")
                                    Log.e("JellyfinPlayer", "      Dashboard → Playback → Transcoding")
                                    Log.e("JellyfinPlayer", "")
                                    runtimeAV1Detected = true
                                } else {
                                    Log.d("JellyfinPlayer", "✅ Non-AV1 codec detected: $detectedMime")
                                }
                            }
                            
                            // Log available tracks for debugging
                            Log.d("JellyfinPlayer", "Tracks changed: ${tracks.groups.size} track groups")
                            tracks.groups.forEach { group ->
                                Log.d("JellyfinPlayer", "Track group: type=${group.type}, supported=${group.isSupported}, trackCount=${group.mediaTrackGroup.length}, selected=${group.isSelected}")
                            }
                            
                            // When tracks are available, select subtitle track if specified
                            // Handle subtitle selection or disabling
                            // Filter out ONLY internal CEA-608/708 captions (auto-generated closed captions from video decoder)
                            // Keep all Jellyfin subtitles: external, embedded, and internal
                            // NOTE: APPLICATION_MEDIA3_CUES is the MIME type ExoPlayer uses for processed text subtitles, so we keep it
                            val textTrackGroups = tracks.groups.filter { group ->
                                if (group.type != androidx.media3.common.C.TRACK_TYPE_TEXT || !group.isSupported) {
                                    return@filter false
                                }
                                
                                val format = group.mediaTrackGroup.getFormat(0)
                                val isInternalCaption = format.sampleMimeType == MimeTypes.APPLICATION_CEA608 ||
                                                       format.sampleMimeType == MimeTypes.APPLICATION_CEA708
                                
                                // Only filter out CEA-608/708 captions, keep everything else
                                !isInternalCaption
                            }
                            
                            Log.d("JellyfinPlayer", "Found ${textTrackGroups.size} supported text track groups")
                            Log.d("JellyfinPlayer", "Jellyfin subtitle streams available for matching: ${jellyfinSubtitleStreams.size}")
                            
                            // ⭐ STEP 1: REGISTER ALL EXOPLAYER TRACKS WITH COMPOSITE KEYS (Production-Safe!)
                            // This must happen BEFORE selection logic so composite keys are available
                            // Only register tracks ONCE to prevent duplicates!
                            if (!hasRegisteredTracks && textTrackGroups.isNotEmpty()) {
                                hasRegisteredTracks = true // Mark as registered
                                Log.d("JellyfinPlayer", "⭐ STARTING TRACK REGISTRATION PHASE (first time only)")
                                Log.d("JellyfinPlayer", "   Text track groups to process: ${textTrackGroups.size}")
                                Log.d("JellyfinPlayer", "   Jellyfin subtitle streams to match: ${jellyfinSubtitleStreams.size}")
                                jellyfinSubtitleStreams.forEach { stream ->
                                    Log.d("JellyfinPlayer", "     JF Index=${stream.Index}, Lang=${stream.Language}, IsCC=${stream.IsHearingImpaired}, IsForced=${stream.IsForced}, IsExternal=${stream.IsExternal}")
                                }
                                
                                // ⚠️ CRITICAL: Find the ACTUAL group index in tracks.groups, not the filtered textTrackGroups index
                                textTrackGroups.forEachIndexed { filteredIndex, group ->
                                    // Find the original group index in the full tracks list
                                    val actualGroupIndex = tracks.groups.indexOf(group)
                                    
                                    val format = group.mediaTrackGroup.getFormat(0)
                                    val trackIndex = 0 // First track in group
                                    
                                    Log.d("JellyfinPlayer", "  Registering ExoPlayer subtitle track group $filteredIndex (actual index=$actualGroupIndex):")
                                    Log.d("JellyfinPlayer", "    Language: '${format.language}', Label: '${format.label}'")
                                    Log.d("JellyfinPlayer", "    MIME: ${format.sampleMimeType}, ID: ${format.id}")
                                    Log.d("JellyfinPlayer", "    Selection flags: ${format.selectionFlags}, Role flags: ${format.roleFlags}")
                                    
                                    // Match this ExoPlayer track to a Jellyfin subtitle by language + flags
                                    // ⚠️ DO NOT match by MIME - ExoPlayer transforms it to x-media3-cues!
                                    
                                    // Extract flags from ExoPlayer format
                                    val isForced = format.label?.contains("forced", ignoreCase = true) == true
                                    val isCC = format.label?.contains("cc", ignoreCase = true) == true || 
                                              format.label?.contains("sdh", ignoreCase = true) == true ||
                                              format.label?.contains("hearing impaired", ignoreCase = true) == true
                                    val isExternal = format.label?.contains("external", ignoreCase = true) == true
                                    
                                    Log.d("JellyfinPlayer", "    Detected flags: forced=$isForced, cc=$isCC, external=$isExternal")
                                    Log.d("JellyfinPlayer", "    Attempting to match against ${jellyfinSubtitleStreams.size} Jellyfin stream(s)")
                                    
                                    // ⚠️ CRITICAL: Match by language + flags, NOT by position!
                                    // ExoPlayer reorders tracks internally (alphabetically or by priority),
                                    // so filteredIndex doesn't correspond to Jellyfin Index order!
                                    
                                    // First try: exact match by language + flags
                                    var matchingStream = jellyfinSubtitleStreams.firstOrNull { stream ->
                                        val langMatch = stream.Language == format.language || 
                                                       stream.Language?.take(2) == format.language?.take(2) ||
                                                       normalizeLanguageCode(stream.Language) == normalizeLanguageCode(format.language)
                                        
                                        val forcedMatch = (stream.IsForced == true) == isForced
                                        val ccMatch = (stream.IsHearingImpaired == true) == isCC
                                        val externalMatch = (stream.IsExternal == true) == isExternal
                                        
                                        if (langMatch && forcedMatch && ccMatch && externalMatch) {
                                            Log.d("JellyfinPlayer", "      Exact match: JF index=${stream.Index} (${stream.Language}/${stream.DisplayTitle})")
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    
                                    // Second try: match by language only (ignore flags)
                                    if (matchingStream == null) {
                                        matchingStream = jellyfinSubtitleStreams.firstOrNull { stream ->
                                            val langMatch = stream.Language == format.language || 
                                                           stream.Language?.take(2) == format.language?.take(2) ||
                                                           normalizeLanguageCode(stream.Language) == normalizeLanguageCode(format.language)
                                            
                                            if (langMatch) {
                                                Log.d("JellyfinPlayer", "      Language-only match: JF index=${stream.Index} (${stream.Language}/${stream.DisplayTitle})")
                                            }
                                            langMatch
                                        }
                                    }
                                    
                                    if (matchingStream?.Index != null) {
                                        // Register track with composite key using the ACTUAL group index
                                        com.flex.elefin.player.SubtitleMapper.registerExoPlayerTrack(
                                            format = format,
                                            groupIndex = actualGroupIndex,
                                            trackIndex = trackIndex,
                                            jellyfinIndex = matchingStream.Index,
                                            metadata = matchingStream
                                        )
                                        Log.d("JellyfinPlayer", "    ✅ Registered: Filtered=$filteredIndex, Actual=$actualGroupIndex → JF index=${matchingStream.Index}")
                                    } else {
                                        Log.w("JellyfinPlayer", "    ⚠️ Could NOT match to Jellyfin subtitle (CEA-608/internal?)")
                                    }
                                }
                                Log.d("JellyfinPlayer", "⭐ TRACK REGISTRATION COMPLETE")
                            } else {
                                Log.d("JellyfinPlayer", "⚠️ Skipping track re-registration (already registered)")
                            }
                            
                            // ⭐ STEP 2: CHECK IF USER SELECTED A SUBTITLE
                            // Find the selected track and its group index using composite key resolution
                            val selectedFilteredIndex = textTrackGroups.indexOfFirst { it.isSelected }
                            
                            if (selectedFilteredIndex >= 0) {
                                val selectedTextTrackGroup = textTrackGroups[selectedFilteredIndex]
                                // Get the ACTUAL group index in the full tracks list
                                val selectedActualGroupIndex = tracks.groups.indexOf(selectedTextTrackGroup)
                                val selectedFormat = selectedTextTrackGroup.mediaTrackGroup.getFormat(0)
                                val trackIndex = 0 // First track in group
                                
                                // ⭐ RESOLVE USING COMPOSITE KEY (100% RELIABLE!)
                                // Uses stable ExoPlayer attributes: groupIndex + trackIndex + MIME + language + flags
                                val (jellyfinIndex, metadata) = com.flex.elefin.player.SubtitleMapper.resolveJellyfinIndexFromFormat(
                                    format = selectedFormat,
                                    groupIndex = selectedActualGroupIndex,  // Use ACTUAL index, not filtered
                                    trackIndex = trackIndex
                                )
                                
                                Log.d("JellyfinPlayer", "Subtitle selected via ExoPlayer controller:")
                                Log.d("JellyfinPlayer", "  Filtered=$selectedFilteredIndex, Actual=$selectedActualGroupIndex, Track=$trackIndex")
                                Log.d("JellyfinPlayer", "  Format.id = '${selectedFormat.id}' (ExoPlayer internal)")
                                Log.d("JellyfinPlayer", "  Language = ${selectedFormat.language}, MIME = ${selectedFormat.sampleMimeType}")
                                
                                if (jellyfinIndex != null) {
                                    Log.d("JellyfinPlayer", "🔥 Composite key resolved: Jellyfin index=$jellyfinIndex")
                                    Log.d("JellyfinPlayer", "   Metadata: ${metadata?.DisplayTitle ?: metadata?.Language}, IsExternal=${metadata?.IsExternal}, IsForced=${metadata?.IsForced}")
                                    
                                    // Save the selection (including forced subtitles - don't clear anything)
                                    if (jellyfinIndex != lastSelectedSubtitleIndex) {
                                        lastSelectedSubtitleIndex = jellyfinIndex
                                        currentSubtitleIndex = jellyfinIndex
                                        settings.setSubtitlePreference(item.Id, jellyfinIndex)
                                        Log.d("JellyfinPlayer", "💾 Saved subtitle preference: $jellyfinIndex (COMPOSITE KEY - 100% RELIABLE)")
                                    }
                                } else {
                                    // Could not resolve - ExoPlayer internal track (CEA-608, etc.)
                                    Log.w("JellyfinPlayer", "⚠️ Composite key resolution failed")
                                    Log.w("JellyfinPlayer", "   This is likely: CEA-608, embedded metadata, or ExoPlayer auto-generated track")
                                    Log.w("JellyfinPlayer", "   Not saving preference (doesn't map to Jellyfin subtitle)")
                                }
                            } else if (lastSelectedSubtitleIndex != null || textTrackGroups.isNotEmpty()) {
                                // No subtitle selected (user deselected via ExoPlayer's UI)
                                // Clear our overrides to respect the user's choice
                                if (lastSelectedSubtitleIndex != null) {
                                    Log.d("JellyfinPlayer", "Subtitle deselected via ExoPlayer controller")
                                    lastSelectedSubtitleIndex = null
                                    currentSubtitleIndex = null
                                    settings.setSubtitlePreference(item.Id, null)
                                }
                                
                                // Actually clear the subtitle selection by removing overrides
                                try {
                                    val updatedParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                        .build()
                                    
                                    player.trackSelectionParameters = updatedParameters
                                    Log.d("JellyfinPlayer", "✅ Cleared subtitle overrides (user selected None)")
                                } catch (e: Exception) {
                                    Log.w("JellyfinPlayer", "Error clearing subtitle overrides: ${e.message}", e)
                                }
                            }
                            
                            // Ensure subtitle button is visible in controller when tracks are available
                            playerViewRef.value?.let { view ->
                                view.post {
                                    // Explicitly show subtitle button when tracks are available
                                    view.setShowSubtitleButton(textTrackGroups.isNotEmpty())
                                    
                                    val controller = view.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                                    controller?.let { controlView ->
                                        // The subtitle button should automatically appear when text tracks are available
                                        // Force a refresh of the controller to ensure button visibility
                                        controlView.invalidate()
                                        
                                        // ⚠️ Keep default subtitle button hidden (we're using custom settings button)
                                        val subtitleButton = controlView.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_subtitle)
                                        subtitleButton?.let { button ->
                                            button.visibility = android.view.View.GONE
                                        }
                                        
                                        Log.d("JellyfinPlayer", "Controller invalidated to refresh subtitle button visibility. Text tracks: ${textTrackGroups.size}, Subtitle button visible: ${subtitleButton?.visibility == android.view.View.VISIBLE}")
                                    }
                                }
                            }
                            
                            // Only apply subtitleStreamIndex (from series/movie page) ONCE on initial load
                            // After that, let the user control subtitles via ExoPlayer UI
                            if (subtitleStreamIndex != null && itemDetails != null && textTrackGroups.isNotEmpty() && !hasAppliedInitialSubtitlePreference) {
                                // User selected a subtitle track from series/movie page - select it ONCE
                                hasAppliedInitialSubtitlePreference = true // Mark as applied so it doesn't re-apply
                                try {
                                    Log.d("JellyfinPlayer", "⭐ Applying initial subtitle preference: Jellyfin index=$subtitleStreamIndex")
                                    
                                    // ⭐ USE SUBTITLEMAPPER TO GET EXOPLAYER TRACK INFO (100% RELIABLE!)
                                    val trackInfo = com.flex.elefin.player.SubtitleMapper.getExoPlayerTrackInfo(subtitleStreamIndex)
                                    
                                    if (trackInfo != null) {
                                        val (actualGroupIndex, trackIndex) = trackInfo
                                        Log.d("JellyfinPlayer", "  SubtitleMapper found: ExoPlayer group=$actualGroupIndex, track=$trackIndex")
                                        
                                        // Find the track group in our list using the actual group index
                                        val groupToSelect = tracks.groups.getOrNull(actualGroupIndex)
                                        
                                        if (groupToSelect != null && groupToSelect.type == C.TRACK_TYPE_TEXT) {
                                            val trackSelectionOverride = TrackSelectionOverride(
                                                groupToSelect.mediaTrackGroup,
                                                trackIndex
                                            )
                                            
                                            val updatedParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .addOverride(trackSelectionOverride)
                                                .build()
                                            
                                            player.trackSelectionParameters = updatedParameters
                                            currentSubtitleIndex = subtitleStreamIndex
                                            lastSelectedSubtitleIndex = subtitleStreamIndex
                                            
                                            Log.d("JellyfinPlayer", "✅ Applied subtitle preference using SubtitleMapper!")
                                            Log.d("JellyfinPlayer", "   Jellyfin index=$subtitleStreamIndex → ExoPlayer group=$actualGroupIndex")
                                        } else {
                                            Log.w("JellyfinPlayer", "⚠️ Track group not found or not a text track: actualGroupIndex=$actualGroupIndex")
                                        }
                                    } else {
                                        Log.w("JellyfinPlayer", "⚠️ SubtitleMapper could not find ExoPlayer track for Jellyfin index=$subtitleStreamIndex")
                                        Log.w("JellyfinPlayer", "   This might happen if track registration hasn't completed yet")
                                    }
                                } catch (e: Exception) {
                                    Log.w("JellyfinPlayer", "Error applying subtitle preference: ${e.message}", e)
                                }
                            } else if (subtitleStreamIndex == null && currentSubtitleIndex == null && textTrackGroups.isNotEmpty() && textTrackGroups.none { it.isSelected } && !hasAppliedInitialSubtitlePreference) {
                                // User explicitly selected "None" from series/movie page AND no subtitle is currently selected
                                // Only apply this ONCE on initial load
                                hasAppliedInitialSubtitlePreference = true // Mark as applied
                                try {
                                    // Clear any existing subtitle overrides to ensure no subtitles are selected
                                    val updatedParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .clearOverrides() // Clear any subtitle overrides
                                        .build()
                                    
                                    player.trackSelectionParameters = updatedParameters
                                    Log.d("JellyfinPlayer", "✅ Cleared subtitle track selection (None selected)")
                                } catch (e: Exception) {
                                    Log.w("JellyfinPlayer", "Error clearing subtitle track selection: ${e.message}", e)
                                }
                            }
                            
                            // ⚠️ DEPRECATED OLD MATCHING LOGIC - KEPT FOR REFERENCE BUT NOT USED
                            /*
                            // Get the subtitle stream info from Jellyfin to match by language
                            val subtitleStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                ?.find { it.Type == "Subtitle" && it.Index == subtitleStreamIndex }
                            val subtitleLanguage = subtitleStream?.Language
                                    
                                    // Get all Jellyfin subtitle streams sorted by Index to create a mapping
                                    val allJellyfinSubtitleStreams = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                        ?.filter { it.Type == "Subtitle" }
                                        ?.sortedBy { it.Index ?: 0 } ?: emptyList()
                                    
                                    Log.d("JellyfinPlayer", "Jellyfin subtitle streams: ${allJellyfinSubtitleStreams.map { "Index=${it.Index}, Language=${it.Language}" }}")
                                    Log.d("JellyfinPlayer", "ExoPlayer track groups: ${textTrackGroups.mapIndexed { idx, group -> "Index=$idx, Language=${group.mediaTrackGroup.getFormat(0).language}, MimeType=${group.mediaTrackGroup.getFormat(0).sampleMimeType}" }}")
                                    
                                    // Find the track group that matches the subtitle we loaded via SubtitleConfiguration
                                    // The subtitle we loaded should be in the track groups, and we can match it by:
                                    // 1. Language (exact or partial match, including ISO 639-1 vs ISO 639-2 variations)
                                    // 2. MIME type (VTT) if we loaded it
                                    // 3. Position in sorted Jellyfin list (if we loaded it via SubtitleConfiguration, it should be at a predictable position)
                                    
                                    // Language code mapping for common variations (ISO 639-1 to ISO 639-2)
                                    val languageVariations = if (subtitleLanguage != null) {
                                        val lang = subtitleLanguage.lowercase()
                                        when {
                                            lang == "tur" || lang == "tr" -> listOf("tur", "tr", "turkish")
                                            lang == "vie" || lang == "vi" -> listOf("vie", "vi", "vietnamese")
                                            lang == "eng" || lang == "en" -> listOf("eng", "en", "english")
                                            lang == "spa" || lang == "es" -> listOf("spa", "es", "spanish")
                                            lang == "fra" || lang == "fr" -> listOf("fra", "fr", "french")
                                            lang == "deu" || lang == "de" -> listOf("deu", "de", "german")
                                            lang == "jpn" || lang == "ja" -> listOf("jpn", "ja", "japanese")
                                            lang == "kor" || lang == "ko" -> listOf("kor", "ko", "korean")
                                            lang == "chi" || lang == "zh" -> listOf("chi", "zh", "chinese")
                                            else -> listOf(lang, lang.take(2), lang.take(3))
                                        }
                                    } else {
                                        emptyList()
                                    }
                                    
                                    // Try to match by language first, then by position in sorted list
                                    val groupToSelect = if (subtitleLanguage != null) {
                                        // Find track group with matching language
                                        textTrackGroups.firstOrNull { group ->
                                            val format = group.mediaTrackGroup.getFormat(0)
                                            // Match language code (e.g., "eng" matches "en" or "eng")
                                            format.language?.let { trackLang ->
                                                trackLang.equals(subtitleLanguage, ignoreCase = true) ||
                                                trackLang.startsWith(subtitleLanguage.take(2), ignoreCase = true) ||
                                                subtitleLanguage.startsWith(trackLang.take(2), ignoreCase = true)
                                            } ?: false
                                        } ?: run {
                                            // If no language match, try to match by position in the sorted list
                                            // Find the position of this subtitle in the sorted Jellyfin subtitle list
                                            val jellyfinPosition = allJellyfinSubtitleStreams.indexOfFirst { it.Index == subtitleStreamIndex }
                                            if (jellyfinPosition >= 0 && jellyfinPosition < textTrackGroups.size) {
                                                Log.d("JellyfinPlayer", "No language match found. Using position-based matching: Jellyfin position=$jellyfinPosition, ExoPlayer groups=${textTrackGroups.size}")
                                                textTrackGroups[jellyfinPosition]
                                            } else {
                                                // Last resort: log and use first available
                                                Log.w("JellyfinPlayer", "No language match and position out of range. Available track languages: ${textTrackGroups.map { it.mediaTrackGroup.getFormat(0).language }}")
                                                Log.w("JellyfinPlayer", "Looking for language: $subtitleLanguage, Jellyfin index: $subtitleStreamIndex")
                                                textTrackGroups.firstOrNull()
                                            }
                                        }
                                    } else {
                                        // No language info, try to match by position in sorted list
                                        val jellyfinPosition = allJellyfinSubtitleStreams.indexOfFirst { it.Index == subtitleStreamIndex }
                                        if (jellyfinPosition >= 0 && jellyfinPosition < textTrackGroups.size) {
                                            Log.d("JellyfinPlayer", "No language info. Using position-based matching: Jellyfin position=$jellyfinPosition")
                                            textTrackGroups[jellyfinPosition]
                                        } else {
                                            // Last resort: use first available
                                            Log.w("JellyfinPlayer", "No language info and position out of range. Using first available track.")
                                            textTrackGroups.firstOrNull()
                                        }
                                    }
                                    
                                    if (groupToSelect != null) {
                                    val trackSelectionOverride = androidx.media3.common.TrackSelectionOverride(
                                        groupToSelect.mediaTrackGroup,
                                        0 // Select first track in the group
                                    )
                                    
                                    val updatedParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .addOverride(trackSelectionOverride)
                                        .build()
                                    
                                    player.trackSelectionParameters = updatedParameters
                                    
                                    val selectedFormat = groupToSelect.mediaTrackGroup.getFormat(0)
                                    currentSubtitleIndex = subtitleStreamIndex
                                    lastSelectedSubtitleIndex = subtitleStreamIndex
                                    
                                    Log.d("JellyfinPlayer", "✅ Selected subtitle track:")
                                    Log.d("JellyfinPlayer", "   ExoPlayer: lang=${selectedFormat.language}, id=${selectedFormat.id}")
                                    Log.d("JellyfinPlayer", "   Jellyfin: index=$subtitleStreamIndex")
                                    Log.d("JellyfinPlayer", "   Composite key will be registered in onTracksChanged")
                                } else {
                                    Log.w("JellyfinPlayer", "Could not find matching ExoPlayer track group for Jellyfin subtitle index $subtitleStreamIndex")
                                }
                                } catch (e: Exception) {
                                    Log.w("JellyfinPlayer", "Error selecting subtitle track: ${e.message}", e)
                                }
                            }
                            */
                            
                            // Handle audio track selection
                            // Get all audio track groups (both supported and unsupported for mapping)
                            val allAudioTrackGroups = tracks.groups.filter { group ->
                                group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO
                            }
                            val audioTrackGroups = allAudioTrackGroups.filter { it.isSupported }
                            
                            Log.d("JellyfinPlayer", "Found ${allAudioTrackGroups.size} total audio track groups (${audioTrackGroups.size} supported)")
                            
                            // Log all audio tracks for debugging (both supported and unsupported)
                            allAudioTrackGroups.forEachIndexed { index, group ->
                                val format = group.mediaTrackGroup.getFormat(0)
                                Log.d("JellyfinPlayer", "Audio track group $index: language=${format.language}, codec=${format.codecs}, supported=${group.isSupported}, selected=${group.isSelected}")
                            }
                            
                            // Log current audio preference
                            val audioIndexToApply = storedAudioPreference ?: audioStreamIndex
                            Log.d("JellyfinPlayer", "Audio preference to apply: $audioIndexToApply (stored: $storedAudioPreference, provided: $audioStreamIndex, current: $currentAudioIndex)")
                            
                            // Get Jellyfin audio streams for mapping
                            val jellyfinAudioStreams = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                ?.filter { it.Type == "Audio" }
                                ?.sortedBy { it.Index ?: 0 } ?: emptyList()
                            
                            Log.d("JellyfinPlayer", "Jellyfin audio streams: ${jellyfinAudioStreams.map { "Index=${it.Index}, Language=${it.Language}, Codec=${it.Codec}" }}")
                            
                            // Check if user selected an audio track via ExoPlayer's controller
                            val selectedAudioTrackGroup = audioTrackGroups.firstOrNull { it.isSelected }
                            if (selectedAudioTrackGroup != null && itemDetails != null) {
                                // User selected an audio track via ExoPlayer's controller
                                val selectedFormat = selectedAudioTrackGroup.mediaTrackGroup.getFormat(0)
                                val selectedLanguage = selectedFormat.language
                                
                                // Try to match by language to find the Jellyfin audio stream index
                                val matchingAudioStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                    ?.filter { it.Type == "Audio" }
                                    ?.firstOrNull { stream ->
                                        stream.Language?.let { lang ->
                                            lang.equals(selectedLanguage, ignoreCase = true) ||
                                            lang.startsWith(selectedLanguage?.take(2) ?: "", ignoreCase = true) ||
                                            selectedLanguage?.startsWith(lang.take(2), ignoreCase = true) == true
                                        } == true
                                    }
                                
                                val newAudioIndex = matchingAudioStream?.Index
                                
                                if (newAudioIndex != null && newAudioIndex != lastSelectedAudioIndex) {
                                    Log.d("JellyfinPlayer", "Audio track selected via ExoPlayer controller: index=$newAudioIndex, language=${matchingAudioStream.Language}")
                                    lastSelectedAudioIndex = newAudioIndex
                                    currentAudioIndex = newAudioIndex
                                    
                                    // Save preference
                                    settings.setAudioPreference(item.Id, newAudioIndex)
                                }
                            }
                            
                            // Apply audio preference (from series/movie page or stored preference) if it's different from current selection
                            // Note: We apply even if currentAudioIndex matches, but ExoPlayer has auto-selected a different track
                            // Check allAudioTrackGroups (including unsupported) so we can force selection of unsupported tracks
                            if (audioIndexToApply != null && itemDetails != null && allAudioTrackGroups.isNotEmpty()) {
                                // Check if the currently selected track matches our preference
                                // Check allAudioTrackGroups (including unsupported) to see what's currently selected
                                val currentlySelected = allAudioTrackGroups.firstOrNull { it.isSelected }
                                val needsUpdate = if (currentlySelected != null) {
                                    // Check if the selected track matches our preference
                                    val selectedFormat = currentlySelected.mediaTrackGroup.getFormat(0)
                                    val selectedLanguage = selectedFormat.language
                                    val audioStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                                        ?.find { it.Type == "Audio" && it.Index == audioIndexToApply }
                                    val preferenceLanguage = audioStream?.Language
                                    
                                    // If languages don't match, or if currentAudioIndex doesn't match, we need to update
                                    val languageMatches = preferenceLanguage?.let { prefLang ->
                                        selectedLanguage?.let { selLang ->
                                            prefLang.equals(selLang, ignoreCase = true) ||
                                            prefLang.startsWith(selLang.take(2), ignoreCase = true) ||
                                            selLang.startsWith(prefLang.take(2), ignoreCase = true)
                                        } ?: false
                                    } ?: false
                                    
                                    !languageMatches || currentAudioIndex != audioIndexToApply
                                } else {
                                    // No track selected, we need to select our preference
                                    true
                                }
                                
                                if (needsUpdate) {
                                // User selected an audio track from series/movie page - select it
                                try {
                                    // Get the Jellyfin audio stream with the preferred index
                                    val preferredAudioStream = jellyfinAudioStreams.find { it.Index == audioIndexToApply }
                                    
                                    if (preferredAudioStream == null) {
                                        Log.w("JellyfinPlayer", "Jellyfin audio stream index $audioIndexToApply not found")
                                    } else {
                                        Log.d("JellyfinPlayer", "Looking for ExoPlayer track matching Jellyfin audio stream index=$audioIndexToApply, language=${preferredAudioStream.Language}, codec=${preferredAudioStream.Codec ?: "null"}")
                                        
                                        // Fix 2: Handle null codec - try to match by language even if codec is null
                                        // Fix 3: Force selection even for unsupported tracks
                                        var groupToSelect: Tracks.Group? = null
                                        var groupIndexToSelect = -1
                                        var trackIndexToSelect = 0
                                        
                                        // Try to find matching ExoPlayer track group by matching with all track groups (including unsupported)
                                        // First try to find by language and codec
                                        // Use for loop instead of forEach to allow early exit
                                        for ((groupIdx, group) in allAudioTrackGroups.withIndex()) {
                                            val format = group.mediaTrackGroup.getFormat(0)
                                            val trackLang = format.language
                                            val trackCodec = format.codecs
                                            
                                            // Match by language (primary)
                                            val languageMatch = preferredAudioStream.Language?.let { prefLang ->
                                                trackLang?.let { tLang ->
                                                    prefLang.equals(tLang, ignoreCase = true) ||
                                                    prefLang.startsWith(tLang.take(2), ignoreCase = true) ||
                                                    tLang.startsWith(prefLang.take(2), ignoreCase = true)
                                                } ?: false
                                            } ?: false
                                            
                                            // Match by codec (secondary, but only if codec is not null)
                                            val codecMatch = preferredAudioStream.Codec?.let { prefCodec ->
                                                trackCodec?.equals(prefCodec, ignoreCase = true) ?: false
                                            } ?: false
                                            
                                            // If language matches (or codec matches if both are available), select this track
                                            // Prefer supported tracks over unsupported ones
                                            if (languageMatch || codecMatch) {
                                                // If we haven't found a match yet, or if this one is supported and previous wasn't, use this one
                                                if (groupToSelect == null || (group.isSupported && !groupToSelect.isSupported)) {
                                                    groupToSelect = group
                                                    groupIndexToSelect = groupIdx
                                                    trackIndexToSelect = 0 // Select first track in the group
                                                    Log.d("JellyfinPlayer", "Found matching track: group=$groupIdx, language=$trackLang, codec=${trackCodec ?: "null"}, supported=${group.isSupported}")
                                                    
                                                    // If we found a supported match, we can break (it's the best match)
                                                    if (group.isSupported) {
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // If found a match, force selection even if unsupported (Fix 3)
                                        if (groupToSelect != null && groupIndexToSelect >= 0) {
                                            try {
                                                val trackGroup = groupToSelect.mediaTrackGroup
                                                
                                                // Fix 3: Try to force selection even for unsupported tracks
                                                // Use addOverride which sometimes works even for unsupported tracks
                                                // (especially with extension renderers enabled)
                                                val trackSelectionOverride = androidx.media3.common.TrackSelectionOverride(
                                                    trackGroup,
                                                    trackIndexToSelect
                                                )
                                                
                                                val updatedParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                                    .addOverride(trackSelectionOverride)
                                                    .build()
                                                
                                                player.trackSelectionParameters = updatedParameters
                                                
                                                val selectedFormat = groupToSelect.mediaTrackGroup.getFormat(0)
                                                currentAudioIndex = audioIndexToApply
                                                lastSelectedAudioIndex = audioIndexToApply
                                                
                                                if (groupToSelect.isSupported) {
                                                    Log.d("JellyfinPlayer", "✅ Selected audio track: language=${selectedFormat.language}, codec=${selectedFormat.codecs ?: "null"}, Jellyfin index=$audioIndexToApply, ExoPlayer group=$groupIndexToSelect")
                                                } else {
                                                    Log.d("JellyfinPlayer", "⚠️ Attempted to force selection of unsupported audio track: language=${selectedFormat.language}, codec=${selectedFormat.codecs ?: "null"}, Jellyfin index=$audioIndexToApply, ExoPlayer group=$groupIndexToSelect (may not work if codec truly unsupported)")
                                                }
                                            } catch (e: Exception) {
                                                Log.w("JellyfinPlayer", "Error selecting audio track: ${e.message}", e)
                                                // If it's unsupported and addOverride failed, log a warning
                                                if (!groupToSelect.isSupported) {
                                                    Log.w("JellyfinPlayer", "⚠️ Audio track index $audioIndexToApply is not supported by ExoPlayer (language=${preferredAudioStream.Language}, codec=${preferredAudioStream.Codec ?: "null"}) and cannot be forced")
                                                }
                                            }
                                        } else {
                                            Log.w("JellyfinPlayer", "⚠️ Could not find ExoPlayer track matching Jellyfin audio stream index=$audioIndexToApply (language=${preferredAudioStream.Language}, codec=${preferredAudioStream.Codec ?: "null"})")
                                            Log.d("JellyfinPlayer", "Available ExoPlayer tracks: ${allAudioTrackGroups.mapIndexed { idx, g -> 
                                                val f = g.mediaTrackGroup.getFormat(0)
                                                "Group $idx: lang=${f.language}, codec=${f.codecs ?: "null"}, supported=${g.isSupported}"
                                            }}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w("JellyfinPlayer", "Error selecting audio track: ${e.message}", e)
                                }
                                } else {
                                    Log.d("JellyfinPlayer", "Audio track already matches preference, no update needed")
                                }
                            } else {
                                Log.d("JellyfinPlayer", "No audio preference to apply (audioIndexToApply=$audioIndexToApply, itemDetails=${itemDetails != null}, audioTracks=${audioTrackGroups.size})")
                            }
                        }
                        
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_READY -> {
                                    Log.d("JellyfinPlayer", "Player ready")
                                    // Ensure PlayerView is visible when ready
                                    playerViewRef.value?.let { view ->
                                        if (view.visibility != android.view.View.VISIBLE) {
                                            view.visibility = android.view.View.VISIBLE
                                            Log.d("JellyfinPlayer", "Made PlayerView visible")
                                        }
                                        if (view.alpha != 1f) {
                                            view.alpha = 1f
                                        }
                                        // Force a layout pass to ensure rendering
                                        view.post {
                                            view.requestLayout()
                                            view.invalidate()
                                        }
                                    }
                                    // Seek to resume position only once, when player first becomes ready
                                    // IMPORTANT: Only seek if resumePositionMs > 0 (user clicked Resume or selected a chapter)
                                    // If resumePositionMs == 0, user clicked "Play From Start" so don't seek
                                    if (!hasSeekedToResume) {
                                        hasSeekedToResume = true // Mark as handled to prevent re-entry
                                        if (resumePositionMs > 0) {
                                            // Use the exact position passed in - this could be:
                                            // 1. Resume position from UserData (Resume button)
                                            // 2. Chapter start position (Chapter card click)
                                            // Don't override with fresh position as it breaks chapter playback
                                            player.seekTo(resumePositionMs)
                                            Log.d("JellyfinPlayer", "Seeked to position: ${resumePositionMs}ms")
                                        } else {
                                            Log.d("JellyfinPlayer", "Playing from start (resumePositionMs=0)")
                                        }
                                    }
                                }
                                Player.STATE_BUFFERING -> {
                                    Log.d("JellyfinPlayer", "Player buffering")
                                    // Ensure PlayerView is visible during buffering
                                    playerViewRef.value?.let { view ->
                                        if (view.visibility != android.view.View.VISIBLE) {
                                            view.visibility = android.view.View.VISIBLE
                                        }
                                    }
                                }
                                Player.STATE_ENDED -> {
                                    Log.d("JellyfinPlayer", "🎬 STATE_ENDED - Playback ended naturally")
                                    
                                    // Check if we should autoplay (fallback if countdown didn't trigger)
                                    val shouldAutoplay = settings.autoplayNextEpisode && 
                                                         nextEpisodeDetails != null && 
                                                         !autoplayCancelled && 
                                                         !isAutoPlayingNext
                                    
                                    if (shouldAutoplay) {
                                        Log.d("JellyfinPlayer", "🎬 STATE_ENDED: Triggering autoplay (fallback)")
                                        startNextEpisode()
                                    } else if (!isAutoPlayingNext) {
                                        // Not autoplaying - report and go back
                                        Log.d("JellyfinPlayer", "🎬 STATE_ENDED: Not autoplaying, going back")
                                        progressReportingJob?.cancel()
                                        progressReportingJob = null
                                        
                                        scope.launch {
                                            try {
                                                val positionTicks = player.currentPosition * 10_000L
                                                val durationMs = player.duration
                                                val isComplete = durationMs > 0 && player.currentPosition >= durationMs * 0.90
                                                
                                                withContext(Dispatchers.IO) {
                                                    apiService.reportPlaybackStopped(
                                                        itemId = item.Id, 
                                                        positionTicks = positionTicks,
                                                        audioStreamIndex = currentAudioIndex,
                                                        subtitleStreamIndex = currentSubtitleIndex
                                                    )
                                                    if (isComplete) {
                                                        apiService.markAsWatched(item.Id)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.w("JellyfinPlayer", "Error reporting playback stopped", e)
                                            }
                                            withContext(Dispatchers.Main) {
                                                onBack()
                                            }
                                        }
                                    } else {
                                        Log.d("JellyfinPlayer", "🎬 STATE_ENDED: Autoplay already in progress")
                                    }
                                }
                            }
                        }
                    })
                    
                    // Prepare and play
                    player.prepare()
                    player.playWhenReady = true
                    
                    playerInitialized = true
                    Log.d("JellyfinPlayer", "Player initialized and started")
                    
                    // Report playback start to Jellyfin (REQUIRED before progress reports work)
                    // Use fresh PositionTicks from itemDetails if available
                    scope.launch(Dispatchers.IO) {
                        val freshPositionMs = itemDetails?.UserData?.PositionTicks?.let { it / 10_000 } ?: 0L
                        val actualStartPosition = if (freshPositionMs > 0) freshPositionMs else resumePositionMs
                        val startPositionTicks = actualStartPosition * 10_000L
                        Log.d("JellyfinPlayer", "🎬 Reporting playback START for item ${item.Id} at ${actualStartPosition}ms (fresh: ${freshPositionMs}ms)")
                        val success = apiService.reportPlaybackStart(
                            itemId = item.Id, 
                            positionTicks = startPositionTicks,
                            audioStreamIndex = currentAudioIndex,
                            subtitleStreamIndex = currentSubtitleIndex
                        )
                        if (success) {
                            Log.d("JellyfinPlayer", "✅ Playback start reported successfully")
                        } else {
                            Log.w("JellyfinPlayer", "❌ Playback start report failed!")
                        }
                    }
                    
                    // Request focus on PlayerView so it can receive key events
                    playerViewRef.value?.requestFocus()
                } catch (e: Exception) {
                    Log.e("JellyfinPlayer", "Error initializing player", e)
                }
            }
        }
    }

    // Monitor playback position for skip intro/credits buttons
    LaunchedEffect(playerInitialized, skipMarkers) {
        if (playerInitialized && (skipMarkers.introStartMs != null || skipMarkers.creditsStartMs != null)) {
            Log.d("JellyfinPlayer", "Starting skip button monitoring")
            while (true) {
                delay(200) // Check every 200ms for responsive skip buttons
                try {
                    val currentPositionMs = player.currentPosition
                    
                    // Check for intro skip
                    if (skipIntroEnabled && skipMarkers.introStartMs != null && skipMarkers.introEndMs != null) {
                        val inIntro = currentPositionMs >= skipMarkers.introStartMs!! && currentPositionMs < skipMarkers.introEndMs!!
                        if (inIntro != showSkipIntroButton) {
                            showSkipIntroButton = inIntro
                            if (inIntro) {
                                Log.d("JellyfinPlayer", "Showing Skip Intro button (pos: ${currentPositionMs}ms, intro: ${skipMarkers.introStartMs}-${skipMarkers.introEndMs}ms)")
                            }
                        }
                    }
                    
                    // Check for credits skip
                    if (skipCreditsEnabled && skipMarkers.creditsStartMs != null) {
                        val inCredits = currentPositionMs >= skipMarkers.creditsStartMs!!
                        if (inCredits != showSkipCreditsButton) {
                            showSkipCreditsButton = inCredits
                            if (inCredits) {
                                Log.d("JellyfinPlayer", "Showing Skip Credits button (pos: ${currentPositionMs}ms, credits start: ${skipMarkers.creditsStartMs}ms)")
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Player might be released
                    break
                }
            }
        }
    }
    
    // ===================================================================================
    // AUTOPLAY MONITORING - Detect when to show overlay and start countdown
    // ===================================================================================
    LaunchedEffect(nextEpisodeId, playerInitialized, settings.autoplayNextEpisode) {
        // Only monitor if we have a next episode, player is ready, and autoplay is enabled
        if (nextEpisodeId == null || !playerInitialized || !settings.autoplayNextEpisode) {
            Log.d("JellyfinPlayer", "🎬 Autoplay monitoring skipped: nextEpisodeId=$nextEpisodeId, playerInitialized=$playerInitialized, autoplayEnabled=${settings.autoplayNextEpisode}")
            return@LaunchedEffect
        }
        
        Log.d("JellyfinPlayer", "🎬 ===== AUTOPLAY MONITORING STARTED =====")
        Log.d("JellyfinPlayer", "🎬 Next episode: ${nextEpisodeDetails?.Name} (ID: $nextEpisodeId)")
        
        // Testing mode disabled - countdown triggers at end of episode or when credits start
        val testingMode = false
        val testTriggerAfterMs = 10_000L // Only used when testingMode = true
        
        val countdownSeconds = settings.autoplayCountdownSeconds
        var overlayTriggered = false
        
        while (!isAutoPlayingNext && !autoplayCancelled) {
            delay(500) // Check every 500ms
            
            // Exit if conditions changed
            if (!playerInitialized || autoplayCancelled || isAutoPlayingNext) {
                Log.d("JellyfinPlayer", "🎬 Monitoring stopped: playerInit=$playerInitialized, cancelled=$autoplayCancelled, autoplaying=$isAutoPlayingNext")
                break
            }
            
            try {
                val position = withContext(Dispatchers.Main) { player.currentPosition }
                val duration = withContext(Dispatchers.Main) { player.duration }
                
                if (duration <= 0) continue
                
                val remaining = duration - position
                
                // Determine if we should trigger the overlay
                val shouldTrigger = if (testingMode) {
                    // TESTING: Trigger after 10 seconds of playback
                    position >= testTriggerAfterMs
                } else {
                    // PRODUCTION: Trigger when credits start OR in last N seconds
                    val creditsStart = skipMarkers.creditsStartMs
                    if (creditsStart != null && creditsStart > 0) {
                        position >= creditsStart
                    } else {
                        remaining <= countdownSeconds * 1000L
                    }
                }
                
                // Show overlay and START COUNTDOWN (only once!)
                if (shouldTrigger && !overlayTriggered && nextEpisodeDetails != null && !showNextUpOverlay) {
                    overlayTriggered = true
                    showNextUpOverlay = true
                    autoplayCountdown = countdownSeconds
                    Log.d("JellyfinPlayer", "🎬 Next Up overlay shown! (testingMode=$testingMode)")
                    
                    // 🔥 START THE COUNTDOWN COROUTINE - This owns the transition!
                    startAutoplayCountdown()
                    
                    // Exit monitoring loop - countdown coroutine takes over
                    break
                }
                
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Re-throw cancellation
            } catch (e: Exception) {
                Log.w("JellyfinPlayer", "🎬 Error in monitoring loop", e)
            }
        }
        
        Log.d("JellyfinPlayer", "🎬 ===== AUTOPLAY MONITORING ENDED =====")
    }
    
    
    // Report playback progress periodically when playing OR paused (to save position)
    LaunchedEffect(isPlaying, playerInitialized) {
        if (playerInitialized) {
            progressReportingJob?.cancel()
            progressReportingJob = scope.launch {
                // Report immediately on first play
                var firstReport = true
                while (true) {
                    if (firstReport) {
                        delay(1000) // Report after 1 second to ensure position is available
                        firstReport = false
                    } else {
                        delay(5000) // Then report every 5 seconds
                    }
                    if (!playerInitialized) break
                    try {
                        // Access player on main thread
                        val currentPositionMs = withContext(Dispatchers.Main) {
                            player.currentPosition
                        }
                        // Convert milliseconds to ticks: 1 second = 10,000,000 ticks, so 1 ms = 10,000 ticks
                        val positionTicks = currentPositionMs * 10_000L
                        val isPaused = withContext(Dispatchers.Main) {
                            !player.isPlaying
                        }
                        // Report progress even when paused (so Jellyfin saves the position for Continue Watching)
                        // Only report if position is valid (greater than 0)
                        if (positionTicks > 0) {
                            // Report on background thread
                            withContext(Dispatchers.IO) {
                                Log.d("JellyfinPlayer", "📊 Reporting progress: ${currentPositionMs}ms (${currentPositionMs/1000}s) paused=$isPaused")
                                val success = apiService.reportPlaybackProgress(
                                    itemId = item.Id,
                                    positionTicks = positionTicks,
                                    isPaused = isPaused,
                                    audioStreamIndex = currentAudioIndex,
                                    subtitleStreamIndex = currentSubtitleIndex
                                )
                                if (success) {
                                    Log.d("JellyfinPlayer", "✅ Progress reported successfully (A:$currentAudioIndex, S:$currentSubtitleIndex)")
                                } else {
                                    Log.w("JellyfinPlayer", "❌ Progress report failed")
                                }
                            }
                        } else {
                            Log.d("JellyfinPlayer", "⏭️ Skipping progress report - position is 0")
                        }
                    } catch (e: Exception) {
                        Log.w("JellyfinPlayer", "Error reporting playback progress", e)
                    }
                }
            }
        } else {
            progressReportingJob?.cancel()
            progressReportingJob = null
        }
    }

    // Cleanup player on dispose - only release when composable is removed
    DisposableEffect(Unit) {
        onDispose {
            Log.d("JellyfinPlayer", "🧹 onDispose: (isAutoPlayingNext=$isAutoPlayingNext)")
            
            // Cancel jobs
            progressReportingJob?.cancel()
            progressReportingJob = null
            countdownJob?.cancel()
            countdownJob = null
            
            // If autoplay already released the player, skip all cleanup
            if (isAutoPlayingNext) {
                Log.d("JellyfinPlayer", "🧹 Skipping dispose - autoplay already released player")
                return@onDispose
            }
            
            // Report final position for normal exit (not autoplay)
            kotlinx.coroutines.GlobalScope.launch {
                try {
                    val currentPositionMs = withContext(Dispatchers.Main) {
                        try { player.currentPosition } catch (e: Exception) { 0L }
                    }
                    val durationMs = withContext(Dispatchers.Main) {
                        try { player.duration } catch (e: Exception) { 0L }
                    }
                    
                    if (currentPositionMs > 0 && durationMs > 0) {
                        val positionTicks = currentPositionMs * 10_000L
                        val isComplete = currentPositionMs >= durationMs * 0.90
                        
                        withContext(Dispatchers.IO) {
                            apiService.reportPlaybackStopped(item.Id, positionTicks)
                            if (isComplete) {
                                apiService.markAsWatched(item.Id)
                                Log.d("JellyfinPlayer", "🧹 Marked as watched on dispose")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("JellyfinPlayer", "🧹 Error in dispose reporting", e)
                }
            }
            
            // Clean up GL surface if using enhancements
            glSurfaceViewRef.value?.release()
            glSurfaceViewRef.value = null
            
            // Clear video surface before releasing player
            try {
                player.clearVideoSurface()
                player.release()
                Log.d("JellyfinPlayer", "🧹 Player released")
            } catch (e: Exception) {
                Log.w("JellyfinPlayer", "🧹 Player may already be released", e)
            }
        }
    }

    // BackHandler to handle back button
    BackHandler(enabled = true) {
        // Exit player on back button
        onBack()
    }

    // Get series name if this is an episode
    var seriesName by remember { mutableStateOf<String?>(null) }
    
    // Title overlay visibility - show initially, hide after 10 seconds or when controller is visible
    var titleOverlayVisible by remember { mutableStateOf(true) }
    
    // Hide title overlay after 10 seconds
    LaunchedEffect(Unit) {
        delay(10000) // 10 seconds
        titleOverlayVisible = false
    }
    
    // Check if ExoPlayer controller is visible and hide title overlay accordingly
    LaunchedEffect(Unit) {
        while (true) {
            delay(100) // Check every 100ms
            playerViewRef.value?.let { view ->
                val controller = view.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                if (controller != null && controller.visibility == android.view.View.VISIBLE && controller.alpha > 0f) {
                    // Controller is visible, hide title overlay
                    titleOverlayVisible = false
                }
            }
        }
    }
    
    LaunchedEffect(itemDetails?.SeriesId, apiService) {
        if (itemDetails?.Type == "Episode" && itemDetails?.SeriesId != null) {
            withContext(Dispatchers.IO) {
                try {
                    val series = apiService.getItemDetails(itemDetails!!.SeriesId!!)
                    seriesName = series?.Name
                } catch (e: Exception) {
                    Log.w("JellyfinPlayer", "Error fetching series name", e)
                }
            }
        }
    }
    
    // Track last interaction time to reset auto-hide timer
    var controlsInteractionKey by remember { mutableStateOf(0) }
    
    // Auto-hide controls after 5 seconds of inactivity (resets on any interaction)
    LaunchedEffect(showControls, controlsInteractionKey) {
        if (showControls) {
            delay(5000)
            showControls = false
        }
    }
    
    // Update playback position periodically for the progress bar
    LaunchedEffect(playerInitialized, showControls) {
        if (playerInitialized) {
            while (true) {
                currentPosition = player.currentPosition
                duration = player.duration.coerceAtLeast(0L)
                delay(500) // Update every 500ms
            }
        }
    }
    
    // Apply aspect mode to PlayerView when it changes
    LaunchedEffect(currentAspectMode, playerViewRef.value) {
        playerViewRef.value?.let { pv ->
            // Get the content frame (AspectRatioFrameLayout) from PlayerView
            val contentFrame = pv.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
            
            when (currentAspectMode) {
                AspectMode.FIT -> {
                    // Fit video within screen, maintaining aspect ratio (black bars if needed)
                    pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    contentFrame?.setAspectRatio(0f) // Reset to video's natural aspect ratio
                }
                AspectMode.FILL -> {
                    // Fill screen by cropping video (removes black bars)
                    pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    contentFrame?.setAspectRatio(0f) // Reset to video's natural aspect ratio
                }
                AspectMode.LETTERBOX -> {
                    // Force 16:9 letterbox - video fits inside a 16:9 frame with black bars
                    pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    contentFrame?.setAspectRatio(16f / 9f) // Force 16:9 container
                }
                AspectMode.CINEMA -> {
                    // Cinema scope 2.39:1 - movie theater style with wide black bars top/bottom
                    pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    contentFrame?.setAspectRatio(2.39f / 1f) // Force cinemascope aspect ratio
                }
                AspectMode.STRETCH -> {
                    // Stretch to fill screen (may distort video)
                    pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    contentFrame?.setAspectRatio(0f)
                }
                AspectMode.ORIGINAL -> {
                    // Display at native resolution without scaling
                    pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    contentFrame?.setAspectRatio(0f)
                }
            }
            
            Log.d("ExoPlayer", "Applied aspect mode: ${currentAspectMode.label}, contentFrame: ${contentFrame != null}")
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Handle key events for video controls
                if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.DirectionCenter,  // DPAD center
                        Key.Enter,             // Enter key
                        Key.NumPadEnter -> {
                            if (!showControls) {
                                // Show custom Compose controls overlay
                                showControls = true
                                Log.d("ExoPlayer", "Enter/OK pressed - showing custom controls")
                                true
                            } else {
                                // Controls are showing, let them handle the event
                                false
                            }
                        }
                        Key.DirectionLeft -> {
                            // Cancel autoplay if overlay is showing
                            if (showNextUpOverlay) {
                                autoplayCancelled = true
                                showNextUpOverlay = false
                                Log.d("JellyfinPlayer", "Autoplay cancelled by user (Left key)")
                                true
                            } else if (!showControls) {
                                // Controls not showing - seek backward 15 seconds
                                scope.launch(Dispatchers.Main) {
                                    val currentPos = player.currentPosition
                                    val seekTo = (currentPos - 15000).coerceAtLeast(0)
                                    player.seekTo(seekTo)
                                    Log.d("ExoPlayer", "Left pressed - seeking backward to ${seekTo}ms")
                                }
                                true // Consume event
                            } else {
                                false // Don't consume - let controls handle navigation
                            }
                        }
                        Key.DirectionRight -> {
                            // Cancel autoplay if overlay is showing
                            if (showNextUpOverlay) {
                                autoplayCancelled = true
                                showNextUpOverlay = false
                                Log.d("JellyfinPlayer", "Autoplay cancelled by user (Right key)")
                                true
                            } else if (!showControls) {
                                // Controls not showing - seek forward 15 seconds
                                scope.launch(Dispatchers.Main) {
                                    val currentPos = player.currentPosition
                                    val dur = player.duration
                                    val seekTo = if (dur > 0) {
                                        (currentPos + 15000).coerceAtMost(dur)
                                    } else {
                                        currentPos + 15000
                                    }
                                    player.seekTo(seekTo)
                                    Log.d("ExoPlayer", "Right pressed - seeking forward to ${seekTo}ms")
                                }
                                true // Consume event
                            } else {
                                false // Don't consume - let controls handle navigation
                            }
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            // Cancel autoplay if overlay is showing
                            if (showNextUpOverlay) {
                                autoplayCancelled = true
                                showNextUpOverlay = false
                                Log.d("JellyfinPlayer", "Autoplay cancelled by user (Up/Down key)")
                                true
                            } else {
                                false
                            }
                        }
                        Key.Menu -> {
                            // Open settings menu when Menu key is pressed
                            showSettingsMenu = true
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        when {
            mediaUrl != null -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            // Log rendering mode decision
                            // Note: AV1 detection happens at RUNTIME via onTracksChanged listener
                            // At this point we don't know if it's AV1 yet - we use user's GL setting
                            Log.d("JellyfinPlayer", "🎬 Creating video view - GL mode: $useGLEnhancements")
                            
                            if (useGLEnhancements) {
                                // GL Enhancement mode: Use FrameLayout with GL surface + overlaid PlayerView
                                // WARNING: If content is AV1, this will cause black screen on devices without HW AV1 decoder
                                // The runtime AV1 detection in onTracksChanged will log warnings if this happens
                                FrameLayout(ctx).apply {
                                    // Create GL surface for video rendering with effects
                                    val glSurface = GLVideoSurfaceView(ctx).apply {
                                        layoutParams = FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                        )
                                        // Video enhancement effects
                                        this.enableFakeHDR = settings.enableFakeHDR
                                        this.enableSharpening = settings.enableSharpening
                                        this.hdrStrength = settings.hdrStrength
                                        this.sharpeningStrength = settings.sharpenStrength
                                        this.enableFrameBlending = settings.enableFrameBlending
                                        this.frameBlendStrength = settings.frameBlendStrength
                                        
                                        // New video enhancement effects
                                        this.enableDenoise = settings.enableDenoise
                                        this.denoiseStrength = settings.denoiseStrength
                                        this.enableDeband = settings.enableDeband
                                        this.debandStrength = settings.debandStrength
                                        this.enableFXAA = settings.enableFXAA
                                        this.brightness = settings.videoBrightness
                                        this.contrast = settings.videoContrast
                                        this.saturation = settings.videoSaturation
                                        this.colorTemperature = settings.videoColorTemperature
                                        
                                        glSurfaceViewRef.value = this
                                        
                                        // Use callback for when GL surface is ready (async)
                                        // This fixes the issue where getCodecSurface() returns null
                                        // because onSurfaceCreated hasn't been called yet
                                        setOnSurfaceReadyListener { surface ->
                                            // Fix race condition: Ensure view hasn't been disposed
                                            if (glSurfaceViewRef.value != null) {
                                                try {
                                                    player.setVideoSurface(surface)
                                                    Log.d("JellyfinPlayer", "🎬 GL surface attached to player via callback")
                                                } catch (e: Exception) {
                                                    Log.w("JellyfinPlayer", "⚠️ Failed to attach GL surface: ${e.message}")
                                                }
                                            } else {
                                                Log.d("JellyfinPlayer", "🛑 Ignoring GL surface callback - view disposed")
                                            }
                                        }
                                    }
                                    addView(glSurface)
                                    
                                    // Create PlayerView WITHOUT video surface (just for subtitles only - controls handled by Compose)
                                    val playerView = PlayerView(ctx).apply {
                                        this.player = player
                                        // Keep screen on during playback
                                        keepScreenOn = true
                                        // DISABLE built-in controller - Compose handles controls
                                        useController = false
                                        // Block focus from going to PlayerView children
                                        descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                        isFocusable = false
                                        isFocusableInTouchMode = false
                                        
                                        // Make video surface area transparent so GL surface shows through
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                        
                                        layoutParams = FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                        )
                                        
                                        playerViewRef.value = this
                                        
                                        // Hide next/previous track buttons
                                        post {
                                            findViewById<android.view.View>(androidx.media3.ui.R.id.exo_prev)?.visibility = android.view.View.GONE
                                            findViewById<android.view.View>(androidx.media3.ui.R.id.exo_next)?.visibility = android.view.View.GONE
                                            
                                            // Apply ExoPlayer subtitle customization settings
                                            subtitleView?.apply {
                                                val textSizePx = settings.exoSubtitleTextSize.toFloat()
                                                setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizePx)
                                                
                                                setStyle(
                                                    androidx.media3.ui.CaptionStyleCompat(
                                                        settings.exoSubtitleTextColor,
                                                        if (settings.exoSubtitleBgTransparent) android.graphics.Color.TRANSPARENT else settings.exoSubtitleBgColor,
                                                        android.graphics.Color.TRANSPARENT,
                                                        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                                        android.graphics.Color.BLACK,
                                                        null
                                                    )
                                                )
                                            }
                                            
                                            setShowSubtitleButton(true)
                                            
                                            // Get the PlayerControlView for custom settings button
                                            val controller = findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                                            controller?.let { controlView ->
                                                // ⭐ CUSTOM SETTINGS BUTTON
                                                val existingSubtitleButton = controlView.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_subtitle)
                                                existingSubtitleButton?.let { existingBtn ->
                                                    existingBtn.visibility = android.view.View.GONE
                                                    
                                                    val customSettingsButton = android.widget.ImageButton(ctx).apply {
                                                        setImageResource(android.R.drawable.ic_menu_sort_by_size)
                                                        background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                                                        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                                                        setPadding(16, 16, 16, 16)
                                                        contentDescription = "Player Settings"
                                                        isFocusable = true
                                                        isClickable = true
                                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                                            resources.getDimensionPixelSize(androidx.media3.ui.R.dimen.exo_small_icon_width),
                                                            resources.getDimensionPixelSize(androidx.media3.ui.R.dimen.exo_small_icon_height)
                                                        )
                                                        setOnClickListener { showSettingsMenu = true }
                                                    }
                                                    
                                                    (existingBtn.parent as? android.view.ViewGroup)?.let { parent ->
                                                        val existingBtnIndex = parent.indexOfChild(existingBtn)
                                                        parent.addView(customSettingsButton, existingBtnIndex + 1)
                                                    }
                                                }
                                                
                                                // ⭐ PURPLE FOCUS STYLING
                                                val transparentPurple = android.graphics.Color.argb(150, 156, 39, 176)
                                                
                                                // Settings button should not be the default focus
                                                findViewById<android.view.View>(androidx.media3.ui.R.id.exo_settings)?.let { settingsBtn ->
                                                    // Keep it visible but not the first focus
                                                    settingsBtn.isFocusable = true
                                                    settingsBtn.isFocusableInTouchMode = false
                                                }
                                                
                                                val buttonIds = listOf(
                                                    androidx.media3.ui.R.id.exo_play,
                                                    androidx.media3.ui.R.id.exo_pause,
                                                    androidx.media3.ui.R.id.exo_ffwd,
                                                    androidx.media3.ui.R.id.exo_rew,
                                                    androidx.media3.ui.R.id.exo_subtitle,
                                                    androidx.media3.ui.R.id.exo_settings,
                                                    androidx.media3.ui.R.id.exo_progress,
                                                    androidx.media3.ui.R.id.exo_position,
                                                    androidx.media3.ui.R.id.exo_duration,
                                                    androidx.media3.ui.R.id.exo_repeat_toggle,
                                                    androidx.media3.ui.R.id.exo_shuffle
                                                )
                                                
                                                fun applyPurpleFocus(view: android.view.View) {
                                                    val originalBackground = view.background
                                                    view.setOnFocusChangeListener { v, hasFocus ->
                                                        if (hasFocus) {
                                                            v.post {
                                                                val width = v.width
                                                                val height = v.height
                                                                if (width > 0 && height > 0) {
                                                                    val drawable = android.graphics.drawable.GradientDrawable().apply {
                                                                        setColor(transparentPurple)
                                                                        val maxDimension = maxOf(width, height)
                                                                        val cornerRadius = maxDimension * 0.5f
                                                                        setCornerRadius(cornerRadius)
                                                                    }
                                                                    val padding = (maxOf(width, height) * 0.05f).toInt()
                                                                    val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(drawable))
                                                                    layerDrawable.setLayerInset(0, -padding, -padding, -padding, -padding)
                                                                    v.background = layerDrawable
                                                                } else {
                                                                    v.setBackgroundColor(transparentPurple)
                                                                }
                                                            }
                                                        } else {
                                                            v.background = originalBackground
                                                        }
                                                    }
                                                }
                                                
                                                buttonIds.forEach { buttonId ->
                                                    findViewById<android.view.View>(buttonId)?.let { button ->
                                                        applyPurpleFocus(button)
                                                    }
                                                }
                                                
                                                fun applyToAllChildren(parent: android.view.ViewGroup) {
                                                    for (i in 0 until parent.childCount) {
                                                        val child = parent.getChildAt(i)
                                                        if (child is android.view.ViewGroup) {
                                                            applyToAllChildren(child)
                                                        } else if (child is android.widget.Button || 
                                                                  child is android.widget.ImageButton ||
                                                                  (child.isFocusable && child.isClickable)) {
                                                            applyPurpleFocus(child)
                                                        }
                                                    }
                                                }
                                                
                                                applyToAllChildren(controlView)
                                                controlView.invalidate()
                                                
                                                // Auto-focus play/pause button when controller appears
                                                // Set play/pause button as default next focus to prevent settings button from getting focus
                                                val pauseButton = findViewById<android.view.View>(androidx.media3.ui.R.id.exo_pause)
                                                val playButton = findViewById<android.view.View>(androidx.media3.ui.R.id.exo_play)
                                                
                                                // Make play/pause button the default focus
                                                playButton?.let { play ->
                                                    play.nextFocusDownId = android.view.View.NO_ID
                                                    play.nextFocusUpId = android.view.View.NO_ID
                                                    play.isFocusedByDefault = true
                                                }
                                                pauseButton?.let { pause ->
                                                    pause.nextFocusDownId = android.view.View.NO_ID
                                                    pause.nextFocusUpId = android.view.View.NO_ID  
                                                    pause.isFocusedByDefault = true
                                                }
                                                
                                                // Use postDelayed to ensure controller is fully rendered, then request focus
                                                controlView.postDelayed({
                                                    val buttonToFocus = if (player.isPlaying && pauseButton != null) pauseButton else playButton
                                                    
                                                    buttonToFocus?.let { button ->
                                                        button.requestFocus()
                                                        Log.d("ExoPlayer", "GL Mode: Focused play/pause button (hasFocus=${button.hasFocus()})")
                                                    }
                                                }, 200) // 200ms delay to ensure controller is ready
                                                
                                                Log.d("ExoPlayer", "GL Mode: PlayerControlView initialized with purple focus styling")
                                            }
                                        }
                                    }
                                    addView(playerView)
                                }
                            } else {
                                // Standard mode: Regular PlayerView (subtitles only - controls handled by Compose)
                                PlayerView(ctx).apply {
                                    this.player = player
                                    // Keep screen on during playback
                                    keepScreenOn = true
                                // DISABLE built-in controller - Compose handles controls
                                useController = false
                                // Block focus from going to PlayerView children
                                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                isFocusable = false
                                isFocusableInTouchMode = false
                                
                                // Standard mode uses SurfaceView by default which is compatible with all codecs
                                // including software-decoded AV1. This is the safe path.
                                // If AV1 is detected at runtime (via onTracksChanged), it will work here.
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                                Log.d("JellyfinPlayer", "📺 Using standard PlayerView (SurfaceView) - compatible with all codecs including AV1")
                                
                                // Ensure view is visible and properly sized
                                visibility = android.view.View.VISIBLE
                                alpha = 1f
                                
                                playerViewRef.value = this
                                
                                // Hide next/previous track buttons and ensure subtitle button is visible
                                post {
                                    findViewById<android.view.View>(androidx.media3.ui.R.id.exo_prev)?.visibility = android.view.View.GONE
                                    findViewById<android.view.View>(androidx.media3.ui.R.id.exo_next)?.visibility = android.view.View.GONE
                                    
                                    // Ensure the view is properly attached and visible
                                    visibility = android.view.View.VISIBLE
                                    alpha = 1f
                                    
                                    // Apply ExoPlayer subtitle customization settings
                                    subtitleView?.apply {
                                        // Apply text size
                                        val textSizePx = settings.exoSubtitleTextSize.toFloat()
                                        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizePx)
                                        
                                        // Apply text color
                                        setStyle(
                                            androidx.media3.ui.CaptionStyleCompat(
                                                settings.exoSubtitleTextColor, // foregroundColor
                                                if (settings.exoSubtitleBgTransparent) android.graphics.Color.TRANSPARENT else settings.exoSubtitleBgColor, // backgroundColor
                                                android.graphics.Color.TRANSPARENT, // windowColor
                                                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE, // edgeType
                                                android.graphics.Color.BLACK, // edgeColor
                                                null // typeface
                                            )
                                        )
                                        
                                        Log.d("JellyfinPlayer", "Applied ExoPlayer subtitle customization: size=${settings.exoSubtitleTextSize}, color=${settings.exoSubtitleTextColor}, bgTransparent=${settings.exoSubtitleBgTransparent}")
                                    }
                                    
                                    // Explicitly show subtitle button again after view is attached
                                    setShowSubtitleButton(true)
                                    
                                    // Get the PlayerControlView and ensure subtitle button is visible
                                    val controller = findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                                    controller?.let { controlView ->
                                        // Note: TrackNameProvider customization is done via Format.label in SubtitleMapper.buildLabel()
                                        // ExoPlayer's track selection dialog will automatically use Format.label when available
                                        
                                        // ⭐ CUSTOM SETTINGS BUTTON - Uses clean Jellyfin subtitle list (no duplicates)
                                        val existingSubtitleButton = controlView.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_subtitle)
                                        existingSubtitleButton?.let { existingBtn ->
                                            // Hide the default ExoPlayer subtitle button
                                            existingBtn.visibility = android.view.View.GONE
                                            
                                            // Create custom settings button with better icon
                                            val customSettingsButton = android.widget.ImageButton(ctx).apply {
                                                setImageResource(android.R.drawable.ic_menu_sort_by_size) // List icon
                                                background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                                                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                                                setPadding(16, 16, 16, 16)
                                                contentDescription = "Player Settings"
                                                isFocusable = true
                                                isClickable = true
                                                
                                                // Set same size as existing subtitle button
                                                layoutParams = android.view.ViewGroup.LayoutParams(
                                                    resources.getDimensionPixelSize(androidx.media3.ui.R.dimen.exo_small_icon_width),
                                                    resources.getDimensionPixelSize(androidx.media3.ui.R.dimen.exo_small_icon_height)
                                                )
                                                
                                                // Open settings menu on click
                                                setOnClickListener {
                                                    showSettingsMenu = true
                                                }
                                            }
                                            
                                            // Find the parent container of the subtitle button and add our custom button next to it
                                            (existingBtn.parent as? android.view.ViewGroup)?.let { parent ->
                                                val existingBtnIndex = parent.indexOfChild(existingBtn)
                                                parent.addView(customSettingsButton, existingBtnIndex + 1)
                                                
                                                Log.d("JellyfinPlayer", "✅ Added custom settings button and hid default ExoPlayer subtitle button")
                                            }
                                        }
                                        
                                        // Customize control button focus color to purple (transparent)
                                        val transparentPurple = android.graphics.Color.argb(150, 156, 39, 176) // Purple with transparency
                                        
                                        // Settings button should not be the default focus
                                        findViewById<android.view.View>(androidx.media3.ui.R.id.exo_settings)?.let { settingsBtn ->
                                            // Keep it visible but not the first focus
                                            settingsBtn.isFocusable = true
                                            settingsBtn.isFocusableInTouchMode = false
                                        }
                                        
                                        // Apply custom focus color to all control buttons and seekbar
                                        val buttonIds = listOf(
                                            androidx.media3.ui.R.id.exo_play,
                                            androidx.media3.ui.R.id.exo_pause,
                                            androidx.media3.ui.R.id.exo_ffwd,
                                            androidx.media3.ui.R.id.exo_rew,
                                            androidx.media3.ui.R.id.exo_subtitle,
                                            androidx.media3.ui.R.id.exo_settings,
                                            androidx.media3.ui.R.id.exo_progress,
                                            androidx.media3.ui.R.id.exo_position,
                                            androidx.media3.ui.R.id.exo_duration,
                                            androidx.media3.ui.R.id.exo_repeat_toggle,
                                            androidx.media3.ui.R.id.exo_shuffle
                                        )
                                        
                                        // Function to apply purple focus styling with round shape and 10% larger size
                                        fun applyPurpleFocus(view: android.view.View) {
                                            // Store original background
                                            val originalBackground = view.background
                                            
                                            view.setOnFocusChangeListener { v, hasFocus ->
                                                if (hasFocus) {
                                                    // Use post to ensure dimensions are available and avoid interfering with focus navigation
                                                    v.post {
                                                        val width = v.width
                                                        val height = v.height
                                                        
                                                        if (width > 0 && height > 0) {
                                                            // Create a round drawable
                                                            val drawable = android.graphics.drawable.GradientDrawable().apply {
                                                                setColor(transparentPurple)
                                                                // Make it round by setting corner radius to half of larger dimension
                                                                val maxDimension = maxOf(width, height)
                                                                val cornerRadius = maxDimension * 0.5f
                                                                setCornerRadius(cornerRadius)
                                                            }
                                                            
                                                            // Create a LayerDrawable with the round drawable and add padding to make it appear larger
                                                            val padding = (maxOf(width, height) * 0.05f).toInt()
                                                            val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(drawable))
                                                            layerDrawable.setLayerInset(0, -padding, -padding, -padding, -padding)
                                                            
                                                            // Set the drawable as background
                                                            v.background = layerDrawable
                                                        } else {
                                                            // Fallback: just set color if dimensions not available
                                                            v.setBackgroundColor(transparentPurple)
                                                        }
                                                    }
                                                } else {
                                                    // Reset to original background immediately (don't use post)
                                                    v.background = originalBackground
                                                }
                                            }
                                        }
                                        
                                        buttonIds.forEach { buttonId ->
                                            findViewById<android.view.View>(buttonId)?.let { button ->
                                                applyPurpleFocus(button)
                                            }
                                        }
                                        
                                        // Apply to all child views recursively to catch any other buttons
                                        fun applyToAllChildren(parent: android.view.ViewGroup) {
                                            for (i in 0 until parent.childCount) {
                                                val child = parent.getChildAt(i)
                                                if (child is android.view.ViewGroup) {
                                                    applyToAllChildren(child)
                                                } else if (child is android.widget.Button || 
                                                          child is android.widget.ImageButton ||
                                                          (child.isFocusable && child.isClickable)) {
                                                    applyPurpleFocus(child)
                                                }
                                            }
                                        }
                                        
                                        // Apply styling to all focusable/clickable children
                                        applyToAllChildren(controlView)
                                        
                                        // Force a refresh to ensure subtitle button is visible
                                        controlView.invalidate()
                                        
                                        // Auto-focus play/pause button when controller appears
                                        // Set play/pause button as default next focus to prevent settings button from getting focus
                                        val pauseButton = findViewById<android.view.View>(androidx.media3.ui.R.id.exo_pause)
                                        val playButton = findViewById<android.view.View>(androidx.media3.ui.R.id.exo_play)
                                        
                                        // Make play/pause button the default focus
                                        playButton?.let { play ->
                                            play.nextFocusDownId = android.view.View.NO_ID
                                            play.nextFocusUpId = android.view.View.NO_ID
                                            play.isFocusedByDefault = true
                                        }
                                        pauseButton?.let { pause ->
                                            pause.nextFocusDownId = android.view.View.NO_ID
                                            pause.nextFocusUpId = android.view.View.NO_ID  
                                            pause.isFocusedByDefault = true
                                        }
                                        
                                        // Use postDelayed to ensure controller is fully rendered, then request focus
                                        controlView.postDelayed({
                                            val buttonToFocus = if (player.isPlaying && pauseButton != null) pauseButton else playButton
                                            
                                            buttonToFocus?.let { button ->
                                                button.requestFocus()
                                                Log.d("ExoPlayer", "Standard Mode: Focused play/pause button (hasFocus=${button.hasFocus()})")
                                            }
                                        }, 200) // 200ms delay to ensure controller is ready
                                        
                                        Log.d("ExoPlayer", "PlayerControlView initialized, subtitle button explicitly enabled, focus color set to purple for all controls")
                                    }
                                    
                                    // Request layout to ensure proper rendering
                                    requestLayout()
                                }
                            }
                            } // end else (standard PlayerView mode)
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            // Handle both FrameLayout (GL mode) and PlayerView (standard mode)
                            val playerView = if (view is FrameLayout) {
                                // GL mode: find the PlayerView inside the FrameLayout
                                view.getChildAt(1) as? PlayerView
                            } else {
                                // Standard mode: view is the PlayerView
                                view as? PlayerView
                            }
                            
                            playerView?.let { pv ->
                                // Update player reference when view changes
                                if (pv.player != player) {
                                    pv.player = player
                                }
                                // Ensure view is focusable and can receive key events
                                if (!pv.isFocusable) {
                                    pv.isFocusable = true
                                }
                                // Ensure view is visible
                                if (pv.visibility != android.view.View.VISIBLE) {
                                    pv.visibility = android.view.View.VISIBLE
                                }
                                if (pv.alpha != 1f) {
                                    pv.alpha = 1f
                                }
                                // Explicitly show subtitle button - this ensures it's visible when tracks are available
                                pv.setShowSubtitleButton(true)
                                
                                // Hide next/previous track buttons whenever controller is shown
                                pv.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_prev)?.visibility = android.view.View.GONE
                                pv.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_next)?.visibility = android.view.View.GONE
                                
                                // Ensure subtitle button is visible when tracks are available
                                val controller = pv.findViewById<androidx.media3.ui.PlayerControlView>(androidx.media3.ui.R.id.exo_controller)
                                controller?.let { controlView ->
                                    // Force refresh to show subtitle button
                                    controlView.invalidate()
                                    
                                    // ⚠️ Keep default subtitle button hidden (we're using custom settings button)
                                    val subtitleButton = controlView.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_subtitle)
                                    subtitleButton?.visibility = android.view.View.GONE
                                }
                            }
                        }
                    )
                    
                    // Skip Intro Button
                    if (showSkipIntroButton && skipMarkers.introEndMs != null) {
                        SkipButton(
                            text = "Skip Intro",
                            onClick = {
                                player.seekTo(skipMarkers.introEndMs!!)
                                showSkipIntroButton = false
                                Log.d("JellyfinPlayer", "Skipping intro to ${skipMarkers.introEndMs}ms")
                            }
                        )
                    }
                    
                    // Skip Credits Button
                    if (showSkipCreditsButton && !showNextUpOverlay) {
                        SkipButton(
                            text = "Skip Credits",
                            onClick = {
                                // Seek to near the end to trigger next episode
                                val duration = player.duration
                                if (duration > 0) {
                                    player.seekTo(duration - 2000) // 2 seconds before end
                                }
                                showSkipCreditsButton = false
                                Log.d("JellyfinPlayer", "Skipping credits")
                            }
                        )
                    }
                    
                    // Next Up Overlay
                    if (showNextUpOverlay && nextEpisodeDetails != null) {
                        NextUpOverlay(
                            nextEpisode = nextEpisodeDetails!!,
                            countdown = autoplayCountdown,
                            onCancel = {
                                autoplayCancelled = true
                                showNextUpOverlay = false
                            }
                        )
                    }
                    
                    // Title overlay at the top - shows on initial load (10 seconds) OR when controls are visible
                    val displayName = itemDetails?.Name ?: item.Name
                    val isEpisode = itemDetails?.Type == "Episode"
                    // Show title when: initial overlay is visible OR custom controls are showing
                    val showTitle = (titleOverlayVisible || showControls) && (displayName.isNotEmpty() || (isEpisode && seriesName != null))
                    
                    if (showTitle) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 32.dp, top = 32.dp)
                        ) {
                            // Show series name for episodes, or movie/show name for others
                            if (isEpisode && seriesName != null) {
                                androidx.tv.material3.Text(
                                    text = seriesName!!,
                                    style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            } else if (!isEpisode && displayName.isNotEmpty()) {
                                androidx.tv.material3.Text(
                                    text = displayName,
                                    style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            
                            // Show season/episode number and episode name for episodes
                            if (isEpisode) {
                                val seasonNum = itemDetails?.ParentIndexNumber ?: item.ParentIndexNumber
                                val episodeNum = itemDetails?.IndexNumber ?: item.IndexNumber
                                
                                // Build episode info string: "S1 E5 - Episode Name" or "S1 E5" or just episode name
                                val episodeInfo = buildString {
                                    if (seasonNum != null && episodeNum != null) {
                                        append("S${seasonNum} E${episodeNum}")
                                        if (displayName.isNotEmpty()) {
                                            append(" · ")
                                            append(displayName)
                                        }
                                    } else if (episodeNum != null) {
                                        append("Episode ${episodeNum}")
                                        if (displayName.isNotEmpty()) {
                                            append(" · ")
                                            append(displayName)
                                        }
                                    } else if (displayName.isNotEmpty()) {
                                        append(displayName)
                                    }
                                }
                                
                                if (episodeInfo.isNotEmpty()) {
                                    androidx.tv.material3.Text(
                                        text = episodeInfo,
                                        style = androidx.tv.material3.MaterialTheme.typography.titleMedium,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                    
                    // === CUSTOM COMPOSE CONTROLS OVERLAY ===
                    AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        // FocusRequester for play/pause button - ALWAYS gets focus first
                        val playPauseFocusRequester = remember { FocusRequester() }
                        
                        // Request focus on play/pause button when controls appear
                        LaunchedEffect(Unit) {
                            playPauseFocusRequester.requestFocus()
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                                .onPreviewKeyEvent { event ->
                                    // Reset auto-hide timer on any key press
                                    if (event.type == KeyEventType.KeyDown) {
                                        controlsInteractionKey++
                                    }
                                    
                                    if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                                        showControls = false
                                        true
                                    } else {
                                        false
                                    }
                                }
                        ) {
                            // Progress bar at the bottom
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp, vertical = 32.dp)
                            ) {
                                // Time display
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatTime(currentPosition),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = formatTime(duration),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Focusable Seekbar - user can select and use left/right to seek
                                PlayerSeekBar(
                                    currentPosition = currentPosition,
                                    duration = duration,
                                    onSeek = { newPosition ->
                                        player.seekTo(newPosition)
                                    }
                                )
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // Control buttons row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Rewind button
                                    PlayerControlButton(
                                        icon = Icons.Filled.FastRewind,
                                        contentDescription = "Rewind 15s",
                                        onClick = {
                                            val seekTo = (player.currentPosition - 15000).coerceAtLeast(0)
                                            player.seekTo(seekTo)
                                        }
                                    )
                                    
                                    Spacer(modifier = Modifier.width(32.dp))
                                    
                                    // Play/Pause button - DEFAULT FOCUS TARGET
                                    PlayerControlButton(
                                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        onClick = {
                                            if (isPlaying) player.pause() else player.play()
                                        },
                                        modifier = Modifier.focusRequester(playPauseFocusRequester),
                                        isLarge = true
                                    )
                                    
                                    Spacer(modifier = Modifier.width(32.dp))
                                    
                                    // Fast forward button
                                    PlayerControlButton(
                                        icon = Icons.Filled.FastForward,
                                        contentDescription = "Forward 15s",
                                        onClick = {
                                            val dur = player.duration
                                            val seekTo = if (dur > 0) {
                                                (player.currentPosition + 15000).coerceAtMost(dur)
                                            } else {
                                                player.currentPosition + 15000
                                            }
                                            player.seekTo(seekTo)
                                        }
                                    )
                                    
                                    Spacer(modifier = Modifier.width(32.dp))
                                    
                                    // Picture Mode / Aspect Ratio button
                                    AspectModeButton(
                                        currentMode = currentAspectMode,
                                        onClick = {
                                            currentAspectMode = currentAspectMode.next()
                                        }
                                    )
                                    
                                    Spacer(modifier = Modifier.width(32.dp))
                                    
                                    // CC (Subtitles) button - quick access to subtitles menu
                                    PlayerControlButton(
                                        icon = Icons.Filled.ClosedCaption,
                                        contentDescription = "Subtitles",
                                        onClick = {
                                            showControls = false
                                            settingsMenuInitialLevel = "subtitles"
                                            showSettingsMenu = true
                                        }
                                    )
                                    
                                    Spacer(modifier = Modifier.width(32.dp))
                                    
                                    // Settings button
                                    PlayerControlButton(
                                        icon = Icons.Filled.Settings,
                                        contentDescription = "Settings",
                                        onClick = {
                                            showControls = false
                                            settingsMenuInitialLevel = "main"
                                            showSettingsMenu = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                }
            }
            isLoading -> {
                // Loading indicator could go here
            }
        }
        
        // Settings menu with subtitle picker
        if (showSettingsMenu) {
            ExoPlayerSettingsMenu(
                item = itemDetails ?: item,
                apiService = apiService,
                currentSubtitleIndex = currentSubtitleIndex,
                onDismiss = { 
                    showSettingsMenu = false
                    settingsMenuInitialLevel = "main" // Reset for next time
                },
                player = player,
                jellyfinSubtitleStreams = jellyfinSubtitleStreams, // Pass for composite key registration
                downloadedSubtitles = downloadedSubtitles, // Downloaded OpenSubtitles
                initialMenuLevel = settingsMenuInitialLevel, // Open to subtitles if CC button was pressed
                onDownloadedSubtitleSelected = { filePath ->
                    // Select downloaded subtitle by finding its ExoPlayer track
                    scope.launch(Dispatchers.Main) {
                        try {
                            val tracks = player.currentTracks
                            val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                            
                            Log.d("JellyfinPlayer", "🔍 Looking for downloaded subtitle track: $filePath")
                            Log.d("JellyfinPlayer", "   Available text groups: ${textGroups.size}")
                            
                            // Find the track group that matches this downloaded subtitle
                            // Downloaded subtitles have labels like "English (Downloaded)"
                            val fileName = java.io.File(filePath).name
                            val language = fileName.substringBefore("_")
                            
                            var foundTrack = false
                            textGroups.forEachIndexed { groupIndex, group ->
                                val format = group.mediaTrackGroup.getFormat(0)
                                Log.d("JellyfinPlayer", "   Group $groupIndex: label='${format.label}', lang='${format.language}'")
                                
                                // Match by label containing "(Downloaded)" or by file URI
                                val isDownloadedTrack = format.label?.contains("(Downloaded)") == true ||
                                                       format.id?.contains(filePath) == true
                                val matchesLanguage = format.language == language
                                
                                if (isDownloadedTrack && matchesLanguage && !foundTrack) {
                                    foundTrack = true
                                    Log.d("JellyfinPlayer", "   ✅ Found matching downloaded subtitle track at group $groupIndex")
                                    
                                    // Select this track
                                    val updatedParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                        .addOverride(
                                            androidx.media3.common.TrackSelectionOverride(
                                                group.mediaTrackGroup,
                                                listOf(0)
                                            )
                                        )
                                        .build()
                                    
                                    player.trackSelectionParameters = updatedParameters
                                    Log.d("JellyfinPlayer", "✅ Selected downloaded subtitle: $fileName")
                                    currentSubtitleIndex = null // Clear Jellyfin index since this is a downloaded subtitle
                                }
                            }
                            
                            if (!foundTrack) {
                                Log.w("JellyfinPlayer", "⚠️ Could not find ExoPlayer track for downloaded subtitle: $filePath")
                            }
                            
                            showSettingsMenu = false
                        } catch (e: Exception) {
                            Log.e("JellyfinPlayer", "Error selecting downloaded subtitle", e)
                        }
                    }
                },
                onSubtitleSelected = { subtitleIndex ->
                    currentSubtitleIndex = subtitleIndex
                    // Use ExoPlayer track selection API to select the subtitle
                    scope.launch(Dispatchers.Main) {
                        try {
                            // Safety check - make sure player is still valid
                            if (player.playbackState == Player.STATE_IDLE) {
                                Log.w("JellyfinPlayer", "⚠️ Player is idle, skipping subtitle selection")
                                showSettingsMenu = false
                                return@launch
                            }
                            
                            if (subtitleIndex == null) {
                                // Disable all subtitles by clearing overrides only
                                // Do NOT use setTrackTypeDisabled - that prevents ExoPlayer UI from working
                                val updatedParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                    .build()
                                
                                player.trackSelectionParameters = updatedParameters
                                Log.d("JellyfinPlayer", "✅ Cleared subtitle selection (subtitles disabled)")
                            } else {
                                // Find the ExoPlayer track that matches the selected Jellyfin subtitle index
                                Log.d("JellyfinPlayer", "🔍 Attempting to select subtitle: Jellyfin index=$subtitleIndex")
                                Log.d("JellyfinPlayer", "   Available Jellyfin subtitle streams: ${jellyfinSubtitleStreams.size}")
                                jellyfinSubtitleStreams.forEach { stream ->
                                    Log.d("JellyfinPlayer", "     JF Index=${stream.Index}, Lang=${stream.Language}, DisplayTitle=${stream.DisplayTitle}")
                                }
                                
                                val exoTrackInfo = SubtitleMapper.getExoPlayerTrackInfo(subtitleIndex)
                                
                                if (exoTrackInfo != null) {
                                    val (groupIndex, trackIndexInGroup) = exoTrackInfo
                                    val tracks = player.currentTracks
                                    
                                    Log.d("JellyfinPlayer", "   SubtitleMapper found: ExoPlayer group=$groupIndex, track=$trackIndexInGroup")
                                    Log.d("JellyfinPlayer", "   Total track groups: ${tracks.groups.size}")
                                    
                                    if (groupIndex >= 0 && groupIndex < tracks.groups.size) {
                                        val group = tracks.groups[groupIndex]
                                        
                                        // Safety check for track index
                                        if (trackIndexInGroup >= 0 && trackIndexInGroup < group.mediaTrackGroup.length) {
                                            val format = group.mediaTrackGroup.getFormat(trackIndexInGroup)
                                            Log.d("JellyfinPlayer", "   Selected group format: lang=${format.language}, label=${format.label}, mime=${format.sampleMimeType}")
                                            
                                            // Override to select this specific track (text tracks are not disabled, so no need to enable)
                                            val updatedParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                .addOverride(
                                                    androidx.media3.common.TrackSelectionOverride(
                                                        group.mediaTrackGroup,
                                                        listOf(trackIndexInGroup)
                                                    )
                                                )
                                                .build()
                                            
                                            player.trackSelectionParameters = updatedParameters
                                            Log.d("JellyfinPlayer", "✅ Selected subtitle: Jellyfin index=$subtitleIndex, ExoPlayer group=$groupIndex, track=$trackIndexInGroup")
                                            
                                            // Save preference
                                            settings.setSubtitlePreference(item.Id, subtitleIndex)
                                            lastSelectedSubtitleIndex = subtitleIndex
                                            currentSubtitleIndex = subtitleIndex
                                            Log.d("JellyfinPlayer", "💾 Saved subtitle preference: $subtitleIndex")
                                        } else {
                                            Log.w("JellyfinPlayer", "⚠️ Invalid track index: $trackIndexInGroup (group.length=${group.mediaTrackGroup.length})")
                                        }
                                    } else {
                                        Log.w("JellyfinPlayer", "⚠️ Invalid group index: $groupIndex (tracks.groups.size=${tracks.groups.size})")
                                    }
                                } else {
                                    Log.w("JellyfinPlayer", "⚠️ No ExoPlayer track found for Jellyfin subtitle index $subtitleIndex")
                                    Log.w("JellyfinPlayer", "   This means SubtitleMapper doesn't have a mapping for this Jellyfin index")
                                    Log.w("JellyfinPlayer", "   Possible reasons:")
                                    Log.w("JellyfinPlayer", "   1. Track registration hasn't completed yet")
                                    Log.w("JellyfinPlayer", "   2. This subtitle wasn't in the MediaStreams when tracks were registered")
                                    Log.w("JellyfinPlayer", "   3. ExoPlayer failed to load this subtitle (404, MIME error, etc.)")
                                }
                            }
                            
                            showSettingsMenu = false
                        } catch (e: Exception) {
                            Log.e("JellyfinPlayer", "Error selecting subtitle track", e)
                        }
                    }
                }
            )
        }
        
        // AV1 10-bit decoding error dialog
        if (showAV1Error) {
            Dialog(
                onDismissRequest = { showAV1Error = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.tv.material3.Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .wrapContentHeight(),
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.tv.material3.SurfaceDefaults.colors(
                            containerColor = Color(0xFF1A1A2E),
                            contentColor = Color.White
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Error icon
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(64.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "10-bit AV1 Video Not Supported",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "This video uses 10-bit AV1 encoding which requires special hardware or software support not available on this device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Solutions
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF16213E), RoundedCornerShape(8.dp))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Solutions:",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF4ECDC4),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "1. Enable MPV Player in Settings → Playback",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = "2. Configure Jellyfin to transcode AV1 content",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Dismiss button
                            val dismissFocusRequester = remember { FocusRequester() }
                            LaunchedEffect(Unit) {
                                dismissFocusRequester.requestFocus()
                            }
                            
                            var isFocused by remember { mutableStateOf(false) }
                            androidx.tv.material3.Button(
                                onClick = { 
                                    showAV1Error = false
                                    onBack()
                                },
                                modifier = Modifier
                                    .focusRequester(dismissFocusRequester)
                                    .onFocusChanged { isFocused = it.isFocused },
                                colors = androidx.tv.material3.ButtonDefaults.colors(
                                    containerColor = if (isFocused) Color(0xFF4ECDC4) else Color(0xFF2D4059),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Go Back")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Data class to hold audio track information
data class AudioTrackInfo(
    val group: Tracks.Group,
    val index: Int,
    val language: String?,
    val label: String?,
    val codec: String?,
    val isSelected: Boolean,
    val channelCount: Int,
    val sampleRate: Int
)

@Composable
fun ExoPlayerSettingsMenu(
    item: JellyfinItem,
    apiService: JellyfinApiService,
    currentSubtitleIndex: Int?,
    onDismiss: () -> Unit,
    onSubtitleSelected: (Int?) -> Unit,
    player: ExoPlayer? = null,
    jellyfinSubtitleStreams: List<MediaStream> = emptyList(), // For composite key registration
    downloadedSubtitles: List<com.flex.elefin.subtitles.DownloadedSubtitle> = emptyList(), // Downloaded OpenSubtitles
    onDownloadedSubtitleSelected: ((String) -> Unit)? = null, // Callback for selecting downloaded subtitle by file path
    initialMenuLevel: String = "main" // "main", "subtitles", "audio", "speed" - allows opening directly to a submenu
) {
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var isLoadingSubtitles by remember { mutableStateOf(true) }
    var currentTracks by remember { mutableStateOf<Tracks?>(null) }
    
    // Navigation state for multi-level menu
    var currentMenuLevel by remember { mutableStateOf(initialMenuLevel) } // "main", "subtitles", "audio", "speed"
    
    // Focus requesters for auto-focus on first item in each menu
    val mainMenuFirstItemFocusRequester = remember { FocusRequester() }
    val subtitlesFirstItemFocusRequester = remember { FocusRequester() }
    val audioFirstItemFocusRequester = remember { FocusRequester() }
    val speedFirstItemFocusRequester = remember { FocusRequester() }
    
    // Auto-focus first item when menu level changes
    LaunchedEffect(currentMenuLevel) {
        kotlinx.coroutines.delay(150) // Longer delay to ensure UI is fully rendered
        try {
            when (currentMenuLevel) {
                "main" -> mainMenuFirstItemFocusRequester.requestFocus()
                "subtitles" -> subtitlesFirstItemFocusRequester.requestFocus()
                "audio" -> audioFirstItemFocusRequester.requestFocus()
                "speed" -> speedFirstItemFocusRequester.requestFocus()
            }
        } catch (e: IllegalStateException) {
            // FocusRequester not yet attached to a composable - this can happen
            // if the menu is dismissed before the focus request completes
            Log.w("ExoPlayerSettingsMenu", "Focus request failed (menu may have been dismissed): ${e.message}")
        }
    }
    
    // Fetch full item details to get MediaSources with subtitle and audio streams
    LaunchedEffect(item.Id, apiService) {
        withContext(Dispatchers.IO) {
            try {
                val details = apiService.getItemDetails(item.Id)
                itemDetails = details
                isLoadingSubtitles = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation when composable leaves composition - don't log as error
                throw e // Re-throw to respect cancellation
            } catch (e: Exception) {
                Log.e("ExoPlayerSettingsMenu", "Error fetching item details", e)
                isLoadingSubtitles = false
            }
        }
    }
    
    // Update tracks when player tracks change
    // Capture jellyfinSubtitleStreams in the effect scope
    DisposableEffect(player, jellyfinSubtitleStreams) {
        val listener = if (player != null) {
            // Get initial tracks if available
            val initialTracks = player.currentTracks
            if (initialTracks.groups.isNotEmpty()) {
                currentTracks = initialTracks
            }
            
            // Listen for track changes
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    currentTracks = tracks
                    
                    // Log detected subtitle tracks for debugging
                    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                    Log.d("JellyfinPlayer", "ExoPlayer detected ${textGroups.size} subtitle track group(s)")
                    if (textGroups.isEmpty()) {
                        Log.w("JellyfinPlayer", "⚠️ No subtitle tracks detected by ExoPlayer")
                        Log.w("JellyfinPlayer", "   This usually means:")
                        Log.w("JellyfinPlayer", "   1. The subtitle URL returned 404 (file doesn't exist)")
                        Log.w("JellyfinPlayer", "   2. The subtitle MIME type is incorrect")
                        Log.w("JellyfinPlayer", "   3. ExoPlayer couldn't parse the subtitle file")
                    } else {
                        // Log detected subtitle tracks for debugging
                        textGroups.forEachIndexed { idx, group ->
                            val format = group.mediaTrackGroup.getFormat(0)
                            Log.d("JellyfinPlayer", "  ExoPlayer subtitle track group $idx:")
                            Log.d("JellyfinPlayer", "    Format.id: '${format.id}', Lang: ${format.language}, MIME: ${format.sampleMimeType}")
                            Log.d("JellyfinPlayer", "    Label: ${format.label}, Selected: ${group.isSelected}")
                        }
                    }
                }
                
            }.also { player.addListener(it) }
        } else null
        
        onDispose {
            listener?.let { player?.removeListener(it) }
        }
    }
    
    // Get audio tracks from ExoPlayer
    val audioTracks = remember(currentTracks) {
        currentTracks?.groups?.filter { group ->
            group.type == C.TRACK_TYPE_AUDIO && group.isSupported
        }?.mapIndexedNotNull { index, group ->
            // Get format info from the first track in the group
            if (group.mediaTrackGroup.length > 0) {
                val format = group.mediaTrackGroup.getFormat(0)
                AudioTrackInfo(
                    group = group,
                    index = index,
                    language = format.language,
                    label = format.label,
                    codec = format.codecs,
                    isSelected = group.isSelected,
                    channelCount = format.channelCount,
                    sampleRate = format.sampleRate
                )
            } else null
        } ?: emptyList()
    }
    
    // Get audio streams from Jellyfin MediaSources for additional metadata
    val audioStreams = remember(itemDetails?.MediaSources) {
        itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
            ?.filter { it.Type == "Audio" }
            ?.sortedBy { it.Index ?: 0 } ?: emptyList()
    }
    
    // Get subtitle streams from MediaSources
    val subtitleStreams = remember(itemDetails?.MediaSources) {
        itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
            ?.filter { it.Type == "Subtitle" }
            ?.sortedBy { it.Index ?: 0 } ?: emptyList()
    }
    
    // Handle back button navigation
    BackHandler(enabled = currentMenuLevel != "main") {
        when (currentMenuLevel) {
            "subtitles", "audio", "speed" -> currentMenuLevel = "main"
            else -> onDismiss()
        }
    }
    
    Dialog(
        onDismissRequest = {
            if (currentMenuLevel == "main") {
                onDismiss()
            } else {
                currentMenuLevel = "main"
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)), // Darker, more opaque background
            contentAlignment = Alignment.Center
        ) {
            androidx.tv.material3.Surface(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .fillMaxHeight(0.6f),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.tv.material3.SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), // Semi-transparent surface
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Dialog title - changes based on current menu level
                    Text(
                        text = when (currentMenuLevel) {
                            "subtitles" -> "Subtitles"
                            "audio" -> "Audio Tracks"
                            "speed" -> "Playback Speed"
                            else -> "Player Settings"
                        },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.8f
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Custom colors for better visibility - light gray instead of white
                    val listItemColors = androidx.tv.material3.ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = Color(0xFF424242), // Dark gray when focused
                        focusedContentColor = Color.White,
                        selectedContainerColor = Color(0xFF616161), // Medium gray when selected
                        selectedContentColor = Color.White
                    )
                    
                    // Show different content based on current menu level
                    when (currentMenuLevel) {
                        "main" -> {
                            // Main menu - show 3 category buttons
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Audio Tracks button
                                var isFirstMainMenuItem = true
                                if (player != null && audioTracks.isNotEmpty()) {
                                    item {
                                        ListItem(
                                            selected = false,
                                            onClick = { currentMenuLevel = "audio" },
                                            colors = listItemColors,
                                            headlineContent = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.VolumeUp,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Text(
                                                        text = "Audio Tracks",
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            fontSize = MaterialTheme.typography.titleMedium.fontSize * 0.9f
                                                        )
                                                    )
                                                }
                                            },
                                            trailingContent = {
                                                Text(
                                                    text = "▶",
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(mainMenuFirstItemFocusRequester)
                                        )
                                    }
                                    isFirstMainMenuItem = false
                                }
                                
                                // Subtitles button
                                item {
                                    ListItem(
                                        selected = false,
                                        onClick = { currentMenuLevel = "subtitles" },
                                        colors = listItemColors,
                                        headlineContent = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Text(
                                                    text = "Subtitles",
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontSize = MaterialTheme.typography.titleMedium.fontSize * 0.9f
                                                    )
                                                )
                                            }
                                        },
                                        trailingContent = {
                                            Text(
                                                text = "▶",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (isFirstMainMenuItem) Modifier.focusRequester(mainMenuFirstItemFocusRequester)
                                                else Modifier
                                            )
                                    )
                                }
                                
                                // Playback Speed button
                                if (player != null) {
                                    item {
                                        ListItem(
                                            selected = false,
                                            onClick = { currentMenuLevel = "speed" },
                                            colors = listItemColors,
                                            headlineContent = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FastForward,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Text(
                                                        text = "Playback Speed",
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            fontSize = MaterialTheme.typography.titleMedium.fontSize * 0.9f
                                                        )
                                                    )
                                                }
                                            },
                                            trailingContent = {
                                                Text(
                                                    text = "▶",
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                        
                        "audio" -> {
                            // Audio tracks list
                            LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(audioTracks.size) { index ->
                                val track = audioTracks[index]
                                val trackTitle = buildString {
                                    track.label?.let { append(it) }
                                    if (isEmpty()) {
                                        track.language?.let { append(it) } ?: append("Unknown")
                                    }
                                }
                                val trackInfo = buildString {
                                    track.codec?.let { 
                                        if (isNotEmpty()) append(", ")
                                        append(it)
                                    }
                                    if (track.channelCount > 0) {
                                        if (isNotEmpty()) append(", ")
                                        append("${track.channelCount}ch")
                                    }
                                    if (track.sampleRate > 0) {
                                        if (isNotEmpty()) append(", ")
                                        append("${track.sampleRate / 1000}kHz")
                                    }
                                }
                                
                                ListItem(
                                    selected = track.isSelected,
                                    onClick = {
                                        // Select audio track
                                        player?.let { exoPlayer ->
                                            try {
                                                val trackSelectionOverride = TrackSelectionOverride(
                                                    track.group.mediaTrackGroup,
                                                    0 // Select first track in the group
                                                )
                                                val updatedParameters = exoPlayer.trackSelectionParameters
                                                    .buildUpon()
                                                    .addOverride(trackSelectionOverride)
                                                    .build()
                                                exoPlayer.trackSelectionParameters = updatedParameters
                                                Log.d("ExoPlayerSettingsMenu", "Selected audio track: $trackTitle")
                                            } catch (e: Exception) {
                                                Log.e("ExoPlayerSettingsMenu", "Error selecting audio track", e)
                                            }
                                        }
                                    },
                                    colors = listItemColors,
                                    headlineContent = {
                                        Column {
                                            Text(
                                                text = trackTitle,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                                )
                                            )
                                            if (trackInfo.isNotEmpty()) {
                                                Text(
                                                    text = trackInfo,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.7f
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (index == 0) Modifier.focusRequester(audioFirstItemFocusRequester)
                                            else Modifier
                                        )
                                )
                            }
                        }
                        }
                        
                        "subtitles" -> {
                            // Subtitles list
                            if (isLoadingSubtitles) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Loading subtitles...",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        } else {
                            LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // "None" option to disable subtitles
                            item {
                                ListItem(
                                    selected = currentSubtitleIndex == null,
                                    onClick = {
                                        onSubtitleSelected(null)
                                    },
                                    colors = listItemColors,
                                    headlineContent = {
                                        Text(
                                            text = "None (Off)",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(subtitlesFirstItemFocusRequester)
                                )
                            }
                            
                            // Subtitle stream options from Jellyfin
                            items(subtitleStreams) { stream ->
                                val subtitleTitle = stream.DisplayTitle
                                    ?: stream.Language
                                    ?: "Unknown"
                                val subtitleInfo = buildString {
                                    if (stream.IsDefault == true) append("Default")
                                    if (stream.IsForced == true) {
                                        if (isNotEmpty()) append(", ")
                                        append("Forced")
                                    }
                                    if (stream.IsExternal == true) {
                                        if (isNotEmpty()) append(", ")
                                        append("External")
                                    }
                                    // Debug: Show the actual Jellyfin index
                                    if (isNotEmpty()) append(" • ")
                                    append("Index ${stream.Index}")
                                }
                                
                                ListItem(
                                    selected = stream.Index == currentSubtitleIndex,
                                    onClick = {
                                        stream.Index?.let { index ->
                                            Log.d("ExoPlayerSettingsMenu", "📺 User clicked: $subtitleTitle (Jellyfin Index=$index)")
                                            onSubtitleSelected(index)
                                        }
                                    },
                                    colors = listItemColors,
                                    headlineContent = {
                                        Column {
                                            Text(
                                                text = subtitleTitle,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                                )
                                            )
                                            if (subtitleInfo.isNotEmpty()) {
                                                Text(
                                                    text = subtitleInfo,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.7f
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            // ⭐ Downloaded subtitles from OpenSubtitles
                            if (downloadedSubtitles.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Downloaded Subtitles",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontSize = MaterialTheme.typography.titleSmall.fontSize * 0.8f
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                    )
                                }
                                
                                items(downloadedSubtitles) { downloadedSub ->
                                    val subtitleTitle = com.flex.elefin.subtitles.SubtitleLanguages.getDisplayName(downloadedSub.language)
                                    val subtitleInfo = "${downloadedSub.release} (Downloaded)"
                                    
                                    ListItem(
                                        selected = false, // Downloaded subtitles have different selection mechanism
                                        onClick = {
                                            Log.d("ExoPlayerSettingsMenu", "📺 User clicked downloaded subtitle: ${downloadedSub.fileName}")
                                            onDownloadedSubtitleSelected?.invoke(downloadedSub.filePath)
                                        },
                                        colors = listItemColors,
                                        headlineContent = {
                                            Column {
                                                Text(
                                                    text = subtitleTitle,
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                                    )
                                                )
                                                Text(
                                                    text = subtitleInfo,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.7f
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        },
                                        leadingContent = {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        }
                        }
                        
                        "speed" -> {
                            // Playback speed list
                            player?.let { exoPlayer ->
                                val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                                val currentSpeed = exoPlayer.playbackParameters.speed
                                val currentSpeedIndex = speedOptions.indexOfFirst { kotlin.math.abs(it - currentSpeed) < 0.01f }.takeIf { it >= 0 } ?: 3
                                
                                LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                            items(speedOptions.size) { index ->
                                val speed = speedOptions[index]
                                val speedText = if (speed == 1.0f) "Normal (1.0x)" else "${speed}x"
                                
                                ListItem(
                                    selected = index == currentSpeedIndex,
                                    onClick = {
                                        exoPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
                                        Log.d("ExoPlayerSettingsMenu", "Changed playback speed to ${speed}x")
                                    },
                                    colors = listItemColors,
                                    headlineContent = {
                                        Text(
                                            text = speedText,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (index == 0) Modifier.focusRequester(speedFirstItemFocusRequester)
                                            else Modifier
                                        )
                                )
                            }
                        }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NextUpOverlay(
    nextEpisode: JellyfinItem,
    countdown: Int,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.0f)), // Transparent background
        contentAlignment = Alignment.BottomEnd
    ) {
        androidx.tv.material3.Surface(
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 400.dp),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.8f),
                contentColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                // Episode info
                nextEpisode.SeriesName?.let { seriesName ->
                    Text(
                        text = seriesName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                
                // Episode number and name
                val episodeInfo = buildString {
                    nextEpisode.ParentIndexNumber?.let { seasonNum ->
                        append("S$seasonNum")
                    }
                    nextEpisode.IndexNumber?.let { episodeNum ->
                        if (isNotEmpty()) append(" • ")
                        append("E$episodeNum")
                    }
                    if (isNotEmpty() && nextEpisode.Name.isNotEmpty()) {
                        append(" — ")
                    }
                    append(nextEpisode.Name)
                }
                
                Text(
                    text = episodeInfo,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 2
                )
                
                // Countdown
                Text(
                    text = "Autoplay in $countdown…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

/**
 * Normalizes ISO 639-1 (2-letter) and ISO 639-2/T (3-letter) language codes to a common format.
 * This helps match subtitles when ExoPlayer uses different language code standards than Jellyfin.
 * 
 * Examples:
 * - "es" -> "es"
 * - "spa" -> "es"
 * - "en" -> "en"
 * - "eng" -> "en"
 * - "fr" -> "fr"
 * - "fra" -> "fr"
 * - "tur" -> "tr"
 * - "chi" -> "zh"
 */
private fun normalizeLanguageCode(languageCode: String?): String? {
    if (languageCode == null) return null
    
    // Map common ISO 639-2/T (3-letter) codes to ISO 639-1 (2-letter) codes
    val iso639Map = mapOf(
        "eng" to "en",
        "spa" to "es",
        "fra" to "fr",
        "deu" to "de",
        "ita" to "it",
        "por" to "pt",
        "rus" to "ru",
        "jpn" to "ja",
        "chi" to "zh",
        "kor" to "ko",
        "ara" to "ar",
        "tur" to "tr",
        "pol" to "pl",
        "nld" to "nl",
        "swe" to "sv",
        "dan" to "da",
        "fin" to "fi",
        "nor" to "no",
        "ces" to "cs",
        "hun" to "hu",
        "tha" to "th",
        "vie" to "vi",
        "ind" to "id",
        "heb" to "he",
        "ukr" to "uk",
        "ron" to "ro",
        "ell" to "el",
        "cat" to "ca",
        "hrv" to "hr",
        "slk" to "sk",
        "bul" to "bg",
        "srp" to "sr",
        "slv" to "sl",
        "lit" to "lt",
        "lav" to "lv",
        "est" to "et",
        "isl" to "is",
        "msa" to "ms",
        "fil" to "tl",
        "hin" to "hi",
        "ben" to "bn",
        "tam" to "ta",
        "tel" to "te",
        "mar" to "mr",
        "urd" to "ur",
        "fas" to "fa",
        "swa" to "sw"
    )
    
    val lowerCode = languageCode.lowercase()
    
    // If it's a 3-letter code and we have a mapping, return the 2-letter equivalent
    if (lowerCode.length == 3 && iso639Map.containsKey(lowerCode)) {
        return iso639Map[lowerCode]
    }
    
    // If it's already 2 letters, return as-is
    if (lowerCode.length == 2) {
        return lowerCode
    }
    
    // Fallback: return first 2 characters
    return lowerCode.take(2)
}

@Composable
fun SubtitleSelectionDialog(
    item: JellyfinItem,
    apiService: JellyfinApiService,
    currentSubtitleIndex: Int?,
    onDismiss: () -> Unit,
    onSubtitleSelected: (Int?) -> Unit
) {
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Fetch full item details to get subtitle streams
    // First refresh the item on the server to detect any newly added external subtitles
    LaunchedEffect(item.Id, apiService) {
        withContext(Dispatchers.IO) {
            try {
                // Refresh item metadata on server to detect new external subtitle files
                Log.d("SubtitleDialog", "Refreshing item metadata to detect new subtitles...")
                apiService.refreshItemMetadata(item.Id)
                
                // Small delay to allow server to process the refresh
                kotlinx.coroutines.delay(500)
                
                // Now fetch the updated item details
                val details = apiService.getItemDetails(item.Id)
                itemDetails = details
                isLoading = false
                
                val subtitleCount = details?.MediaSources?.firstOrNull()?.MediaStreams
                    ?.count { it.Type == "Subtitle" } ?: 0
                Log.d("SubtitleDialog", "Loaded $subtitleCount subtitle streams after refresh")
            } catch (e: Exception) {
                Log.e("SubtitleDialog", "Error fetching item details", e)
                isLoading = false
            }
        }
    }
    
    // Full-screen dialog with dark background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(
                onClick = onDismiss,
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Dialog content
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .width(600.dp)
                .heightIn(max = 500.dp)
                .background(Color(0xFF1E1E1E), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .padding(24.dp)
                .clickable(
                    onClick = { /* Prevent click from closing dialog */ },
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            androidx.compose.material3.Text(
                text = "Select Subtitle",
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (isLoading) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
                androidx.compose.material3.CircularProgressIndicator(color = Color(0xFF9C27B0))
            } else {
                // Get subtitle streams
                val subtitleStreams = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                    ?.filter { it.Type == "Subtitle" }
                    ?.sortedBy { it.Index ?: 0 } ?: emptyList()
                
                // Scrollable list of subtitles
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // "None" option
                    item {
                        SubtitleOptionItem(
                            title = "None (Off)",
                            isSelected = currentSubtitleIndex == null,
                            onClick = {
                                onSubtitleSelected(null)
                            }
                        )
                    }
                    
                    // Subtitle options
                    items(subtitleStreams.size) { index ->
                        val stream = subtitleStreams[index]
                        val streamIndex = stream.Index ?: 0
                        
                        // Build subtitle title
                        val subtitleTitle = buildString {
                            append(stream.DisplayTitle ?: stream.Language ?: "Unknown")
                            if (stream.IsForced == true) append(" [Forced]")
                            if (stream.IsExternal == true) append(" (External)")
                            if (stream.IsHearingImpaired == true) append(" [CC]")
                        }
                        
                        SubtitleOptionItem(
                            title = subtitleTitle,
                            isSelected = currentSubtitleIndex == streamIndex,
                            onClick = {
                                onSubtitleSelected(streamIndex)
                            }
                        )
                    }
                }
            }
            
            // Close button
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9C27B0)
                ),
                modifier = Modifier.focusable()
            ) {
                androidx.compose.material3.Text("Close", color = Color.White)
            }
        }
    }
}

@Composable
fun SubtitleOptionItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                when {
                    isSelected -> Color(0xFF9C27B0).copy(alpha = 0.3f)
                    isFocused -> Color(0xFF9C27B0).copy(alpha = 0.2f)
                    else -> Color.Transparent
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFF9C27B0) else Color.Transparent,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color(0xFF9C27B0),
                modifier = Modifier.size(20.dp).padding(end = 8.dp)
            )
        }
        
        androidx.compose.material3.Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun AudioSelectionDialog(
    player: ExoPlayer,
    currentAudioIndex: Int?,
    onDismiss: () -> Unit,
    onAudioSelected: (Int?) -> Unit
) {
    val audioGroups = remember(player.currentTracks) {
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(
                onClick = onDismiss,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(600.dp)
                .heightIn(max = 500.dp)
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                .padding(24.dp)
                .clickable(
                    onClick = { /* Prevent click from closing dialog */ },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                text = "Select Audio Track",
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(audioGroups.size) { index ->
                    val group = audioGroups[index]
                    val format = group.mediaTrackGroup.getFormat(0)
                    val trackTitle = buildString {
                        append(format.label ?: format.language ?: "Unknown")
                        format.codecs?.let { append(" • $it") }
                        if (format.channelCount > 0) append(" • ${format.channelCount}ch")
                    }
                    
                    SimpleOptionItem(
                        title = trackTitle,
                        isSelected = group.isSelected,
                        onClick = {
                            val updatedParameters = player.trackSelectionParameters
                                .buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                .addOverride(
                                    TrackSelectionOverride(
                                        group.mediaTrackGroup,
                                        listOf(0)
                                    )
                                )
                                .build()
                            
                            player.trackSelectionParameters = updatedParameters
                            onAudioSelected(index)
                        }
                    )
                }
            }
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9C27B0)
                ),
                modifier = Modifier.focusable()
            ) {
                androidx.compose.material3.Text("Close", color = Color.White)
            }
        }
    }
}

@Composable
fun SpeedSelectionDialog(
    player: ExoPlayer,
    onDismiss: () -> Unit
) {
    val speeds = remember { listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f) }
    var currentSpeed by remember { mutableStateOf(player.playbackParameters.speed) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(
                onClick = onDismiss,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .heightIn(max = 500.dp)
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                .padding(24.dp)
                .clickable(
                    onClick = { /* Prevent click from closing dialog */ },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                text = "Playback Speed",
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(speeds.size) { index ->
                    val speed = speeds[index]
                    val speedText = when (speed) {
                        1.0f -> "Normal (1.0x)"
                        else -> "${speed}x"
                    }
                    
                    SimpleOptionItem(
                        title = speedText,
                        isSelected = (currentSpeed - speed) < 0.01f,
                        onClick = {
                            player.setPlaybackSpeed(speed)
                            currentSpeed = speed
                        }
                    )
                }
            }
            
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9C27B0)
                ),
                modifier = Modifier.focusable()
            ) {
                androidx.compose.material3.Text("Close", color = Color.White)
            }
        }
    }
}


@Composable
fun SimpleOptionItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                when {
                    isSelected -> Color(0xFF9C27B0).copy(alpha = 0.3f)
                    isFocused -> Color(0xFF9C27B0).copy(alpha = 0.2f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFF9C27B0) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color(0xFF9C27B0),
                modifier = Modifier.size(20.dp).padding(end = 8.dp)
            )
        }
        
        androidx.compose.material3.Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Netflix-style Skip Intro / Skip Credits button
 * Positioned at bottom-right, D-pad focusable for Android TV
 */
@Composable
fun SkipButton(
    text: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    
    // Auto-focus the skip button when it appears
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore focus errors
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .padding(32.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .background(
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(4.dp)
                )
                .then(
                    if (isFocused) {
                        Modifier.border(3.dp, Color.White, RoundedCornerShape(4.dp))
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
                androidx.compose.material3.Text(
                    text = text,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

// YouTube TV-style player control button
@Composable
private fun PlayerControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val size = if (isLarge) 80.dp else 56.dp
    val iconSize = if (isLarge) 40.dp else 28.dp
    
    Box(
        modifier = modifier
            .size(size)
            .background(
                color = when {
                    isFocused -> Color.White
                    else -> Color.White.copy(alpha = 0.2f)
                },
                shape = RoundedCornerShape(50)
            )
            .border(
                width = if (isFocused) 3.dp else 2.dp,
                color = Color.White,
                shape = RoundedCornerShape(50)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

// Format time in HH:MM:SS or MM:SS format
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

// Focusable seekbar that allows manual seeking with D-pad
@Composable
private fun PlayerSeekBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
    
    // Seek step when progress bar is focused: 3% of duration or 30 seconds (much faster than regular seeking)
    // This allows users to quickly scrub through the video
    // For a 2-hour movie: ~3.6 minutes per press
    // For a 1-hour show: ~1.8 minutes per press
    // For a 30-min episode: ~54 seconds per press
    val seekStep = if (duration > 0) {
        maxOf(duration / 33, 30000L) // 3% of duration, minimum 30 seconds
    } else {
        30000L
    }
    
    val barHeight = if (isFocused) 12.dp else 6.dp
    val thumbSize = if (isFocused) 18.dp else 0.dp
    
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp) // Fixed height to accommodate thumb
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (isFocused && event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            // Seek backward
                            val newPosition = (currentPosition - seekStep).coerceAtLeast(0)
                            onSeek(newPosition)
                            true
                        }
                        Key.DirectionRight -> {
                            // Seek forward
                            val newPosition = if (duration > 0) {
                                (currentPosition + seekStep).coerceAtMost(duration)
                            } else {
                                currentPosition + seekStep
                            }
                            onSeek(newPosition)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val trackWidth = maxWidth
        
        // Track background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(
                    color = Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(barHeight / 2)
                )
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) Color(0xFF9C27B0) else Color.Transparent,
                    shape = RoundedCornerShape(barHeight / 2)
                )
        ) {
            // Progress fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(
                        color = if (isFocused) Color(0xFF9C27B0) else Color.White,
                        shape = RoundedCornerShape(barHeight / 2)
                    )
            )
        }
        
        // Thumb indicator - only show when focused
        if (isFocused && thumbSize > 0.dp) {
            val thumbOffset = with(androidx.compose.ui.platform.LocalDensity.current) {
                (trackWidth.toPx() * progress.coerceIn(0f, 1f) - thumbSize.toPx() / 2).toDp()
            }
            
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .align(Alignment.CenterStart)
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(50)
                    )
                    .border(
                        width = 2.dp,
                        color = Color(0xFF9C27B0),
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    }
}

// Picture Mode / Aspect Ratio button
@Composable
private fun AspectModeButton(
    currentMode: AspectMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .size(56.dp)
            .background(
                color = when {
                    isFocused -> Color.White
                    else -> Color.White.copy(alpha = 0.2f)
                },
                shape = RoundedCornerShape(50)
            )
            .border(
                width = if (isFocused) 3.dp else 2.dp,
                color = Color.White,
                shape = RoundedCornerShape(50)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AspectRatio,
                contentDescription = "Picture Mode: ${currentMode.label}",
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = currentMode.label,
                color = if (isFocused) Color.Black else Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


