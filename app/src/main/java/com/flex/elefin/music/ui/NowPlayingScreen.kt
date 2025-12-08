package com.flex.elefin.music.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flex.elefin.music.model.Track
import com.flex.elefin.music.player.AudioQueueManager
import com.flex.elefin.music.player.PlayerConnection
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NowPlayingScreen(
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
            delay(1000)
        }
    }

    // View state - show queue or main view
    var showQueue by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF0D0D0D),
                        Color(0xFF0D0D0D)
                    )
                )
            )
    ) {
        if (currentTrack == null) {
            EmptyNowPlaying(onBackPress = onBackPress)
        } else if (showQueue) {
            QueueView(
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
            MainNowPlayingView(
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
private fun EmptyNowPlaying(onBackPress: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
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
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Nothing Playing",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select a song to start playing",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MainNowPlayingView(
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
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBackPress,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = onQueueClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(320.dp)
                    .graphicsLayer {
                        if (isPlaying) {
                            rotationZ = rotation
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF1A1A1A),
                                    Color(0xFF0D0D0D),
                                    Color(0xFF1A1A1A),
                                    Color(0xFF0D0D0D)
                                )
                            )
                        )
                )

                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(track.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = track.album,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(200.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .border(4.dp, Color(0xFF282828), CircleShape)
                )

                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color(0xFF1DB954))
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.width(48.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = track.name,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                onClick = onArtistClick,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF1DB954),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                onClick = onAlbumClick,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {
                Text(
                    text = track.album,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { value ->
                        if (duration > 0) {
                            onSeek((value * duration).toLong())
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF1DB954),
                        activeTrackColor = Color(0xFF1DB954),
                        inactiveTrackColor = Color(0xFF535353)
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
                        color = Color.Gray
                    )
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onShuffleToggle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) Color(0xFF1DB954) else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Button(
                    onClick = onPlayPause,
                    modifier = Modifier.size(72.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF1DB954),
                        contentColor = Color.Black
                    ),
                    shape = ButtonDefaults.shape(CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                IconButton(
                    onClick = onRepeatToggle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = when (repeatMode) {
                            AudioQueueManager.RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = when (repeatMode) {
                            AudioQueueManager.RepeatMode.OFF -> Color.Gray
                            else -> Color(0xFF1DB954)
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                Text(
                    text = audioInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QueueView(
    queue: List<Track>,
    currentIndex: Int,
    onTrackClick: (Int) -> Unit,
    onBackPress: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
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
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = "Queue",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${queue.size} tracks",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(queue) { index, track ->
                QueueTrackRow(
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
private fun QueueTrackRow(
    track: Track,
    index: Int,
    isCurrentTrack: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor = when {
        isCurrentTrack -> Color(0xFF1DB954).copy(alpha = 0.2f)
        else -> Color.Transparent
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = backgroundColor,
            focusedContainerColor = if (isCurrentTrack) Color(0xFF1DB954).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
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
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrentTrack) Color(0xFF1DB954) else Color.White,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
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
