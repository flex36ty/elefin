package com.flex.elefin.music.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.music.data.JellyfinMusicApi
import com.flex.elefin.music.data.MusicRepository
import com.flex.elefin.music.model.Album
import com.flex.elefin.music.model.Track
import com.flex.elefin.music.player.AudioPlayerService
import com.flex.elefin.music.player.AudioQueueManager
import com.flex.elefin.music.player.PlayerConnection

private const val TAG = "AlbumScreen"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AlbumScreen(
    albumId: String,
    onArtistClick: (String) -> Unit = {},
    onBackPress: () -> Unit = {}
) {
    val context = LocalContext.current
    val config = remember { JellyfinConfig(context) }

    // Create repository
    val repository = remember {
        val api = JellyfinMusicApi(
            baseUrl = config.serverUrl,
            accessToken = config.accessToken,
            userId = config.userId
        )
        MusicRepository(api)
    }

    // State
    var album by remember { mutableStateOf<Album?>(null) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Current playing track
    val currentTrack by AudioQueueManager.currentTrack.collectAsState()
    val isPlaying by PlayerConnection.isPlaying.collectAsState()

    // Load data
    LaunchedEffect(albumId) {
        isLoading = true
        try {
            tracks = repository.getTracksForAlbum(albumId)
            
            // Create album from first track info
            if (tracks.isNotEmpty()) {
                val firstTrack = tracks.first()
                album = Album(
                    id = albumId,
                    name = firstTrack.album,
                    artist = firstTrack.artist,
                    artistId = firstTrack.artistId,
                    year = null,
                    imageUrl = firstTrack.imageUrl,
                    overview = null,
                    trackCount = tracks.size,
                    durationTicks = tracks.sumOf { it.durationMs * 10000 }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading album data", e)
        }
        isLoading = false
    }

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
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF1DB954))
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left side - Album info
                AlbumInfoPanel(
                    album = album,
                    tracks = tracks,
                    onBackPress = onBackPress,
                    onArtistClick = {
                        album?.artistId?.let { onArtistClick(it) }
                    },
                    onPlayAll = {
                        if (tracks.isNotEmpty()) {
                            startPlayback(context, tracks, 0)
                        }
                    },
                    onShuffle = {
                        if (tracks.isNotEmpty()) {
                            startPlayback(context, tracks.shuffled(), 0)
                        }
                    }
                )

                // Right side - Track list
                TrackListPanel(
                    tracks = tracks,
                    currentTrackId = currentTrack?.id,
                    isPlaying = isPlaying,
                    onTrackClick = { index ->
                        startPlayback(context, tracks, index)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AlbumInfoPanel(
    album: Album?,
    tracks: List<Track>,
    onBackPress: () -> Unit,
    onArtistClick: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    val totalDuration = tracks.sumOf { it.durationMs }

    Column(
        modifier = Modifier
            .width(400.dp)
            .fillMaxHeight()
            .padding(48.dp)
    ) {
        // Back button
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

        Spacer(modifier = Modifier.height(24.dp))

        // Album artwork
        Card(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            colors = CardDefaults.colors(
                containerColor = Color(0xFF282828)
            ),
            shape = CardDefaults.shape(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(album?.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = album?.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Album title
        Text(
            text = album?.name ?: "Unknown Album",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Artist name (clickable)
        Surface(
            onClick = onArtistClick,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.1f)
            )
        ) {
            Text(
                text = album?.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF1DB954),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Album info
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            album?.year?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
            Text(
                text = "${tracks.size} songs",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Text(
                text = formatTotalDuration(totalDuration),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action buttons
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.colors(
                    containerColor = Color(0xFF1DB954),
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onShuffle,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shuffle")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackListPanel(
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onTrackClick: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 48.dp, top = 48.dp, bottom = 48.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        itemsIndexed(tracks) { index, track ->
            AlbumTrackRow(
                track = track,
                index = index + 1,
                isCurrentTrack = track.id == currentTrackId,
                isPlaying = isPlaying && track.id == currentTrackId,
                onClick = { onTrackClick(index) }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AlbumTrackRow(
    track: Track,
    index: Int,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
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
            .padding(vertical = 2.dp)
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track number or playing indicator
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentTrack && isPlaying) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Playing",
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(20.dp)
                    )
                } else if (isFocused) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrentTrack) Color(0xFF1DB954) else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrentTrack) Color(0xFF1DB954) else Color.White,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val audioInfo = buildString {
                    track.codec?.let { append(it.uppercase()) }
                    track.bitrate?.let { 
                        if (isNotEmpty()) append(" • ")
                        append("${it / 1000} kbps")
                    }
                }
                if (audioInfo.isNotEmpty()) {
                    Text(
                        text = audioInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatTotalDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    
    return if (hours > 0) {
        "%d hr %d min".format(hours, minutes)
    } else {
        "%d min".format(minutes)
    }
}

private fun startPlayback(context: Context, tracks: List<Track>, startIndex: Int) {
    val intent = Intent(context, AudioPlayerService::class.java)
    context.startForegroundService(intent)
    PlayerConnection.connect(context)
    PlayerConnection.playTracks(tracks, startIndex)
}
