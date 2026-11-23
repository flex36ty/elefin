package com.flex.elefin.music.ui.mobile

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.music.data.JellyfinMusicApi
import com.flex.elefin.music.data.MusicRepository
import com.flex.elefin.music.model.Album
import com.flex.elefin.music.model.Artist
import com.flex.elefin.music.model.Track
import com.flex.elefin.music.player.AudioPlayerService
import com.flex.elefin.music.player.AudioQueueManager
import com.flex.elefin.music.player.PlayerConnection
import com.flex.elefin.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "JellyfinMobileMusic"

enum class MobileMusicTab {
    HOME,
    ALBUMS,
    ARTISTS,
    SONGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinMobileMusicScreen(
    bottomPadding: Dp = 0.dp,
    showFullHeader: Boolean = false,
    onBackPress: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = remember { JellyfinConfig(context) }

    val repository = remember {
        val api = JellyfinMusicApi(
            baseUrl = config.serverUrl,
            accessToken = config.accessToken,
            userId = config.userId
        )
        MusicRepository(api)
    }

    var selectedTab by remember { mutableStateOf(MobileMusicTab.HOME) }
    var recommendedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var recentlyAddedAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var allAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var artists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var allSongs by remember { mutableStateOf<List<Track>>(emptyList()) }
    var randomAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var randomArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedAlbumForDetail by remember { mutableStateOf<Album?>(null) }

    // Bottom sheet Now Playing state
    var isNowPlayingExpanded by remember { mutableStateOf(false) }

    // Connect player
    val currentTrack by AudioQueueManager.currentTrack.collectAsState()
    val isPlaying by PlayerConnection.isPlaying.collectAsState()
    val currentPosition by PlayerConnection.currentPosition.collectAsState()
    val duration by PlayerConnection.duration.collectAsState()

    LaunchedEffect(Unit) {
        PlayerConnection.connect(context)
    }

    LaunchedEffect(Unit) {
        isLoading = true
        recommendedTracks = repository.getRecommendedTracks(50)
        recentlyAddedAlbums = repository.getRecentlyAddedAlbums(20)
        allAlbums = repository.getAllAlbums(500)
        randomAlbums = allAlbums.shuffled()
        artists = repository.getArtists(500)
        randomArtists = artists.shuffled()
        allSongs = repository.getAllTracks(1000)
        isLoading = false
    }

    LaunchedEffect(selectedTab, searchQuery) {
        if (selectedTab == MobileMusicTab.HOME && searchQuery.isNotBlank()) {
            isSearching = true
            delay(300) // Debounce
            val results = repository.searchMusic(searchQuery)
            searchResults = results.third
        } else {
            isSearching = false
        }
    }

    fun playAlbum(album: Album) {
        scope.launch {
            isLoading = true
            val albumTracks = repository.getTracksForAlbum(album.id)
            isLoading = false
            if (albumTracks.isNotEmpty()) {
                startPlayback(context, albumTracks, 0)
            }
        }
    }

    fun playArtist(artist: Artist) {
        scope.launch {
            isLoading = true
            val artistTracks = repository.getTracksByArtist(artist.id, limit = 50)
            isLoading = false
            if (artistTracks.isNotEmpty()) {
                startPlayback(context, artistTracks, 0)
            }
        }
    }

    Scaffold(
        bottomBar = {
            Column {
                NavigationBar(
                    containerColor = JetcasterSurface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == MobileMusicTab.HOME,
                        onClick = { selectedTab = MobileMusicTab.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JetcasterPrimary,
                            selectedTextColor = JetcasterPrimary,
                            unselectedIconColor = Color.White.copy(alpha = 0.6f),
                            unselectedTextColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == MobileMusicTab.ALBUMS,
                        onClick = { selectedTab = MobileMusicTab.ALBUMS },
                        icon = { Icon(Icons.Default.Album, contentDescription = "Albums") },
                        label = { Text("Albums") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JetcasterPrimary,
                            selectedTextColor = JetcasterPrimary,
                            unselectedIconColor = Color.White.copy(alpha = 0.6f),
                            unselectedTextColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == MobileMusicTab.ARTISTS,
                        onClick = { selectedTab = MobileMusicTab.ARTISTS },
                        icon = { Icon(Icons.Default.Person, contentDescription = "Artists") },
                        label = { Text("Artists") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JetcasterPrimary,
                            selectedTextColor = JetcasterPrimary,
                            unselectedIconColor = Color.White.copy(alpha = 0.6f),
                            unselectedTextColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == MobileMusicTab.SONGS,
                        onClick = { selectedTab = MobileMusicTab.SONGS },
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = "All Songs") },
                        label = { Text("Songs") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JetcasterPrimary,
                            selectedTextColor = JetcasterPrimary,
                            unselectedIconColor = Color.White.copy(alpha = 0.6f),
                            unselectedTextColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(bottomPadding))
            }
        },
        contentWindowInsets = WindowInsets(0),
        containerColor = Color(0xFF0F0F0F)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (showFullHeader) Modifier.statusBarsPadding() else Modifier)
            ) {
                // Header (Menu, Search, Avatar)
                if (showFullHeader) {
                    MobileMusicHeader(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        showFullHeader = showFullHeader,
                        onBack = onBackPress
                    )
                }

                // Main Content
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        MobileMusicTab.HOME -> {
                            if (isLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = JetcasterPrimary)
                                }
                            } else if (isSearching) {
                                MobileSongList(
                                    tracks = searchResults,
                                    onTrackClick = { idx ->
                                        startPlayback(context, searchResults, idx)
                                    }
                                )
                            } else {
                                MobileHomeContent(
                                    recentlyAdded = recentlyAddedAlbums,
                                    randomAlbums = randomAlbums,
                                    randomArtists = randomArtists,
                                    onAlbumClick = { selectedAlbumForDetail = it },
                                    onArtistClick = { playArtist(it) }
                                )
                            }
                        }
                        MobileMusicTab.ALBUMS -> {
                            MobileAlbumsContent(
                                albums = allAlbums.ifEmpty { recentlyAddedAlbums + randomAlbums },
                                onAlbumClick = { selectedAlbumForDetail = it }
                            )
                        }
                        MobileMusicTab.ARTISTS -> {
                            MobileArtistsContent(
                                artists = artists.ifEmpty { randomArtists },
                                onArtistClick = { playArtist(it) }
                            )
                        }
                        MobileMusicTab.SONGS -> {
                            MobileAllSongsContent(
                                tracks = allSongs.ifEmpty { recommendedTracks },
                                onTrackClick = { idx ->
                                    val tracks = allSongs.ifEmpty { recommendedTracks }
                                    startPlayback(context, tracks, idx)
                                }
                            )
                        }
                    }
                }
            }

            // Floating Mini Player
            if (currentTrack != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                ) {
                    MobileMiniPlayer(
                        track = currentTrack!!,
                        isPlaying = isPlaying,
                        positionMs = currentPosition,
                        durationMs = duration,
                        onClick = { isNowPlayingExpanded = true },
                        onPlayPause = { PlayerConnection.playPause() },
                        onNext = { PlayerConnection.skipNext() }
                    )
                }
            }
        }
    }

    // Now Playing Expansion Modal
    AnimatedVisibility(
        visible = isNowPlayingExpanded,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        JellyfinMobileNowPlayingScreen(
            onCollapse = { isNowPlayingExpanded = false },
            onClose = {
                isNowPlayingExpanded = false
                PlayerConnection.stop()
            }
        )
    }

    // Album Detail Slide-over
    AnimatedVisibility(
        visible = selectedAlbumForDetail != null,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    ) {
        selectedAlbumForDetail?.let { album ->
            MobileAlbumDetailScreen(
                album = album,
                repository = repository,
                applyStatusBarPadding = showFullHeader,
                onBack = { selectedAlbumForDetail = null },
                onTrackClick = { tracks, startIndex ->
                    startPlayback(context, tracks, startIndex)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMusicHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    showFullHeader: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (showFullHeader) 10.dp else 0.dp,
                bottom = if (showFullHeader) 10.dp else 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showFullHeader) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = Color.White
                )
            }
        } else {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        // Search Bar container
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search for tracks, albums...", fontSize = 14.sp) },
            prefix = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 4.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                focusedBorderColor = Color.White.copy(alpha = 0.12f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        )

        if (showFullHeader) {
            // Profile Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun MobileHomeContent(
    recentlyAdded: List<Album>,
    randomAlbums: List<Album>,
    randomArtists: List<Artist>,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Row 1: Recently Added Albums
        if (recentlyAdded.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Recently Added",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recentlyAdded) { album ->
                            AlbumCard(album = album, onClick = { onAlbumClick(album) })
                        }
                    }
                }
            }
        }

        // Row 2: Random Albums
        if (randomAlbums.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Random Albums",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(randomAlbums) { album ->
                            AlbumCard(album = album, onClick = { onAlbumClick(album) })
                        }
                    }
                }
            }
        }

        // Row 3: Random Artists
        if (randomArtists.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Random Artists",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(randomArtists) { artist ->
                            ArtistCard(artist = artist, onClick = { onArtistClick(artist) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(album.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ArtistCard(artist: Artist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artist.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = artist.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = artist.name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun MobileTrackListItem(
    track: Track,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover art
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(track.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = track.album,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        )

        Spacer(modifier = Modifier.width(14.dp))

        // Title and artist/album info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Quality Badges
                val badge = remember(track.codec, track.bitrate) {
                    if (track.codec?.uppercase() == "FLAC" || (track.bitrate ?: 0) > 400000) "FLAC" else "HD"
                }
                Box(
                    modifier = Modifier
                        .border(0.5.dp, JetcasterPrimary.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp, vertical = 0.5.dp)
                ) {
                    Text(
                        text = badge,
                        color = JetcasterPrimary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${track.artist} • ${track.album}",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Duration & More actions
        val minutes = (track.durationMs / 1000) / 60
        val seconds = (track.durationMs / 1000) % 60
        val durationText = String.format("%d:%02d", minutes, seconds)

        Text(
            text = durationText,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun MobileSongList(
    tracks: List<Track>,
    onTrackClick: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        itemsIndexed(tracks) { index, track ->
            MobileTrackListItem(
                track = track,
                onClick = { onTrackClick(index) }
            )
        }
    }
}

@Composable
fun MobileAlbumsContent(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Albums",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            items(albums) { album ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumClick(album) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.DarkGray)
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
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = album.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${album.trackCount} tracks",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MobileArtistsContent(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Artists",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            items(artists) { artist ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArtistClick(artist) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artist.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = artist.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun MobileAllSongsContent(
    tracks: List<Track>,
    onTrackClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "All Songs",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 72.dp)
        ) {
            itemsIndexed(tracks) { idx, track ->
                MobileTrackListItem(
                    track = track,
                    onClick = { onTrackClick(idx) }
                )
            }
        }
    }
}

@Composable
fun MobileSettingsContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Settings",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val categories = listOf("Appearance", "Interface", "Scrobbling", "Audio", "Downloads", "Instances", "System")
            items(categories) { cat ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (cat == "Audio") JetcasterPrimary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
                        .then(
                            if (cat == "Audio") Modifier.border(1.dp, JetcasterPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)) else Modifier
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cat,
                        color = if (cat == "Audio") JetcasterPrimary else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Settings items
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Music Provider", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Jellyfin Client", color = Color.White, fontSize = 14.sp)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                }
            }

            Text("Streaming Quality", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hi-Res Lossless (up to 24-bit/192kHz)", color = Color.White, fontSize = 14.sp)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Show Quality Badges", color = Color.White, fontSize = 14.sp)
                    Text("Display HD/FLAC badges beside songs", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                }
                Switch(
                    checked = true,
                    onCheckedChange = {},
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = JetcasterPrimary)
                )
            }
        }
    }
}

