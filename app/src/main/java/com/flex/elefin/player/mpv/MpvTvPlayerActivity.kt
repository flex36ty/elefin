package com.flex.elefin.player.mpv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.flex.elefin.JellyfinAppTheme
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinConfig
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVView
import `is`.xyz.mpv.MPVView.Track
import kotlinx.coroutines.*
import java.io.File

/**
 * Android TV optimized MPV player activity.
 * 
 * Features:
 *   ✔ Native MPV playback
 *   ✔ Compose-based UI Controls (ported from ExoPlayer)
 *   ✔ Resume position support
 *   ✔ Jellyfin progress reporting
 *   ✔ D-pad navigation
 *   ✔ Track selection (Audio/Subtitles)
 *   ✔ Aspect ratio control
 */
class MpvTvPlayerActivity : ComponentActivity() {

    private var mpvView: MPVView? = null
    private var apiService: JellyfinApiService? = null

    companion object {
        private const val TAG = "MpvTvPlayer"
        private const val EXTRA_URL = "url"
        private const val EXTRA_HEADERS = "headers"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ITEM_ID = "item_id"
        private const val EXTRA_RESUME_MS = "resume_ms"
        private const val EXTRA_AUDIO_URL = "audio_url"
        private const val EXTRA_IS_TRAILER = "is_trailer"

        fun createIntent(
            context: Context,
            url: String,
            headers: String,
            title: String,
            itemId: String,
            resumePositionMs: Long = 0L
        ): Intent {
            return Intent(context, MpvTvPlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_HEADERS, headers)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ITEM_ID, itemId)
                putExtra(EXTRA_RESUME_MS, resumePositionMs)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            Log.e(TAG, "No URL provided")
            finish()
            return
        }
        val headers = intent.getStringExtra(EXTRA_HEADERS) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Video"
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: ""
        val resumePositionMs = intent.getLongExtra(EXTRA_RESUME_MS, 0L)
        // Check for subtitle file in extras (passed by Launcher or other means)
        val subtitleFile = intent.getStringExtra("subtitle_file")
        
