package com.flex.elefin.music.ui.mobile

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flex.elefin.music.model.Track
import com.flex.elefin.music.player.AudioQueueManager
import com.flex.elefin.music.player.PlayerConnection
import com.flex.elefin.theme.JetcasterPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinMobileNowPlayingScreen(
    onCollapse: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val track by AudioQueueManager.currentTrack.collectAsState()
    val isPlaying by PlayerConnection.isPlaying.collectAsState()
    val currentPosition by PlayerConnection.currentPosition.collectAsState()
    val duration by PlayerConnection.duration.collectAsState()
    val repeatMode by AudioQueueManager.repeatMode.collectAsState()
    val shuffleEnabled by AudioQueueManager.shuffleEnabled.collectAsState()
    val queue by AudioQueueManager.queue.collectAsState()
    val currentIndex by AudioQueueManager.currentIndex.collectAsState()

    var showLyrics by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(0.8f) } // Mock volume state

    // Periodic position updater
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            PlayerConnection.updatePosition()
            delay(500)
        }
    }

    if (track == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = JetcasterPrimary)
        }
        return
    }

    if (showLyrics) {
        JellyfinMobileLyricsScreen(
            track = track,
            currentPositionMs = currentPosition,
            onClose = { showLyrics = false }
        )
        return
    }

    val nextTrack = remember(queue, currentIndex) {
        queue.getOrNull(currentIndex + 1)
    }

    // Format millisecond duration into "m:ss"
    fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        // Ambient background color glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1F1B24).copy(alpha = 0.5f),
                            Color(0xFF0F0F0F)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // Top Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Now Playing",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = { showLyrics = true }) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Show Lyrics",
                        tint = JetcasterPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Player",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // Album Artwork with premium shadow/glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(
                            elevation = 24.dp,
                            shape = RoundedCornerShape(20.dp),
                            clip = false,
                            spotColor = JetcasterPrimary.copy(alpha = 0.6f)
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(track?.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = track?.album,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // Song Title and Artist Metadata
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = track?.name ?: "Unknown Song",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 24.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quick Actions Bar (Like, Download, Share, Playlist)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { isFavorite = !isFavorite }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like Song",
                        tint = if (isFavorite) Color.Red else Color.White.copy(alpha = 0.7f)
                    )
                }

                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Download Song",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }

                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }

                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.PlaylistAdd,
                        contentDescription = "Add to Playlist",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Up Next Preview Panel
            AnimatedVisibility(
                visible = nextTrack != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UP NEXT: ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = JetcasterPrimary
                        )
                    )
                    Text(
                        text = "${nextTrack?.name} - ${nextTrack?.artist}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.7f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.05f))

            // Seek Bar slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() else 0f,
                    onValueChange = {
                        PlayerConnection.seekTo(it.toLong())
                    },
                    valueRange = 0f..(if (duration > 0) duration.toFloat() else 100f),
                    colors = SliderDefaults.colors(
                        activeTrackColor = JetcasterPrimary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                        thumbColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            // Playback controls row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                IconButton(onClick = { AudioQueueManager.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) JetcasterPrimary else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Skip Previous Button
                IconButton(onClick = { PlayerConnection.skipPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play / Pause Circle Button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { PlayerConnection.playPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Skip Next Button
                IconButton(onClick = { PlayerConnection.skipNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Repeat Mode Button
                IconButton(onClick = { AudioQueueManager.cycleRepeatMode() }) {
                    Icon(
                        imageVector = when (repeatMode) {
                            AudioQueueManager.RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode != AudioQueueManager.RepeatMode.OFF) JetcasterPrimary else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Volume adjustment slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeDown,
                    contentDescription = "Volume Down",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.White.copy(alpha = 0.6f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                        thumbColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Volume Up",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
