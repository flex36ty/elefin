package com.flex.elefin.player.mpv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import `is`.xyz.mpv.MPVView.Track
import java.util.Locale

// Picture mode / aspect ratio options
enum class AspectMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    LETTERBOX("16:9"),
    CINEMA("Cinema"),
    STRETCH("Stretch"),
    ORIGINAL("Original");

    fun next(): AspectMode {
        val modes = values()
        return modes[(ordinal + 1) % modes.size]
    }
}

@Composable
fun MpvControls(
    isVisible: Boolean,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    currentAspectMode: AspectMode,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onFastRewind: () -> Unit,
    onFastForward: () -> Unit,
    onAspectModeChange: () -> Unit,
    onOpenSettings: (String) -> Unit, // "main", "subtitles", "audio", etc.
    onHide: () -> Unit,
    onResetHideTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        // FocusRequester for play/pause button - to default focus to it
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
                        onResetHideTimer()
                    }
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
                        onHide()
                        true
                    } else {
                        false
                    }
                }
        ) {
            // Shadow gradient at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
            )

            // Progress bar and controls at the bottom
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

                // Focusable Seekbar
                PlayerSeekBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    onSeek = onSeek,
                    onInteraction = onResetHideTimer
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
                        onClick = onFastRewind
                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    // Play/Pause button - DEFAULT FOCUS TARGET
                    PlayerControlButton(
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        onClick = onPlayPause,
                        modifier = Modifier.focusRequester(playPauseFocusRequester),
                        isLarge = true
                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    // Fast forward button
                    PlayerControlButton(
                        icon = Icons.Filled.FastForward,
                        contentDescription = "Forward 15s",
                        onClick = onFastForward
                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    // Picture Mode / Aspect Ratio button
                    AspectModeButton(
                        currentMode = currentAspectMode,
                        onClick = onAspectModeChange
                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    // CC (Subtitles) button - quick access to subtitles menu
                    PlayerControlButton(
                        icon = Icons.Filled.ClosedCaption,
                        contentDescription = "Subtitles",
                        onClick = { onOpenSettings("subtitles") }
                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    // Settings button
                    PlayerControlButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        onClick = { onOpenSettings("main") }
                    )
                }
            }
        }
    }
}


