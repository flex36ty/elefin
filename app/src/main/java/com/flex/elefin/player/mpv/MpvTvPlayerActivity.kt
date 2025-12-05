package com.flex.elefin.player.mpv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.flex.elefin.JellyfinAppTheme
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinConfig
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVView
import kotlinx.coroutines.*
import java.io.File

/**
 * Android TV optimized MPV player activity with ExoPlayer-style controls.
 * 
 * Features:
 *   ✔ YouTube TV-style controls
 *   ✔ Resume position support
 *   ✔ Jellyfin progress reporting
 *   ✔ D-pad navigation
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

        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            Log.e(TAG, "No URL provided")
            finish()
            return
        }
        val headers = intent.getStringExtra(EXTRA_HEADERS) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Video"
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: ""
        val resumePositionMs = intent.getLongExtra(EXTRA_RESUME_MS, 0L)

        Log.d(TAG, "Loading: $url")
        Log.d(TAG, "Resume position: ${resumePositionMs}ms")

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
    }

    override fun onDestroy() {
        super.onDestroy()
        mpvView?.destroy()
        mpvView = null
    }
}

@Composable
private fun MpvPlayerScreen(
    url: String,
    headers: String,
    title: String,
    itemId: String,
    resumePositionMs: Long,
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
    
    // Focus
    val playPauseFocusRequester = remember { FocusRequester() }
    var seekBarFocused by remember { mutableStateOf(false) }
    
    // Progress reporting job
    var progressReportingJob by remember { mutableStateOf<Job?>(null) }

    // Update playback state periodically
    LaunchedEffect(mpvViewRef) {
        while (true) {
            delay(500)
            mpvViewRef?.let { mpv ->
                try {
                    isPlaying = mpv.paused != true
                    currentPositionMs = ((mpv.timePos ?: 0.0) * 1000).toLong()
                    durationMs = ((mpv.duration ?: 0.0) * 1000).toLong()
                    isBuffering = false
                } catch (e: Exception) {
                    // MPV not ready yet
                }
            }
        }
    }

    // Auto-hide controls after 5 seconds
    LaunchedEffect(lastInteractionTime) {
        delay(5000)
        if (System.currentTimeMillis() - lastInteractionTime >= 5000) {
            controlsVisible = false
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
                                    isPaused = !isPlaying
                                )
                                Log.d("MpvPlayer", "Reported progress: ${currentPositionMs}ms")
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
            if (apiService != null && itemId.isNotEmpty()) {
                scope.launch {
                    try {
                        val positionTicks = currentPositionMs * 10_000L
                        withContext(Dispatchers.IO) {
                            apiService.reportPlaybackStopped(itemId, positionTicks)
                            // Mark as watched if completed 90%+
                            if (durationMs > 0 && currentPositionMs >= durationMs * 0.90) {
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

    // Focus on play button when controls become visible
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(100)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (e: Exception) { }
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
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter -> {
                            if (!controlsVisible) {
                                showControls()
                                true
                            } else {
                                false // Let focused button handle it
                            }
                        }
                        Key.Back -> {
                            if (controlsVisible) {
                                controlsVisible = false
                                true
                            } else {
                                onBack()
                                true
                            }
                        }
                        Key.DirectionLeft -> {
                            if (!controlsVisible) {
                                mpvViewRef?.seek(-10)
                                showControls()
                                true
                            } else if (seekBarFocused) {
                                mpvViewRef?.seek(-30)
                                showControls()
                                true
                            } else {
                                showControls()
                                false
                            }
                        }
                        Key.DirectionRight -> {
                            if (!controlsVisible) {
                                mpvViewRef?.seek(10)
                                showControls()
                                true
                            } else if (seekBarFocused) {
                                mpvViewRef?.seek(30)
                                showControls()
                                true
                            } else {
                                showControls()
                                false
                            }
                        }
                        else -> {
                            showControls()
                            false
                        }
                    }
                } else {
                    false
                }
            }
            .focusable()
    ) {
        // MPV View
        AndroidView(
            factory = { ctx ->
                MPVView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    // Set headers before initialize
                    setHttpHeaders(headers)
                    
                    val configDir = File(ctx.filesDir, "mpv")
                    configDir.mkdirs()
                    initialize(configDir.absolutePath, ctx.cacheDir.absolutePath)
                    
                    playFile(url)
                    
                    // Seek to resume position after a delay
                    if (resumePositionMs > 0) {
                        postDelayed({
                            seekTo(resumePositionMs / 1000.0)
                            Log.d("MpvPlayer", "Seeked to resume position: ${resumePositionMs}ms")
                        }, 1000)
                    }
                    
                    mpvViewRef = this
                    onMpvViewCreated(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Title overlay (shown when controls visible)
        AnimatedVisibility(
            visible = controlsVisible,
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

        // Loading indicator
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        // Controls overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentPositionMs),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                            .height(8.dp)
                            .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .onFocusChanged { seekBarFocused = it.isFocused }
                            .focusable()
                            .then(
                                if (seekBarFocused) {
                                    Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp))
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        val progress = if (durationMs > 0) {
                            (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                        } else 0f
                        
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(Color.Red, RoundedCornerShape(4.dp))
                        )
                    }
                    
                    Text(
                        text = formatTime(durationMs),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Control buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = {
                            mpvViewRef?.seek(-10)
                            showControls()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // Play/Pause
                    IconButton(
                        onClick = {
                            mpvViewRef?.cyclePause()
                            showControls()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White, CircleShape)
                            .focusRequester(playPauseFocusRequester)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    
                    // Forward 10s
                    IconButton(
                        onClick = {
                            mpvViewRef?.seek(10)
                            showControls()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Forward10,
                            contentDescription = "Forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
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

