package com.flex.elefin.music.ui.apple

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.sp
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

private const val TAG = "AppleArtistScreen"

// Apple Music signature colors
private val AppleMusicRed = Color(0xFFFA2D55)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppleArtistScreen(
    artistId: String,
    onAlbumClick: (String) -> Unit = {},
    onBackPress: () -> Unit = {}
) {
    val context = LocalContext.current
    val config = remember { JellyfinConfig(context) }
    val scope = rememberCoroutineScope()

    val repository = remember {
        val api = JellyfinMusicApi(
            baseUrl = config.serverUrl,
            accessToken = config.accessToken,
            userId = config.userId
        )
        MusicRepository(api)
    }

    var artist by remember { mutableStateOf<Artist?>(null) }
    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var topTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(artistId) {
        isLoading = true
        try {
            albums = repository.getAlbumsForArtist(artistId)
            topTracks = repository.getTracksByArtist(artistId, 10)
            
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred background
        AppleBlurBackground(
            imageUrl = artist?.imageUrl,
            darken = 0.7f,
            blurRadius = 120f
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.8f)
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                // Artist header
                item {
                    AppleArtistHeader(
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
                        Spacer(modifier = Modifier.height(40.dp))
                        AppleSectionTitle("Top Songs")
                    }

                    items(topTracks.take(5)) { track ->
                        AppleArtistTrackRow(
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
                        Spacer(modifier = Modifier.height(48.dp))
                        AppleSectionTitle("Albums")
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 60.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            items(albums) { album ->
                                AppleAlbumCard(
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
private fun AppleArtistHeader(
    artist: Artist?,
    albumCount: Int,
    onBackPress: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
    ) {
        // Hero background image with gradient
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
                    .graphicsLayer { alpha = 0.4f }
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
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(60.dp)
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

            Spacer(modifier = Modifier.weight(1f))

            // Artist info row
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                // Artist image - circular Apple style
                if (artist?.imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artist.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .border(4.dp, AppleMusicRed, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2C2C2E))
                            .border(4.dp, AppleMusicRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF8E8E93),
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(40.dp))

                Column {
                    Text(
                        text = "ARTIST",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8E8E93),
                        letterSpacing = 1.5.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = artist?.name ?: "Unknown Artist",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 44.sp,
                            lineHeight = 50.sp
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "$albumCount albums",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF8E8E93)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = onPlayAll,
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
                }
            }
        }
    }
}

@Composable
private fun AppleSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 60.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppleArtistTrackRow(
    track: Track,
    index: Int,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "row_scale"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 60.dp, vertical = 4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track number
            Box(
                modifier = Modifier.width(36.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isFocused) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
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

            // Album art
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
                    color = Color(0xFF8E8E93),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppleAlbumCard(
    album: Album,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "card_scale"
    )

    Column(
        modifier = Modifier
            .width(200.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .size(200.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .then(
                    if (isFocused) Modifier.border(
                        3.dp,
                        AppleMusicRed,
                        RoundedCornerShape(12.dp)
                    ) else Modifier
                ),
            colors = CardDefaults.colors(
                containerColor = Color(0xFF2C2C2E)
            ),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp))
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

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            album.year?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8E93)
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E8E93)
                )
            }
            Text(
                text = "${album.trackCount} songs",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF8E8E93)
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