        val subtitleStreamIndex = intent.getIntExtra("subtitle_stream_index", -1)
        val audioStreamIndex = intent.getIntExtra("audio_stream_index", -1)
        val audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL)
        val isTrailer = intent.getBooleanExtra(EXTRA_IS_TRAILER, false)

        Log.d("MpvTvPlayer", "Loading: $url")
        Log.d(TAG, "Resume position: ${resumePositionMs}ms")
        if (subtitleFile != null) Log.d(TAG, "External subtitle: $subtitleFile")
        
        Log.d("MpvTvPlayer", "Received Intent Extras -> IsTrailer: $isTrailer, AudioUrl: $audioUrl")

        // Initialize API service for progress reporting
        val config = JellyfinConfig(this)
        if (config.isConfigured()) {
            apiService = JellyfinApiService(
                baseUrl = config.serverUrl,
                accessToken = config.accessToken,
                userId = config.userId,
                config = config
            )
        }

        setContent {
            JellyfinAppTheme {
                MpvPlayerScreen(
                    url = url,
                    headers = headers,
                    title = title,
                    itemId = itemId,
                    resumePositionMs = resumePositionMs,
                    subtitleFile = subtitleFile,
                    initialSubtitleStreamIndex = subtitleStreamIndex,
                    initialAudioStreamIndex = audioStreamIndex,
                    externalAudioUrl = audioUrl,
                    isTrailer = isTrailer,
                    apiService = apiService,
                    onMpvViewCreated = { view -> mpvView = view },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mpvView?.pause()
        // Surface lifecycle hardening:
        // When activity pauses (often before destroy), disable video keys to avoid surface detach crash
        // mpvView?.onPause() // If MPVAndroidView exposes this
    }

    override fun onDestroy() {
        super.onDestroy()
        mpvView?.destroy()
        mpvView = null
    }
}

private fun applySuperResolutionScalers() {
    // AI-style Super Resolution (lightweight)
    MPVLib.setOptionString("scale", "ewa_lanczossharp")
    MPVLib.setOptionString("cscale", "ewa_lanczossharp")
    MPVLib.setOptionString("dscale", "mitchell")
    MPVLib.setOptionString("linear-downscaling", "no")
    MPVLib.setOptionString("sigmoid-upscaling", "yes")
}

@Composable
private fun MpvPlayerScreen(
    url: String,
    headers: String,
    title: String,
    itemId: String,
    resumePositionMs: Long,
    subtitleFile: String? = null,
    initialSubtitleStreamIndex: Int = -1,
    initialAudioStreamIndex: Int = -1,
    externalAudioUrl: String? = null,
    isTrailer: Boolean = false,
    apiService: JellyfinApiService?,
    onMpvViewCreated: (MPVView) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var mpvViewRef by remember { mutableStateOf<MPVView?>(null) }
    
    // Playback state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }
    
    // Controls visibility
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // Settings state
    var showSettingsMenu by remember { mutableStateOf(false) }
    var settingsInitialLevel by remember { mutableStateOf("main") }

    // Track data directly from MPV
    // Using explicit type to avoid import ambiguity if any
    var tracks by remember { mutableStateOf<Map<String, List<Track>>>(emptyMap()) }
    var currentAudioId by remember { mutableStateOf(-1) }
    var currentSubtitleId by remember { mutableStateOf(-1) }
    var playbackSpeed by remember { mutableStateOf(1.0) }
    
    // Aspect mode
    var currentAspectMode by remember { mutableStateOf(AspectMode.FIT) }

    // Jellyfin Stream Index Tracking
    // We store the resolved Jellyfin Stream Index (not MPV ID) to report back to server
    var mediaStreams by remember { mutableStateOf<List<com.flex.elefin.jellyfin.MediaStream>>(emptyList()) }
    var currentJellyfinAudioIndex by remember { mutableStateOf(initialAudioStreamIndex) }
    var currentJellyfinSubtitleIndex by remember { mutableStateOf(initialSubtitleStreamIndex) }
    
    // Progress reporting job
    var progressReportingJob by remember { mutableStateOf<Job?>(null) }

    // Focus for the root container to capture keys when controls are hidden
    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    LaunchedEffect(mpvViewRef) {
        if (mpvViewRef != null) {
            // once MPVView exists, steal focus back to Compose
            delay(50)
            if (!controlsVisible) {
                rootFocusRequester.requestFocus()
            }
        }
    }

    // Update playback state periodically
    LaunchedEffect(mpvViewRef) {
        withContext(Dispatchers.IO) {
            var loopCount = 0
            while (isActive) {
                delay(1000)
                val mpv = mpvViewRef
                if (mpv == null) continue

                try {
                    // JNI Calls on IO Thread
                    val paused = mpv.paused == true
                    val time = ((mpv.timePos ?: 0.0) * 1000).toLong()
                    val dur = ((mpv.duration ?: 0.0) * 1000).toLong()
                    val eof = mpv.eofReached == true
                    
                     // Track sync (throttled)
                     var newTracks: Map<String, List<Track>>? = null
                     var newSpeed = 1.0
                     var newAid = -1
                     var newSid = -1
                     
                     // Check tracks every 5 seconds
                     if (loopCount % 5 == 0) {
                         // Use a lock-like mechanism or just simple check?
                         // We are on IO thread. Startup also uses IO now.
                         // But to be safe, we check if tracks are empty or count mismatch.
                         val currentTrackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
                         val knownRealTracks = mpv.tracks.values.flatten().count { it.mpvId != -1 }
                        
                         // Only reload if count mismatch AND we haven't tried recently
                         // Or just blindly trust MPV if mismatch?
                         // If we have 0 tracks but MPV says > 0, definitely load.
                         // If we have mismatch, maybe reload.
                         if (currentTrackCount != knownRealTracks) {
                             Log.d("MpvTvPlayer", "Track count mismatch (MPV: $currentTrackCount, Known: $knownRealTracks). Reloading tracks.")
                             mpv.loadTracks() // Heavy JNI - safe on IO
                             newTracks = mpv.tracks.mapValues { it.value.toList() }
                         } else {
                             // Just update IDs/Speed if counts match
                             newTracks = mpv.tracks.mapValues { it.value.toList() }
                         }

                         if (mpv.tracks.isNotEmpty()) {
                             newSpeed = mpv.playbackSpeed ?: 1.0
                             newAid = mpv.aid
                             newSid = mpv.sid
                         }
                     }
                    
                    // Update UI State on Main Thread
                    withContext(Dispatchers.Main) {
                        if (eof) {
                            Log.d("MpvTvPlayer", "EOF reached")
                            onBack()
                        } else {
                            isPlaying = !paused
                            currentPositionMs = time
                            durationMs = dur
                            if (dur > 0) isBuffering = false
                            
                            // Update tracks info if we checked them
                            if (loopCount % 5 == 0 && mpv.tracks.isNotEmpty()) {
                                playbackSpeed = newSpeed
                                // Only update ID if changed to avoid jitter? 
                                if (currentAudioId != newAid) currentAudioId = newAid
                                if (currentSubtitleId != newSid) currentSubtitleId = newSid
                                
                                if (newTracks != null) {
                                    tracks = newTracks
                                }
                            }
                        }
                    }

                } catch (e: Exception) {
                    // MPV not ready or other error
                }
                loopCount++
            }
        }
    }

    // Auto-hide controls
    LaunchedEffect(lastInteractionTime, controlsVisible, showSettingsMenu) {
        if (controlsVisible && !showSettingsMenu) {
            delay(5000)
            if (System.currentTimeMillis() - lastInteractionTime >= 5000) {
                controlsVisible = false
            }
        }
    }

    // Capture focus when controls are hidden so we can show them again
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            // Small delay to allow previous focus to be cleared/layout to update
            delay(100)
            try {
                rootFocusRequester.requestFocus()
                Log.d("MpvTvPlayer", "Requested focus to root container")
            } catch (e: Exception) {
                Log.w("MpvTvPlayer", "Failed to request focus: ${e.message}")
            }
        }
    }

    // Report playback start when MPV view is ready
    var hasReportedStart by remember { mutableStateOf(false) }
    LaunchedEffect(mpvViewRef) {
        if (mpvViewRef != null && apiService != null && itemId.isNotEmpty() && !hasReportedStart) {
            withContext(Dispatchers.IO) {
                val startPositionTicks = resumePositionMs * 10_000L
                // Use initial indices for start report
                apiService.reportPlaybackStart(
                    itemId, 
                    startPositionTicks,
                    audioStreamIndex = if (initialAudioStreamIndex != -1) initialAudioStreamIndex else null,
                    subtitleStreamIndex = if (initialSubtitleStreamIndex != -1) initialSubtitleStreamIndex else null
                )
                hasReportedStart = true
            }
        }
    }

    // Progress reporting to Jellyfin (every 10 seconds)
    LaunchedEffect(isPlaying, mpvViewRef) {
        if (isPlaying && mpvViewRef != null && apiService != null && itemId.isNotEmpty()) {
            progressReportingJob?.cancel()
            progressReportingJob = scope.launch {
                while (isActive) {
                    delay(10000) // Report every 10 seconds
                    try {
                        val positionTicks = currentPositionMs * 10_000L
                        if (positionTicks > 0) {
                            withContext(Dispatchers.IO) {
                                apiService.reportPlaybackProgress(
                                    itemId = itemId,
                                    positionTicks = positionTicks,
                                    isPaused = !isPlaying,
                                    audioStreamIndex = if (currentJellyfinAudioIndex != -1) currentJellyfinAudioIndex else null,
                                    subtitleStreamIndex = if (currentJellyfinSubtitleIndex != -1) currentJellyfinSubtitleIndex else null
                                )
                                Log.d("MpvPlayer", "Reported progress: ${currentPositionMs}ms (A:$currentJellyfinAudioIndex, S:$currentJellyfinSubtitleIndex)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("MpvPlayer", "Error reporting progress", e)
                    }
                }
            }
        } else {
            progressReportingJob?.cancel()
        }
    }

    // Report stopped when exiting
    DisposableEffect(Unit) {
        onDispose {
            progressReportingJob?.cancel()
            val finalPos = currentPositionMs
            val finalDur = durationMs
            if (apiService != null && itemId.isNotEmpty()) {
                scope.launch {
                    try {
                        val positionTicks = finalPos * 10_000L
                        withContext(Dispatchers.IO) {
                            apiService.reportPlaybackStopped(
                                itemId, 
                                positionTicks,
                                audioStreamIndex = if (currentJellyfinAudioIndex != -1) currentJellyfinAudioIndex else null,
                                subtitleStreamIndex = if (currentJellyfinSubtitleIndex != -1) currentJellyfinSubtitleIndex else null
                            )
                            // Mark as watched if completed 90%+
                            if (finalDur > 0 && finalPos >= finalDur * 0.90) {
                                apiService.markAsWatched(itemId)
                                Log.d("MpvPlayer", "Marked as watched")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("MpvPlayer", "Error reporting stopped", e)
                    }
                }
            }
        }
    }

    // Apply aspect mode
    LaunchedEffect(currentAspectMode, mpvViewRef) {
        mpvViewRef?.let { mpv ->
            when (currentAspectMode) {
                AspectMode.FIT -> {
                    MPVLib.setOptionString("video-aspect-override", "no")
                    MPVLib.setOptionString("video-aspect-method", "container")
                    MPVLib.setOptionString("panscan", "0.0")
                    MPVLib.setOptionString("video-unscaled", "no")
                }
                AspectMode.FILL -> {
                    MPVLib.setOptionString("video-aspect-override", "no")
                    MPVLib.setOptionString("video-aspect-method", "container")
                    MPVLib.setOptionString("panscan", "1.0")
                    MPVLib.setOptionString("video-unscaled", "no")
                }
                AspectMode.LETTERBOX -> {
                    MPVLib.setOptionString("video-aspect-override", "16:9")
                    MPVLib.setOptionString("panscan", "0.0")
                    MPVLib.setOptionString("video-unscaled", "no")
                }
                AspectMode.CINEMA -> {
                    MPVLib.setOptionString("video-aspect-override", "2.39:1")
                    MPVLib.setOptionString("panscan", "0.0")
                    MPVLib.setOptionString("video-unscaled", "no")
                }
                AspectMode.STRETCH -> {
                    MPVLib.setOptionString("video-aspect-override", "no")
                    MPVLib.setOptionString("keepaspect", "no")
                    MPVLib.setOptionString("panscan", "0.0")
                    MPVLib.setOptionString("video-unscaled", "no")
                }
                AspectMode.ORIGINAL -> {
                    MPVLib.setOptionString("video-aspect-override", "no")
                    MPVLib.setOptionString("video-aspect-method", "container")
                    MPVLib.setOptionString("panscan", "0.0")
                    MPVLib.setOptionString("video-unscaled", "yes")
                }
            }
            if (currentAspectMode != AspectMode.STRETCH) {
                MPVLib.setOptionString("keepaspect", "yes")
            }
        }
    }

    // Load tracks when MPV is ready
    LaunchedEffect(mpvViewRef, durationMs) {
        if (mpvViewRef != null && durationMs > 0) {
            delay(1500)
            
            // Move entire track loading & matching logic to IO thread to avoid Main Thread hang/Race
            withContext(Dispatchers.IO) {
                val mpv = mpvViewRef ?: return@withContext
                
                // Safe JNI calls
                MPVLib.setPropertyBoolean("sub-visibility", true)
                mpv.loadTracks()
                
                // Trigger initial track state update (pass back to UI)
                val loadedTracks = mpv.tracks.mapValues { it.value.toList() }
                val loadedAid = mpv.aid
                val loadedSid = mpv.sid
                
                withContext(Dispatchers.Main) {
                    tracks = loadedTracks
                    currentAudioId = loadedAid
                    currentSubtitleId = loadedSid
                }
                
                // Try to sync with selected Jellyfin streams if provided, OR find defaults
                if (apiService != null) {
                     try {
                         // valid check
                         if (mpv.tracks.isEmpty()) return@withContext
                         
                         // Skip stream matching for trailers (custom ID structure causes API errors)
                         if (isTrailer) {
                             Log.d("MpvTvPlayer", "Skipping stream matching for trailer playback")
                             return@withContext
                         }
                         
                         Log.d("MpvTvPlayer", "Fetching item details to match streams. Initial: Sub=$initialSubtitleStreamIndex, Audio=$initialAudioStreamIndex")
                         val itemDetails = apiService.getItemDetails(itemId)
                         val streams = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams ?: emptyList()
                         // Save streams to state for later reverse matching
                         withContext(Dispatchers.Main) {
                             mediaStreams = streams
                         }
                         
                         // 1. Match Subtitle
                         // If external file is provided, skip internal matching as MPV will select the file we added
                         if (subtitleFile == null) {
                             var targetSubStream = if (initialSubtitleStreamIndex != -1) {
                                 mediaStreams.find { it.Index == initialSubtitleStreamIndex && it.Type == "Subtitle" }
                             } else {
                                 null
                             }

                             if (targetSubStream != null) {
                                 val targetLang = targetSubStream.Language
                                 val targetTitle = targetSubStream.DisplayTitle ?: targetSubStream.Title
                                 Log.d("MpvTvPlayer", "Target Subtitle: Lang=$targetLang, Title=$targetTitle, Index=${targetSubStream.Index}, Default=${targetSubStream.IsDefault}, Forced=${targetSubStream.IsForced}")
                                 
                                 // Find matching MPV track
                                 val mpvSubTracks = loadedTracks["sub"] ?: emptyList()
                                 
                                 // Matching logic:
                                 // 1. If we have a language, try to match by language (fuzzy)
                                 // 2. If we have a title, try to match by title
                                 val bestMatch = mpvSubTracks.find { track -> 
                                     (targetLang != null && track.lang?.startsWith(targetLang.take(2), ignoreCase = true) == true) ||
                                     (targetTitle != null && track.name.contains(targetTitle, ignoreCase = true))
                                 }
                                 
                                 if (bestMatch != null && bestMatch.mpvId != -1) {
                                     withContext(Dispatchers.Main) {
                                         mpv.sid = bestMatch.mpvId
                                         currentSubtitleId = bestMatch.mpvId
                                         Log.d("MpvTvPlayer", "Matched and selected subtitle: ${bestMatch.name} (id=${bestMatch.mpvId})")
                                     }
                                 } else {
                                      Log.w("MpvTvPlayer", "Could not find MPV subtitle track matching: $targetTitle")
                                 }
                             } else {
                                 Log.d("MpvTvPlayer", "No target subtitle found (User selection: ${initialSubtitleStreamIndex}, Auto-detect: true)")
                             }
                         } else {
                             Log.d("MpvTvPlayer", "External subtitle provided ($subtitleFile), skipping internal track matching.")
                         }
                         
                         // 2. Match Audio
                         var targetAudioStream = if (initialAudioStreamIndex != -1) {
                             mediaStreams.find { it.Index == initialAudioStreamIndex && it.Type == "Audio" }
                         } else {
                             mediaStreams.find { (it.IsDefault == true) && it.Type == "Audio" }
                         }
                         
                          if (targetAudioStream != null) {
                             val targetLang = targetAudioStream.Language
                             Log.d("MpvTvPlayer", "Target Audio: Lang=$targetLang")
                             
                             val mpvAudioTracks = loadedTracks["audio"] ?: emptyList()
                             val bestMatch = mpvAudioTracks.find { track -> 
                                 targetLang != null && track.lang?.startsWith(targetLang.take(2), ignoreCase = true) == true
                             }
                             
                             if (bestMatch != null && bestMatch.mpvId != -1) {
                                 withContext(Dispatchers.Main) {
                                     mpv.aid = bestMatch.mpvId
                                     currentAudioId = bestMatch.mpvId
                                      Log.d("MpvTvPlayer", "Matched and selected audio: ${bestMatch.name} (id=${bestMatch.mpvId})")
                                 }
                             }
                         }
                         
                     } catch (e: Exception) {
                         Log.e("MpvTvPlayer", "Error matching streams", e)
                     }
                }
            }
        }
    }
    
    // Reverse Matching Logic: MPV ID -> Jellyfin Index
    // Runs whenever MPV track selection changes or mediaStreams are loaded
    LaunchedEffect(currentAudioId, currentSubtitleId, mediaStreams) {
        if (mediaStreams.isNotEmpty() && (currentAudioId != -1 || currentSubtitleId != -1)) {
            withContext(Dispatchers.Default) {
                try {
                    // Update Audio Index
                    if (currentAudioId != -1) {
                         // Find MPV track details
                         val mpvTrack = tracks["audio"]?.find { it.mpvId == currentAudioId }
                         if (mpvTrack != null) {
                             // Match to Jellyfin stream
                             // Priority: Exact Language match first
                             val jStream = mediaStreams.filter { it.Type == "Audio" }.find { stream ->
                                 val langMatch = stream.Language != null && mpvTrack.lang?.startsWith(stream.Language.take(2), ignoreCase = true) == true
                                 langMatch
                             } ?: mediaStreams.filter { it.Type == "Audio" }.firstOrNull() // Fallback? Or maybe iterate by index?
                             
                             // Ideally we would match by more properties, but language is the main one MPV exposes reliably
                             if (jStream != null) {
                                 withContext(Dispatchers.Main) {
                                     currentJellyfinAudioIndex = jStream.Index ?: -1
                                     Log.d("MpvTvPlayer", "Updated Jellyfin Audio Index: ${jStream.Index} (from MPV ID $currentAudioId)")
                                 }
                             }
                         }
                    }
                    
                    // Update Subtitle Index
                    if (currentSubtitleId != -1) {
                        val mpvTrack = tracks["sub"]?.find { it.mpvId == currentSubtitleId }
                        if (mpvTrack != null) {
                            // Match to Jellyfin stream
                            val jStream = mediaStreams.filter { it.Type == "Subtitle" }.find { stream ->
                                // Match by Name/Title if available, or Language
                                val titleMatch = stream.Title != null && mpvTrack.name.contains(stream.Title, ignoreCase = true)
                                val displayTitleMatch = stream.DisplayTitle != null && mpvTrack.name.contains(stream.DisplayTitle, ignoreCase = true)
                                val langMatch = stream.Language != null && mpvTrack.lang?.startsWith(stream.Language.take(2), ignoreCase = true) == true
                                
                                titleMatch || displayTitleMatch || langMatch
                            }
                            
                            if (jStream != null) {
                                withContext(Dispatchers.Main) {
                                    currentJellyfinSubtitleIndex = jStream.Index ?: -1
                                    Log.d("MpvTvPlayer", "Updated Jellyfin Subtitle Index: ${jStream.Index} (from MPV ID $currentSubtitleId)")
                                }
                            }
                        }
                    } else {
                        // Subtitles disabled
                         withContext(Dispatchers.Main) {
                             // If -1 (disabled), we should probably report null or -1. 
                             // API expects index optional. If disabled, maybe send null?
                             // But wait, if we explicitly turned it off, we might want to say "off".
                             // However, usually turning off just means not sending an index, or sending a specific value.
                             // Jellyfin stores "null" index as "no subtitle".
                             currentJellyfinSubtitleIndex = -1 
                         }
                    }
                } catch (e: Exception) {
                    Log.w("MpvTvPlayer", "Error resolving Jellyfin indices", e)
                }
            }
        }
    }

    fun showControls() {
        controlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusTarget()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                
                // Helper to update interaction timer
                fun consumeAndTouch(): Boolean {
                    lastInteractionTime = System.currentTimeMillis()
                    return true
                }

                if (controlsVisible) {
                    // When controls are visible, we allow standard navigation (Up/Down/Left/Right/Enter)
                    // to reach the buttons. We ONLY intercept Back to hide controls.
                    if (event.key == Key.Back) {
                        if (showSettingsMenu) {
                            showSettingsMenu = false
                            return@onPreviewKeyEvent consumeAndTouch()
                        } else {
                            controlsVisible = false
                            return@onPreviewKeyEvent consumeAndTouch()
                        }
                    }
                    // For all other keys (Arrows, Enter), let Compose FocusManager handle them!
                    return@onPreviewKeyEvent false
                }

                // --- CONTROLS HIDDEN LOGIC ---
                // We capture keys to show controls or seek
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        // Netflix-style: First click shows controls
                        showControls()
                        consumeAndTouch()
                    }

                    Key.DirectionDown -> {
                        showControls()
                        consumeAndTouch()
                    }
                    
                    Key.DirectionUp -> {
                        showControls()
                        consumeAndTouch()
                    }

                    Key.DirectionLeft -> {
                        // Seek when hidden
                        mpvViewRef?.seek(-10)
                        consumeAndTouch()
                    }

                    Key.DirectionRight -> {
                        // Seek when hidden
                        mpvViewRef?.seek(10)
                        consumeAndTouch()
                    }

                    Key.MediaPlayPause -> {
                        // If hidden, show controls. If specific media key, maybe just toggle?
                        // Let's mirror Netflix: Media Button always acts on media
                        mpvViewRef?.cyclePause()
                        showControls()
                        consumeAndTouch()
                    }
                    
                    Key.Back -> {
                        onBack()
                        true
                    }

                    else -> false
                }
            }
            .focusable()
    ) {
        // MPV View
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .focusable(false),
            factory = { ctx ->
                MPVView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // Make sure the View can't take focus
                    isFocusable = false
                    isFocusableInTouchMode = false

                    val configDir = File(ctx.filesDir, "mpv")
                    configDir.mkdirs()

                    // ✅ Write TV-hard config files BEFORE initialize()
                    writeMpvTvConfig(configDir)
                    
                    // ✅ Install Shaders
                    MpvShaderManager.installShaders(ctx)

                    initialize(configDir.absolutePath, ctx.cacheDir.absolutePath)
                    
                    // ✅ Apply Shader Profile
                    val settings = AppSettings(ctx)
                    try {
                        val profileName = settings.mpvShaderProfile
                        val profile = MpvShaderManager.ShaderProfile.fromString(profileName)
                        Log.d("MpvTvPlayer", "Applying Shader Profile: ${profile.displayName}")
                        
                        // Check for Dynamic Tone Mapping Setting if using relevant profile
                        // For HdrBoostPlus, we enforce it if the profile is selected, BUT
                        // we also check the boolean setting to see if user DISABLED it explicitly 
                        // relative to the profile? 
                        // Actually, the user's instructions imply the profile "HdrBoostPlus" IS the feature.
                        // But also asked for a setting.
                        // Let's assume if the User selected "HDR++ (Dynamic)" in the picker, they want it.
                        // BUT "implement this and add an option in settings to enable / disable it. set it disable by default"
                        // This usually means the *feature capability* is gated. 
                        // If I disable the setting, maybe HdrBoostPlus falls back to HdrBoost? 
                        // Or maybe I just strictly follow: If setting OFF, and they pick HdrBoostPlus, 
                        // we filter out the dynamic shader from the list?
                        
                        // Let's go with: MpvShaderManager handles the list. 
                        // I need to filter the list here if I didn't do it in Manager.
                        // In Manager, I just added it.
                        // So I should check `settings.enableDynamicToneMapping` here.
                        
                        var shaderPaths = MpvShaderManager.getShadersForProfile(ctx, profile).toMutableList()
                        
                        // Logic: If Dynamic Tone Mapping setting is disabled, REMOVE the dynamic shader 
                        // from the list if it was added (e.g. if user selected HdrBoostPlus)
                        if (!settings.enableDynamicToneMapping) {
                            val dynShaderPath = MpvShaderManager.getShaderPath(ctx, MpvShaderManager.SHADER_DYN_TONEMAP)
                            shaderPaths.remove(dynShaderPath)
                            Log.d("MpvTvPlayer", "Dynamic Tone Mapping disabled in settings, removing shader.")
                        }

                        if (shaderPaths.isNotEmpty()) {
                            // Join with standard path separator (:)
                            val shaderList = shaderPaths.joinToString(File.pathSeparator)
                            Log.d("MpvTvPlayer", "Setting glsl-shaders: $shaderList")
                            MPVLib.setOptionString("glsl-shaders", shaderList)
                        } else {
                            // If None, clear shaders just in case (though init should be clean)
                            MPVLib.setOptionString("glsl-shaders", "")
                        }

                        // Profile-specific extra settings
                        when (profile) {
                            MpvShaderManager.ShaderProfile.Cinema -> {
                                MPVLib.setOptionString("deband", "yes")
                                MPVLib.setOptionString("deband-iterations", "2")
                                MPVLib.setOptionString("deband-threshold", "48")
                            }
                            MpvShaderManager.ShaderProfile.Sports -> {
                                applySuperResolutionScalers()
                            }
                            MpvShaderManager.ShaderProfile.Sharp -> {
                                applySuperResolutionScalers()
                            }
                            MpvShaderManager.ShaderProfile.HdrBoostPlus -> {
                                // Smart HDR logic is mostly in shader, but we can enable scaler too if desired?
                                // User didn't explicitly ask for SR on HDR++, only on Sports/Sharp.
                                // "HdrBoostPlus = “smart HDR” for mixed content"
                                // "Sharp/Sports = enable the SR scalers"
                                // So leave default scaling for HdrBoostPlus unless user adds it manually.
                                MPVLib.setOptionString("scale", "bilinear")
                                MPVLib.setOptionString("deband", "no")
                            }
                            else -> {
                                // Default safer scaling
                                MPVLib.setOptionString("scale", "bilinear")
                                MPVLib.setOptionString("deband", "no")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MpvTvPlayer", "Error applying shader profile", e)
                    }
                    
                    // 🔥 Force-enable subtitle renderer at runtime
                    MPVLib.setPropertyBoolean("sub-ass", true)
                    MPVLib.setPropertyBoolean("sub-visibility", true)
                    MPVLib.setPropertyString("sub-ass-override", "scale")
                    MPVLib.setPropertyString("sub-auto", "fuzzy")
                    MPVLib.setPropertyString("sub-fix-timing", "yes")
                    MPVLib.setPropertyBoolean("embeddedfonts", true)

                    // Still keep these as reinforcement
                    MPVLib.setOptionString("osc", "no")
                    MPVLib.setOptionString("input-touch", "no")
                    MPVLib.setOptionString("input-default-bindings", "no")
                    MPVLib.setOptionString("input-builtin-bindings", "no")

                    if (resumePositionMs > 0) {
                        val startSeconds = resumePositionMs / 1000.0
                        MPVLib.setOptionString("start", startSeconds.toString())
                    }

                    setHttpHeaders(headers)

                    // TRAILER OPTIMIZATION
                    if (isTrailer) {
                        MPVLib.command(arrayOf("apply-profile", "trailer"))
                    }

                    playFile(url)

                    // AUDIO TRACK
                    if (externalAudioUrl != null) {
                        // Use Handler to ensure the main file load has initialized the player core
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                             Log.d("MpvTvPlayer", "Executing delayed audio-add: $externalAudioUrl")
                             MPVLib.command(arrayOf("audio-add", externalAudioUrl, "select"))
                        }, 500)
                    }

                    if (subtitleFile != null) {
                        postDelayed({
                            Log.d("MpvTvPlayer", "Adding external subtitle: $subtitleFile")
                            MPVLib.command(arrayOf("sub-add", subtitleFile, "select"))
                        }, 1000) // Load slightly earlier than track matcher
                    }

                    mpvViewRef = this
                    onMpvViewCreated(this)
                }
            }
        )

        // Title overlay (always show when controls visible)
        AnimatedVisibility(
            visible = controlsVisible && title.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                        )
                    )
                    .padding(24.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Buffering indicator
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        // NEW Controls Overlay
        MpvControls(
            isVisible = controlsVisible,
            isPlaying = isPlaying,
            currentPosition = currentPositionMs,
            duration = durationMs,
            currentAspectMode = currentAspectMode,
            onPlayPause = { mpvViewRef?.cyclePause() },
            onSeek = { pos -> 
                mpvViewRef?.timePos = pos / 1000.0 
                lastInteractionTime = System.currentTimeMillis()
            },
            onFastRewind = { 
                mpvViewRef?.seek(-15) 
                lastInteractionTime = System.currentTimeMillis()
            },
            onFastForward = { 
                mpvViewRef?.seek(15)
                lastInteractionTime = System.currentTimeMillis()
            },
            onAspectModeChange = {
                currentAspectMode = currentAspectMode.next()
                lastInteractionTime = System.currentTimeMillis()
            },
            onOpenSettings = { level ->
                if (level == "subtitles") {
                     settingsInitialLevel = "subtitles"
                } else {
                     settingsInitialLevel = "main"
                }
                showSettingsMenu = true
                controlsVisible = false 
            },
            onHide = { controlsVisible = false },
            onResetHideTimer = { lastInteractionTime = System.currentTimeMillis() },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        
        // Settings Menu
        if (showSettingsMenu) {
            MpvSettingsMenu(
                tracks = tracks,
                selectedAudio = currentAudioId,
                selectedSub = currentSubtitleId,
                playbackSpeed = playbackSpeed,
                onDismiss = { 
                    showSettingsMenu = false 
                    showControls()
                },
                onAudioSelected = { trackId ->
                    mpvViewRef?.aid = trackId
                    currentAudioId = trackId
                },
                onSubtitleSelected = { trackId ->
                    mpvViewRef?.sid = trackId
                    currentSubtitleId = trackId
                    if (trackId != -1) {
                         MPVLib.setPropertyBoolean("sub-visibility", true)
                         // Force redraw/rescan
                         MPVLib.command(arrayOf("rescan-external-files"))
                    }
                },
                onPlaybackSpeedChange = { playbackSpeed ->
                    mpvViewRef?.playbackSpeed = playbackSpeed
                },
                initialMenuLevel = settingsInitialLevel
            )
        }
    }
}

