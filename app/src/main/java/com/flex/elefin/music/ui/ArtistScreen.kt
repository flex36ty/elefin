package com.flex.elefin.music.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.flex.elefin.music.model.Artist
import com.flex.elefin.music.model.Track
import com.flex.elefin.music.player.AudioPlayerService
import com.flex.elefin.music.player.PlayerConnection
import kotlinx.coroutines.launch

private const val TAG = "ArtistScreen"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistId: String,
    onAlbumClick: (String) -> Unit = {},
    onBackPress: () -> Unit = {}
) {
    val context = LocalContext.current
    val config = remember { JellyfinConfig(context) }
    val scope = rememberCoroutineScope()

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
    var artist by remember { mutableStateOf<Artist?>(null) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var topTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load data
    LaunchedEffect(artistId) {
        isLoading = true
        try {
            albums = repository.getAlbumsForArtist(artistId)
            topTracks = repository.getTracksByArtist(artistId, 10)
            
            // Create artist from first album info if available
            if (albums.isNotEmpty()) {
                val firstAlbum = albums.first()
                artist = Artist(
                    id = artistId,
                    name = firstAlbum.artist,
                    overview = null,
                    imageUrl = firstAlbum.imageUrl,
                    albumCount = albums.size,
                    songCount = topTracks.size
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading artist data", e)
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                // Artist header
                item {
                    ArtistHeader(
                        artist = artist,
                        albumCount = albums.size,
                        onBackPress = onBackPress,
                        onPlayAll = {
                            scope.launch {
                                val allTracks = albums.flatMap { album ->
                                    repository.getTracksForAlbum(album.id)
                                }
                                if (allTracks.isNotEmpty()) {
                                    startPlayback(context, allTracks, 0)
                                }
                            }
                        },
                        onShuffle = {
                            scope.launch {
                                val allTracks = albums.flatMap { album ->
                                    repository.getTracksForAlbum(album.id)
                                }.shuffled()
                                if (allTracks.isNotEmpty()) {
                                    startPlayback(context, allTracks, 0)
                                }
                            }
                        }
                    )
                }

                // Top tracks section
                if (topTracks.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionTitle("Popular Tracks")
                    }

                    items(topTracks.take(5)) { track ->
                        TrackRow(
                            track = track,
                            index = topTracks.indexOf(track) + 1,
                            onClick = {
                                startPlayback(context, topTracks, topTracks.indexOf(track))
                            }
                        )
                    }
                }

                // Albums section
                if (albums.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                        SectionTitle("Albums")
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(albums) { album ->
                                ArtistAlbumCard(
                                    album = album,
                                    onClick = { onAlbumClick(album.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ArtistHeader(
    artist: Artist?,
    albumCount: Int,
    onBackPress: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
    ) {
        // Background image with gradient
        if (artist?.imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artist.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.3f }
            )
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF0D0D0D)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
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

            Spacer(modifier = Modifier.weight(1f))

            // Artist info
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                // Artist image
                if (artist?.imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artist.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(180.dp)
                            .clip(CircleShape)
                            .border(4.dp, Color(0xFF1DB954), CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF282828))
                            .border(4.dp, Color(0xFF1DB954), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(32.dp))

                Column {
                    Text(
                        text = "ARTIST",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artist?.name ?: "Unknown Artist",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$albumCount albums",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = onPlayAll,
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF1DB954),
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play All", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onShuffle,
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
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 48.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackRow(
    track: Track,
    index: Int,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 4.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track number
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.width(32.dp)
            )

            // Album art
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

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            // Play indicator on focus
            if (isFocused) {
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF1DB954),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ArtistAlbumCard(
    album: Album,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "scale")

    Column(
        modifier = Modifier
            .width(180.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .size(180.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .then(
                    if (isFocused) Modifier.border(
                        3.dp,
                        Color(0xFF1DB954),
                        RoundedCornerShape(8.dp)
                    ) else Modifier
                ),
            colors = CardDefaults.colors(
                containerColor = Color(0xFF282828)
            ),
            shape = CardDefaults.shape(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(album.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            album.year?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Text(
                text = "${album.trackCount} tracks",
                style = MaterialTheme.typography.bodySmall,
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

private fun startPlayback(context: Context, tracks: List<Track>, startIndex: Int) {
    val intent = Intent(context, AudioPlayerService::class.java)
    context.startForegroundService(intent)
    PlayerConnection.connect(context)
    PlayerConnection.playTracks(tracks, startIndex)
}
