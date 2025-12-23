package com.flex.elefin.music.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.flex.elefin.components.DigitalClock
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.music.data.JellyfinMusicApi
import com.flex.elefin.music.data.MusicRepository
import com.flex.elefin.music.model.Album
import com.flex.elefin.music.model.Artist
import com.flex.elefin.music.model.Track
import com.flex.elefin.music.player.AudioPlayerService
import com.flex.elefin.music.player.AudioQueueManager
import com.flex.elefin.music.player.PlayerConnection
import kotlinx.coroutines.launch

private const val TAG = "MusicHomeScreen"

enum class MusicTab {
    HOME,
    ARTISTS,
    ALBUMS
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MusicHomeScreen(
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onNowPlayingClick: () -> Unit = {},
    onBackPress: () -> Unit = {}
) {
    val context = LocalContext.current
    val config = remember { JellyfinConfig(context) }
    val settings = remember { AppSettings(context) }
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
    var selectedTab by remember { mutableStateOf(MusicTab.HOME) }
    var recentAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var allAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Player state
    val currentTrack by AudioQueueManager.currentTrack.collectAsState()
    val isPlaying by PlayerConnection.isPlaying.collectAsState()

    // Connect to player service
    LaunchedEffect(Unit) {
        PlayerConnection.connect(context)
    }

    // Load data
    LaunchedEffect(Unit) {
        isLoading = true
        Log.d(TAG, "Loading music data...")
        try {
            recentAlbums = repository.getRecentlyAddedAlbums(20)
            Log.d(TAG, "Loaded ${recentAlbums.size} recent albums")
            artists = repository.getArtists(100)
            Log.d(TAG, "Loaded ${artists.size} artists")
            allAlbums = repository.getAllAlbums(100)
            Log.d(TAG, "Loaded ${allAlbums.size} albums")

            // Fallback: If direct artist loading fails but we have albums, extract artists from them
            if (artists.isEmpty() && allAlbums.isNotEmpty()) {
                Log.d(TAG, "Artists list empty, extracting unique artists from loaded albums...")
                artists = allAlbums.mapNotNull { album ->
                    if (album.artistId != null) {
                        Artist(
                            id = album.artistId,
                            name = album.artist,
                            overview = null,
                            imageUrl = album.imageUrl, // Use album art as fallback for artist profile
                            albumCount = 0,
                            songCount = 0
                        )
                    } else null
                }.distinctBy { it.id }.sortedBy { it.name }
                Log.d(TAG, "Extracted ${artists.size} unique artists from albums")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading music data", e)
        }
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with tabs
            MusicHeader(
                selectedTab = selectedTab,
                onTabSelected = { 
                    Log.d(TAG, "Tab selected: $it")
                    selectedTab = it 
                },
                onBackPress = onBackPress,
                use24HourFormat = settings.use24HourTime
            )

            // Content based on selected tab
            when (selectedTab) {
                MusicTab.HOME -> MusicHomeContent(
                    recentAlbums = recentAlbums,
                    artists = artists,
                    isLoading = isLoading,
                    onArtistClick = onArtistClick,
                    onAlbumClick = onAlbumClick,
                    onPlayAlbum = { album ->
                        scope.launch {
                            val tracks = repository.getTracksForAlbum(album.id)
                            if (tracks.isNotEmpty()) {
                                startPlayback(context, tracks, 0)
                            }
                        }
                    }
                )
                MusicTab.ARTISTS -> ArtistListContent(
                    artists = artists,
                    isLoading = isLoading,
                    onArtistClick = onArtistClick
                )
                MusicTab.ALBUMS -> AlbumGridContent(
                    albums = allAlbums,
                    isLoading = isLoading,
                    onAlbumClick = onAlbumClick
                )
            }
        }

        // Mini player at bottom
        AnimatedVisibility(
            visible = currentTrack != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MiniPlayer(
                track = currentTrack,
                isPlaying = isPlaying,
                onPlayPause = { PlayerConnection.playPause() },
                onNext = { PlayerConnection.skipNext() },
                onClick = onNowPlayingClick
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MusicHeader(
    selectedTab: MusicTab,
    onTabSelected: (MusicTab) -> Unit,
    onBackPress: () -> Unit,
    use24HourFormat: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 17.dp), // Reduced from 24 to 17 (30% reduction)
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Back button
            IconButton(
                onClick = onBackPress,
                modifier = Modifier.size(34.dp) // Reduced from 48 to 34
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp) // Reduced icon size
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Music icon and title
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color(0xFF1DB954), // Spotify green accent
                modifier = Modifier.size(22.dp) // Reduced from 32 to 22
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Music",
                style = MaterialTheme.typography.headlineSmall, // Reduced from Medium to Small
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        // Tab row using TV Material3 TabRow for consistent indicator style
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            indicator = { tabPositions, doesFocusExist ->
                if (selectedTab.ordinal < tabPositions.size) {
                    TabRowDefaults.UnderlinedIndicator(
                        currentTabPosition = tabPositions[selectedTab.ordinal],
                        doesTabRowHaveFocus = doesFocusExist
                    )
                }
            },
            separator = { Spacer(modifier = Modifier.width(8.dp)) }, // Reduced from 12 to 8
            modifier = Modifier.wrapContentWidth()
        ) {
            MusicTab.entries.forEach { tab ->
                var isFocused by remember { mutableStateOf(false) }
                val isSelected = selectedTab == tab
                
                Tab(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    onFocus = { /* Handled by TabRow */ },
                    colors = TabDefaults.underlinedIndicatorTabColors(
                        contentColor = Color.White.copy(alpha = 0.7f),
                        selectedContentColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    modifier = Modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .then(
                            if (isFocused) {
                                Modifier.background(Color.White, RoundedCornerShape(4.dp))
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Text(
                        text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.85f // Increased by 10% from 0.77f
                        ),
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp) // Reduced horizontal from 16 to 11, vertical from 8 to 6
                    )
                }
    }
}

// Digital clock on the far right
DigitalClock(use24HourFormat = use24HourFormat)
}
}



@Composable
private fun MusicHomeContent(
    recentAlbums: List<Album>,
    artists: List<Artist>,
    isLoading: Boolean,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlayAlbum: (Album) -> Unit
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
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Recently Added Albums
            if (recentAlbums.isNotEmpty()) {
                item {
                    MusicSection(
                        title = "Recently Added",
                        subtitle = "${recentAlbums.size} albums"
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(recentAlbums) { album ->
                                AlbumCard(
                                    album = album,
                                    onClick = { onAlbumClick(album.id) },
                                    onPlay = { onPlayAlbum(album) }
                                )
                            }
                        }
                    }
                }
            }

            // Featured Artists
            if (artists.isNotEmpty()) {
                item {
                    MusicSection(
                        title = "Artists",
                        subtitle = "${artists.size} artists"
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(artists.take(10)) { artist ->
                                ArtistCard(
                                    artist = artist,
                                    onClick = { onArtistClick(artist.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicSection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    onPlay: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "scale")

    Column(
        modifier = Modifier
            .width(160.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Card(
            onClick = onClick,
            onLongClick = onPlay,
            modifier = Modifier
                .size(160.dp)
                .onFocusChanged { isFocused = it.isFocused }
                .then(
                    if (isFocused) Modifier.border(
                        3.dp,
                        Color.White,
                        RoundedCornerShape(8.dp)
                    ) else Modifier
                ),
            colors = CardDefaults.colors(
                containerColor = Color(0xFF282828),
                focusedContainerColor = Color(0xFF282828)
            ),
            shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
            scale = CardDefaults.scale(focusedScale = 1.0f) // Keep static size, we use outer column scale
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(album.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Play button overlay on focus
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF1DB954), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ArtistCard(
    artist: Artist,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "scale")

    Column(
        modifier = Modifier
            .width(140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .onFocusChanged { isFocused = it.isFocused }
                .then(
                    if (isFocused) Modifier.border(
                        3.dp,
                        Color.White,
                        CircleShape
                    ) else Modifier
                ),
            colors = CardDefaults.colors(
                containerColor = Color(0xFF282828),
                focusedContainerColor = Color(0xFF282828)
            ),
            shape = CardDefaults.shape(CircleShape),
            scale = CardDefaults.scale(focusedScale = 1.0f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (artist.imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artist.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF535353),
                                        Color(0xFF282828)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun ArtistListContent(
    artists: List<Artist>,
    isLoading: Boolean,
    onArtistClick: (String) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF1DB954))
        }
    } else {
        if (artists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No artists found", color = Color.Gray)
            }
        } else {
            // Use a grid for better visibility on TV
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                items(artists) { artist ->
                    ArtistCard(
                        artist = artist,
                        onClick = { onArtistClick(artist.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumGridContent(
    albums: List<Album>,
    isLoading: Boolean,
    onAlbumClick: (String) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF1DB954))
        }
    } else {
        if (albums.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No albums found", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(180.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                items(albums) { album ->
                    AlbumCard(
                        album = album,
                        onClick = { onAlbumClick(album.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MiniPlayer(
    track: Track?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit
) {
    if (track == null) return

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF282828),
            focusedContainerColor = Color(0xFF383838)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(track.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = track.album,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
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
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/**
 * Start playback with the given tracks
 */
private fun startPlayback(context: Context, tracks: List<Track>, startIndex: Int) {
    // Start the audio service
    val intent = Intent(context, AudioPlayerService::class.java)
    context.startForegroundService(intent)

    // Connect and play
    PlayerConnection.connect(context)
    PlayerConnection.playTracks(tracks, startIndex)
}