private fun writeMpvTvConfig(dir: File) {
    val mpvConf = File(dir, "mpv.conf")
    val inputConf = File(dir, "input.conf")

    val mpvText = """
        # Elefin Android TV config
        osc=no
        input-touch=no
        input-default-bindings=no
        input-builtin-bindings=no
        load-scripts=no
        cursor-autohide=no
        terminal=no
        msg-level=all=error
        
        # Instant Start Optimization
        cache-pause=no

        # --- Subtitle rendering (CRITICAL FIX) ---
        sub-ass=yes
        sub-visibility=yes
        sub-auto=fuzzy
        sub-fix-timing=yes
        sub-ass-override=scale
        sub-font-size=48
        sub-border-size=2
        sub-shadow-offset=2
        sub-use-margins=no
        embeddedfonts=yes
        embeddedfonts=yes
        sub-font="Roboto"
        
        [trailer]
        profile=default
        hwdec=mediacodec-copy
        vo=gpu
        scale=bilinear
        dither=no
        interpolation=no
        deband=no
        video-sync=display-resample
    """.trimIndent() + "\n"

    val inputText = "# empty on purpose\n"

    if (!mpvConf.exists() || mpvConf.readText() != mpvText) {
        mpvConf.writeText(mpvText)
    }

    if (!inputConf.exists() || inputConf.readText() != inputText) {
        inputConf.writeText(inputText)
    }
}
