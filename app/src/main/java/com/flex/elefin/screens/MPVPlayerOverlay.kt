package com.flex.elefin.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `is`.xyz.mpv.MPVView
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.AppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Composable
fun MPVPlayerOverlay(
    mpvView: MPVView?,
    visible: Boolean,
    onClose: () -> Unit,
    item: JellyfinItem? = null,
    apiService: JellyfinApiService? = null,
    onSubtitleSelected: ((Int?) -> Unit)? = null,
    isHDR: Boolean = false // HDR status passed from parent
) {
    if (mpvView == null || !visible) return
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { AppSettings(context) }
    
    val currentPosition = remember { mutableStateOf(0.0) }
    
    // ⭐ CRITICAL FIX: Get duration from Jellyfin API, NOT from MPV!
    // MPV's duration property is unreliable with network streams (shows buffer size)
    // Jellyfin's RunTimeTicks is the TRUE duration of the video
    val duration = remember { 
        val ticks = item?.RunTimeTicks ?: 0L
        val seconds = if (ticks > 0) ticks / 10_000_000.0 else 0.0
        android.util.Log.d("MPVPlayerOverlay", "✅ Using Jellyfin duration: ${seconds}s (${ticks} ticks) for ${item?.Name}")
        mutableStateOf(seconds)
    }
    
    val isPaused = remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var currentSubtitleIndex by remember { mutableStateOf<Int?>(null) }
    
    // Focus requester for play/pause button (default focus)
    val playPauseFocusRequester = remember { FocusRequester() }
    
    // Title overlay visibility - show initially, hide after 10 seconds (like ExoPlayer)
    var titleOverlayVisible by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        delay(10000) // 10 seconds
        titleOverlayVisible = false
    }
    
    // Hide title overlay when controls are visible
    LaunchedEffect(visible) {
        if (visible) {
            titleOverlayVisible = false
        }
    }
    
    // Request focus on play/pause button when overlay appears
    LaunchedEffect(visible) {
        if (visible) {
            delay(100) // Small delay to ensure composables are laid out
            try {
                playPauseFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus request may fail if button not yet composed
            }
        }
    }
    
    // Update position and pause state periodically
    // ⭐ CRITICAL: Wait for MPVHolder.ready before polling properties!
    LaunchedEffect(visible, mpvView, com.flex.elefin.player.mpv.MPVHolder.ready) {
        android.util.Log.d("MPVPlayerOverlay", "🔵 LaunchedEffect triggered: visible=$visible, mpvView=$mpvView, ready=${com.flex.elefin.player.mpv.MPVHolder.ready}")
        
        val currentView = mpvView ?: run {
            android.util.Log.w("MPVPlayerOverlay", "❌ mpvView is null, exiting LaunchedEffect")
            return@LaunchedEffect
        }
        
        if (!visible) {
            android.util.Log.d("MPVPlayerOverlay", "⚠️ Overlay not visible, exiting LaunchedEffect")
            return@LaunchedEffect
        }
        
        // Wait for MPV to be ready (file-loaded event must fire first)
        if (!com.flex.elefin.player.mpv.MPVHolder.ready) {
            android.util.Log.d("MPVPlayerOverlay", "⏳ Waiting for MPV to be ready before polling properties... (ready=false)")
            return@LaunchedEffect
        }
        
        android.util.Log.d("MPVPlayerOverlay", "✅ MPV is ready, starting property polling loop...")
        
        while (visible && com.flex.elefin.player.mpv.MPVHolder.ready) {
            val view = mpvView ?: break
            try {
                // Query MPV properties (safe now that file is loaded)
                val pos = view.getCurrentPosition()
                val paused = view.isPaused()
                
                // Update state (duration comes from Jellyfin, not MPV!)
                currentPosition.value = pos
                isPaused.value = paused
                
                android.util.Log.v("MPVPlayerOverlay", "Position: ${pos}s, Duration: ${duration.value}s (from Jellyfin), Paused: $paused")
            } catch (e: Exception) {
                android.util.Log.w("MPVPlayerOverlay", "Error getting MPV properties: ${e.message}")
                // Don't break - just try again next iteration
            }
            delay(500) // Update every 500ms
        }
    }
    
    // Purple focus color (same as ExoPlayer)
    val transparentPurple = Color(0x9C9C27B0) // argb(150, 156, 39, 176)
    
    // Use AnimatedVisibility for smooth show/hide animations (like ExoPlayer's controller)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            // Title overlay at the top (like ExoPlayer) - shows title and HDR indicator
            val displayName = item?.Name ?: ""
            val showTitle = titleOverlayVisible && !visible && displayName.isNotEmpty()
            
            if (showTitle) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 32.dp, top = 32.dp)
                ) {
                    // Title
                    if (displayName.isNotEmpty()) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    
                    // HDR indicator
                    if (isHDR) {
                        Box(
                            modifier = Modifier
                                .background(Color.Black, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "HDR",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
            
            // Bottom controls bar (matching ExoPlayer layout)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Seekbar
                val progress = if (duration.value > 0) {
                    (currentPosition.value / duration.value).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Control buttons row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind button (-15s)
                        var rewindFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (rewindFocused) transparentPurple else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                        ) {
                            IconButton(
                                onClick = {
                                    val currentPos = currentPosition.value
                                    val seekTo = (currentPos - 15.0).coerceAtLeast(0.0)
                                    mpvView?.seekTo(seekTo)
                                },
                                modifier = Modifier
                                    .onFocusChanged { focusState ->
                                        rewindFocused = focusState.isFocused || focusState.hasFocus
                                    },
                                colors = IconButtonDefaults.colors(
                                    contentColor = Color.White,
                                    containerColor = Color.Transparent
                                ),
                                scale = IconButtonDefaults.scale(
                                    scale = 1.0f,
                                    focusedScale = 1.0f,
                                    pressedScale = 0.95f
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 15s",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        
                        // Play/Pause button (default focus)
                        var playPauseFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (playPauseFocused) transparentPurple else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                        ) {
                            IconButton(
                                onClick = {
                                    if (isPaused.value) {
                                        mpvView?.resume()
                                    } else {
                                        mpvView?.pause()
                                    }
                                },
                                modifier = Modifier
                                    .focusRequester(playPauseFocusRequester)
                                    .onFocusChanged { focusState ->
                                        playPauseFocused = focusState.isFocused || focusState.hasFocus
                                    },
                                colors = IconButtonDefaults.colors(
                                    contentColor = Color.White,
                                    containerColor = Color.Transparent
                                ),
                                scale = IconButtonDefaults.scale(
                                    scale = 1.0f,
                                    focusedScale = 1.0f,
                                    pressedScale = 0.95f
                                )
                            ) {
                                Icon(
                                    imageVector = if (isPaused.value) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (isPaused.value) "Play" else "Pause",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        
                        // Fast forward button (+15s)
                        var ffwdFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (ffwdFocused) transparentPurple else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                        ) {
                            IconButton(
                                onClick = {
                                    val currentPos = currentPosition.value
                                    val dur = duration.value
                                    val seekTo = if (dur > 0) {
                                        (currentPos + 15.0).coerceAtMost(dur)
                                    } else {
                                        currentPos + 15.0
                                    }
                                    mpvView?.seekTo(seekTo)
                                },
                                modifier = Modifier
                                    .onFocusChanged { focusState ->
                                        ffwdFocused = focusState.isFocused || focusState.hasFocus
                                    },
                                colors = IconButtonDefaults.colors(
                                    contentColor = Color.White,
                                    containerColor = Color.Transparent
                                ),
                                scale = IconButtonDefaults.scale(
                                    scale = 1.0f,
                                    focusedScale = 1.0f,
                                    pressedScale = 0.95f
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 15s",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    
                    // Time display
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPosition.value.toLong()),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "/",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatTime(duration.value.toLong()),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Right side buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Subtitle button
                        var subtitleFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (subtitleFocused) transparentPurple else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                        ) {
                            IconButton(
                                onClick = {
                                    showSettingsMenu = true
                                },
                                modifier = Modifier
                                    .onFocusChanged { focusState ->
                                        subtitleFocused = focusState.isFocused || focusState.hasFocus
                                    },
                                colors = IconButtonDefaults.colors(
                                    contentColor = Color.White,
                                    containerColor = Color.Transparent
                                ),
                                scale = IconButtonDefaults.scale(
                                    scale = 1.0f,
                                    focusedScale = 1.0f,
                                    pressedScale = 0.95f
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = "Subtitles",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        
                        // Settings button (Menu)
                        var settingsFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (settingsFocused) transparentPurple else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                        ) {
                            IconButton(
                                onClick = {
                                    showSettingsMenu = true
                                },
                                modifier = Modifier
                                    .onFocusChanged { focusState ->
                                        settingsFocused = focusState.isFocused || focusState.hasFocus
                                    },
                                colors = IconButtonDefaults.colors(
                                    contentColor = Color.White,
                                    containerColor = Color.Transparent
                                ),
                                scale = IconButtonDefaults.scale(
                                    scale = 1.0f,
                                    focusedScale = 1.0f,
                                    pressedScale = 0.95f
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
                
                // Seekbar (below buttons)
                Spacer(modifier = Modifier.height(8.dp))
                var seekbarFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                        .onFocusChanged { seekbarFocused = it.isFocused }
                        .focusable()
                        .background(
                            if (seekbarFocused) transparentPurple.copy(alpha = 0.5f) else Color.Transparent,
                            RoundedCornerShape(3.dp)
                        )
                        .padding(if (seekbarFocused) 1.dp else 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.toFloat())
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
    
    // Settings menu (similar to ExoPlayer)
    if (showSettingsMenu && item != null && apiService != null) {
        MPVPlayerSettingsMenu(
            item = item,
            apiService = apiService,
            currentSubtitleIndex = currentSubtitleIndex,
            onDismiss = { showSettingsMenu = false },
            onSubtitleSelected = { index ->
                currentSubtitleIndex = index
                onSubtitleSelected?.invoke(index)
                showSettingsMenu = false
            }
        )
    }
}

@Composable
fun MPVPlayerSettingsMenu(
    item: JellyfinItem,
    apiService: JellyfinApiService,
    currentSubtitleIndex: Int?,
    onDismiss: () -> Unit,
    onSubtitleSelected: (Int?) -> Unit
) {
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var isLoadingSubtitles by remember { mutableStateOf(true) }
    
    // Fetch full item details to get MediaSources with subtitle streams
    LaunchedEffect(item.Id, apiService) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val details = apiService.getItemDetails(item.Id)
                itemDetails = details
                isLoadingSubtitles = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MPVPlayerSettingsMenu", "Error fetching item details", e)
                isLoadingSubtitles = false
            }
        }
    }
    
    // Get subtitle streams from MediaSources
    val subtitleStreams = remember(itemDetails?.MediaSources) {
        itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
            ?.filter { it.Type == "Subtitle" }
            ?.sortedBy { it.Index ?: 0 } ?: emptyList()
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .fillMaxHeight(0.6f),
                shape = RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Dialog title
                    Text(
                        text = "Player Settings",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.8f
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Subtitle section
                    Text(
                        text = "Subtitles",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = MaterialTheme.typography.titleMedium.fontSize * 0.8f
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                    )
                    
                    // Vertical list of subtitle options
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
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // "None" option to disable subtitles
                            item {
                                ListItem(
                                    selected = currentSubtitleIndex == null,
                                    onClick = {
                                        onSubtitleSelected(null)
                                    },
                                    headlineContent = {
                                        Text(
                                            text = "None (Off)",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            // Subtitle stream options
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
                                }
                                
                                ListItem(
                                    selected = stream.Index == currentSubtitleIndex,
                                    onClick = {
                                        stream.Index?.let { index ->
                                            onSubtitleSelected(index)
                                        }
                                    },
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
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}
