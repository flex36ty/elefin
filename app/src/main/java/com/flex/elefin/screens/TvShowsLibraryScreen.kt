package com.flex.elefin.screens

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.JellyfinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * TV Shows Library Screen - A dedicated screen for the TV Shows library
 * that is a 1:1 copy of the home screen layout but focused only on TV show content.
 */
@Composable
fun TvShowsLibraryScreen(
    libraryId: String,
    libraryName: String,
    onItemClick: (JellyfinItem, Long) -> Unit = { _, _ -> },
    onBackPressed: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    showDebugOutlines: Boolean = false
) {
    val context = LocalContext.current
    val config = remember { JellyfinConfig(context) }
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()
    
    // API Service
    val apiService = remember(config.serverUrl, config.accessToken, config.userId) {
        if (config.serverUrl.isNotBlank() && config.accessToken.isNotBlank() && config.userId.isNotBlank()) {
            JellyfinApiService(config.serverUrl, config.accessToken, config.userId, config)
        } else {
            null
        }
    }
    
    // Repository for refresh functionality
    val repository = remember(apiService) {
        apiService?.let { JellyfinRepository(it) }
    }
    
    // Settings states (same as home screen)
    var darkModeEnabled by remember { mutableStateOf(settings.darkModeEnabled) }
    var debugOutlinesEnabled by remember { mutableStateOf(settings.showDebugOutlines || showDebugOutlines) }
    val disableUIAnimations = remember { mutableStateOf(settings.disableUIAnimations) }
    val useSimpleCards = remember { mutableStateOf(settings.useSimpleCards) }
    val useGoogleTvCards = remember { mutableStateOf(settings.useGoogleTvCards) }
    val lowPowerMode = remember { mutableStateOf(settings.lowPowerMode) }
    val hideShowsWithZeroEpisodes by remember { mutableStateOf(settings.hideShowsWithZeroEpisodes) }
    
    // Dialog states
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var darkModeWhenSettingsOpened by remember { mutableStateOf(false) }
    var debugOutlinesWhenSettingsOpened by remember { mutableStateOf(false) }
    var disableUIAnimationsWhenSettingsOpened by remember { mutableStateOf(false) }
    var lowPowerModeWhenSettingsOpened by remember { mutableStateOf(false) }
    var useSimpleCardsWhenSettingsOpened by remember { mutableStateOf(false) }
    var useGoogleTvCardsWhenSettingsOpened by remember { mutableStateOf(false) }
    
    // Tab state: "recommendations" or "library"
    var selectedTab by remember { mutableStateOf("recommendations") }
    
    // Refresh state
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Sort state for library grid
    var sortType by remember { mutableStateOf(SortType.Alphabetically) }
    var showSortDialog by remember { mutableStateOf(false) }
    
    // Data states for recommendations
    var continueWatchingEpisodes by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var recentlyReleasedEpisodes by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var recentlyAddedShows by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var startWatchingShows by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var topRatedShows by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var genreShows1 by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var genreShows2 by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var genreShows3 by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var genreShows4 by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var selectedGenre1 by remember { mutableStateOf("") }
    var selectedGenre2 by remember { mutableStateOf("") }
    var selectedGenre3 by remember { mutableStateOf("") }
    var selectedGenre4 by remember { mutableStateOf("") }
    var availableGenres by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Data states for library grid
    var libraryItems by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    
    var isLoading by remember { mutableStateOf(true) }
    
    // Highlighted item for background (same as home screen)
    var highlightedItem by remember { mutableStateOf<JellyfinItem?>(null) }
    var instantHighlightedItem by remember { mutableStateOf<JellyfinItem?>(null) }
    var instantHighlightedItemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var backgroundChangeJob by remember { mutableStateOf<Job?>(null) }
    
    // Focus requester for initial focus
    val focusRequester = remember { FocusRequester() }
    
    // No-fling behavior for low power mode (same as home screen)
    val noFlingBehavior = remember {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                return 0f
            }
        }
    }
    
    // TV show genres to randomly select from
    val tvGenres = listOf(
        "Drama", "Comedy", "Action", "Adventure", "Sci-Fi", "Fantasy",
        "Horror", "Thriller", "Mystery", "Crime", "Romance", "Animation",
        "Documentary", "Reality", "Family", "Kids"
    )
    
    // Fetch recommendations data - using library-specific API methods
    LaunchedEffect(apiService, libraryId) {
        if (apiService != null && libraryId.isNotEmpty()) {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    Log.d("TvShowsLibraryScreen", "Fetching TV shows for library: $libraryName (ID: $libraryId)")
                    
                    // First fetch available genres from this library
                    val genres = apiService.getGenresFromLibrary(libraryId)
                    availableGenres = genres
                    
                    // Pick 4 random unique genres from available genres
                    val shuffledGenres = if (genres.isNotEmpty()) {
                        genres.filter { it in tvGenres }.shuffled().take(4).ifEmpty { 
                            genres.shuffled().take(4)
                        }
                    } else {
                        tvGenres.shuffled().take(4)
                    }
                    
                    val genre1 = shuffledGenres.getOrNull(0) ?: tvGenres.random()
                    val genre2 = shuffledGenres.getOrNull(1) ?: tvGenres.random()
                    val genre3 = shuffledGenres.getOrNull(2) ?: tvGenres.random()
                    val genre4 = shuffledGenres.getOrNull(3) ?: tvGenres.random()
                    
                    selectedGenre1 = genre1
                    selectedGenre2 = genre2
                    selectedGenre3 = genre3
                    selectedGenre4 = genre4
                    
                    // Fetch all TV show data in parallel using coroutineScope
                    coroutineScope {
                        val continueWatchingDeferred = async { apiService.getContinueWatchingEpisodesFromLibrary(libraryId, 20) }
                        val recentlyReleasedDeferred = async { apiService.getRecentlyReleasedEpisodesFromLibrary(libraryId, 20) }
                        val recentlyAddedDeferred = async { apiService.getRecentlyAddedShowsFromLibrary(libraryId, 20) }
                        val startWatchingDeferred = async { apiService.getRandomUnwatchedShowsFromLibrary(libraryId, 20) }
                        val topRatedDeferred = async { apiService.getTopRatedShowsFromLibrary(libraryId, 20) }
                        val genre1Deferred = async { apiService.getShowsByGenreFromLibrary(libraryId, genre1, 20) }
                        val genre2Deferred = async { apiService.getShowsByGenreFromLibrary(libraryId, genre2, 20) }
                        val genre3Deferred = async { apiService.getShowsByGenreFromLibrary(libraryId, genre3, 20) }
                        val genre4Deferred = async { apiService.getShowsByGenreFromLibrary(libraryId, genre4, 20) }
                        val libraryDeferred = async { apiService.getAllLibraryItems(libraryId) }
                        
                        continueWatchingEpisodes = continueWatchingDeferred.await()
                        recentlyReleasedEpisodes = recentlyReleasedDeferred.await()
                        recentlyAddedShows = recentlyAddedDeferred.await()
                        startWatchingShows = startWatchingDeferred.await()
                        topRatedShows = topRatedDeferred.await()
                        genreShows1 = genre1Deferred.await()
                        genreShows2 = genre2Deferred.await()
                        genreShows3 = genre3Deferred.await()
                        genreShows4 = genre4Deferred.await()
                        libraryItems = libraryDeferred.await().filter { it.Type == "Series" }
                    }
                    
                    Log.d("TvShowsLibraryScreen", "Loaded TV shows for '$libraryName': " +
                        "continueWatching=${continueWatchingEpisodes.size}, " +
                        "recentlyReleased=${recentlyReleasedEpisodes.size}, " +
                        "recentlyAdded=${recentlyAddedShows.size}, " +
                        "startWatching=${startWatchingShows.size}, " +
                        "topRated=${topRatedShows.size}, " +
                        "genre1($selectedGenre1)=${genreShows1.size}, " +
                        "genre2($selectedGenre2)=${genreShows2.size}, " +
                        "genre3($selectedGenre3)=${genreShows3.size}, " +
                        "genre4($selectedGenre4)=${genreShows4.size}, " +
                        "library=${libraryItems.size}")
                    
                    // Set initial highlighted item
                    val firstItem = continueWatchingEpisodes.firstOrNull() 
                        ?: recentlyReleasedEpisodes.firstOrNull()
                        ?: recentlyAddedShows.firstOrNull()
                        ?: startWatchingShows.firstOrNull()
                    if (firstItem != null) {
                        highlightedItem = firstItem
                        instantHighlightedItem = firstItem
                    }
                } catch (e: Exception) {
                    Log.e("TvShowsLibraryScreen", "Error loading TV shows for library $libraryId", e)
                }
            }
            isLoading = false
        }
    }
    
    // Fetch item details when highlighted item changes (same as home screen)
    LaunchedEffect(instantHighlightedItem?.Id) {
        instantHighlightedItem?.let { item ->
            withContext(Dispatchers.IO) {
                try {
                    val details = apiService?.getItemDetails(item.Id)
                    if (details != null) {
                        instantHighlightedItemDetails = details
                    }
                } catch (e: Exception) {
                    Log.e("TvShowsLibraryScreen", "Error fetching item details", e)
                }
            }
        }
    }
    
    // Request initial focus
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            delay(100)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus errors
            }
        }
    }
    
    // Sort library items
    val sortedLibraryItems = remember(libraryItems, sortType) {
        when (sortType) {
            SortType.Alphabetically -> libraryItems.sortedBy { it.Name?.lowercase() }
            SortType.DateAdded -> {
                libraryItems.sortedByDescending { 
                    it.DateCreated?.let { dateStr ->
                        try {
                            val formats = listOf(
                                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                                SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            )
                            formats.firstNotNullOfOrNull { format ->
                                try { format.parse(dateStr)?.time } catch (e: Exception) { null }
                            } ?: Long.MIN_VALUE
                        } catch (e: Exception) { Long.MIN_VALUE }
                    } ?: Long.MIN_VALUE
                }
            }
            SortType.DateReleased -> {
                libraryItems.sortedByDescending { 
                    it.PremiereDate?.let { dateStr ->
                        try {
                            val formats = listOf(
                                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                                SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            )
                            formats.firstNotNullOfOrNull { format ->
                                try { format.parse(dateStr)?.time } catch (e: Exception) { null }
                            } ?: Long.MIN_VALUE
                        } catch (e: Exception) { Long.MIN_VALUE }
                    } ?: Long.MIN_VALUE
                }
            }
        }
    }
    
    // Main content (same structure as home screen)
    Box(Modifier.fillMaxSize()) {
        // Featured carousel with backdrop - extends behind bottom container
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Get image URL for current highlighted item - use backdrop photo
            val imageUrl = highlightedItem?.let { item ->
                // Low power mode uses 720p, normal mode uses 1080p
                val bgMaxWidth = if (lowPowerMode.value) 1280 else 1920
                val bgMaxHeight = if (lowPowerMode.value) 720 else 1080
                val bgQuality = if (lowPowerMode.value) 75 else 90
                
                // For episodes, try to get the series backdrop
                val itemIdForBackdrop = if (item.Type == "Episode" && item.SeriesId != null) {
                    item.SeriesId
                } else {
                    item.Id
                }
                
                val backdropUrl = apiService?.getImageUrl(itemIdForBackdrop, "Backdrop", null, maxWidth = bgMaxWidth, maxHeight = bgMaxHeight, quality = bgQuality) ?: ""
                if (backdropUrl.isNotEmpty()) {
                    backdropUrl
                } else {
                    // Fall back to primary image if no backdrop
                    apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = bgMaxWidth, maxHeight = bgMaxHeight, quality = bgQuality) ?: ""
                }
            } ?: ""
            
            // Use Crossfade for smooth fade in/out animation (same as home screen)
            if (!darkModeEnabled && selectedTab == "recommendations") {
                Crossfade(
                    targetState = imageUrl,
                    animationSpec = tween(durationMillis = 500),
                    label = "background_fade"
                ) { currentUrl ->
                    if (currentUrl.isNotEmpty() && apiService != null) {
                        val headerMap = apiService.getImageRequestHeaders()
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(currentUrl)
                                .headers(headerMap)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(300)
                                .allowHardware(true)
                                .build(),
                            contentDescription = highlightedItem?.Name ?: "",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds,
                            alignment = Alignment.Center
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
            } else {
                // Dark mode or Library view: use Material dark background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
            
            // Dark overlay and scrim (same as home screen)
            if (!darkModeEnabled && selectedTab == "recommendations") {
                // Default view: 20% darkness + gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                )
                
                // Scrim gradient overlay (same as home screen)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .carouselGradient()
                )
            } else {
                // Library view: 50% darkness (no gradient scrim)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            }
        }
        
        // Top row with buttons and tabs (same as home screen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 22.4.dp)
                .then(
                    if (debugOutlinesEnabled) {
                        Modifier.border(4.dp, Color.Red)
                    } else {
                        Modifier
                    }
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Settings button
                    IconButton(
                        onClick = {
                            darkModeWhenSettingsOpened = settings.darkModeEnabled
                            debugOutlinesWhenSettingsOpened = settings.showDebugOutlines
                            disableUIAnimationsWhenSettingsOpened = settings.disableUIAnimations
                            lowPowerModeWhenSettingsOpened = settings.lowPowerMode
                            useSimpleCardsWhenSettingsOpened = settings.useSimpleCards
                            useGoogleTvCardsWhenSettingsOpened = settings.useGoogleTvCards
                            showSettings = true
                        },
                        colors = IconButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .padding(start = 54.dp, end = 20.dp)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Search button
                    IconButton(
                        onClick = { showSearch = true },
                        colors = IconButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .padding(end = 20.dp)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Refresh/Sort button
                    val infiniteTransition = rememberInfiniteTransition(label = "refresh_rotation")
                    val rotationAngle by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, delayMillis = 0),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                        ),
                        label = "refresh_rotation_angle"
                    )
                    
                    val isLibraryTab = selectedTab == "library"
                    
                    IconButton(
                        onClick = {
                            if (isLibraryTab) {
                                // Show sort dialog when library tab is selected
                                showSortDialog = true
                            } else {
                                // Refresh when on recommendations tab
                                if (!isRefreshing && apiService != null) {
                                    isRefreshing = true
                                    scope.launch {
                                        try {
                                            // Clear image cache
                                            withContext(Dispatchers.IO) {
                                                val imageLoader = context.imageLoader
                                                imageLoader.diskCache?.clear()
                                                imageLoader.memoryCache?.clear()
                                            }
                                            
                                            // Pick 4 new random unique genres
                                            val shuffledGenres = if (availableGenres.isNotEmpty()) {
                                                availableGenres.filter { it in tvGenres }.shuffled().take(4).ifEmpty { 
                                                    availableGenres.shuffled().take(4)
                                                }
                                            } else {
                                                tvGenres.shuffled().take(4)
                                            }
                                            
                                            val genre1 = shuffledGenres.getOrNull(0) ?: tvGenres.random()
                                            val genre2 = shuffledGenres.getOrNull(1) ?: tvGenres.random()
                                            val genre3 = shuffledGenres.getOrNull(2) ?: tvGenres.random()
                                            val genre4 = shuffledGenres.getOrNull(3) ?: tvGenres.random()
                                            
                                            selectedGenre1 = genre1
                                            selectedGenre2 = genre2
                                            selectedGenre3 = genre3
                                            selectedGenre4 = genre4
                                            
                                            // Refresh data - using library-specific methods
                                            withContext(Dispatchers.IO) {
                                                coroutineScope {
                                                    val continueWatchingDeferred = async { apiService.getContinueWatchingEpisodesFromLibrary(libraryId, 20) }
                                                    val recentlyReleasedDeferred = async { apiService.getRecentlyReleasedEpisodesFromLibrary(libraryId, 20) }
                                                    val recentlyAddedDeferred = async { apiService.getRecentlyAddedShowsFromLibrary(libraryId, 20) }
                                                    val startWatchingDeferred = async { apiService.getRandomUnwatchedShowsFromLibrary(libraryId, 20) }
                                                    val topRatedDeferred = async { apiService.getTopRatedShowsFromLibrary(libraryId, 20) }
                                                    val genre1Deferred = async { apiService.getShowsByGenreFromLibrary(libraryId, genre1, 20) }
                                                    val genre2Deferred = async { apiService.getShowsByGenreFromLibrary(libraryId, genre2, 20) }
                                                    val genre3Deferred = async { apiService.getShowsByGenreFromLibrary(libraryId, genre3, 20) }
                                                    val genre4Deferred = async { apiService.getShowsByGenreFromLibrary(libraryId, genre4, 20) }
                                                    val libraryDeferred = async { apiService.getAllLibraryItems(libraryId) }
                                                    
                                                    continueWatchingEpisodes = continueWatchingDeferred.await()
                                                    recentlyReleasedEpisodes = recentlyReleasedDeferred.await()
                                                    recentlyAddedShows = recentlyAddedDeferred.await()
                                                    startWatchingShows = startWatchingDeferred.await()
                                                    topRatedShows = topRatedDeferred.await()
                                                    genreShows1 = genre1Deferred.await()
                                                    genreShows2 = genre2Deferred.await()
                                                    genreShows3 = genre3Deferred.await()
                                                    genreShows4 = genre4Deferred.await()
                                                    libraryItems = libraryDeferred.await().filter { it.Type == "Series" }
                                                }
                                            }
                                            
                                            Log.d("TvShowsLibraryScreen", "Manual refresh completed")
                                        } catch (e: Exception) {
                                            Log.e("TvShowsLibraryScreen", "Manual refresh error", e)
                                        } finally {
                                            isRefreshing = false
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isRefreshing || isLibraryTab,
                        colors = IconButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .padding(end = 20.dp)
                            .size(48.dp)
                    ) {
                        if (isLibraryTab) {
                            // Show sort icon when library tab is selected
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Sort",
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            // Show refresh icon on recommendations tab
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = if (isRefreshing) "Refreshing..." else "Refresh",
                                modifier = Modifier
                                    .size(20.dp)
                                    .then(
                                        if (isRefreshing) {
                                            Modifier.rotate(rotationAngle)
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        }
                    }
                    
                    // Home button - navigates back to home screen
                    var homeFocused by remember { mutableStateOf(false) }
                    
                    TabRow(
                        modifier = Modifier.padding(end = 20.dp),
                        selectedTabIndex = -1, // Never selected since we're not on home
                        indicator = { _, _ -> } // No indicator
                    ) {
                        Tab(
                            selected = false,
                            onFocus = { },
                            onClick = { onBackPressed() },
                            colors = TabDefaults.underlinedIndicatorTabColors(),
                            modifier = Modifier
                                .onFocusChanged { focusState ->
                                    homeFocused = focusState.isFocused || focusState.hasFocus
                                }
                                .then(
                                    if (homeFocused) {
                                        Modifier.background(Color.White, RoundedCornerShape(4.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = if (homeFocused) Color.Black else Color.White,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .size(28.dp)
                            )
                        }
                    }
                    
                    // Recommendations and Library tabs
                    val tabs = listOf("Recommendations" to "recommendations", "$libraryName Library" to "library")
                    val selectedTabIndex = tabs.indexOfFirst { it.second == selectedTab }.takeIf { it >= 0 } ?: 0
                    
                    TabRow(
                        modifier = Modifier.fillMaxWidth(),
                        selectedTabIndex = selectedTabIndex,
                        separator = { Spacer(modifier = Modifier.width(16.dp)) },
                        indicator = { tabPositions, doesTabRowHaveFocus ->
                            if (selectedTabIndex >= 0 && selectedTabIndex < tabPositions.size) {
                                TabRowDefaults.UnderlinedIndicator(
                                    currentTabPosition = tabPositions[selectedTabIndex],
                                    doesTabRowHaveFocus = doesTabRowHaveFocus
                                )
                            }
                        }
                    ) {
                        tabs.forEachIndexed { index, (tabName, tabId) ->
                            var isFocused by remember { mutableStateOf(false) }
                            val isSelected = selectedTab == tabId
                            
                            Tab(
                                selected = isSelected,
                                onFocus = { },
                                onClick = { selectedTab = tabId },
                                colors = TabDefaults.underlinedIndicatorTabColors(),
                                modifier = Modifier
                                    .onFocusChanged { focusState ->
                                        isFocused = focusState.isFocused || focusState.hasFocus
                                    }
                                    .then(
                                        if (isFocused) {
                                            Modifier.background(Color.White, RoundedCornerShape(4.dp))
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                val scaledFontSize = MaterialTheme.typography.labelLarge.fontSize * 1.17f
                                val horizontalPadding = 16.dp * 1.2f
                                Text(
                                    text = tabName,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = scaledFontSize
                                    ),
                                    color = if (isFocused) Color.Black else Color.White,
                                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Content based on selected tab
        if (selectedTab == "recommendations") {
            // Item details section (same as home screen)
            val metadataKey = instantHighlightedItem?.Id ?: ""
            
            Crossfade(
                targetState = metadataKey,
                animationSpec = tween(durationMillis = 200),
                label = "metadata_fade"
            ) { currentKey ->
                val item = instantHighlightedItem
                if (item != null && currentKey == item.Id) {
                    val details = instantHighlightedItemDetails ?: item
                    val runtimeText = formatRuntime(details.RunTimeTicks)
                    val yearText = details.ProductionYear?.toString() ?: ""
                    val genreText = details.Genres?.take(3)?.joinToString(", ") ?: ""
                    
                    // For episodes, show series name and episode info
                    val displayTitle = if (item.Type == "Episode") {
                        item.SeriesName ?: item.Name ?: ""
                    } else {
                        item.Name ?: ""
                    }
                    
                    val episodeInfo = if (item.Type == "Episode") {
                        val season = item.ParentIndexNumber ?: 0
                        val episode = item.IndexNumber ?: 0
                        if (season > 0 && episode > 0) "S${season}E${episode}" else ""
                    } else ""
                    
                    Column(
                        modifier = Modifier
                            .padding(start = 54.dp, top = 77.dp, end = 38.dp)
                            .fillMaxWidth(0.75f)
                    ) {
                        // Title (same styling as home screen)
                        TitleOrLogo(
                            item = if (item.Type == "Episode" && item.SeriesId != null) {
                                // For episodes, try to get series details for logo
                                details.copy(Name = displayTitle)
                            } else {
                                details
                            },
                            apiService = apiService,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Episode info if applicable
                        if (episodeInfo.isNotEmpty()) {
                            Text(
                                text = "$episodeInfo: ${item.Name ?: ""}",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.9f
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        // Metadata row (same as home screen)
                        Row(
                            modifier = Modifier.padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Text-based metadata
                            if (yearText.isNotEmpty() || runtimeText.isNotEmpty() || genreText.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (yearText.isNotEmpty()) {
                                        Text(
                                            text = yearText,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                                            ),
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                    if (runtimeText.isNotEmpty()) {
                                        Text(
                                            text = runtimeText,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                                            ),
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                    if (genreText.isNotEmpty()) {
                                        Text(
                                            text = genreText,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                                            ),
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                            
                            // MetadataBox components (same as home screen)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val audioStream = details.MediaSources?.firstOrNull()?.MediaStreams?.firstOrNull { it.Type == "Audio" }
                                
                                // Maturity Rating
                                details.OfficialRating?.let { rating ->
                                    TvShowMetadataBox(text = rating)
                                }
                                
                                // Community Rating
                                details.CommunityRating?.let { rating ->
                                    TvShowMetadataBox(text = "★ ${String.format("%.1f", rating)}")
                                }
                                
                                // Language
                                audioStream?.Language?.let { lang ->
                                    TvShowMetadataBox(text = lang.uppercase())
                                }
                            }
                        }
                        
                        // Synopsis (same as home screen)
                        details.Overview?.let { synopsis ->
                            if (synopsis.isNotEmpty()) {
                                Text(
                                    text = synopsis,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f,
                                        lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 0.8f * 1.1f
                                    ),
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // Bottom container with rows (same structure as home screen)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (debugOutlinesEnabled) {
                            Modifier.border(4.dp, Color.Blue)
                        } else {
                            Modifier
                        }
                    )
            ) {
                // Spacer to push content down (same as home screen - 40% for details)
                Spacer(modifier = Modifier.weight(0.4f))
                
                // Content rows (same as home screen - 60% for rows)
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 20.dp * 1.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                        .padding(start = 54.dp, top = 0.dp, end = 38.dp, bottom = 0.dp)
                        .then(
                            if (debugOutlinesEnabled) {
                                Modifier.border(3.dp, Color.Yellow)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .focusRequester(focusRequester)
                        ) {
                            // Continue Watching row
                            if (continueWatchingEpisodes.isNotEmpty()) {
                                Text(
                                    text = "Continue Watching",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp, top = 12.dp)
                                )
                                
                                LazyRow(
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f)),
                                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                                    flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
                                    modifier = if (debugOutlinesEnabled) {
                                        Modifier.border(2.dp, Color.Magenta)
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    items(
                                        items = continueWatchingEpisodes,
                                        key = { it.Id },
                                        contentType = { "horizontal_card_progress" }
                                    ) { item ->
                                        JellyfinHorizontalCardWithProgress(
                                            item = item,
                                            apiService = apiService,
                                            onClick = {
                                                val resumePositionMs = item.UserData?.PositionTicks?.let { it / 10_000 } ?: 0L
                                                onItemClick(item, resumePositionMs)
                                            },
                                            onFocusChanged = { isFocused ->
                                                if (isFocused) {
                                                    instantHighlightedItem = item
                                                    backgroundChangeJob?.cancel()
                                                    backgroundChangeJob = scope.launch {
                                                        delay(1000)
                                                        highlightedItem = item
                                                    }
                                                }
                                            },
                                            useSimpleCards = useSimpleCards.value,
                                            useGoogleTvCards = useGoogleTvCards.value,
                                            lowPowerMode = lowPowerMode.value
                                        )
                                    }
                                }
                            }
                            
                            // Recently Released Episodes row - using poster cards (vertical)
                            if (recentlyReleasedEpisodes.isNotEmpty()) {
                                Text(
                                    text = "Recently Released Episodes",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp)
                                )
                                
                                LazyRow(
                                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f)),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
                                    modifier = if (debugOutlinesEnabled) {
                                        Modifier.border(2.dp, Color.Magenta)
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    items(recentlyReleasedEpisodes) { item ->
                                        // Use poster card for episodes - shows series poster
                                        JellyfinHorizontalCard(
                                            item = item,
                                            apiService = apiService,
                                            onClick = { onItemClick(item, 0L) },
                                            onFocusChanged = { isFocused ->
                                                if (isFocused) {
                                                    instantHighlightedItem = item
                                                    backgroundChangeJob?.cancel()
                                                    backgroundChangeJob = scope.launch {
                                                        delay(1000)
                                                        highlightedItem = item
                                                    }
                                                }
                                            },
                                            useSeriesPosterForEpisodes = true, // Show series poster for episodes
                                            useSimpleCards = useSimpleCards.value,
                                            useGoogleTvCards = useGoogleTvCards.value,
                                            lowPowerMode = lowPowerMode.value
                                        )
                                    }
                                }
                            }
                            
                            // Recently Added in TV Shows row
                            if (recentlyAddedShows.isNotEmpty()) {
                                Text(
                                    text = "Recently Added in $libraryName",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp)
                                )
                                
                                LazyRow(
                                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f)),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
                                    modifier = if (debugOutlinesEnabled) {
                                        Modifier.border(2.dp, Color.Magenta)
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    items(recentlyAddedShows) { item ->
                                        JellyfinHorizontalCard(
                                            item = item,
                                            apiService = apiService,
                                            onClick = { onItemClick(item, 0L) },
                                            onFocusChanged = { isFocused ->
                                                if (isFocused) {
                                                    instantHighlightedItem = item
                                                    backgroundChangeJob?.cancel()
                                                    backgroundChangeJob = scope.launch {
                                                        delay(1000)
                                                        highlightedItem = item
                                                    }
                                                }
                                            },
                                            useSimpleCards = useSimpleCards.value,
                                            useGoogleTvCards = useGoogleTvCards.value,
                                            lowPowerMode = lowPowerMode.value
                                        )
                                    }
                                }
                            }
                            
                            // Start Watching row (random unwatched suggestions)
                            if (startWatchingShows.isNotEmpty()) {
                                Text(
                                    text = "Start Watching",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp)
                                )
                                
                                LazyRow(
                                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f)),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
                                    modifier = if (debugOutlinesEnabled) {
                                        Modifier.border(2.dp, Color.Magenta)
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    items(startWatchingShows) { item ->
                                        JellyfinHorizontalCard(
                                            item = item,
                                            apiService = apiService,
                                            onClick = { onItemClick(item, 0L) },
                                            onFocusChanged = { isFocused ->
                                                if (isFocused) {
                                                    instantHighlightedItem = item
                                                    backgroundChangeJob?.cancel()
                                                    backgroundChangeJob = scope.launch {
                                                        delay(1000)
                                                        highlightedItem = item
                                                    }
                                                }
                                            },
                                            useSimpleCards = useSimpleCards.value,
                                            useGoogleTvCards = useGoogleTvCards.value,
                                            lowPowerMode = lowPowerMode.value
                                        )
                                    }
                                }
                            }
                            
                            // Top Rated TV Shows row
                            if (topRatedShows.isNotEmpty()) {
                                Text(
                                    text = "Top Rated TV Shows",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp)
                                )
                                
                                LazyRow(
                                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f)),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
                                    modifier = if (debugOutlinesEnabled) {
                                        Modifier.border(2.dp, Color.Magenta)
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    items(topRatedShows) { item ->
                                        JellyfinHorizontalCard(
                                            item = item,
                                            apiService = apiService,
                                            onClick = { onItemClick(item, 0L) },
                                            onFocusChanged = { isFocused ->
                                                if (isFocused) {
                                                    instantHighlightedItem = item
                                                    backgroundChangeJob?.cancel()
                                                    backgroundChangeJob = scope.launch {
                                                        delay(1000)
                                                        highlightedItem = item
                                                    }
                                                }
                                            },
                                            useSimpleCards = useSimpleCards.value,
                                            useGoogleTvCards = useGoogleTvCards.value,
                                            lowPowerMode = lowPowerMode.value
                                        )
                                    }
                                }
                            }
                            
                            // More in <Genre> row 1 (randomly selected genre)
                            if (genreShows1.isNotEmpty() && selectedGenre1.isNotEmpty()) {
                                Text(
                                    text = "More in $selectedGenre1",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp)
                                )
                                
                                LazyRow(
                                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f)),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
                                    modifier = if (debugOutlinesEnabled) {
                                        Modifier.border(2.dp, Color.Magenta)
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    items(genreShows1) { item ->
                                        JellyfinHorizontalCard(
                                            item = item,
                                            apiService = apiService,
                                            onClick = { onItemClick(item, 0L) },
                                            onFocusChanged = { isFocused ->
                                                if (isFocused) {
                                                    instantHighlightedItem = item
                                                    backgroundChangeJob?.cancel()
                                                    backgroundChangeJob = scope.launch {
                                                        delay(1000)
                                                        highlightedItem = item
                                                    }
                                                }
                                            },
                                            useSimpleCards = useSimpleCards.value,
                                            useGoogleTvCards = useGoogleTvCards.value,
                                            lowPowerMode = lowPowerMode.value
                                        )
                                    }
                                }
                            }
                            
                            // More in <Genre> row 2 (randomly selected genre)
                            if (genreShows2.isNotEmpty() && selectedGenre2.isNotEmpty()) {
                                Text(
                                    text = "More in $selectedGenre2",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp)
                                )
                                
                                LazyRow(
                                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f)),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
                                    modifier = if (debugOutlinesEnabled) {
                                        Modifier.border(2.dp, Color.Magenta)
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    items(genreShows2) { item ->
                                        JellyfinHorizontalCard(
                                            item = item,
                                            apiService = apiService,
                                            onClick = { onItemClick(item, 0L) },
                                            onFocusChanged = { isFocused ->
                                                if (isFocused) {
                                                    instantHighlightedItem = item
                                                    backgroundChangeJob?.cancel()
                                                    backgroundChangeJob = scope.launch {
                                                        delay(1000)
                                                        highlightedItem = item
                                                    }
                                                }
                                            },
                                            useSimpleCards = useSimpleCards.value,
                                            useGoogleTvCards = useGoogleTvCards.value,
                                            lowPowerMode = lowPowerMode.value
                                        )
                                    }
                                }
                            }
                            
                            // More in <Genre> row 3 (randomly selected genre)
                            if (genreShows3.isNotEmpty() && selectedGenre3.isNotEmpty()) {
                                Text(
                                    text = "More in $selectedGenre3",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp)
                                )
                                
                                LazyRow(
                                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f)),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
                                    modifier = if (debugOutlinesEnabled) {
                                        Modifier.border(2.dp, Color.Magenta)
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    items(genreShows3) { item ->
                                        JellyfinHorizontalCard(
                                            item = item,
                                            apiService = apiService,
                                            onClick = { onItemClick(item, 0L) },
                                            onFocusChanged = { isFocused ->
                                                if (isFocused) {
                                                    instantHighlightedItem = item
                                                    backgroundChangeJob?.cancel()
                                                    backgroundChangeJob = scope.launch {
                                                        delay(1000)
                                                        highlightedItem = item
                                                    }
                                                }
                                            },
                                            useSimpleCards = useSimpleCards.value,
                                            useGoogleTvCards = useGoogleTvCards.value,
                                            lowPowerMode = lowPowerMode.value
                                        )
                                    }
                                }
                            }
                            
                            // More in <Genre> row 4 (randomly selected genre)
                            if (genreShows4.isNotEmpty() && selectedGenre4.isNotEmpty()) {
                                Text(
                                    text = "More in $selectedGenre4",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp)
                                )
                                
                                LazyRow(
                                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f)),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
                                    modifier = if (debugOutlinesEnabled) {
                                        Modifier.border(2.dp, Color.Magenta)
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    items(genreShows4) { item ->
                                        JellyfinHorizontalCard(
                                            item = item,
                                            apiService = apiService,
                                            onClick = { onItemClick(item, 0L) },
                                            onFocusChanged = { isFocused ->
                                                if (isFocused) {
                                                    instantHighlightedItem = item
                                                    backgroundChangeJob?.cancel()
                                                    backgroundChangeJob = scope.launch {
                                                        delay(1000)
                                                        highlightedItem = item
                                                    }
                                                }
                                            },
                                            useSimpleCards = useSimpleCards.value,
                                            useGoogleTvCards = useGoogleTvCards.value,
                                            lowPowerMode = lowPowerMode.value
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Library grid view (same as home screen library view)
            val columns = 6
            val lazyListState = rememberLazyListState()
            
            // A-Z Index state - only show when sorted alphabetically AND not in low power mode
            val showAlphabetIndex = sortType == SortType.Alphabetically && !lowPowerMode.value
            val letterIndexMap = remember(sortedLibraryItems, columns) {
                if (showAlphabetIndex) buildTvShowLetterIndexMap(sortedLibraryItems, columns) else emptyMap()
            }
            val availableLetters = remember(letterIndexMap) { letterIndexMap.keys }
            var selectedLetter by remember { mutableStateOf<Char?>(null) }
            var showLetterOverlay by remember { mutableStateOf(false) }
            
            // Auto-hide letter overlay after delay
            LaunchedEffect(selectedLetter) {
                if (selectedLetter != null) {
                    showLetterOverlay = true
                    delay(800)
                    showLetterOverlay = false
                }
            }
            
            // Scroll to letter when selected
            LaunchedEffect(selectedLetter, letterIndexMap) {
                if (selectedLetter != null && letterIndexMap.containsKey(selectedLetter)) {
                    val rowIndex = letterIndexMap[selectedLetter] ?: return@LaunchedEffect
                    lazyListState.animateScrollToItem(rowIndex)
                }
            }
            
            // Container for library grid - positioned below tab row
            Spacer(modifier = Modifier.height(86.dp))
            
            if (sortedLibraryItems.isNotEmpty()) {
                androidx.tv.material3.Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 86.dp)
                        .then(
                            if (debugOutlinesEnabled) {
                                Modifier.border(3.dp, Color.Green)
                            } else {
                                Modifier
                            }
                        ),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    colors = androidx.tv.material3.SurfaceDefaults.colors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // A-Z Index Bar on the left (only when sorted alphabetically)
                        if (showAlphabetIndex) {
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .fillMaxHeight()
                                    .padding(start = 8.dp, top = 24.dp, bottom = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TvShowAlphabetIndexBar(
                                    availableLetters = availableLetters,
                                    selectedLetter = selectedLetter,
                                    onLetterFocused = { letter ->
                                        selectedLetter = letter
                                    },
                                    onLetterSelected = { letter ->
                                        selectedLetter = letter
                                    }
                                )
                            }
                        }
                        
                        // Library grid
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = PaddingValues(bottom = 20.dp * 1.15f, top = 24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = if (showAlphabetIndex) 8.dp else 54.dp, end = 38.dp)
                                .focusRequester(focusRequester)
                        ) {
                        items(
                            items = sortedLibraryItems.chunked(columns),
                            key = { rowItems -> rowItems.firstOrNull()?.Id ?: "" },
                            contentType = { "library_row" }
                        ) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                
                                rowItems.forEachIndexed { index, item ->
                                    if (index > 0) {
                                        Spacer(modifier = Modifier.width(20.dp))
                                    }
                                    Column(
                                        modifier = Modifier.width(105.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        JellyfinHorizontalCard(
                                            item = item,
                                            apiService = apiService,
                                            onClick = { onItemClick(item, 0L) },
                                            onFocusChanged = { isFocused ->
                                                if (isFocused) {
                                                    instantHighlightedItem = item
                                                    backgroundChangeJob?.cancel()
                                                    backgroundChangeJob = scope.launch {
                                                        delay(1000)
                                                        highlightedItem = item
                                                    }
                                                }
                                            },
                                            useSimpleCards = useSimpleCards.value,
                                            useGoogleTvCards = useGoogleTvCards.value,
                                            lowPowerMode = lowPowerMode.value
                                        )
                                        // Item name below the card
                                        if (!lowPowerMode.value) {
                                            Text(
                                                text = item.Name ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.9f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier
                                                    .padding(top = 6.dp)
                                                    .fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                
                                // Fill remaining space if row has fewer than columns items
                                if (rowItems.size < columns) {
                                    repeat(columns - rowItems.size) {
                                        Spacer(modifier = Modifier.width(105.dp + 20.dp))
                                    }
                                }
                                
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    }
                    
                    // Letter overlay (shown briefly when navigating A-Z)
                    if (showAlphabetIndex) {
                        TvShowLetterOverlay(
                            letter = selectedLetter,
                            visible = showLetterOverlay
                        )
                    }
                }
            }
        }
        
        // Loading indicator removed - content loads progressively without blocking UI
        
        // Settings dialog
        if (showSettings) {
            Dialog(
                onDismissRequest = { 
                    // Check if dark mode changed and refresh UI if needed
                    val darkModeChanged = settings.darkModeEnabled != darkModeWhenSettingsOpened
                    if (darkModeChanged) {
                        darkModeEnabled = settings.darkModeEnabled
                    }
                    // Check if debug outlines changed and refresh UI if needed
                    val debugOutlinesChanged = settings.showDebugOutlines != debugOutlinesWhenSettingsOpened
                    if (debugOutlinesChanged) {
                        debugOutlinesEnabled = settings.showDebugOutlines
                    }
                    // Check if UI animations setting changed and refresh UI if needed
                    val animationsChanged = settings.disableUIAnimations != disableUIAnimationsWhenSettingsOpened
                    if (animationsChanged) {
                        disableUIAnimations.value = settings.disableUIAnimations
                    }
                    // Check if low power mode changed
                    val lowPowerModeChanged = settings.lowPowerMode != lowPowerModeWhenSettingsOpened
                    if (lowPowerModeChanged) {
                        lowPowerMode.value = settings.lowPowerMode
                    }
                    // Check if simple cards or Google TV cards settings changed and refresh UI if needed
                    val simpleCardsChanged = settings.useSimpleCards != useSimpleCardsWhenSettingsOpened
                    val googleTvCardsChanged = settings.useGoogleTvCards != useGoogleTvCardsWhenSettingsOpened
                    if (simpleCardsChanged || googleTvCardsChanged || lowPowerModeChanged) {
                        useSimpleCards.value = settings.useSimpleCards
                        useGoogleTvCards.value = settings.useGoogleTvCards
                    }
                    showSettings = false 
                },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.tv.material3.Surface(
                        modifier = Modifier
                            .width((context.resources.displayMetrics.widthPixels * 0.8f).dp)
                            .height((context.resources.displayMetrics.heightPixels * 0.8f).dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.tv.material3.SurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        SettingsScreen(
                            onBack = { 
                                // Check if dark mode changed and refresh UI if needed
                                val darkModeChanged = settings.darkModeEnabled != darkModeWhenSettingsOpened
                                if (darkModeChanged) {
                                    darkModeEnabled = settings.darkModeEnabled
                                }
                                // Check if debug outlines changed and refresh UI if needed
                                val debugOutlinesChanged = settings.showDebugOutlines != debugOutlinesWhenSettingsOpened
                                if (debugOutlinesChanged) {
                                    debugOutlinesEnabled = settings.showDebugOutlines
                                }
                                // Check if UI animations setting changed and refresh UI if needed
                                val animationsChanged = settings.disableUIAnimations != disableUIAnimationsWhenSettingsOpened
                                if (animationsChanged) {
                                    disableUIAnimations.value = settings.disableUIAnimations
                                }
                                // Check if low power mode changed
                                val lowPowerModeChanged = settings.lowPowerMode != lowPowerModeWhenSettingsOpened
                                if (lowPowerModeChanged) {
                                    lowPowerMode.value = settings.lowPowerMode
                                }
                                // Check if simple cards or Google TV cards settings changed and refresh UI if needed
                                val simpleCardsChanged = settings.useSimpleCards != useSimpleCardsWhenSettingsOpened
                                val googleTvCardsChanged = settings.useGoogleTvCards != useGoogleTvCardsWhenSettingsOpened
                                if (simpleCardsChanged || googleTvCardsChanged || lowPowerModeChanged) {
                                    useSimpleCards.value = settings.useSimpleCards
                                    useGoogleTvCards.value = settings.useGoogleTvCards
                                }
                                showSettings = false 
                            }
                        )
                    }
                }
            }
        }
        
        // Search dialog
        if (showSearch) {
            Dialog(
                onDismissRequest = { showSearch = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.tv.material3.Surface(
                        modifier = Modifier
                            .width((context.resources.displayMetrics.widthPixels * 0.9f).dp)
                            .height((context.resources.displayMetrics.heightPixels * 0.9f).dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.tv.material3.SurfaceDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        SearchScreen(
                            apiService = apiService,
                            onItemClick = { item ->
                                showSearch = false
                                onItemClick(item, 0L)
                            },
                            onBack = { showSearch = false }
                        )
                    }
                }
            }
        }
        
        // Sort dialog
        if (showSortDialog) {
            SortDialog(
                currentSortType = sortType,
                onSortSelected = { newSortType ->
                    sortType = newSortType
                    showSortDialog = false
                },
                onDismiss = { showSortDialog = false }
            )
        }
    }
}

/**
 * Simple metadata box for displaying TV show metadata (same as home screen MetadataBox)
 */
@Composable
private fun TvShowMetadataBox(text: String) {
    Box(
        modifier = Modifier
            .background(Color.Black, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.8f
            ),
            color = Color.White
        )
    }
}

// =============================================================================
// A-Z ALPHABET INDEX BAR (Plex-style jump navigation)
// =============================================================================

/**
 * Builds a map of first letter -> row index for alphabetically sorted items.
 */
private fun buildTvShowLetterIndexMap(items: List<JellyfinItem>, columns: Int = 6): Map<Char, Int> {
    val map = mutableMapOf<Char, Int>()
    
    items.forEachIndexed { index, item ->
        val name = item.Name ?: return@forEachIndexed
        val first = name.firstOrNull()?.uppercaseChar() ?: return@forEachIndexed
        
        // Only map A-Z letters, and only the first occurrence
        if (first in 'A'..'Z' && first !in map) {
            val rowIndex = index / columns
            map[first] = rowIndex
        }
    }
    
    return map
}

/**
 * A-Z Alphabet Index Bar for TV navigation.
 */
@Composable
private fun TvShowAlphabetIndexBar(
    modifier: Modifier = Modifier,
    letters: List<Char> = ('A'..'Z').toList(),
    availableLetters: Set<Char> = emptySet(),
    selectedLetter: Char?,
    onLetterFocused: (Char) -> Unit,
    onLetterSelected: (Char) -> Unit
) {
    val lazyListState = rememberLazyListState()
    
    // Scroll to selected letter when it changes
    LaunchedEffect(selectedLetter) {
        if (selectedLetter != null) {
            val index = letters.indexOf(selectedLetter)
            if (index >= 0) {
                lazyListState.animateScrollToItem(
                    index = maxOf(0, index - 3),
                    scrollOffset = 0
                )
            }
        }
    }
    
    androidx.compose.foundation.lazy.LazyColumn(
        state = lazyListState,
        modifier = modifier
            .width(36.dp)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(letters) { letter ->
            val isSelected = letter == selectedLetter
            val isAvailable = letter in availableLetters
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            
            // Animate size change
            val scale by animateFloatAsState(
                targetValue = when {
                    isFocused -> 1.4f
                    isSelected -> 1.2f
                    else -> 1f
                },
                animationSpec = tween(durationMillis = 150),
                label = "letterScale"
            )
            
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(
                        when {
                            isFocused -> MaterialTheme.colorScheme.primary
                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> Color.Transparent
                        }
                    )
                    .focusable(interactionSource = interactionSource)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && isAvailable) {
                            onLetterFocused(letter)
                        }
                    }
                    .clickable(enabled = isAvailable) {
                        onLetterSelected(letter)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = when {
                        isFocused -> MaterialTheme.colorScheme.onPrimary
                        isAvailable -> Color.White
                        else -> Color.White.copy(alpha = 0.3f)
                    }
                )
            }
        }
    }
}

/**
 * Large letter overlay shown when navigating the A-Z index.
 */
@Composable
private fun TvShowLetterOverlay(
    letter: Char?,
    visible: Boolean
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible && letter != null,
        enter = androidx.compose.animation.fadeIn(animationSpec = tween(150)),
        exit = androidx.compose.animation.fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter?.toString() ?: "",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 180.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

