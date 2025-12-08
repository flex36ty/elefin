package com.flex.elefin.music.ui.apple

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private const val TAG = "AppleAlbumScreen"

// Apple Music signature colors
private val AppleMusicRed = Color(0xFFFA2D55)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppleAlbumScreen(
    albumId: String,
    onArtistClick: (String) -> Unit = {},
    onBackPress: () -> Unit = {}
) {
    val context = LocalContext.current
    val config = remember { JellyfinConfig(context) }

    val repository = remember {
        val api = JellyfinMusicApi(
            baseUrl = config.serverUrl,
            accessToken = config.accessToken,
            userId = config.userId
        )
        MusicRepository(api)
    }

    var album by remember { mutableStateOf<Album?>(null) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val currentTrack by AudioQueueManager.currentTrack.collectAsState()
    val isPlaying by PlayerConnection.isPlaying.collectAsState()

    LaunchedEffect(albumId) {
        isLoading = true
        try {
            tracks = repository.getTracksForAlbum(albumId)
            
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred background
        AppleBlurBackground(
            imageUrl = album?.imageUrl,
            darken = 0.65f,
            blurRadius = 100f
        )

        // Additional gradient for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.2f)
                        )
                    )
                )
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppleMusicRed)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(60.dp)
            ) {
                // Left side - Album info
                AppleAlbumInfoPanel(
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

                Spacer(modifier = Modifier.width(60.dp))

                // Right side - Track list
                AppleAlbumTrackList(
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
private fun AppleAlbumInfoPanel(
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
            .width(420.dp)
            .fillMaxHeight()
    ) {
        // Back button
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

        Spacer(modifier = Modifier.height(24.dp))

        // Large album artwork
        AppleLargeAlbumArt(
            imageUrl = album?.imageUrl,
            size = 340.dp,
            cornerRadius = 12.dp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Album title
        Text(
            text = album?.name ?: "Unknown Album",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 28.sp,
                lineHeight = 34.sp
            ),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Artist name (clickable)
        Surface(
            onClick = onArtistClick,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.1f)
            )
        ) {
            Text(
                text = album?.artist ?: "Unknown Artist",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = AppleMusicRed,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Album metadata
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ALBUM",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E8E93)
            )
            album?.year?.let {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8E8E93)
                )
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8E8E93)
                )
            }
            Text(
                text = "•",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E8E93)
            )
            Text(
                text = "${tracks.size} songs, ${formatTotalDuration(totalDuration)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8E8E93)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action buttons - Apple style pills
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.colors(
                    containerColor = AppleMusicRed,
                    contentColor = Color.White,
                    focusedContainerColor = AppleMusicRed.copy(alpha = 0.8f)
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play", fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = onShuffle,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = AppleMusicRed,
                    focusedContainerColor = Color.White.copy(alpha = 0.25f)
                ),
                shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shuffle", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AppleAlbumTrackList(
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onTrackClick: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(tracks) { index, track ->
            AppleTrackRow(
                track = track,
                index = index + 1,
                onClick = { onTrackClick(index) },
                isCurrentTrack = track.id == currentTrackId,
                isPlaying = isPlaying && track.id == currentTrackId,
                showAlbumArt = false  // Don't show album art in album view
            )
        }
    }
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

