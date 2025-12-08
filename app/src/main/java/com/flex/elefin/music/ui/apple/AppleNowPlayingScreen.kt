package com.flex.elefin.music.ui.apple

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flex.elefin.music.model.Track
import com.flex.elefin.music.player.AudioQueueManager
import com.flex.elefin.music.player.PlayerConnection
import kotlinx.coroutines.delay

// Apple Music signature red/pink color
private val AppleMusicRed = Color(0xFFFA2D55)
private val AppleMusicPink = Color(0xFFFC3C6C)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppleNowPlayingScreen(
    onBackPress: () -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {}
) {
    // Player state
    val currentTrack by AudioQueueManager.currentTrack.collectAsState()
    val queue by AudioQueueManager.queue.collectAsState()
    val currentIndex by AudioQueueManager.currentIndex.collectAsState()
    val isPlaying by PlayerConnection.isPlaying.collectAsState()
    val currentPosition by PlayerConnection.currentPosition.collectAsState()
    val duration by PlayerConnection.duration.collectAsState()
    val shuffleEnabled by AudioQueueManager.shuffleEnabled.collectAsState()
    val repeatMode by AudioQueueManager.repeatMode.collectAsState()

    // Update position periodically
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            PlayerConnection.updatePosition()
            delay(500)
        }
    }

    // View state
    var showQueue by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred background
        AppleBlurBackground(
            imageUrl = currentTrack?.imageUrl,
            darken = 0.6f,
            blurRadius = 100f
        )

        // Gradient overlay for depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.7f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        if (currentTrack == null) {
            AppleEmptyNowPlaying(onBackPress = onBackPress)
        } else if (showQueue) {
            AppleQueueView(
                queue = queue,
                currentIndex = currentIndex,
                onTrackClick = { index ->
                    AudioQueueManager.skipTo(index)?.let { track ->
                        PlayerConnection.playTrack(track)
                    }
                },
                onBackPress = { showQueue = false }
            )
        } else {
            AppleMainNowPlayingView(
                track = currentTrack!!,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                onBackPress = onBackPress,
                onPlayPause = { PlayerConnection.playPause() },
                onNext = { PlayerConnection.skipNext() },
                onPrevious = { PlayerConnection.skipPrevious() },
                onSeek = { PlayerConnection.seekTo(it) },
                onShuffleToggle = { AudioQueueManager.toggleShuffle() },
                onRepeatToggle = { AudioQueueManager.cycleRepeatMode() },
                onQueueClick = { showQueue = true },
                onAlbumClick = { currentTrack?.albumId?.let { onAlbumClick(it) } },
                onArtistClick = { currentTrack?.artistId?.let { onArtistClick(it) } }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppleEmptyNowPlaying(onBackPress: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = onBackPress,
            modifier = Modifier
                .align(Alignment.Start)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2C2C2E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color(0xFF8E8E93),
                modifier = Modifier.size(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Not Playing",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Select something to play",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF8E8E93)
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppleMainNowPlayingView(
    track: Track,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: AudioQueueManager.RepeatMode,
    onBackPress: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onQueueClick: () -> Unit,
    onAlbumClick: () -> Unit,
    onArtistClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(60.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left side - Album art
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBackPress,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }

                IconButton(
                    onClick = onQueueClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Large album art with shadow
            AppleLargeAlbumArt(
                imageUrl = track.imageUrl,
                size = 360.dp,
                cornerRadius = 16.dp
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.width(60.dp))

        // Right side - Controls
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Track title
            Text(
                text = track.name,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 32.sp,
                    lineHeight = 40.sp
                ),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Artist (clickable)
            Surface(
                onClick = onArtistClick,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = AppleMusicRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Album (clickable)
            Surface(
                onClick = onAlbumClick,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = track.album,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF8E8E93),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Progress bar
            Column(modifier = Modifier.fillMaxWidth(0.9f)) {
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { value ->
                        if (duration > 0) {
                            onSeek((value * duration).toLong())
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = AppleMusicRed,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currentPosition),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E8E93)
                    )
                    Text(
                        text = "-${formatDuration(duration - currentPosition)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E8E93)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Main playback controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(
                    onClick = onShuffleToggle,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) AppleMusicRed else Color(0xFF8E8E93),
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Previous
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Play/Pause - Apple style pill button
                Button(
                    onClick = onPlayPause,
                    modifier = Modifier.size(80.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        focusedContainerColor = AppleMusicRed,
                        focusedContentColor = Color.White
                    ),
                    shape = ButtonDefaults.shape(CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Next
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Repeat
                IconButton(
                    onClick = onRepeatToggle,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = when (repeatMode) {
                            AudioQueueManager.RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = when (repeatMode) {
                            AudioQueueManager.RepeatMode.OFF -> Color(0xFF8E8E93)
                            else -> AppleMusicRed
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Audio quality badge
            val audioInfo = buildString {
                track.codec?.let { append(it.uppercase()) }
                track.bitrate?.let {
                    if (isNotEmpty()) append(" • ")
                    append("${it / 1000} kbps")
                }
                track.sampleRate?.let {
                    if (isNotEmpty()) append(" • ")
                    append("${it / 1000} kHz")
                }
            }
            
            if (audioInfo.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = audioInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8E8E93)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppleQueueView(
    queue: List<Track>,
    currentIndex: Int,
    onTrackClick: (Int) -> Unit,
    onBackPress: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(60.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackPress,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = "Up Next",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${queue.size} songs",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8E8E93)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(queue) { index, track ->
                AppleQueueTrackRow(
                    track = track,
                    index = index + 1,
                    isCurrentTrack = index == currentIndex,
                    onClick = { onTrackClick(index) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppleQueueTrackRow(
    track: Track,
    index: Int,
    isCurrentTrack: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor = when {
        isCurrentTrack -> AppleMusicRed.copy(alpha = 0.2f)
        else -> Color.Transparent
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = backgroundColor,
            focusedContainerColor = if (isCurrentTrack) 
                AppleMusicRed.copy(alpha = 0.3f) 
            else 
                Color.White.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentTrack) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Playing",
                        tint = AppleMusicRed,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8E8E93)
                    )
                }
            }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = track.album,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrentTrack) AppleMusicRed else Color.White,
                    fontWeight = if (isCurrentTrack) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8E93),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

