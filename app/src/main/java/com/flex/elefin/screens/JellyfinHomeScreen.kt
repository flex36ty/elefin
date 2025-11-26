package com.flex.elefin.screens

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Surface
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import coil.ImageLoader
import coil.imageLoader
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.jellyfin.AppSettings
import kotlinx.coroutines.delay
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.JellyfinLibrary
import com.flex.elefin.jellyfin.JellyfinRepository
import java.text.SimpleDateFormat
import java.util.Locale

enum class SortType {
    Alphabetically,
    DateAdded,
    DateReleased
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun JellyfinHomeScreen(
    onItemClick: (JellyfinItem, Long) -> Unit = { _, _ -> },
    showDebugOutlines: Boolean = false,
    preloadLibraryImages: Boolean = false,
    cacheLibraryImages: Boolean = true,
    reducePosterResolution: Boolean = false
) {
    val context = LocalContext.current
    val config = remember { JellyfinConfig(context) }
    val settings = remember { AppSettings(context) }
    var showServerEntry by remember { mutableStateOf(config.serverUrl.isBlank()) }
    var showLoginScreen by remember { mutableStateOf(!config.isConfigured() && !config.serverUrl.isBlank()) }
    val scope = rememberCoroutineScope()
    
    // Dark mode setting - read from settings and update when screen resumes
    var darkModeEnabled by remember { mutableStateOf(settings.darkModeEnabled) }
    
    // Debug outlines setting - read from settings and update when settings dialog closes
    var debugOutlinesEnabled by remember { mutableStateOf(settings.showDebugOutlines) }
    
    // Hide shows with zero episodes setting - read from settings
    var hideShowsWithZeroEpisodes by remember { mutableStateOf(settings.hideShowsWithZeroEpisodes) }
    
    val apiService = remember(config.isConfigured(), config.serverUrl) {
        val serverUrl = config.serverUrl
        // Only create API service if server URL is valid
        if (config.isConfigured() && serverUrl.isNotEmpty() && 
            (serverUrl.startsWith("http://") || serverUrl.startsWith("https://"))) {
            try {
                JellyfinApiService(
                    baseUrl = serverUrl,
                    accessToken = config.accessToken,
                    userId = config.userId,
                    config = config
                )
            } catch (e: Exception) {
                android.util.Log.e("JellyfinHomeScreen", "Error creating API service: ${e.message}", e)
                null
            }
        } else {
            null
        }
    }
    
    val repository = remember(apiService) {
        apiService?.let { JellyfinRepository(it) }
    }
    
    // Show server entry screen if server URL is not configured
    if (showServerEntry) {
        ServerEntryScreen(
            onServerConnected = { serverUrl ->
                config.serverUrl = serverUrl
                showServerEntry = false
                showLoginScreen = true
            }
        )
        return
    }
    
    // Show login screen if not authenticated but server is configured
    if (showLoginScreen) {
        JellyfinLoginScreen(
            serverUrl = config.serverUrl,
            onLoginSuccess = {
                showLoginScreen = false
            },
            onCancel = {
                // Clear server URL and go back to server entry screen
                config.serverUrl = ""
                showLoginScreen = false
                showServerEntry = true
            }
        )
        return
    }
    
    val continueWatchingItemsState = repository?.continueWatchingItems?.collectAsState(initial = emptyList())
    val continueWatchingItems = continueWatchingItemsState?.value ?: emptyList()
    
    val nextUpItemsState = repository?.nextUpItems?.collectAsState(initial = emptyList())
    val nextUpItems = nextUpItemsState?.value ?: emptyList()
    
    val recentlyAddedMoviesByLibraryState = repository?.recentlyAddedMoviesByLibrary?.collectAsState(initial = emptyMap())
    val recentlyAddedMoviesByLibrary = recentlyAddedMoviesByLibraryState?.value ?: emptyMap()
    
    // Get movie libraries from the existing libraries state (defined later in the file)
    // We'll use the libraries state that's already defined, but filter for movie libraries
    val movieLibrariesState = repository?.libraries?.collectAsState(initial = emptyList())
    val allMovieLibraries = movieLibrariesState?.value ?: emptyList()
    
    // Get movie libraries (libraries that have movies)
    val movieLibraries = allMovieLibraries.filter { library ->
        recentlyAddedMoviesByLibrary.containsKey(library.Id)
    }.sortedBy { it.Name } // Sort by name for consistent ordering
    
    val recentlyReleasedMoviesState = repository?.recentlyReleasedMovies?.collectAsState(initial = emptyList())
    val recentlyReleasedMovies = recentlyReleasedMoviesState?.value ?: emptyList()
    
    val recentlyAddedShowsByLibraryState = repository?.recentlyAddedShowsByLibrary?.collectAsState(initial = emptyMap())
    val recentlyAddedShowsByLibrary = recentlyAddedShowsByLibraryState?.value ?: emptyMap()
    
    val recentlyAddedEpisodesByLibraryState = repository?.recentlyAddedEpisodesByLibrary?.collectAsState(initial = emptyMap())
    val recentlyAddedEpisodesByLibrary = recentlyAddedEpisodesByLibraryState?.value ?: emptyMap()
    
    // Get TV show libraries (libraries that have shows or episodes)
    val tvShowLibraries = (movieLibrariesState?.value ?: emptyList()).filter { library ->
        recentlyAddedShowsByLibrary.containsKey(library.Id) || recentlyAddedEpisodesByLibrary.containsKey(library.Id)
    }.sortedBy { it.Name } // Sort by name for consistent ordering
    
    val librariesState = repository?.libraries?.collectAsState(initial = emptyList())
    val libraries = librariesState?.value ?: emptyList()
    
    val collectionsState = repository?.collections?.collectAsState(initial = emptyList())
    val collections = collectionsState?.value ?: emptyList()
    
    val libraryItemsState = repository?.libraryItems?.collectAsState(initial = emptyMap())
    val libraryItems = libraryItemsState?.value ?: emptyMap()
    
    val collectionItemsState = repository?.collectionItems?.collectAsState(initial = emptyMap())
    val collectionItems = collectionItemsState?.value ?: emptyMap()
    
    // Track unwatched episode counts for TV shows (series ID -> count)
    var unwatchedEpisodeCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    
    var selectedLibraryId by remember { mutableStateOf<String?>(null) }
    var selectedCollectionId by remember { mutableStateOf<String?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var darkModeWhenSettingsOpened by remember { mutableStateOf(false) }
    var debugOutlinesWhenSettingsOpened by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var sortType by remember { mutableStateOf<SortType>(
        when (settings.getSortType()) {
            "DateAdded" -> SortType.DateAdded
            "DateReleased" -> SortType.DateReleased
            else -> SortType.Alphabetically
        }
    ) }
    
    
    // Handle back button press
    BackHandler(enabled = true) {
        if (selectedLibraryId != null) {
            // If library is selected, deselect it
            selectedLibraryId = null
        } else if (selectedCollectionId == "__COLLECTIONS__") {
            // If Collections tab is selected, deselect it
            selectedCollectionId = null
        } else {
            // If on home screen, show exit confirmation
            showExitConfirmation = true
        }
    }
    
    LaunchedEffect(repository, config.isConfigured()) {
        // Only fetch data if properly configured
        if (config.isConfigured() && repository != null) {
            repository.fetchContinueWatching()
            repository.fetchNextUp()
            repository.fetchRecentlyAddedMovies()
            repository.fetchRecentlyReleasedMovies()
            repository.fetchLibraries()
            repository.fetchCollections()
            repository.fetchRecentlyAddedShows()
            repository.fetchRecentlyAddedEpisodes()
            repository.fetchLibraries()
        }
    }
    
    // Fetch unwatched episode counts for recently added shows
    // Fetch unwatched episode counts for shows in all libraries
    LaunchedEffect(recentlyAddedShowsByLibrary, apiService) {
        if (apiService != null && recentlyAddedShowsByLibrary.isNotEmpty()) {
            val countsMap = unwatchedEpisodeCounts.toMutableMap()
            recentlyAddedShowsByLibrary.values.flatten().filter { it.Type == "Series" }.forEach { show ->
                try {
                    val count = apiService.getUnwatchedEpisodeCount(show.Id)
                    countsMap[show.Id] = count
                } catch (e: Exception) {
                    Log.e("JellyfinHomeScreen", "Error fetching unwatched count for ${show.Name}", e)
                }
            }
            unwatchedEpisodeCounts = countsMap
        }
    }
    
    // Fetch unwatched episode counts for library TV shows
    LaunchedEffect(libraryItems, selectedLibraryId, apiService) {
        if (apiService != null && selectedLibraryId != null) {
            val libraryItemsList = libraryItems[selectedLibraryId] ?: emptyList()
            val tvShows = libraryItemsList.filter { it.Type == "Series" }
            if (tvShows.isNotEmpty()) {
                val countsMap = unwatchedEpisodeCounts.toMutableMap()
                tvShows.forEach { show ->
                    // Only fetch if not already in the map to avoid unnecessary API calls
                    if (!countsMap.containsKey(show.Id)) {
                        try {
                            val count = apiService.getUnwatchedEpisodeCount(show.Id)
                            countsMap[show.Id] = count
                        } catch (e: Exception) {
                            Log.e("JellyfinHomeScreen", "Error fetching unwatched count for ${show.Name}", e)
                        }
                    }
                }
                unwatchedEpisodeCounts = countsMap
            }
        }
    }
    
    // Refresh Continue Watching and Next Up when the screen becomes visible again
    // This ensures items appear after watching/partially watching content
    // Also refresh settings when screen resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Refresh settings when screen resumes
                darkModeEnabled = settings.darkModeEnabled
                hideShowsWithZeroEpisodes = settings.hideShowsWithZeroEpisodes
                // Refresh Continue Watching and Next Up when returning to the screen
                scope.launch {
                    repository?.fetchContinueWatching()
                    repository?.fetchNextUp()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Auto-refresh: Periodically check for new media and refresh rows if new content is detected
    var autoRefreshEnabled by remember { mutableStateOf(settings.autoRefreshEnabled) }
    var autoRefreshIntervalMinutes by remember { mutableStateOf(settings.autoRefreshIntervalMinutes) }
    
    LaunchedEffect(autoRefreshEnabled, autoRefreshIntervalMinutes, repository) {
        if (autoRefreshEnabled && repository != null) {
            while (true) {
                // Wait for the specified interval (convert minutes to milliseconds)
                delay(autoRefreshIntervalMinutes * 60 * 1000L)
                
                // Check if auto-refresh is still enabled (user might have disabled it)
                autoRefreshEnabled = settings.autoRefreshEnabled
                autoRefreshIntervalMinutes = settings.autoRefreshIntervalMinutes
                
                if (!autoRefreshEnabled) {
                    break // Exit loop if disabled
                }
                
                // Check for new media and refresh if found (only checks for media already detected by Jellyfin backend)
                try {
                    val refreshed = repository.checkForNewMediaAndRefresh()
                    if (refreshed) {
                        android.util.Log.d("JellyfinHomeScreen", "Auto-refresh: New media detected, rows refreshed")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("JellyfinHomeScreen", "Auto-refresh: Error checking for new media", e)
                }
            }
        }
    }
    
    // Fetch library items when a library is selected (only on Enter/OK press via onClick, not on focus)
    LaunchedEffect(selectedLibraryId, repository) {
        selectedLibraryId?.let { libraryId ->
            repository?.fetchLibraryItems(libraryId)
        }
    }
    
    // Fetch collection items for all collections when Collections tab is selected
    LaunchedEffect(selectedCollectionId, collections, repository) {
        // When Collections tab is selected (selectedCollectionId == "__COLLECTIONS__"), fetch items for all collections
        if (selectedCollectionId == "__COLLECTIONS__" && collections.isNotEmpty()) {
            collections.forEach { collection ->
                repository?.fetchCollectionItems(collection.Id)
            }
        }
    }
    val focusRequester = remember { FocusRequester() }
    var highlightedItem by remember { mutableStateOf<JellyfinItem?>(null) }
    var highlightedItemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    // Track the original episode item when highlighting a series from an episode
    var originalEpisodeItem by remember { mutableStateOf<JellyfinItem?>(null) }
    
    // Set initial highlighted item to first continue watching item or first recently added movie
    LaunchedEffect(continueWatchingItems, recentlyAddedMoviesByLibrary) {
        if (highlightedItem == null) {
            // Get first movie from first library as fallback
            val firstMovie = movieLibraries.firstOrNull()?.let { library ->
                recentlyAddedMoviesByLibrary[library.Id]?.firstOrNull()
            }
            highlightedItem = continueWatchingItems.firstOrNull() ?: firstMovie
        }
    }
    
    // Fetch details for highlighted item
    LaunchedEffect(highlightedItem?.Id, apiService) {
        highlightedItemDetails = null
        highlightedItem?.Id?.let { itemId ->
            if (apiService != null) {
                try {
                    val details = apiService.getItemDetails(itemId)
                    highlightedItemDetails = details
                } catch (e: Exception) {
                    // Silently fail - use basic item info
                    highlightedItemDetails = highlightedItem
                }
            }
        }
    }
    
    Box(Modifier.fillMaxSize()) {
        // Featured carousel with backdrop - extends behind bottom container
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Get image URL for current highlighted item - use backdrop photo
            // For episodes, use the series backdrop; for other items, use their own backdrop
            val imageUrl = highlightedItem?.let { item ->
                // If this is an episode, get the series backdrop
                val itemId = if (item.Type == "Episode" && item.SeriesId != null) {
                    item.SeriesId
                } else {
                    item.Id
                }
                
                // Prioritize backdrop for home screen background
                val backdropUrl = apiService?.getImageUrl(itemId, "Backdrop", null, maxWidth = 3840, maxHeight = 2160, quality = 90) ?: ""
                if (backdropUrl.isNotEmpty()) {
                    backdropUrl
                } else {
                    // Fall back to primary image if no backdrop
                    apiService?.getImageUrl(itemId, "Primary", null, maxWidth = 3840, maxHeight = 2160, quality = 90) ?: ""
                }
            } ?: ""
            
            // Use Crossfade for smooth fade in/out animation
            // In dark mode, don't show background image - use Material dark background instead
            // Hide carousel when Collections tab is selected
            if (!darkModeEnabled && selectedCollectionId != "__COLLECTIONS__") {
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
                // Dark mode or Collections view: use Material dark background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
            
            // Dark overlay and scrim - different opacity based on view mode
            // Skip overlay in dark mode since we're using a dark background
            if (selectedLibraryId == null && selectedCollectionId != "__COLLECTIONS__" && !darkModeEnabled) {
                // Default view: 20% darkness + gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f))
                )
                
                // Scrim gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .carouselGradient()
                )
            } else {
                // Library or Collections view: 50% darkness (no gradient scrim)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                )
            }
        }
        
        // Top row with home button and library buttons - positioned absolutely on top of carousel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 22.4.dp) // Reduced by 30% (32 * 0.7 = 22.4)
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
                // Settings button - first on the left (same size as library buttons)
                IconButton(
                    onClick = {
                        darkModeWhenSettingsOpened = settings.darkModeEnabled
                        debugOutlinesWhenSettingsOpened = settings.showDebugOutlines
                        showSettings = true
                    },
                    colors = IconButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .padding(start = 54.dp, end = 20.dp)
                        .size(48.dp) // 30% smaller (from default ~68dp to ~48dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(20.dp) // Reduced icon size to decrease visual padding around it
                    )
                }
                
                // Search button - between settings and refresh buttons
                IconButton(
                    onClick = {
                        showSearch = true
                    },
                    colors = IconButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .padding(end = 20.dp)
                        .size(48.dp) // Same size as settings button
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Manual Refresh button - to the right of search button
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
                
                IconButton(
                    onClick = {
                        if (!isRefreshing && repository != null) {
                            isRefreshing = true
                            scope.launch {
                                try {
                                    // Trigger server-side library scan and refresh all media rows
                                    repository.triggerLibraryScanAndRefresh()
                                    
                                    // Also refresh libraries in case new ones were added
                                    repository.fetchLibraries()
                                    
                                    Log.d("JellyfinHomeScreen", "Manual refresh completed")
                                } catch (e: Exception) {
                                    Log.e("JellyfinHomeScreen", "Manual refresh error", e)
                                } finally {
                                    isRefreshing = false
                                }
                            }
                        }
                    },
                    enabled = !isRefreshing,
                    colors = IconButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .padding(end = 20.dp)
                        .size(48.dp) // Same size as settings button
                ) {
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
                
                // Home button - styled like tab row items with underline
                var homeFocused by remember { mutableStateOf(false) }
                val homeSelected = selectedLibraryId == null && selectedCollectionId == null
                
                // Create a mini TabRow for the home button to get the underline indicator
                TabRow(
                    modifier = Modifier.padding(end = 20.dp),
                    selectedTabIndex = if (homeSelected) 0 else -1,
                    indicator = { tabPositions, doesTabRowHaveFocus ->
                        if (homeSelected && tabPositions.isNotEmpty()) {
                            TabRowDefaults.UnderlinedIndicator(
                                currentTabPosition = tabPositions[0],
                                doesTabRowHaveFocus = doesTabRowHaveFocus
                            )
                        }
                    }
                ) {
                    Tab(
                        selected = homeSelected,
                        onFocus = {
                            // Do nothing on focus - only load on click
                        },
                        onClick = {
                            selectedLibraryId = null
                            selectedCollectionId = null
                        },
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
                        val scaledFontSize = MaterialTheme.typography.labelLarge.fontSize * 1.17f // 30% bigger, then 10% smaller = 1.3 * 0.9
                        // Add horizontal padding (20% increase from default 16dp = 19.2dp)
                        val horizontalPadding = 16.dp * 1.2f
                        Text(
                            text = "Home",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = scaledFontSize
                            ),
                            color = if (homeFocused) Color.Black else Color.White,
                            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 6.dp)
                        )
                    }
                }
                
                // Library buttons with underlined indicator using TV Material3 TabRow
                // Add a single "Collections" tab if collections are available
                val allTabs = remember(libraries, collections) {
                    buildList<Pair<String?, String>> {
                        // Add all libraries (exclude any library named "Collections" to avoid conflicts)
                        addAll(libraries.filter { !it.Name.equals("Collections", ignoreCase = true) }.map { null to it.Id })
                        // Add a single "Collections" tab if collections exist
                        // Use a unique identifier to avoid conflicts with library names
                        if (collections.isNotEmpty()) {
                            add("__COLLECTIONS__" to "__COLLECTIONS__")
                        }
                    }
                }
                
                val selectedTabIndex = remember(selectedLibraryId, selectedCollectionId, allTabs) {
                    val selectedId = selectedLibraryId ?: if (selectedCollectionId == "__COLLECTIONS__") "__COLLECTIONS__" else null
                    allTabs.indexOfFirst { it.second == selectedId }.takeIf { it >= 0 } ?: 0
                }
                
                var focusedTabIndex by remember { mutableStateOf<Int?>(null) }
                
                if (allTabs.isNotEmpty()) {
                    TabRow(
                        modifier = Modifier.fillMaxWidth(),
                        selectedTabIndex = if (selectedLibraryId != null || selectedCollectionId == "__COLLECTIONS__") selectedTabIndex else -1,
                        separator = { Spacer(modifier = Modifier.width(16.dp)) },
                        indicator = { tabPositions, doesTabRowHaveFocus ->
                            if ((selectedLibraryId != null || selectedCollectionId == "__COLLECTIONS__") && selectedTabIndex >= 0 && selectedTabIndex < tabPositions.size) {
                                TabRowDefaults.UnderlinedIndicator(
                                    currentTabPosition = tabPositions[selectedTabIndex],
                                    doesTabRowHaveFocus = doesTabRowHaveFocus
                                )
                            }
                        }
                    ) {
                        allTabs.forEachIndexed { index, (tabName, itemId) ->
                            var isFocused by remember { mutableStateOf(false) }
                            
                            val isCollectionsTab = tabName == "__COLLECTIONS__"
                            val isSelected = if (isCollectionsTab) {
                                selectedCollectionId == "__COLLECTIONS__"
                            } else {
                                selectedLibraryId == itemId
                            }
                            val itemName = if (isCollectionsTab) {
                                "Collections"
                            } else {
                                libraries.find { it.Id == itemId }?.Name ?: ""
                            }
                            
                            Tab(
                                selected = isSelected,
                                onFocus = {
                                    // Do nothing on focus - only load on click
                                },
                                onClick = {
                                    // Only load library/collections on Enter/OK press, not on focus
                                    if (isSelected) {
                                        // Deselect if already selected
                                        if (isCollectionsTab) {
                                            selectedCollectionId = null
                                        } else {
                                            selectedLibraryId = null
                                        }
                                    } else {
                                        // Select the new tab
                                        if (isCollectionsTab) {
                                            // When Collections tab is clicked, set the special Collections identifier
                                            selectedCollectionId = "__COLLECTIONS__"
                                            selectedLibraryId = null
                                        } else {
                                            selectedLibraryId = itemId
                                            selectedCollectionId = null
                                        }
                                    }
                                },
                                colors = TabDefaults.underlinedIndicatorTabColors(),
                                modifier = Modifier
                                    .onFocusChanged { focusState ->
                                        isFocused = focusState.isFocused || focusState.hasFocus
                                        if (focusState.isFocused || focusState.hasFocus) {
                                            focusedTabIndex = index
                                        } else {
                                            if (focusedTabIndex == index) {
                                                focusedTabIndex = null
                                            }
                                        }
                                    }
                                    .then(
                                        if (isFocused) {
                                            Modifier.background(Color.White, RoundedCornerShape(4.dp))
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                // Make 30% bigger, then 10% smaller (1.3 * 0.9 = 1.17x normal size)
                                val scaledFontSize = MaterialTheme.typography.labelLarge.fontSize * 1.17f
                                // Add horizontal padding (20% increase from default 16dp = 19.2dp)
                                val horizontalPadding = 16.dp * 1.2f
                                Text(
                                    text = itemName,
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
                
                // Sort button - on the far right, same vertical position as settings button
                // Only shown when a library is selected (not for collections view)
                if (selectedLibraryId != null) {
                    IconButton(
                        onClick = {
                            showSortDialog = true
                        },
                        colors = IconButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .padding(start = 20.dp, end = 54.dp)
                            .size(48.dp) // Same size as settings button
                    ) {
                        // Use SwapVert icon for sort (vertical arrows - common sort indicator)
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Sort",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        // Item details section - below settings button (only show when not viewing a library)
        // Don't show highlighted item panel when viewing collections
        if (selectedLibraryId == null && selectedCollectionId != "__COLLECTIONS__") {
            highlightedItem?.let { item ->
                val details = highlightedItemDetails ?: item
            val runtimeText = formatRuntime(details.RunTimeTicks)
            
            // For episodes from Continue Watching, Next Up, or Recently Added Episodes, show air date instead of ProductionYear
            val yearText = if (originalEpisodeItem != null && originalEpisodeItem?.Type == "Episode") {
                // Show episode air date formatted like on season info screen
                formatDate(originalEpisodeItem?.PremiereDate ?: originalEpisodeItem?.DateCreated)
            } else {
                // For movies and series, show ProductionYear
                details.ProductionYear?.toString() ?: ""
            }
            
            val genreText = details.Genres?.take(3)?.joinToString(", ") ?: ""
            // Only show episode info if this is an episode highlight (from Continue Watching, Next Up, or Recently Added Episodes row)
            // For Series items in Recently Added Shows row, never show episode info
            val isEpisodeHighlight = originalEpisodeItem != null && item.Type == "Series"
            val isSeriesItem = item.Type == "Series" && originalEpisodeItem == null
            
            // Get season and episode number for episodes (only for episode highlights, not for series)
            val seasonEpisodeText = if (isEpisodeHighlight && originalEpisodeItem != null && !isSeriesItem) {
                val episode = originalEpisodeItem!!
                val seasonNumber = episode.ParentIndexNumber
                val episodeNumber = episode.IndexNumber
                if (seasonNumber != null && episodeNumber != null) {
                    "S${seasonNumber} E${episodeNumber}"
                } else null
            } else null
            
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 54.dp, top = 77.dp, end = 38.dp) // Increased by 10% (70 * 1.1 = 77)
                    .fillMaxWidth(0.75f) // Increased by 50%: 0.5 * 1.5 = 0.75 (50% wider horizontally)
            ) {
                // Title (Series name for episodes, item name for others)
                Text(
                    text = details.Name,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f // Reduced by 20% (0.8 * 0.8 = 0.64)
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Episode name below title (for recently added episodes only, not for series)
                if (isEpisodeHighlight && originalEpisodeItem != null && !isSeriesItem) {
                    Text(
                        text = originalEpisodeItem!!.Name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f // Same size as synopsis
                        ),
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                // Metadata: Season/Episode, Year, Runtime, Genre (old text-based) + new MetadataBox items
                Row(
                    modifier = Modifier.padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Old text-based metadata (Season/Episode, Year, Runtime, Genre)
                    if (seasonEpisodeText != null || yearText.isNotEmpty() || runtimeText.isNotEmpty() || genreText.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (seasonEpisodeText != null) {
                                Text(
                                    text = seasonEpisodeText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                                    ),
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            if (yearText.isNotEmpty()) {
                                Text(
                                    text = yearText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                                    ),
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            // Don't show runtime for Series items (shows)
                            if (runtimeText.isNotEmpty() && !isSeriesItem) {
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
                    
                    // New MetadataBox components (to the right of old text-based metadata)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Get media information
                        val videoStream = details.MediaSources?.firstOrNull()?.MediaStreams?.firstOrNull { it.Type == "Video" }
                        val audioStream = details.MediaSources?.firstOrNull()?.MediaStreams?.firstOrNull { it.Type == "Audio" }
                        
                        // Maturity Rating
                        details.OfficialRating?.let { rating ->
                            MetadataBox(text = rating)
                        }
                        
                        // Review Rating with Rotten Tomatoes icons support
                        RatingDisplay(
                            item = details,
                            communityRating = details.CommunityRating,
                            criticRating = details.CriticRating
                        )
                        
                        // Language
                        audioStream?.Language?.let { lang ->
                            MetadataBox(text = lang.uppercase())
                        }
                    }
                }
                
                // Synopsis - use episode synopsis if available, otherwise use series/movie synopsis
                val synopsisText = if (isEpisodeHighlight && originalEpisodeItem != null && !isSeriesItem) {
                    originalEpisodeItem!!.Overview ?: details.Overview
                } else {
                    details.Overview
                }
                
                synopsisText?.let { synopsis ->
                    if (synopsis.isNotEmpty()) {
                        Text(
                            text = synopsis,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f,
                                lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 0.8f * 1.1f // Reduced line spacing (10% of font size)
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
            }
        }
        
        // Bottom container with rows - positioned on top of carousel
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
            // Spacer to push content down, allowing carousel to show behind top 10%
            // Only show spacer when not viewing a library or collections (on home screen)
            if (selectedLibraryId == null && selectedCollectionId != "__COLLECTIONS__") {
                Spacer(modifier = Modifier.weight(0.4f))
            }
            
            // Show library grid if a library is selected
            if (selectedLibraryId != null) {
                // No spacer needed - grid starts immediately after tab row
                val libraryId = selectedLibraryId!!
                val unsortedItems = libraryItems[libraryId] ?: emptyList()
                
                // Sort items based on selected sort type, then filter if needed
                val items = remember(unsortedItems, sortType, hideShowsWithZeroEpisodes) {
                    val sortedItems = when (sortType) {
                        SortType.Alphabetically -> unsortedItems.sortedBy { it.Name.lowercase() }
                        SortType.DateAdded -> {
                            // Sort by DateCreated (most recent first)
                            unsortedItems.sortedByDescending { 
                                it.DateCreated?.let { dateStr ->
                                    try {
                                        // Try ISO format first (e.g., "2024-01-15T12:00:00Z")
                                        val formats = listOf(
                                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                                            SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                        )
                                        formats.firstNotNullOfOrNull { format ->
                                            try {
                                                format.parse(dateStr)?.time
                                            } catch (e: Exception) {
                                                null
                                            }
                                        } ?: Long.MIN_VALUE
                                    } catch (e: Exception) {
                                        Long.MIN_VALUE
                                    }
                                } ?: Long.MIN_VALUE
                            }
                        }
                        SortType.DateReleased -> {
                            // Sort by PremiereDate (most recent first)
                            unsortedItems.sortedByDescending { 
                                it.PremiereDate?.let { dateStr ->
                                    try {
                                        // Try ISO format first (e.g., "2024-01-15T12:00:00Z")
                                        val formats = listOf(
                                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                                            SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                        )
                                        formats.firstNotNullOfOrNull { format ->
                                            try {
                                                format.parse(dateStr)?.time
                                            } catch (e: Exception) {
                                                null
                                            }
                                        } ?: Long.MIN_VALUE
                                    } catch (e: Exception) {
                                        Long.MIN_VALUE
                                    }
                                } ?: Long.MIN_VALUE
                            }
                        }
                    }
                    
                    // Filter shows with zero episodes if setting is enabled
                    if (hideShowsWithZeroEpisodes) {
                        sortedItems.filter { item ->
                            // Keep non-Series items, or Series items with episodes (ChildCount > 0)
                            item.Type != "Series" || (item.ChildCount != null && item.ChildCount!! > 0)
                        }
                    } else {
                        sortedItems
                    }
                }
                val context = LocalContext.current
                val imageLoader = context.imageLoader
                val lazyListState = rememberLazyListState()
                
                // Preload images for items that are about to come into view
                LaunchedEffect(items, apiService, selectedLibraryId, preloadLibraryImages, cacheLibraryImages, reducePosterResolution) {
                    if (preloadLibraryImages && apiService != null && items.isNotEmpty()) {
                        // Preload images for the first 6 rows (36 items) - more aggressive preloading
                        val preloadCount = minOf(36, items.size) // First 6 rows (6 columns * 6 rows)
                        
                        items.take(preloadCount).forEach { item ->
                            // Use reduced resolution (600x900) or 4K resolution (3840x5760) based on setting
                            val imageUrl = if (reducePosterResolution) {
                                apiService.getImageUrl(item.Id, "Primary", null, maxWidth = 600, maxHeight = 900, quality = 90)
                            } else {
                                apiService.getImageUrl(item.Id, "Primary", null, maxWidth = 3840, maxHeight = 5760, quality = 90)
                            }
                            if (imageUrl.isNotEmpty()) {
                                try {
                                    val request = ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .headers(apiService.getImageRequestHeaders())
                                        .size(300) // Hint to Coil about target size
                                        .memoryCachePolicy(if (cacheLibraryImages) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                        .diskCachePolicy(if (cacheLibraryImages) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                        .build()
                                    imageLoader.enqueue(request)
                                } catch (e: Exception) {
                                    // Silently fail preloading
                                }
                            }
                        }
                    }
                }
                
                // Preload images as user scrolls - more aggressive (5 rows ahead)
                LaunchedEffect(lazyListState.firstVisibleItemIndex, items, apiService, selectedLibraryId, preloadLibraryImages, cacheLibraryImages, reducePosterResolution) {
                    if (preloadLibraryImages && apiService != null && items.isNotEmpty()) {
                        val firstVisible = lazyListState.firstVisibleItemIndex
                        val columns = 6
                        val preloadStart = (firstVisible + 5) * columns // Start preloading 5 rows ahead
                        val preloadEnd = minOf(preloadStart + (5 * columns), items.size) // Preload 5 rows
                        
                        if (preloadStart < items.size && preloadEnd > preloadStart) {
                            items.subList(preloadStart, preloadEnd).forEach { item ->
                                // Use reduced resolution (600x900) or 4K resolution (3840x5760) based on setting
                                val imageUrl = if (reducePosterResolution) {
                                    apiService.getImageUrl(item.Id, "Primary", null, maxWidth = 600, maxHeight = 900, quality = 90)
                                } else {
                                    apiService.getImageUrl(item.Id, "Primary", null, maxWidth = 3840, maxHeight = 5760, quality = 90)
                                }
                                if (imageUrl.isNotEmpty()) {
                                    try {
                                        val request = ImageRequest.Builder(context)
                                            .data(imageUrl)
                                            .headers(apiService.getImageRequestHeaders())
                                            .size(300) // Hint to Coil about target size
                                            .memoryCachePolicy(if (cacheLibraryImages) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                            .diskCachePolicy(if (cacheLibraryImages) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                            .build()
                                        imageLoader.enqueue(request)
                                    } catch (e: Exception) {
                                        // Silently fail preloading
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Container for library grid - positioned below tab row
                Spacer(modifier = Modifier.height(86.dp)) // Add space below tab row (reduced by 40% from 144: 144 * 0.6 = 86)
                
                if (items.isNotEmpty()) {
                    androidx.tv.material3.Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
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
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = PaddingValues(bottom = 20.dp * 1.15f, top = 24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 54.dp, end = 38.dp)
                                .then(
                                    if (debugOutlinesEnabled) {
                                        Modifier.border(3.dp, Color.Blue)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                        // Grid layout with 6 columns - integrate directly into LazyColumn
                        val columns = 6
                        items(
                            items = items.chunked(columns),
                            key = { rowItems -> rowItems.firstOrNull()?.Id ?: "" },
                            contentType = { "library_row" }
                        ) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // Add spacer at the start for equal spacing
                                Spacer(modifier = Modifier.weight(1f))
                                
                                // Cards with spacing between them
                                rowItems.forEachIndexed { index, item ->
                                    if (index > 0) {
                                        Spacer(modifier = Modifier.width(20.dp))
                                    }
                                    JellyfinHorizontalCard(
                                        item = item,
                                        apiService = apiService,
                                        onClick = {
                                            // Library item click - pass fromLibrary flag
                                            val intent = when (item.Type) {
                                                "Series" -> {
                                                    com.flex.elefin.SeriesDetailsActivity.createIntent(
                                                        context = context,
                                                        item = item,
                                                        fromLibrary = true
                                                    )
                                                }
                                                else -> {
                                                    // Movies and other types
                                                    com.flex.elefin.MovieDetailsActivity.createIntent(
                                                        context = context,
                                                        item = item,
                                                        fromLibrary = true
                                                    )
                                                }
                                            }
                                            context.startActivity(intent)
                                        },
                                        onFocusChanged = { isFocused ->
                                            if (isFocused) {
                                                highlightedItem = item
                                            }
                                        },
                                        enableCaching = cacheLibraryImages,
                                        reducePosterResolution = reducePosterResolution,
                                        unwatchedEpisodeCount = if (item.Type == "Series") unwatchedEpisodeCounts[item.Id] else null
                                    )
                                }
                                
                                // Fill remaining space if row has fewer than columns items
                                if (rowItems.size < columns) {
                                    repeat(columns - rowItems.size) {
                                        Spacer(modifier = Modifier.width(105.dp + 20.dp)) // Width of card + spacing
                                    }
                                }
                                
                                // Add spacer at the end for equal spacing
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    }
                } else {
                    // Loading state for library items
                    Spacer(modifier = Modifier.height(86.dp)) // Add space below tab row (reduced by 40% from 144: 144 * 0.6 = 86)
                    androidx.tv.material3.Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        colors = androidx.tv.material3.SurfaceDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 54.dp, top = 24.dp, end = 38.dp)
                                .height(400.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading...",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
            
            // Show collections in grid (like libraries) when Collections tab is selected
            if (selectedCollectionId == "__COLLECTIONS__") {
                // Display all collections as individual grid items
                // Container for collections grid - positioned below tab row
                Spacer(modifier = Modifier.height(86.dp)) // Add space below tab row (reduced by 40% from 144: 144 * 0.6 = 86)
                
                val context = LocalContext.current
                val lazyListState = rememberLazyListState()
                
                // Get all collection items combined
                val allCollectionItems = remember(collections, collectionItems) {
                    collections.flatMap { collection ->
                        collectionItems[collection.Id] ?: emptyList()
                    }
                }
                
                // Sort items based on selected sort type, then filter if needed
                val items = remember(allCollectionItems, sortType, hideShowsWithZeroEpisodes) {
                    val sortedItems = when (sortType) {
                        SortType.Alphabetically -> allCollectionItems.sortedBy { it.Name.lowercase() }
                        SortType.DateAdded -> {
                            // Sort by DateCreated (most recent first)
                            allCollectionItems.sortedByDescending { 
                                it.DateCreated?.let { dateStr ->
                                    try {
                                        val formats = listOf(
                                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                                            SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                        )
                                        formats.firstNotNullOfOrNull { format ->
                                            try {
                                                format.parse(dateStr)?.time
                                            } catch (e: Exception) {
                                                null
                                            }
                                        } ?: Long.MIN_VALUE
                                    } catch (e: Exception) {
                                        Long.MIN_VALUE
                                    }
                                } ?: Long.MIN_VALUE
                            }
                        }
                        SortType.DateReleased -> {
                            // Sort by PremiereDate (most recent first)
                            allCollectionItems.sortedByDescending { 
                                it.PremiereDate?.let { dateStr ->
                                    try {
                                        val formats = listOf(
                                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
                                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                                            SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                        )
                                        formats.firstNotNullOfOrNull { format ->
                                            try {
                                                format.parse(dateStr)?.time
                                            } catch (e: Exception) {
                                                null
                                            }
                                        } ?: Long.MIN_VALUE
                                    } catch (e: Exception) {
                                        Long.MIN_VALUE
                                    }
                                } ?: Long.MIN_VALUE
                            }
                        }
                    }
                    
                    // Filter shows with zero episodes if setting is enabled
                    if (hideShowsWithZeroEpisodes) {
                        sortedItems.filter { item ->
                            // Keep non-Series items, or Series items with episodes (ChildCount > 0)
                            item.Type != "Series" || (item.ChildCount != null && item.ChildCount!! > 0)
                        }
                    } else {
                        sortedItems
                    }
                }
                
                val imageLoader = context.imageLoader
                
                // Preload images for items that are about to come into view
                LaunchedEffect(items, apiService, selectedCollectionId, preloadLibraryImages, cacheLibraryImages, reducePosterResolution) {
                    if (preloadLibraryImages && apiService != null && items.isNotEmpty()) {
                        // Preload images for the first 6 rows (36 items) - more aggressive preloading
                        val preloadCount = minOf(36, items.size) // First 6 rows (6 columns * 6 rows)
                        
                        items.take(preloadCount).forEach { item ->
                            // Use reduced resolution (600x900) or 4K resolution (3840x5760) based on setting
                            val imageUrl = if (reducePosterResolution) {
                                apiService.getImageUrl(item.Id, "Primary", null, maxWidth = 600, maxHeight = 900, quality = 90)
                            } else {
                                apiService.getImageUrl(item.Id, "Primary", null, maxWidth = 3840, maxHeight = 5760, quality = 90)
                            }
                            if (imageUrl.isNotEmpty()) {
                                try {
                                    val request = ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .headers(apiService.getImageRequestHeaders())
                                        .size(300) // Hint to Coil about target size
                                        .memoryCachePolicy(if (cacheLibraryImages) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                        .diskCachePolicy(if (cacheLibraryImages) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                        .build()
                                    imageLoader.enqueue(request)
                                } catch (e: Exception) {
                                    // Silently fail preloading
                                }
                            }
                        }
                    }
                }
                
                // Preload images as user scrolls - more aggressive (5 rows ahead)
                LaunchedEffect(lazyListState.firstVisibleItemIndex, items, apiService, selectedCollectionId, preloadLibraryImages, cacheLibraryImages, reducePosterResolution) {
                    if (preloadLibraryImages && apiService != null && items.isNotEmpty()) {
                        val firstVisible = lazyListState.firstVisibleItemIndex
                        val columns = 6
                        val preloadStart = (firstVisible + 5) * columns // Start preloading 5 rows ahead
                        val preloadEnd = minOf(preloadStart + (5 * columns), items.size) // Preload 5 rows
                        
                        if (preloadStart < items.size && preloadEnd > preloadStart) {
                            items.subList(preloadStart, preloadEnd).forEach { item ->
                                // Use reduced resolution (600x900) or 4K resolution (3840x5760) based on setting
                                val imageUrl = if (reducePosterResolution) {
                                    apiService.getImageUrl(item.Id, "Primary", null, maxWidth = 600, maxHeight = 900, quality = 90)
                                } else {
                                    apiService.getImageUrl(item.Id, "Primary", null, maxWidth = 3840, maxHeight = 5760, quality = 90)
                                }
                                if (imageUrl.isNotEmpty()) {
                                    try {
                                        val request = ImageRequest.Builder(context)
                                            .data(imageUrl)
                                            .headers(apiService.getImageRequestHeaders())
                                            .size(300) // Hint to Coil about target size
                                            .memoryCachePolicy(if (cacheLibraryImages) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                            .diskCachePolicy(if (cacheLibraryImages) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                            .build()
                                        imageLoader.enqueue(request)
                                    } catch (e: Exception) {
                                        // Silently fail preloading
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Show loading indicator while items are being fetched
                if (items.isEmpty() && collections.isNotEmpty() && collections.all { collectionItems[it.Id].isNullOrEmpty() }) {
                    androidx.tv.material3.Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        colors = androidx.tv.material3.SurfaceDefaults.colors(
                            containerColor = Color.Transparent
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 54.dp, top = 24.dp, end = 38.dp)
                                .height(400.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading...",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    androidx.tv.material3.Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
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
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = PaddingValues(bottom = 20.dp * 1.15f, top = 24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 54.dp, end = 38.dp)
                                .then(
                                    if (debugOutlinesEnabled) {
                                        Modifier.border(3.dp, Color.Blue)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            // Grid layout with 6 columns - integrate directly into LazyColumn
                            val columns = 6
                            items(
                                items = items.chunked(columns),
                                key = { rowItems -> rowItems.firstOrNull()?.Id ?: "" },
                                contentType = { "collection_row" }
                            ) { rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    // Add spacer at the start for equal spacing
                                    Spacer(modifier = Modifier.weight(1f))
                                    
                                    // Cards with spacing between them
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
                                                onClick = {
                                                    // Collection item click - pass fromLibrary flag
                                                    val intent = when (item.Type) {
                                                        "Series" -> {
                                                            com.flex.elefin.SeriesDetailsActivity.createIntent(
                                                                context = context,
                                                                item = item,
                                                                fromLibrary = true
                                                            )
                                                        }
                                                        else -> {
                                                            // Movies and other types
                                                            com.flex.elefin.MovieDetailsActivity.createIntent(
                                                                context = context,
                                                                item = item,
                                                                fromLibrary = true
                                                            )
                                                        }
                                                    }
                                                    context.startActivity(intent)
                                                },
                                                onFocusChanged = { },
                                                enableCaching = cacheLibraryImages,
                                                reducePosterResolution = reducePosterResolution,
                                                unwatchedEpisodeCount = if (item.Type == "Series") unwatchedEpisodeCounts[item.Id] else null
                                            )
                                            // Item name below the card
                                            Text(
                                                text = item.Name ?: "",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.85f
                                                ),
                                                color = Color.White,
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                modifier = Modifier
                                                    .padding(top = 8.dp)
                                                    .fillMaxWidth()
                                            )
                                        }
                                    }
                                    
                                    // Fill remaining space if row has fewer than columns items
                                    if (rowItems.size < columns) {
                                        repeat(columns - rowItems.size) {
                                            Spacer(modifier = Modifier.width(105.dp + 20.dp)) // Width of card + spacing
                                        }
                                    }
                                    
                                    // Add spacer at the end for equal spacing
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            
            if (selectedLibraryId == null && selectedCollectionId != "__COLLECTIONS__") {
                    // Default rows when no library or collection is selected
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 20.dp * 1.15f), // 15% increase in bottom padding
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
                            Text(
                                text = "Continue Watching",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                ),
                                modifier = Modifier.padding(bottom = 12.dp, top = 12.dp)
                            )
                            
                            LazyRow(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f)), // Bottom increased by another 20% (15.87 * 1.05 * 1.05 * 1.1 * 1.2 = 19.24 * 1.2 = 23.09)
                                horizontalArrangement = Arrangement.spacedBy(26.dp), // Increased by 30%: 20 * 1.3 = 26.dp
                                modifier = if (debugOutlinesEnabled) {
                                    Modifier.border(2.dp, Color.Magenta)
                                } else {
                                    Modifier
                                }
                            ) {
                                items(continueWatchingItems) { item ->
                                    // For episodes, use series info for highlighting; for movies, use item itself
                                    val itemForHighlight = if (item.Type == "Episode" && item.SeriesId != null) {
                                        // For episodes, we'll fetch series details when focused
                                        item
                                    } else {
                                        item
                                    }
                                    
                                    JellyfinHorizontalCardWithProgress(
                                        item = item,
                                        apiService = apiService,
                                        onClick = {
                                            // Convert PositionTicks to milliseconds (10,000,000 ticks = 1 second)
                                            val resumePositionMs = item.UserData?.PositionTicks?.let { 
                                                it / 10_000 
                                            } ?: 0L
                                            onItemClick(item, resumePositionMs)
                                        },
                                        onFocusChanged = { isFocused ->
                                            if (isFocused) {
                                                // For episodes, highlight the series instead of the episode
                                                if (item.Type == "Episode" && item.SeriesId != null) {
                                                    // Store the original episode item to show its name
                                                    originalEpisodeItem = item
                                                    // Fetch series details for highlighting
                                                    scope.launch {
                                                        val seriesDetails = apiService?.getItemDetails(item.SeriesId)
                                                        if (seriesDetails != null) {
                                                            highlightedItem = seriesDetails
                                                        } else {
                                                            highlightedItem = item
                                                            originalEpisodeItem = null
                                                        }
                                                    }
                                                } else {
                                                    highlightedItem = item
                                                    originalEpisodeItem = null
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            
                            // Next Up row (duplicate of Continue Watching)
                            Text(
                                text = "Next Up",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                ),
                                modifier = Modifier.padding(bottom = 12.dp, top = 30.3186.dp) // Increased by 15%: 26.364 * 1.15 = 30.3186.dp
                            )
                            
                            LazyRow(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f)),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = if (debugOutlinesEnabled) {
                                    Modifier.border(2.dp, Color.Magenta)
                                } else {
                                    Modifier
                                }
                            ) {
                                items(nextUpItems) { item ->
                                    // For episodes, use series info for highlighting; for movies, use item itself
                                    val itemForHighlight = if (item.Type == "Episode" && item.SeriesId != null) {
                                        // For episodes, we'll fetch series details when focused
                                        item
                                    } else {
                                        item
                                    }
                                    
                                    JellyfinHorizontalCardWithProgress(
                                        item = item,
                                        apiService = apiService,
                                        onClick = {
                                            // Next Up items are unplayed, so start from beginning (position 0)
                                            onItemClick(item, 0L)
                                        },
                                        onFocusChanged = { isFocused ->
                                            if (isFocused) {
                                                // For episodes, highlight the series instead of the episode
                                                if (item.Type == "Episode" && item.SeriesId != null) {
                                                    // Store the original episode item to show its name
                                                    originalEpisodeItem = item
                                                    // Fetch series details for highlighting
                                                    scope.launch {
                                                        val seriesDetails = apiService?.getItemDetails(item.SeriesId)
                                                        if (seriesDetails != null) {
                                                            highlightedItem = seriesDetails
                                                        } else {
                                                            highlightedItem = item
                                                            originalEpisodeItem = null
                                                        }
                                                    }
                                                } else {
                                                    highlightedItem = item
                                                    originalEpisodeItem = null
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            
                            // Recently Added Movies rows - one per movie library
                            movieLibraries.forEachIndexed { index, library ->
                                val libraryMovies = recentlyAddedMoviesByLibrary[library.Id] ?: emptyList()
                                if (libraryMovies.isNotEmpty()) {
                                    // Row title: "Recently Added Movies" for first library, "Recently Added <libraryname>" for others
                                    val rowTitle = if (index == 0) {
                                        "Recently Added Movies"
                                    } else {
                                        "Recently Added ${library.Name}"
                                    }
                                    
                                    Text(
                                        text = rowTitle,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                        ),
                                        modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp) // Increased by 15%: 26.4 * 1.15 = 30.36.dp
                                    )
                                    
                                    LazyRow(
                                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f)), // Bottom increased by another 20% (15.87 * 1.05 * 1.05 * 1.1 * 1.2 = 19.24 * 1.2 = 23.09)
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                        modifier = if (debugOutlinesEnabled) {
                                            Modifier.border(2.dp, Color.Magenta)
                                        } else {
                                            Modifier
                                        }
                                    ) {
                                        items(libraryMovies) { item ->
                                            JellyfinHorizontalCard(
                                                item = item,
                                                apiService = apiService,
                                                onClick = {
                                                    onItemClick(item, 0L)
                                                },
                                                onFocusChanged = { isFocused ->
                                                    if (isFocused) {
                                                        highlightedItem = item
                                                    }
                                                },
                                                enableCaching = cacheLibraryImages,
                                                reducePosterResolution = reducePosterResolution
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Recently Released Movies row
                            Text(
                                text = "Recently Released Movies",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                ),
                                modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp) // Increased by 15%: 26.4 * 1.15 = 30.36.dp
                            )
                            
                            LazyRow(
                                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f)), // Bottom increased by another 20% (15.87 * 1.05 * 1.05 * 1.1 * 1.2 = 19.24 * 1.2 = 23.09)
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = if (debugOutlinesEnabled) {
                                    Modifier.border(2.dp, Color.Magenta)
                                } else {
                                    Modifier
                                }
                            ) {
                                items(recentlyReleasedMovies) { item ->
                                    JellyfinHorizontalCard(
                                        item = item,
                                        apiService = apiService,
                                        onClick = {
                                            onItemClick(item, 0L)
                                        },
                                        onFocusChanged = { isFocused ->
                                            if (isFocused) {
                                                highlightedItem = item
                                            }
                                        },
                                        enableCaching = cacheLibraryImages,
                                        reducePosterResolution = reducePosterResolution
                                    )
                                }
                            }
                            
                            // Recently Added Shows rows - one per TV show library
                            tvShowLibraries.forEachIndexed { libraryIndex, library ->
                                val libraryShows = recentlyAddedShowsByLibrary[library.Id]?.let { shows ->
                                    // Filter shows with zero episodes if setting is enabled
                                    if (hideShowsWithZeroEpisodes) {
                                        shows.filter { item ->
                                            // Keep non-Series items, or Series items with episodes (ChildCount > 0)
                                            item.Type != "Series" || (item.ChildCount != null && item.ChildCount!! > 0)
                                        }
                                    } else {
                                        shows
                                    }
                                } ?: emptyList()
                                
                                if (libraryShows.isNotEmpty()) {
                                    // Row title: "Recently Added Shows" for first library, "Recently Added Shows in <libraryname>" for others
                                    val rowTitle = if (libraryIndex == 0) {
                                        "Recently Added Shows"
                                    } else {
                                        "Recently Added Shows in ${library.Name}"
                                    }
                                    
                                    Text(
                                        text = rowTitle,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                        ),
                                        modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp) // Increased by 15%: 26.4 * 1.15 = 30.36.dp
                                    )
                                    
                                    LazyRow(
                                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f)), // Bottom increased by another 20% (15.87 * 1.05 * 1.05 * 1.1 * 1.2 = 19.24 * 1.2 = 23.09)
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                        modifier = if (debugOutlinesEnabled) {
                                            Modifier.border(2.dp, Color.Magenta)
                                        } else {
                                            Modifier
                                        }
                                    ) {
                                        items(libraryShows) { item ->
                                            JellyfinHorizontalCard(
                                                item = item,
                                                apiService = apiService,
                                                onClick = {
                                                    onItemClick(item, 0L)
                                                },
                                                onFocusChanged = { isFocused ->
                                                    if (isFocused) {
                                                        highlightedItem = item
                                                    }
                                                },
                                                enableCaching = cacheLibraryImages,
                                                reducePosterResolution = reducePosterResolution,
                                                unwatchedEpisodeCount = if (item.Type == "Series") unwatchedEpisodeCounts[item.Id] else null
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Recently Added Episodes rows - one per TV show library
                            tvShowLibraries.forEachIndexed { libraryIndex, library ->
                                val libraryEpisodes = recentlyAddedEpisodesByLibrary[library.Id] ?: emptyList()
                                
                                if (libraryEpisodes.isNotEmpty()) {
                                    // Row title: "Recently Added Episodes" for first library, "Recently Added Episodes in <libraryname>" for others
                                    val rowTitle = if (libraryIndex == 0) {
                                        "Recently Added Episodes"
                                    } else {
                                        "Recently Added Episodes in ${library.Name}"
                                    }
                                    
                                    Text(
                                        text = rowTitle,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                        ),
                                        modifier = Modifier.padding(bottom = 12.dp, top = 36.96.dp) // Increased by 40%: 26.4 * 1.4 = 36.96.dp
                                    )
                                    
                                    LazyRow(
                                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f * 1.4f * 1.3f)), // Bottom increased by another 30%: (15.87 * 1.4553 * 1.4) * 1.3 = 42.024.dp
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                                        modifier = if (debugOutlinesEnabled) {
                                            Modifier.border(2.dp, Color.Magenta)
                                        } else {
                                            Modifier
                                        }
                                    ) {
                                        items(libraryEpisodes) { item ->
                                            // For episodes, use series info for highlighting; for movies, use item itself
                                            JellyfinHorizontalCard(
                                                item = item,
                                                apiService = apiService,
                                                onClick = {
                                                    // Convert PositionTicks to milliseconds (10,000,000 ticks = 1 second)
                                                    val resumePositionMs = item.UserData?.PositionTicks?.let { 
                                                        it / 10_000 
                                                    } ?: 0L
                                                    onItemClick(item, resumePositionMs)
                                                },
                                                onFocusChanged = { isFocused ->
                                                    if (isFocused) {
                                                        // For episodes, highlight the series instead of the episode
                                                        if (item.Type == "Episode" && item.SeriesId != null) {
                                                            // Store the original episode item to show its name
                                                            originalEpisodeItem = item
                                                            // Fetch series details for highlighting
                                                            scope.launch {
                                                                val seriesDetails = apiService?.getItemDetails(item.SeriesId)
                                                                if (seriesDetails != null) {
                                                                    highlightedItem = seriesDetails
                                                                } else {
                                                                    highlightedItem = item
                                                                    originalEpisodeItem = null
                                                                }
                                                            }
                                                        } else {
                                                            highlightedItem = item
                                                            originalEpisodeItem = null
                                                        }
                                                    }
                                                },
                                                enableCaching = cacheLibraryImages,
                                                reducePosterResolution = reducePosterResolution,
                                                // For episodes in Recently Added Episodes, use series poster
                                                useSeriesPosterForEpisodes = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Exit confirmation dialog
    if (showExitConfirmation) {
        ExitConfirmationDialog(
            onConfirm = {
                showExitConfirmation = false
                // Exit the app
                (context as? Activity)?.finish()
            },
            onDismiss = {
                showExitConfirmation = false
            }
        )
    }
    
    // Settings screen
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
                        .width((LocalContext.current.resources.displayMetrics.widthPixels * 0.8f).dp)
                        .height((LocalContext.current.resources.displayMetrics.heightPixels * 0.8f).dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
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
                        .width((LocalContext.current.resources.displayMetrics.widthPixels * 0.9f).dp)
                        .height((LocalContext.current.resources.displayMetrics.heightPixels * 0.9f).dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
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
            onDismiss = { showSortDialog = false },
            onSortSelected = { newSortType ->
                sortType = newSortType
                // Save sort preference
                settings.setSortType(
                    when (newSortType) {
                        SortType.DateAdded -> "DateAdded"
                        SortType.DateReleased -> "DateReleased"
                        else -> "Alphabetically"
                    }
                )
                showSortDialog = false
            }
        )
    }
}

@Composable
fun SortDialog(
    currentSortType: SortType,
    onDismiss: () -> Unit,
    onSortSelected: (SortType) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.TopEnd
        ) {
            androidx.tv.material3.Surface(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .heightIn(max = (LocalContext.current.resources.displayMetrics.heightPixels * 0.5f).dp)
                    .padding(top = 80.dp, end = 54.dp), // Align with sort button position
                shape = RoundedCornerShape(16.dp),
                colors = androidx.tv.material3.SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Dialog title
                    Text(
                        text = "Sort By",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.7f
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Sort options
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        item {
                            ListItem(
                                selected = currentSortType == SortType.Alphabetically,
                                onClick = {
                                    onSortSelected(SortType.Alphabetically)
                                },
                                headlineContent = {
                                    Text(
                                        text = "Alphabetically",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.7f
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        item {
                            ListItem(
                                selected = currentSortType == SortType.DateAdded,
                                onClick = {
                                    onSortSelected(SortType.DateAdded)
                                },
                                headlineContent = {
                                    Text(
                                        text = "Date Added",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.7f
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        item {
                            ListItem(
                                selected = currentSortType == SortType.DateReleased,
                                onClick = {
                                    onSortSelected(SortType.DateReleased)
                                },
                                headlineContent = {
                                    Text(
                                        text = "Date Released",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.7f
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExitConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .width(600.dp)
                .padding(32.dp),
            tonalElevation = 8.dp,
            colors = androidx.tv.material3.SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Exit App?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "Are you sure you want to exit the app?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Exit",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use same styling as Settings button - no focus color changes
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(
            imageVector = Icons.Default.Home,
            contentDescription = "Home",
            modifier = Modifier.size(24.dp) // Scale icon proportionally
        )
    }
}

@Composable
fun LibraryButton(
    library: JellyfinLibrary,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Use same styling as Settings button - surfaceVariant with 0.8 alpha, onSurface text
    // Make 30% smaller - reduce font size and padding
    val scaledFontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
    val scaledPadding = 10.dp * 0.7f
    
    Button(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = scaledPadding)
            .onFocusChanged { isFocused = it.isFocused },
        colors = androidx.tv.material3.ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Box {
            // Shadow layer (slightly offset dark text for readability)
            Text(
                text = library.Name,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = scaledFontSize
                ),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.offset(x = 1.dp, y = 1.dp)
            )
            // Main text layer - dark when focused, otherwise normal
            Text(
                text = library.Name,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = scaledFontSize
                ),
                color = if (isFocused) {
                    Color.Black // Dark text when focused
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}


@Composable
fun JellyfinCard(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    onClick: () -> Unit,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    enableCaching: Boolean = true,
    reducePosterResolution: Boolean = false
) {
    // Use reduced resolution (600x900 for 2:3 aspect ratio) or 4K resolution (3840x5760) based on setting
    val imageUrl = if (reducePosterResolution) {
        apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 600, maxHeight = 900, quality = 90) ?: ""
    } else {
        apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 3840, maxHeight = 5760, quality = 90) ?: ""
    }
    
    StandardCardContainer(
        modifier = Modifier
            .width(268.dp)
            .onFocusChanged { focusState ->
                onFocusChanged?.invoke(focusState.isFocused)
            },
        imageCard = {
            Card(
                onClick = onClick,
                interactionSource = it,
                colors = CardDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            ) {
                if (imageUrl.isNotEmpty() && apiService != null) {
                    val headerMap = apiService.getImageRequestHeaders()
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .headers(headerMap)
                            .memoryCachePolicy(if (enableCaching) CachePolicy.ENABLED else CachePolicy.DISABLED)
                            .diskCachePolicy(if (enableCaching) CachePolicy.ENABLED else CachePolicy.DISABLED)
                            .build(),
                        contentDescription = item.Name,
                        modifier = Modifier
                            .width(268.dp)
                            .height(151.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .width(268.dp)
                            .height(151.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        },
        title = { }
    )
}

@Composable
fun JellyfinCardWithProgress(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    onClick: () -> Unit,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    enableCaching: Boolean = true,
    reducePosterResolution: Boolean = false
) {
    // Use reduced resolution (600x900 for 2:3 aspect ratio) or 4K resolution (3840x5760) based on setting
    val imageUrl = if (reducePosterResolution) {
        apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 600, maxHeight = 900, quality = 90) ?: ""
    } else {
        apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 3840, maxHeight = 5760, quality = 90) ?: ""
    }
    
    // Calculate progress percentage
    val progress = item.UserData?.PlayedPercentage?.toFloat()?.div(100f) ?: 0f
    
    StandardCardContainer(
        modifier = Modifier
            .width(268.dp)
            .onFocusChanged { focusState ->
                onFocusChanged?.invoke(focusState.isFocused)
            },
        imageCard = {
            Card(
                onClick = onClick,
                interactionSource = it,
                colors = CardDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (imageUrl.isNotEmpty() && apiService != null) {
                        val headerMap = apiService.getImageRequestHeaders()
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .headers(headerMap)
                                .memoryCachePolicy(if (enableCaching) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                .diskCachePolicy(if (enableCaching) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                .build(),
                            contentDescription = item.Name,
                            modifier = Modifier
                                .width(268.dp)
                                .height(151.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .width(268.dp)
                                .height(151.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    
                    // Progress bar at the bottom
                    if (progress > 0f && progress < 1f) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(4.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        },
        title = { }
    )
}

@Composable
fun JellyfinHorizontalCard(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    onClick: () -> Unit,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    enableCaching: Boolean = true,
    reducePosterResolution: Boolean = false,
    useSeriesPosterForEpisodes: Boolean = false,
    unwatchedEpisodeCount: Int? = null
) {
    // For episodes, use series poster (Primary) if requested; otherwise use poster (Primary) for movies/shows
    val imageUrl = remember(item.Id, item.Type, item.SeriesId, useSeriesPosterForEpisodes, reducePosterResolution) {
        if (item.Type == "Episode" && useSeriesPosterForEpisodes && item.SeriesId != null) {
            // Use series poster (Primary) for episodes
            if (reducePosterResolution) {
                apiService?.getImageUrl(item.SeriesId, "Primary", null, maxWidth = 600, maxHeight = 900, quality = 90) ?: ""
            } else {
                apiService?.getImageUrl(item.SeriesId, "Primary", null, maxWidth = 3840, maxHeight = 5760, quality = 90) ?: ""
            }
        } else {
            // Use poster (Primary) for movies and shows
            if (reducePosterResolution) {
                apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 600, maxHeight = 900, quality = 90) ?: ""
            } else {
                apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 3840, maxHeight = 5760, quality = 90) ?: ""
            }
        }
    }
    val context = LocalContext.current
    
    // Vertical card with 2:3 aspect ratio matching Plex dimensions
    // 30% smaller (105dp instead of 150dp)
    StandardCardContainer(
        modifier = Modifier
            .width(105.dp)
            .onFocusChanged { focusState ->
                onFocusChanged?.invoke(focusState.isFocused)
            },
        imageCard = {
            Card(
                onClick = onClick,
                interactionSource = it,
                colors = CardDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            ) {
                if (imageUrl.isNotEmpty() && apiService != null) {
                    val headerMap = apiService.getImageRequestHeaders()
                    // Use stable ImageRequest based on item ID to ensure proper caching
                    // Coil will cache based on the URL, but using remember ensures we don't recreate the request on recomposition
                    val imageRequest = remember(item.Id, imageUrl, enableCaching) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .headers(headerMap)
                            .size(300) // Hint to Coil about target size for optimization
                            .crossfade(true) // Smooth fade-in when image loads
                            .memoryCachePolicy(if (enableCaching) CachePolicy.ENABLED else CachePolicy.DISABLED)
                            .diskCachePolicy(if (enableCaching) CachePolicy.ENABLED else CachePolicy.DISABLED)
                            .build()
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = item.Name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f), // 2:3 portrait aspect ratio for posters (movies/shows/episodes)
                            contentScale = ContentScale.Crop,
                            placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        
                        // Watched indicator - checkmark in black box (top-right corner)
                        // Check Played boolean first, then PlayedPercentage as fallback
                        val isWatched = (item.UserData?.Played == true) ||
                                       (item.UserData?.PlayedPercentage == 100.0)
                        if (isWatched) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Watched",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        
                        // Unwatched episodes badge for TV shows (top-right corner)
                        // Only show if series is not fully watched and has unwatched episodes
                        if (item.Type == "Series" && !isWatched && unwatchedEpisodeCount != null && unwatchedEpisodeCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .background(Color.Black, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = unwatchedEpisodeCount.toString(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                } else {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        },
        title = { }
    )
}

@Composable
fun JellyfinEpisodeCard(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    onClick: () -> Unit,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    // For episodes, use series backdrop if available, otherwise use episode backdrop or primary
    val imageUrl = remember(item.SeriesId, item.Id) {
        if (item.SeriesId != null) {
            // First try to get series backdrop
            val seriesBackdrop = apiService?.getImageUrl(item.SeriesId, "Backdrop") ?: ""
            if (seriesBackdrop.isNotEmpty()) {
                seriesBackdrop
            } else {
                // Fall back to episode backdrop
                val episodeBackdrop = apiService?.getImageUrl(item.Id, "Backdrop") ?: ""
                if (episodeBackdrop.isNotEmpty()) {
                    episodeBackdrop
                } else {
                    // Last resort: episode primary
                    // Use 4K resolution for library cards (3840x5760 for 2:3 aspect ratio, quality 90)
                    apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 3840, maxHeight = 5760, quality = 90) ?: ""
                }
            }
            } else {
                // No series ID, use episode images
                val episodeBackdrop = apiService?.getImageUrl(item.Id, "Backdrop") ?: ""
                if (episodeBackdrop.isNotEmpty()) {
                    episodeBackdrop
                } else {
                    // Use 4K resolution for library cards (3840x5760 for 2:3 aspect ratio, quality 90)
                    apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 3840, maxHeight = 5760, quality = 90) ?: ""
                }
            }
    }
    
    // Display episode name with series name if available
    val displayName = remember(item.SeriesName, item.Name) {
        if (item.SeriesName != null && item.SeriesName.isNotEmpty()) {
            "${item.SeriesName} - ${item.Name}"
        } else {
            item.Name
        }
    }
    
    // Vertical card with 2:3 aspect ratio for episode posters
    StandardCardContainer(
        modifier = Modifier
            .width(105.dp)
            .onFocusChanged { focusState ->
                onFocusChanged?.invoke(focusState.isFocused)
            },
        imageCard = {
            Card(
                onClick = onClick,
                interactionSource = it,
                colors = CardDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            ) {
                if (imageUrl.isNotEmpty() && apiService != null) {
                    val headerMap = apiService.getImageRequestHeaders()
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .headers(headerMap)
                            .build(),
                        contentDescription = displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f), // 2:3 portrait aspect ratio
                        contentScale = ContentScale.Crop
                    )
                } else {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        },
        title = { }
    )
}

@Composable
fun JellyfinHorizontalCardWithProgress(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    onClick: () -> Unit,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    // Use thumbnail (Thumb) images for both episodes and movies
    // For episodes, prioritize series/parent thumb image (like official Jellyfin Android TV app)
    // For movies, use item's own thumb if available
    val imageUrl = when {
        // For episodes, try series thumb first (official app's preferParentThumb behavior)
        item.Type == "Episode" && item.SeriesId != null -> {
            // Try to get series thumb image (most common for episodes)
            val seriesThumb = apiService?.getImageUrl(item.SeriesId, "Thumb", null, maxWidth = 1920, maxHeight = 1080, quality = 90) ?: ""
            if (seriesThumb.isNotEmpty()) {
                seriesThumb
            } else {
                // Fall back to episode's own thumb
                val episodeThumb = item.ImageTags?.get("Thumb")?.let { thumbTag ->
                    apiService?.getImageUrl(item.Id, "Thumb", thumbTag, maxWidth = 1920, maxHeight = 1080, quality = 90) ?: ""
                } ?: ""
                if (episodeThumb.isNotEmpty()) {
                    episodeThumb
                } else {
                    // Last resort: series backdrop, then episode backdrop, then primary
                    val seriesBackdrop = apiService?.getImageUrl(item.SeriesId, "Backdrop", null, maxWidth = 1920, maxHeight = 1080, quality = 90) ?: ""
                    seriesBackdrop.ifEmpty {
                        val episodeBackdrop = apiService?.getImageUrl(item.Id, "Backdrop", null, maxWidth = 1920, maxHeight = 1080, quality = 90) ?: ""
                        episodeBackdrop.ifEmpty {
                            apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 3840, maxHeight = 5760, quality = 90) ?: ""
                        }
                    }
                }
            }
        }
        // For movies or other items, use item's own thumb if available
        item.ImageTags?.containsKey("Thumb") == true -> {
            item.ImageTags?.get("Thumb")?.let { thumbTag ->
                apiService?.getImageUrl(item.Id, "Thumb", thumbTag, maxWidth = 1920, maxHeight = 1080, quality = 90) ?: ""
            } ?: ""
        }
        // Fallback for movies: backdrop, then primary
        else -> {
            val backdrop = apiService?.getImageUrl(item.Id, "Backdrop", null, maxWidth = 1920, maxHeight = 1080, quality = 90) ?: ""
            backdrop.ifEmpty {
                apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 3840, maxHeight = 5760, quality = 90) ?: ""
            }
        }
    }
    
    // Calculate progress percentage
    val progress = item.UserData?.PlayedPercentage?.toFloat()?.div(100f) ?: 0f
    
    // Horizontal card with 16:9 aspect ratio for backdrop images (landscape)
    // 40% smaller: 268 * 0.6 = 160.8, rounded to 161.dp
    StandardCardContainer(
        modifier = Modifier
            .width(161.dp)
            .onFocusChanged { focusState ->
                onFocusChanged?.invoke(focusState.isFocused)
            },
        imageCard = {
            Card(
                onClick = onClick,
                interactionSource = it,
                colors = CardDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            ) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))) {
                    if (imageUrl.isNotEmpty() && apiService != null) {
                        val headerMap = apiService.getImageRequestHeaders()
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .headers(headerMap)
                                .build(),
                            contentDescription = item.Name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f) // 16:9 landscape aspect ratio
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Fallback to primary image if no thumbnail/backdrop
                        // Use 4K resolution for library cards (3840x5760 for 2:3 aspect ratio, quality 90)
                        val primaryUrl = apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 3840, maxHeight = 5760, quality = 90) ?: ""
                        if (primaryUrl.isNotEmpty() && apiService != null) {
                            val headerMap = apiService.getImageRequestHeaders()
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(primaryUrl)
                                    .headers(headerMap)
                                    .build(),
                                contentDescription = item.Name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                    }
                    
                    // Progress bar at the bottom
                    if (progress > 0f && progress < 1f) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(4.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        },
        title = { 
            // Title removed - no text displayed under continue watching cards
        }
    )
}

fun Modifier.carouselGradient(): Modifier = composed {
    val color = MaterialTheme.colorScheme.surface

    val colorAlphaList = listOf(1.0f, 0.2f, 0.0f)
    val colorStopList = listOf(0.2f, 0.8f, 0.9f)

    val colorAlphaList2 = listOf(1.0f, 0.1f, 0.0f)
    val colorStopList2 = listOf(0.1f, 0.4f, 0.9f)
    this
        .then(
            background(
                brush = Brush.linearGradient(
                    colorStopList[0] to color.copy(alpha = colorAlphaList[0]),
                    colorStopList[1] to color.copy(alpha = colorAlphaList[1]),
                    colorStopList[2] to color.copy(alpha = colorAlphaList[2]),
                    start = Offset(0.0f, 0.0f),
                    end = Offset(Float.POSITIVE_INFINITY, 0.0f)
                )
            )
        )
        .then(
            background(
                brush = Brush.linearGradient(
                    colorStopList2[0] to color.copy(alpha = colorAlphaList2[0]),
                    colorStopList2[1] to color.copy(alpha = colorAlphaList2[1]),
                    colorStopList2[2] to color.copy(alpha = colorAlphaList2[2]),
                    start = Offset(0f, Float.POSITIVE_INFINITY),
                    end = Offset(0f, 0f)
                )
            )
        )
}

// Metadata box component - matching MovieDetailsScreen/SeriesDetailsScreen
@Composable
private fun MetadataBox(text: String) {
    Box(
        modifier = Modifier
            .background(Color.Black, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

// Format resolution helper function
private fun formatResolution(width: Int?, height: Int?): String? {
    if (width == null || height == null) return null
    
    return when {
        width >= 3840 || height >= 2160 -> "4K"
        width >= 1920 || height >= 1080 -> "1080p"
        width >= 1280 || height >= 720 -> "720p"
        width >= 854 || height >= 480 -> "480p"
        else -> "${width}x${height}"
    }
}

// Rating display with Rotten Tomatoes icon support - matching MovieDetailsScreen/SeriesDetailsScreen
@Composable
private fun RatingDisplay(
    item: JellyfinItem,
    communityRating: Float?,
    criticRating: Float?
) {
    // Calculate percentages
    fun calculatePercentage(rating: Float): Int {
        return if (rating > 10) {
            // Already in percentage format (0-100)
            rating.toInt()
        } else {
            // Convert from 0-10 scale to percentage
            (rating * 10).toInt()
        }
    }
    
    // Determine critic rating type and display if available
    val criticRatingType: com.flex.elefin.screens.RatingType? = if (criticRating != null) {
        // Pass null for communityRating to focus on critic rating
        determineRatingType(item.ProviderIds, null, criticRating, preferCommunity = false)
    } else {
        null
    }
    
    // Determine community rating type and display if available (as audience rating)
    val communityRatingType: com.flex.elefin.screens.RatingType? = if (communityRating != null) {
        // Pass null for criticRating to focus on community rating
        determineRatingType(item.ProviderIds, communityRating, null, preferCommunity = true)
    } else {
        null
    }
    
    // Show critic rating (RT Fresh/Rotten or generic)
    if (criticRating != null) {
        val percentage = calculatePercentage(criticRating)
        when (criticRatingType) {
            com.flex.elefin.screens.RatingType.RottenTomatoesFresh -> {
                RatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = com.flex.elefin.R.drawable.ic_rt_fresh,
                    label = "RT"
                )
            }
            com.flex.elefin.screens.RatingType.RottenTomatoesRotten -> {
                RatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = com.flex.elefin.R.drawable.ic_rt_rotten,
                    label = "RT"
                )
            }
            com.flex.elefin.screens.RatingType.IMDb -> {
                RatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = com.flex.elefin.R.drawable.ic_imdb,
                    label = "IMDb"
                )
            }
            else -> {
                MetadataBox(text = "${percentage}%")
            }
        }
    }
    
    // Show audience rating (RT Popcorn or generic) if available and different from critic
    if (communityRating != null && (criticRating == null || communityRating != criticRating)) {
        val percentage = calculatePercentage(communityRating)
        when (communityRatingType) {
            com.flex.elefin.screens.RatingType.RottenTomatoesAudience -> {
                RatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = com.flex.elefin.R.drawable.ic_rt_popcorn,
                    label = "RT"
                )
            }
            com.flex.elefin.screens.RatingType.IMDb -> {
                // Only show IMDb if we didn't already show it for critic
                if (criticRatingType != com.flex.elefin.screens.RatingType.IMDb) {
                    RatingBoxWithIcon(
                        percentage = percentage,
                        iconRes = com.flex.elefin.R.drawable.ic_imdb,
                        label = "IMDb"
                    )
                }
            }
            else -> {
                // Show generic community rating only if we didn't show critic rating
                if (criticRating == null) {
                    MetadataBox(text = "${percentage}%")
                }
            }
        }
    }
}

// Determine rating type from ProviderIds and rating values
private fun determineRatingType(
    providerIds: Map<String, String>?,
    communityRating: Float?,
    criticRating: Float?,
    preferCommunity: Boolean
): com.flex.elefin.screens.RatingType {
    // Check for Rotten Tomatoes provider IDs first
    val rtId = providerIds?.get("RottenTomatoes") ?: providerIds?.get("rottentomatoes") ?: 
               providerIds?.get("Rotten Tomatoes") ?: providerIds?.get("RottenTomatoes.tomato") ?:
               providerIds?.get("RottenTomatoes.audience")
    
    // If preferCommunity is true and CommunityRating exists with RT ID, return Audience
    if (preferCommunity && rtId != null && communityRating != null) {
        return com.flex.elefin.screens.RatingType.RottenTomatoesAudience
    }
    
    // If CriticRating exists, it's likely RT Fresh/Rotten rating
    if (criticRating != null && !preferCommunity) {
        // RT Fresh = 60%+ (6.0/10), RT Rotten = <60%
        // Show RT icons even if ProviderIds don't explicitly say RT, as CriticRating is typically RT
        return if (criticRating >= 6.0f) {
            com.flex.elefin.screens.RatingType.RottenTomatoesFresh
        } else {
            com.flex.elefin.screens.RatingType.RottenTomatoesRotten
        }
    }
    
    // If we have RT provider ID and CommunityRating, it might be RT Audience
    if (rtId != null && communityRating != null) {
        return com.flex.elefin.screens.RatingType.RottenTomatoesAudience
    }
    
    // Check for IMDb
    if (providerIds != null) {
        val imdbId = providerIds["Imdb"] ?: providerIds["imdb"] ?: providerIds["IMDb"] ?:
                     providerIds["imdbid"]
        if (imdbId != null) {
            return com.flex.elefin.screens.RatingType.IMDb
        }
    }
    
    return com.flex.elefin.screens.RatingType.Generic
}

// Rating box with icon - matching MovieDetailsScreen/SeriesDetailsScreen
@Composable
private fun RatingBoxWithIcon(
    percentage: Int,
    iconRes: Int,
    label: String
) {
    Box(
        modifier = Modifier
            .background(Color.Black, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon for rating source - height to match metadata item height, width adjusts to preserve aspect ratio
            Image(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.height(12.dp), // Match height of labelSmall text in MetadataBox
                contentScale = ContentScale.Fit // Preserve aspect ratio, fill height
            )
            Text(
                text = "${percentage}%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}