@Composable
fun MobileMiniPlayer(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1B1B1B))
            .clickable { onClick() }
    ) {
        // Progress bar line at top
        val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(2.dp)
                .background(JetcasterPrimary)
                .align(Alignment.TopStart)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
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
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.DarkGray)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// Helpers
private fun startPlayback(context: Context, tracks: List<Track>, startIndex: Int) {
    val intent = Intent(context, AudioPlayerService::class.java)
    context.startForegroundService(intent)
    PlayerConnection.connect(context)
    PlayerConnection.playTracks(tracks, startIndex)
}

private fun borderStroke(width: androidx.compose.ui.unit.Dp, color: Color) =
    androidx.compose.foundation.BorderStroke(width, color)

@Composable
fun MobileAlbumDetailScreen(
    album: Album,
    repository: MusicRepository,
    applyStatusBarPadding: Boolean = true,
    onBack: () -> Unit,
    onTrackClick: (List<Track>, Int) -> Unit
) {
    val context = LocalContext.current
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(album.id) {
        isLoading = true
        tracks = repository.getTracksForAlbum(album.id)
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
    ) {
        // Blurred background art
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(album.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(30.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.2f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 2.dp,
                        bottom = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Album",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = JetcasterPrimary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    // Album header info
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(album.imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = album.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = album.name,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = album.artist,
                                color = JetcasterPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (album.year != null) {
                                Text(
                                    text = album.year.toString(),
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Play / Shuffle Buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { if (tracks.isNotEmpty()) onTrackClick(tracks, 0) },
                                    colors = ButtonDefaults.buttonColors(containerColor = JetcasterPrimary),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFF313002)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Play", fontWeight = FontWeight.Bold, color = Color(0xFF313002))
                                }

                                OutlinedButton(
                                    onClick = { if (tracks.isNotEmpty()) onTrackClick(tracks.shuffled(), 0) },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Shuffle", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Track items
                    itemsIndexed(tracks) { index, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTrackClick(tracks, index) }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (index + 1).toString(),
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 14.sp,
                                modifier = Modifier.width(32.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.name,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val badge = if (track.codec?.uppercase() == "FLAC" || (track.bitrate ?: 0) > 400000) "FLAC" else "HD"
                                    Box(
                                        modifier = Modifier
                                            .border(0.5.dp, JetcasterPrimary.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 3.dp, vertical = 0.5.dp)
                                    ) {
                                        Text(
                                            text = badge,
                                            color = JetcasterPrimary,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = track.artist,
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            val minutes = (track.durationMs / 1000) / 60
                            val seconds = (track.durationMs / 1000) % 60
                            Text(
                                text = String.format("%d:%02d", minutes, seconds),
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