@Composable
fun MpvSettingsMenu(
    tracks: Map<String, List<Track>>, // "audio", "sub", "video"
    selectedAudio: Int,
    selectedSub: Int,
    playbackSpeed: Double,
    onDismiss: () -> Unit,
    onAudioSelected: (Int) -> Unit,
    onSubtitleSelected: (Int) -> Unit,
    onPlaybackSpeedChange: (Double) -> Unit,
    initialMenuLevel: String = "main" // "main", "subtitles", "audio", "speed"
) {
    var currentMenuLevel by remember { mutableStateOf(initialMenuLevel) }

    // Focus requesters for auto-focus
    val mainMenuFirstItemFocusRequester = remember { FocusRequester() }
    val subtitlesFirstItemFocusRequester = remember { FocusRequester() }
    val audioFirstItemFocusRequester = remember { FocusRequester() }
    val speedFirstItemFocusRequester = remember { FocusRequester() }

    val audioTracks = tracks["audio"] ?: emptyList()
    val subTracks = tracks["sub"] ?: emptyList()

    // Auto-focus first item when menu level changes
    LaunchedEffect(currentMenuLevel) {
        kotlinx.coroutines.delay(150)
        try {
            when (currentMenuLevel) {
                "main" -> mainMenuFirstItemFocusRequester.requestFocus()
                "subtitles" -> subtitlesFirstItemFocusRequester.requestFocus()
                "audio" -> audioFirstItemFocusRequester.requestFocus()
                "speed" -> speedFirstItemFocusRequester.requestFocus()
            }
        } catch (e: Exception) {
            // Ignore focus errors
        }
    }

    // Handle back button navigation
    androidx.activity.compose.BackHandler(enabled = currentMenuLevel != "main") {
        when (currentMenuLevel) {
            "subtitles", "audio", "speed" -> currentMenuLevel = "main"
            else -> onDismiss()
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = {
            if (currentMenuLevel == "main") {
                onDismiss()
            } else {
                currentMenuLevel = "main"
            }
        },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.tv.material3.Surface(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .fillMaxHeight(0.6f),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.tv.material3.SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
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
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    val listItemColors = androidx.tv.material3.ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = Color(0xFF424242),
                        focusedContentColor = Color.White,
                        selectedContainerColor = Color(0xFF616161),
                        selectedContentColor = Color.White
                    )

                    when (currentMenuLevel) {
                        "main" -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Subtitles (Main)
                                item {
                                    val currentSubTrackName = subTracks.find { it.mpvId == selectedSub }?.name ?: "Off"
                                    androidx.tv.material3.ListItem(
                                        selected = false,
                                        onClick = { currentMenuLevel = "subtitles" },
                                        colors = listItemColors,
                                        headlineContent = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.ClosedCaption,
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
                                            .focusRequester(mainMenuFirstItemFocusRequester)
                                    )
                                }
                                // Audio (Main)
                                item {
                                    val currentAudioTrackName = audioTracks.find { it.mpvId == selectedAudio }?.name ?: "Default"
                                    androidx.tv.material3.ListItem(
                                        selected = false,
                                        onClick = { currentMenuLevel = "audio" },
                                        colors = listItemColors,
                                        headlineContent = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.VolumeUp,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Text(
                                                    text = "Audio",
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
                                // Speed (Main)
                                item {
                                    androidx.tv.material3.ListItem(
                                        selected = false,
                                        onClick = { currentMenuLevel = "speed" },
                                        colors = listItemColors,
                                        headlineContent = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Speed,
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
                        "audio" -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(audioTracks.size) { index ->
                                    val track = audioTracks[index]
                                    val isSelected = track.mpvId == selectedAudio
                                    val title = buildString { 
                                        append(track.name ?: "Track ${track.mpvId}")
                                        if (track.lang != null) append(" - ${track.lang}")
                                    }
                                    
                                    androidx.tv.material3.ListItem(
                                        selected = isSelected,
                                        onClick = { 
                                            onAudioSelected(track.mpvId)
                                            // Optional: stay in menu to confirm selection or go back. Exo behavior usually stays or flashes.
                                            // currentMenuLevel = "main" // Uncomment to auto-back
                                        },
                                        colors = listItemColors,
                                        headlineContent = {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                                )
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (index == 0) Modifier.focusRequester(audioFirstItemFocusRequester) else Modifier)
                                    )
                                }
                            }
                        }
                        "subtitles" -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // None option
                                item {
                                    androidx.tv.material3.ListItem(
                                        selected = selectedSub == -1,
                                        onClick = { 
                                            onSubtitleSelected(-1) 
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
                                
                                items(subTracks.size) { index ->
                                    val track = subTracks[index]
                                    val isSelected = track.mpvId == selectedSub
                                    val title = buildString {
                                         append(track.name ?: "Subtitle ${track.mpvId}")
                                         if (track.lang != null) append(" - ${track.lang}")
                                    }
                                    
                                    androidx.tv.material3.ListItem(
                                        selected = isSelected,
                                        onClick = { 
                                            onSubtitleSelected(track.mpvId) 
                                        },
                                        colors = listItemColors,
                                        headlineContent = {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                                )
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        "speed" -> {
                            val speedOptions = listOf(0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(speedOptions.size) { index ->
                                    val speed = speedOptions[index]
                                    val isSelected = kotlin.math.abs(playbackSpeed - speed) < 0.01
                                    
                                    androidx.tv.material3.ListItem(
                                        selected = isSelected,
                                        onClick = { 
                                            onPlaybackSpeedChange(speed) 
                                        },
                                        colors = listItemColors,
                                        headlineContent = {
                                            Text(
                                                text = "${speed}x",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                                )
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (index == 0) Modifier.focusRequester(speedFirstItemFocusRequester) else Modifier)
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

@Composable
private fun PlayerSeekBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

    // Seek step: 3% or 30s
    val seekStep = if (duration > 0) {
        maxOf(duration / 33, 30000L)
    } else {
        30000L
    }

    val barHeight = if (isFocused) 12.dp else 6.dp
    val thumbSize = if (isFocused) 18.dp else 0.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (isFocused && event.type == KeyEventType.KeyDown) {
                    onInteraction()
                    when (event.key) {
                        Key.DirectionLeft -> {
                            val newPosition = (currentPosition - seekStep).coerceAtLeast(0)
                            onSeek(newPosition)
                            true
                        }
                        Key.DirectionRight -> {
                            val newPosition = (if (duration > 0) (currentPosition + seekStep).coerceAtMost(duration) else currentPosition + seekStep)
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

        // Thumb
        if (isFocused && thumbSize > 0.dp) {
             val thumbOffset = with(LocalDensity.current) {
                (trackWidth.toPx() * progress.coerceIn(0f, 1f) - thumbSize.toPx() / 2).toDp()
            }

            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .align(Alignment.CenterStart)
                    .background(Color.White, RoundedCornerShape(50))
                    .border(2.dp, Color(0xFF9C27B0), RoundedCornerShape(50))
            )
        }
    }
}

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

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.tv.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
        androidx.tv.material3.Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color.Gray
        )
    }
}

@Composable
private fun TrackMenuItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = when {
                    isFocused -> Color.White.copy(alpha = 0.2f)
                    isSelected -> Color(0xFF9C27B0).copy(alpha = 0.3f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            androidx.tv.material3.Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = Color(0xFF9C27B0)
            )
        }
    }
}
