package com.flex.elefin.screens

import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.VideoLibrary

import com.flex.elefin.ui.shelfUpdateModifier

import com.flex.elefin.components.RatingDisplay

import android.app.Activity
import android.content.Context
import android.util.Log
import com.flex.elefin.ui.TvBringIntoViewProvider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.draw.rotate
import com.flex.elefin.components.DigitalClock
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import coil.imageLoader
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.jellyfin.AppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.JellyfinLibrary
import com.flex.elefin.jellyfin.JellyfinRepository
import java.text.SimpleDateFormat
import java.util.Locale
import com.flex.elefin.ui.HomeBackdrop
import com.flex.elefin.ui.HomeStyle
import com.flex.elefin.ui.homeCardFocus
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers

private data class HomeShelf(val id: String, val title: String, val items: List<JellyfinItem>, val progress: Boolean = false, val resume: Boolean = false)

enum class SortType {
    Alphabetically,
    DateAdded,
    DateReleased
}

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun JellyfinHomeScreen(
    initialTabAction: String? = null,
    onTabActionHandled: () -> Unit = {},
    onItemClick: (JellyfinItem, Long) -> Unit = { _, _ -> },
    onMusicClick: () -> Unit = {},
    onMoviesLibraryClick: (libraryId: String, libraryName: String) -> Unit = { _, _ -> },
    onTvShowsLibraryClick: (libraryId: String, libraryName: String) -> Unit = { _, _ -> },
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
    
    // GL Pipeline warmup for NVIDIA Shield - prevents initial frame stutter and ANR
    // Allows the GPU to warm up and UI to render before loading heavy data
    LaunchedEffect(Unit) {
        delay(100) // 100ms delay to prevent ANR and warm up GL pipeline
    }
    
    // Dark mode setting - read from settings and update when screen resumes
    var darkModeEnabled by remember { mutableStateOf(settings.darkModeEnabled) }
    
    // Debug outlines setting - read from settings and update when settings dialog closes
    var debugOutlinesEnabled by remember { mutableStateOf(settings.showDebugOutlines) }
    
    // UI animations setting - read from settings
    val disableUIAnimations = remember { mutableStateOf(settings.disableUIAnimations) }
    
    // Low power mode - enables all performance optimizations
    val lowPowerMode = remember { mutableStateOf(settings.lowPowerMode) }
    
    // Simple cards setting - read from settings (for low-spec devices)
    val useSimpleCards = remember { mutableStateOf(settings.useSimpleCards) }
    
    // Google TV style cards setting - lightweight with subtle scale animation
    val useGoogleTvCards = remember { mutableStateOf(settings.useGoogleTvCards) }
    
    // No-fling behavior for when animations are disabled (instant scroll, no smooth animation)
    val noFlingBehavior = remember {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                return 0f // No fling - instant stop
            }
        }
    }
    
    
    // Hide shows with zero episodes setting - read from settings
    var hideShowsWithZeroEpisodes by remember { mutableStateOf(settings.hideShowsWithZeroEpisodes) }
    
    // 24-hour time format setting - read from settings
    var use24HourTime by remember { mutableStateOf(settings.use24HourTime) }
    
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
        apiService?.let { JellyfinRepository(it, settings) }
    }

    val isTv = remember(context) { com.flex.elefin.ui.DeviceUtils.isTvDevice(context) }
    if (!isTv && repository != null && !showServerEntry && !showLoginScreen) {
        JellyfinMobileHomeScreen(
            initialTabAction = initialTabAction,
            onTabActionHandled = onTabActionHandled,
            repository = repository,
            onItemClick = onItemClick,
            onMusicClick = onMusicClick,
            onMoviesLibraryClick = onMoviesLibraryClick,
            onTvShowsLibraryClick = onTvShowsLibraryClick,
            preloadLibraryImages = preloadLibraryImages,
            cacheLibraryImages = cacheLibraryImages,
            reducePosterResolution = reducePosterResolution
        )
        return
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
    var initialDrawerCardFocused by remember { mutableStateOf(false) }
    
    val nextUpItemsState = repository?.nextUpItems?.collectAsState(initial = emptyList())
    val nextUpItems = nextUpItemsState?.value ?: emptyList()
    
    val recentlyAddedMoviesByLibraryState = repository?.recentlyAddedMoviesByLibrary?.collectAsState(initial = emptyMap())
    val recentlyAddedMoviesByLibrary = recentlyAddedMoviesByLibraryState?.value ?: emptyMap()
    
    // Get movie libraries from the existing libraries state (defined later in the file)
    // We'll use the libraries state that's already defined, but filter for movie libraries
    val movieLibrariesState = repository?.libraries?.collectAsState(initial = emptyList())
    val allMovieLibraries = movieLibrariesState?.value ?: emptyList()
    
    // Get movie libraries (libraries that have movies)
    val movieLibraries = remember(allMovieLibraries, recentlyAddedMoviesByLibrary) {
        allMovieLibraries.filter { library ->
            recentlyAddedMoviesByLibrary.containsKey(library.Id)
        }.sortedBy { it.Name } // Sort by name for consistent ordering
    }
    
    val recentlyReleasedMoviesState = repository?.recentlyReleasedMovies?.collectAsState(initial = emptyList())
    val recentlyReleasedMovies = recentlyReleasedMoviesState?.value ?: emptyList()
    val releasedByLibraryState = repository?.recentlyReleasedMoviesByLibrary?.collectAsState(initial = emptyMap())
    val releasedByLibrary = releasedByLibraryState?.value ?: emptyMap()
    
    val recentlyAddedShowsByLibraryState = repository?.recentlyAddedShowsByLibrary?.collectAsState(initial = emptyMap())
    val recentlyAddedShowsByLibrary = recentlyAddedShowsByLibraryState?.value ?: emptyMap()
    
    val recentlyAddedEpisodesByLibraryState = repository?.recentlyAddedEpisodesByLibrary?.collectAsState(initial = emptyMap())
    val recentlyAddedEpisodesByLibrary = recentlyAddedEpisodesByLibraryState?.value ?: emptyMap()
    
    // Get TV show libraries (libraries that have shows or episodes)
    val tvShowLibraries = remember(allMovieLibraries, recentlyAddedShowsByLibrary, recentlyAddedEpisodesByLibrary) {
        allMovieLibraries.filter { library ->
            recentlyAddedShowsByLibrary.containsKey(library.Id) || recentlyAddedEpisodesByLibrary.containsKey(library.Id)
        }.sortedBy { it.Name } // Sort by name for consistent ordering
    }
    
    val librariesState = repository?.libraries?.collectAsState(initial = emptyList())
    val libraries = librariesState?.value ?: emptyList()
    
    val collectionsState = repository?.collections?.collectAsState(initial = emptyList())
    val collections = collectionsState?.value ?: emptyList()
    
    val libraryItemsState = repository?.libraryItems?.collectAsState(initial = emptyMap())
    val libraryItems = libraryItemsState?.value ?: emptyMap()
    
    val collectionItemsState = repository?.collectionItems?.collectAsState(initial = emptyMap())
    val collectionItems = collectionItemsState?.value ?: emptyMap()
    
    // Note: Unwatched episode counts are now provided directly by Jellyfin API via UserData.UnplayedItemCount
    // No need to manually track them - just use item.UserData?.UnplayedItemCount
    
    var selectedLibraryId by remember { mutableStateOf<String?>(null) }
    var selectedCollectionId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initialTabAction) {
        if (initialTabAction?.startsWith("library:") == true) {
            selectedLibraryId = initialTabAction.removePrefix("library:")
            selectedCollectionId = null
            onTabActionHandled()
        }
    }

    var showExitConfirmation by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var darkModeWhenSettingsOpened by remember { mutableStateOf(false) }
    var debugOutlinesWhenSettingsOpened by remember { mutableStateOf(false) }
    var disableUIAnimationsWhenSettingsOpened by remember { mutableStateOf(false) }
    var lowPowerModeWhenSettingsOpened by remember { mutableStateOf(false) }
    var rowCardCountWhenSettingsOpened by remember { mutableStateOf(25) }
    var useSimpleCardsWhenSettingsOpened by remember { mutableStateOf(false) }
    var useGoogleTvCardsWhenSettingsOpened by remember { mutableStateOf(false) }
    var use24HourTimeWhenSettingsOpened by remember { mutableStateOf(false) }
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
    val handleHomeBack: () -> Unit = {
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
    BackHandler(enabled = true, onBack = handleHomeBack)
    
    LaunchedEffect(repository, config.isConfigured()) {
        // Only fetch data if properly configured
        if (config.isConfigured() && repository != null) {
            // Small delay to allow UI to render first and prevent ANR
            delay(150)
            repository.fetchLibraries()
            repository.fetchContinueWatching()
            repository.fetchNextUp()
            repository.fetchRecentlyAddedMovies()
            repository.fetchRecentlyReleasedMovies()
            repository.fetchCollections()
            repository.fetchRecentlyAddedShows()
            repository.fetchRecentlyAddedEpisodes()
        }
    }
    
    // Refresh all content when the screen becomes visible again
    // This ensures items appear after watching/partially watching content
    // Also refresh settings when screen resumes
    // This is critical when app resumes from memory after device sleep/power up
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Refresh settings when screen resumes
                darkModeEnabled = settings.darkModeEnabled
                hideShowsWithZeroEpisodes = settings.hideShowsWithZeroEpisodes
                // Refresh all content when returning to the screen
                // This fixes issue where only Continue Watching and Next Up show after device resume
                scope.launch {
                    repository?.fetchLibraries()
                    repository?.fetchContinueWatching()
                    repository?.fetchNextUp()
                    repository?.fetchRecentlyAddedMovies()
                    repository?.fetchRecentlyReleasedMovies()
                    repository?.fetchCollections()
                    repository?.fetchRecentlyAddedShows()
                    repository?.fetchRecentlyAddedEpisodes()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    var imageRefreshKey by remember { mutableStateOf(0L) }
    LiveLibraryUpdates(repository?.apiService) {
        repository?.refreshLibraryRows()
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
    
    // Primary scroll states for different views
    val homeLazyListState = rememberLazyListState(
        prefetchStrategy = remember { com.flex.elefin.ui.HomeShelfPrefetchStrategy() }
    )
    val libraryLazyListState = rememberLazyListState()
    val collectionsLazyListState = rememberLazyListState()
    
    // highlightedItem is used for background image (debounced)
    var highlightedItem by remember { mutableStateOf<JellyfinItem?>(null) }
    // instantHighlightedItem is used for metadata text (updates immediately)
    var instantHighlightedItem by remember { mutableStateOf<JellyfinItem?>(null) }
    
    // Debounced states for metadata panel to optimize scroll performance on TV
    
    // Track the original episode item when highlighting a series from an episode
    var originalEpisodeItem by remember { mutableStateOf<JellyfinItem?>(null) }
    
    // Job to track debounced background image changes
    var backgroundChangeJob by remember { mutableStateOf<Job?>(null) }
    
    // Set initial highlighted item to first continue watching item or first recently added movie
    LaunchedEffect(continueWatchingItems, recentlyAddedMoviesByLibrary) {
        if (highlightedItem == null) {
            // Get first movie from first library as fallback
            val firstMovie = movieLibraries.firstOrNull()?.let { library ->
                recentlyAddedMoviesByLibrary[library.Id]?.firstOrNull()
            }
            val initialItem = continueWatchingItems.firstOrNull() ?: firstMovie
            highlightedItem = initialItem
            instantHighlightedItem = initialItem
        }
    }
    
    
    val drawerEnabled = rememberTvDrawerEnabled(settings)
    val openDrawerSettings: () -> Unit = {
                        darkModeWhenSettingsOpened = settings.darkModeEnabled
                        debugOutlinesWhenSettingsOpened = settings.showDebugOutlines
                        disableUIAnimationsWhenSettingsOpened = settings.disableUIAnimations
                        lowPowerModeWhenSettingsOpened = settings.lowPowerMode
                        useSimpleCardsWhenSettingsOpened = settings.useSimpleCards
                        useGoogleTvCardsWhenSettingsOpened = settings.useGoogleTvCards
                        use24HourTimeWhenSettingsOpened = settings.use24HourTime
                        rowCardCountWhenSettingsOpened = settings.rowCardCount
                        showSettings = true
                    }
    val refreshFromDrawer: () -> Unit = {
                        if (selectedLibraryId != null) {
                            // Show sort dialog when library is selected
                            showSortDialog = true
                        } else {
                            // Refresh when on home screen
                            if (!isRefreshing && repository != null) {
                                isRefreshing = true
                                scope.launch {
                                    try {
                                        // Clear image cache for home screen cards so new images can be downloaded
                                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val imageLoader = context.imageLoader
                                            imageLoader.diskCache?.clear()
                                            imageLoader.memoryCache?.clear()
                                            Log.d("JellyfinHomeScreen", "Image cache cleared for home screen refresh")
                                        }

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
                        }
                    }
    val drawerEntries = buildList {
        add(TvDrawerEntry("search", "Search", Icons.Default.Search) { showSearch = true })
        add(TvDrawerEntry("home", "Home", Icons.Default.Home, selectedLibraryId == null && selectedCollectionId == null) {
            selectedLibraryId = null
            selectedCollectionId = null
        })
        libraries.filter { !it.Name.equals("Collections", true) && (settings.showMusicLibraries || it.CollectionType != "music") }.forEach { library ->
            val icon = when (library.CollectionType) {
                "movies" -> Icons.Default.Movie
                "tvshows" -> Icons.Default.Tv
                "music" -> Icons.Default.LibraryMusic
                else -> Icons.Default.VideoLibrary
            }
            add(TvDrawerEntry(library.Id, library.Name, icon, selectedLibraryId == library.Id) {
                when (library.CollectionType) {
                    "movies" -> onMoviesLibraryClick(library.Id, library.Name)
                    "tvshows" -> onTvShowsLibraryClick(library.Id, library.Name)
                    "music" -> onMusicClick()
                    else -> { selectedLibraryId = library.Id; selectedCollectionId = null }
                }
            })
        }
        if (collections.isNotEmpty()) add(TvDrawerEntry("collections", "Collections", Icons.Default.Collections, selectedCollectionId == "__COLLECTIONS__") {
            selectedLibraryId = null
            selectedCollectionId = "__COLLECTIONS__"
        })
        add(TvDrawerEntry("refresh", if (selectedLibraryId != null) "Sort" else "Refresh", if (selectedLibraryId != null) Icons.Default.SwapVert else Icons.Default.Refresh,
            enabled = !isRefreshing || selectedLibraryId != null, onClick = refreshFromDrawer))
    }

    TvBringIntoViewProvider {
        TvNavigationDrawer(
            enabled = drawerEnabled,
            entries = drawerEntries,
            settingsEntry = TvDrawerEntry("settings", "Settings", Icons.Default.Settings, onClick = openDrawerSettings)
        ) {

        Box(Modifier.fillMaxSize()) {
            TvHomeBackground(
                itemProvider = { highlightedItem },
                isScrolling = { homeLazyListState.isScrollInProgress },
                apiService = apiService,
                darkModeEnabled = darkModeEnabled,
                lowPowerMode = lowPowerMode.value,
                use4KBackgrounds = settings.use4KBackgrounds,
                selectedLibraryId = selectedLibraryId,
                selectedCollectionId = selectedCollectionId,
                disableAnimations = disableUIAnimations.value
            )
        Box(Modifier.fillMaxSize().padding(start = if (drawerEnabled) 16.dp else 0.dp)) {

        
        // Top row with home button and library buttons - positioned absolutely on top of carousel
        if (!drawerEnabled) Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 22.dp) // Reduced by 30% (32 * 0.7 = 22.4, rounded to 22)
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
                    modifier = Modifier.weight(1f)
                ) {
                // Settings button - first on the left (same size as library buttons)
                IconButton(
                    onClick = openDrawerSettings,
                    colors = IconButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .padding(start = 38.dp, end = 14.dp) // Start reduced from 54 to 38, end from 20 to 14
                        .size(34.dp) // 30% smaller (from 48dp to 34dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(14.dp) // Reduced from 20dp to 14dp
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
                        .padding(end = 14.dp) // Reduced from 20 to 14
                        .size(34.dp) // Same size as settings button (34dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(14.dp) // Reduced from 20dp to 14dp
                    )
                }
                
                // Refresh/Sort button - to the right of search button
                // Shows refresh button on home screen, sort button when library is selected
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
                
                val isLibrarySelected = selectedLibraryId != null
                
                IconButton(
                    onClick = refreshFromDrawer,
                    enabled = !isRefreshing || isLibrarySelected,
                    colors = IconButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .padding(end = 14.dp) // Reduced from 20 to 14
                        .size(34.dp) // Same size as settings button (34dp)
                ) {
                    if (isLibrarySelected) {
                        // Show sort icon when library is selected
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Sort",
                            modifier = Modifier.size(14.dp) // Reduced from 20dp to 14dp
                        )
                    } else {
                        // Show refresh icon on home screen
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (isRefreshing) "Refreshing..." else "Refresh",
                            modifier = Modifier
                                .size(14.dp) // Reduced from 20dp to 14dp
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
                
                // Home button - styled like tab row items with underline
                var homeFocused by remember { mutableStateOf(false) }
                val homeSelected = selectedLibraryId == null && selectedCollectionId == null
                
                // Create a mini TabRow for the home button to get the underline indicator
                TabRow(
                    modifier = Modifier.padding(end = 14.dp), // Reduced from 20 to 14
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
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            tint = if (homeFocused) Color.Black else Color.White,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp) // Reduced from (12, 6) to (8, 4)
                                .size(20.dp) // Reduced from 28dp to 20dp
                        )
                    }
                }
                
                // Library buttons with underlined indicator using TV Material3 TabRow
                // Add a single "Collections" tab if collections are available
                val showMusicLibraries = remember { settings.showMusicLibraries }
                val allTabs = remember(libraries, collections, showMusicLibraries) {
                    // Debug: Log all libraries and their CollectionType
                    libraries.forEach { lib ->
                        android.util.Log.d("JellyfinHomeScreen", "ðŸ“š Library loaded: ${lib.Name}, Type: ${lib.Type}, CollectionType: ${lib.CollectionType}, Id: ${lib.Id}")
                    }
                    
                    buildList<Pair<String?, String>> {
                        // Add all libraries (exclude "Collections" tab conflicts, and music if disabled)
                        addAll(libraries.filter { lib ->
                            !lib.Name.equals("Collections", ignoreCase = true) &&
                            (showMusicLibraries || lib.CollectionType != "music")
                        }.map { null to it.Id })
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
                        separator = { Spacer(modifier = Modifier.width(11.dp)) }, // Reduced from 16dp to 11dp (30% smaller)
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
                            
                            // Check if this is a music, movies, or TV shows library
                            val library = libraries.find { it.Id == itemId }
                            val isMusicLibrary = !isCollectionsTab && library?.CollectionType == "music"
                            val isMoviesLibrary = !isCollectionsTab && library?.CollectionType == "movies"
                            val isTvShowsLibrary = !isCollectionsTab && library?.CollectionType == "tvshows"
                            
                            // Debug log for library detection
                            android.util.Log.d("JellyfinHomeScreen", "Library: ${library?.Name}, CollectionType: ${library?.CollectionType}, isMusicLibrary: $isMusicLibrary, isMoviesLibrary: $isMoviesLibrary, isTvShowsLibrary: $isTvShowsLibrary")
                            
                            Tab(
                                selected = isSelected,
                                onFocus = {
                                    // Do nothing on focus - only load on click
                                },
                                onClick = {
                                    // Handle music library specially - navigate to music screen
                                    if (isMusicLibrary) {
                                        android.util.Log.d("JellyfinHomeScreen", "ðŸŽµ Music library clicked! Navigating to music screen...")
                                        onMusicClick()
                                        return@Tab
                                    }
                                    
                                    // Handle movies library specially - navigate to movies library screen
                                    if (isMoviesLibrary && library != null) {
                                        android.util.Log.d("JellyfinHomeScreen", "ðŸŽ¬ Movies library clicked! Navigating to movies library screen...")
                                        onMoviesLibraryClick(library.Id, library.Name)
                                        return@Tab
                                    }
                                    
                                    // Handle TV shows library specially - navigate to TV shows library screen
                                    if (isTvShowsLibrary && library != null) {
                                        android.util.Log.d("JellyfinHomeScreen", "ðŸ“º TV Shows library clicked! Navigating to TV shows library screen...")
                                        onTvShowsLibraryClick(library.Id, library.Name)
                                        return@Tab
                                    }
                                    
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
                                // Now reduced by additional 30% from the enlarged state: 1.17 * 0.7 = 0.819f
                                val scaledFontSize = 13.sp
                                // Add horizontal padding (Reduced by 30% from current enlarged state: 1.2 * 0.7 = 0.84)
                                val horizontalPadding = 14.dp
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
                
            // Digital clock on the far right
            DigitalClock(use24HourFormat = use24HourTime)
        }
    }
        
        if (drawerEnabled) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 22.dp)) {
                DigitalClock(use24HourFormat = use24HourTime)
            }
        }

        // Item details section - below settings button (only show when not viewing a library)
        // Show highlighted item panel for home screen and collections
        // Use debouncedHighlightedItem for smooth metadata updates without layouts/image requests on intermediate cards
        // Use Crossfade to animate all metadata together when item changes
        if (selectedLibraryId == null) {
            HomeHero(
                itemProvider = { instantHighlightedItem },
                isScrolling = { homeLazyListState.isScrollInProgress },
                episodeProvider = { originalEpisodeItem },
                apiService = apiService,
                imageRefreshKey = imageRefreshKey,
                disableAnimations = disableUIAnimations.value || lowPowerMode.value
            )
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
            // Spacer to push content down, allowing carousel to show behind top area
            // Show spacer when on home screen or viewing collections (not libraries)
            if (selectedLibraryId == null) {
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
                            // Keep non-Series items, or Series items with episodes
                            // Use RecursiveItemCount (total episodes) if available, fall back to ChildCount (seasons)
                            if (item.Type != "Series") {
                                true
                            } else {
                                val episodeCount = item.RecursiveItemCount ?: item.ChildCount ?: 0
                                episodeCount > 0
                            }
                        }
                    } else {
                        sortedItems
                    }
                }
                val context = LocalContext.current
                val imageLoader = context.imageLoader
                
                com.flex.elefin.ui.LibraryImagePrefetch(
                    state = libraryLazyListState, items = items, api = apiService,
                    enabled = preloadLibraryImages, cacheImages = cacheLibraryImages,
                    reducedResolution = reducePosterResolution
                )
                // Container for library grid - positioned below tab row
                Spacer(modifier = Modifier.height(86.dp)) // Add space below tab row (reduced by 40% from 144: 144 * 0.6 = 86)
                
                if (items.isNotEmpty()) {
                    // A-Z Index state - only show when sorted alphabetically AND not in low power mode
                    // The A-Z index with animations can cause scrolling lag on lower-end devices
                    val showAlphabetIndex = sortType == SortType.Alphabetically && !lowPowerMode.value
                    val columns = 6
                    val letterIndexMap = remember(items, columns) {
                        if (showAlphabetIndex) buildLetterIndexMap(items, columns) else emptyMap()
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
                            val targetRow = letterIndexMap[selectedLetter] ?: return@LaunchedEffect
                            libraryLazyListState.animateScrollToItem(targetRow)
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
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
                                    AlphabetIndexBar(
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
                            
                            // Main content area
                            androidx.tv.material3.Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
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
                                    state = libraryLazyListState,
                                    contentPadding = PaddingValues(bottom = 20.dp * 1.15f, top = 24.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = if (showAlphabetIndex) 8.dp else 54.dp, end = 38.dp)
                                        .then(
                                            if (debugOutlinesEnabled) {
                                                Modifier.border(3.dp, Color.Blue)
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    // Grid layout with 6 columns - integrate directly into LazyColumn
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
                                                Column(
                                                    modifier = Modifier.width(105.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
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
                                                                // Update metadata text immediately
                                                                instantHighlightedItem = item
                                                                originalEpisodeItem = null
                                                                
                                                                // Cancel any pending background change
                                                                backgroundChangeJob?.cancel()
                                                                
                                                                // Debounce: wait 1 second before changing background
                                                                backgroundChangeJob = scope.launch {
                                                                delay(400)
                                                                highlightedItem = item
                                                            }
                                                            }
                                                        },
                                                        enableCaching = cacheLibraryImages,
                                                        reducePosterResolution = reducePosterResolution,
                                                        unwatchedEpisodeCount = if (item.Type == "Series") item.UserData?.UnplayedItemCount else null,
                                                        disableAnimations = disableUIAnimations.value,
                                                        useSimpleCards = useSimpleCards.value,
                                                        useGoogleTvCards = useGoogleTvCards.value,
                                                        lowPowerMode = lowPowerMode.value,
                                                        imageRefreshKey = imageRefreshKey
                                                    )
                                                    // Item name below the card - skip in low power mode for smoother scrolling
                                                    if (!lowPowerMode.value) {
                                                        Text(
                                                            text = item.Name ?: "",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = Color.White.copy(alpha = 0.9f),
                                                            maxLines = 1,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
                        
                        // Letter overlay (shown briefly when navigating A-Z)
                        if (showAlphabetIndex) {
                            LetterOverlay(
                                letter = selectedLetter,
                                visible = showLetterOverlay
                            )
                        }
                    }
                }
                // Loading state removed - content loads progressively without blocking UI
            }
            
            // Show collections as rows (like library screens) when Collections tab is selected
            // Each collection gets its own row with the collection name as the title
            if (selectedCollectionId == "__COLLECTIONS__") {
                // Content rows - same layout as library screens
                LazyColumn(
                    state = collectionsLazyListState,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                        .padding(start = 54.dp, top = 36.dp, end = 38.dp, bottom = 0.dp)
                            .clipToBounds()
                        .then(
                            if (debugOutlinesEnabled) {
                                Modifier.border(3.dp, Color.Yellow)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    // Iterate through each collection and create a separate item row for it
                    collections.forEachIndexed { index, collection ->
                        val items = collectionItems[collection.Id] ?: emptyList()
                        
                        // Only show collections that have items
                        if (items.isNotEmpty()) {
                            item(
                                key = collection.Id,
                                contentType = "collection_row"
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(top = if (index == 0) 24.dp else 0.dp)
                                        .then(
                                            if (index == 0) {
                                                Modifier.focusRequester(focusRequester)
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    // Collection name as row title
                                    Text(
                                        text = collection.Name,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                        ),
                                        modifier = Modifier.padding(
                                            bottom = 12.dp,
                                            top = if (index == 0) 12.dp else 30.3186.dp
                                        )
                                    )
                                    
                                    LazyRow(
                                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f * 1.2f * 1.3f)),
                                        horizontalArrangement = Arrangement.spacedBy(HomeStyle.shelfGap),
                                        flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
                                        modifier = if (debugOutlinesEnabled) {
                                            Modifier.border(2.dp, Color.Magenta)
                                        } else {
                                            Modifier
                                        }
                                    ) {
                                        items(
                                            items = items,
                                            key = { it.Id },
                                            contentType = { "collection_item" }
                                        ) { item ->
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
                                                onFocusChanged = { isFocused ->
                                                    if (isFocused) {
                                                        instantHighlightedItem = item
                                                        originalEpisodeItem = null
                                                        backgroundChangeJob?.cancel()
                                                        backgroundChangeJob = scope.launch {
                                                                delay(400)
                                                                highlightedItem = item
                                                            }
                                                    }
                                                },
                                                enableCaching = cacheLibraryImages,
                                                reducePosterResolution = reducePosterResolution,
                                                useSeriesPosterForEpisodes = true,
                                                useSimpleCards = useSimpleCards.value,
                                                useGoogleTvCards = useGoogleTvCards.value,
                                                lowPowerMode = lowPowerMode.value,
                                                imageRefreshKey = imageRefreshKey
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (selectedLibraryId == null && selectedCollectionId != "__COLLECTIONS__") {
                    val homeShelves = remember(continueWatchingItems, nextUpItems, movieLibraries, allMovieLibraries,
                        recentlyAddedMoviesByLibrary, releasedByLibrary, tvShowLibraries,
                        recentlyAddedShowsByLibrary, recentlyAddedEpisodesByLibrary, hideShowsWithZeroEpisodes) {
                        buildList {
                            fun addShelf(id: String, title: String, items: List<JellyfinItem>, progress: Boolean = false, resume: Boolean = false) {
                                if (items.isNotEmpty()) add(HomeShelf(id, title, items, progress, resume))
                            }
                            addShelf("continue", "Continue Watching", continueWatchingItems, progress = true)
                            addShelf("next", "Next Up", nextUpItems)
                            movieLibraries.forEach { library ->
                                addShelf("movies:${library.Id}", "Recently Added in ${library.Name}",
                                    recentlyAddedMoviesByLibrary[library.Id].orEmpty())
                            }
                            allMovieLibraries.filter { it.CollectionType.equals("movies", ignoreCase = true) }.sortedBy { it.Name }.forEach { library ->
                                addShelf("released:${library.Id}", "Recently Released in ${library.Name}", releasedByLibrary[library.Id].orEmpty())
                            }
                            tvShowLibraries.forEach { library ->
                                val shows = recentlyAddedShowsByLibrary[library.Id].orEmpty().filter {
                                    !hideShowsWithZeroEpisodes || it.Type != "Series" || (it.RecursiveItemCount ?: it.ChildCount ?: 0) > 0
                                }
                                addShelf("shows:${library.Id}", "Recently Added Shows in ${library.Name}", shows)
                            }
                            tvShowLibraries.forEach { library ->
                                addShelf("episodes:${library.Id}", "Recently Added Episodes in ${library.Name}",
                                    recentlyAddedEpisodesByLibrary[library.Id].orEmpty(), resume = true)
                            }
                        }
                    }
                    // Reserve one complete shelf plus only the next shelf's title.
                    // Equal shelf slots keep short Continue Watching cards from exposing the next row.
                    val shelfHeaderHeight = 44.dp
                    val shelfCardsHeight = 157.5.dp + HomeStyle.shelfVerticalPadding * 2
                    val shelfViewportHeight = shelfCardsHeight + shelfHeaderHeight * 2
                    val shelfDensity = androidx.compose.ui.platform.LocalDensity.current
                    val shelfVerticalSpec = remember(shelfDensity) {
                        com.flex.elefin.ui.TvBringIntoViewSpec(
                            vertical = true,
                            verticalAnchor = with(shelfDensity) {
                                (shelfHeaderHeight + HomeStyle.shelfVerticalPadding).toPx()
                            })
                    }
                    var horizontalShelfNavigation by remember { mutableStateOf(false) }
                    // Focus history is consulted only when pendingShelfFocus changes. Making
                    // every focus write observable invalidates all composed shelves.
                    val focusedCardByShelf = remember { mutableMapOf<String, String>() }
                    val shelfHorizontalSpec = remember {
                        com.flex.elefin.ui.TvBringIntoViewSpec(vertical = false,
                            horizontalPivotEnabled = { horizontalShelfNavigation })
                    }
                    var pendingShelfFocus by remember { mutableStateOf<String?>(null) }
                    var shelfNavigationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                    val shelfTransitionFocus = remember { FocusRequester() }
                    CompositionLocalProvider(
                        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides shelfVerticalSpec
                    ) {
                    LazyColumn(
                        state = homeLazyListState,
                        contentPadding = PaddingValues(bottom = shelfHeaderHeight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 54.dp, top = 36.dp, end = 38.dp)
                            .height(shelfViewportHeight)
                            .clipToBounds()
                            .onPreviewKeyEvent { event ->
                                val key = event.nativeKeyEvent
                                if (key.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                                    // Back must leave Home, not exit the card's focus group
                                    // and re-enter it at the leading card.
                                    if (key.action == android.view.KeyEvent.ACTION_UP && !key.isCanceled) {
                                        shelfNavigationJob?.cancel()
                                        pendingShelfFocus = null
                                        handleHomeBack()
                                    }
                                    true
                                } else {
                                    if (key.action == android.view.KeyEvent.ACTION_DOWN &&
                                        shelfNavigationJob?.isActive != true && pendingShelfFocus == null) {
                                        when (key.keyCode) {
                                            android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> horizontalShelfNavigation = true
                                            android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN -> horizontalShelfNavigation = false
                                        }
                                    }
                                    val directional = key.keyCode in listOf(
                                        android.view.KeyEvent.KEYCODE_DPAD_UP,
                                        android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                                        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
                                    directional && (shelfNavigationJob?.isActive == true || pendingShelfFocus != null)
                                }
                            }
                            .focusRequester(shelfTransitionFocus)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    scope.launch {
                                        androidx.compose.runtime.withFrameNanos { }
                                        // Entering from the navigation bar should land on a card.
                                        if (shelfNavigationJob?.isActive != true && pendingShelfFocus == null) {
                                            pendingShelfFocus = homeShelves.getOrNull(homeLazyListState.firstVisibleItemIndex)?.id
                                        }
                                    }
                                }
                            }
                            .focusable()
                            .then(
                                if (debugOutlinesEnabled) {
                                    Modifier.border(3.dp, Color.Yellow)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        items(items = homeShelves, key = { it.id }, contentType = { "home_shelf" }) { shelf ->
                            // Prepare the visible cards of the next shelf during idle frames.
                            val cardSlot = if (shelf.progress && !useGoogleTvCards.value) 181 else 125
                            val visibleCards = ((androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 92) / cardSlot + 1).coerceAtLeast(1)
                            val shelfState = rememberLazyListState(
                                prefetchStrategy = remember(visibleCards) {
                                    androidx.compose.foundation.lazy.LazyListPrefetchStrategy(visibleCards)
                                }
                            )
                            val entryCard by remember(shelf.id, shelf.items) {
                                derivedStateOf {
                                    if (pendingShelfFocus != shelf.id) null else {
                                        focusedCardByShelf[shelf.id]?.takeIf { id -> shelf.items.any { it.Id == id } }
                                            ?: (shelfState.layoutInfo.visibleItemsInfo.firstOrNull {
                                                it.offset >= shelfState.layoutInfo.viewportStartOffset &&
                                                    it.offset + it.size <= shelfState.layoutInfo.viewportEndOffset
                                            }?.key as? String)
                                            ?: shelf.items.getOrNull(shelfState.firstVisibleItemIndex)?.Id
                                    }
                                }
                            }
                            LaunchedEffect(pendingShelfFocus, shelf.id) {
                                if (pendingShelfFocus == shelf.id) {
                                    val index = shelf.items.indexOfFirst { it.Id == focusedCardByShelf[shelf.id] }
                                    if (index >= 0 && shelfState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                                        shelfState.scrollToItem(index)
                                    }
                                }
                            }
                            Column {
                                Text(
                                    text = shelf.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.height(shelfHeaderHeight)
                                        .padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
                                )
                                CompositionLocalProvider(
                                    androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides shelfHorizontalSpec
                                ) {
                                LazyRow(
                                    state = shelfState,
                                    contentPadding = PaddingValues(horizontal = HomeStyle.shelfInset, vertical = HomeStyle.shelfVerticalPadding),
                                    horizontalArrangement = Arrangement.spacedBy(HomeStyle.shelfGap),
                                    flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
                                    modifier = Modifier.height(shelfCardsHeight)
                                        .drawWithContent {
                                            // Keep scrolled-off cards out of the leading gutter,
                                            // while retaining space for the first card's focus outline.
                                            clipRect(left = (HomeStyle.shelfInset / 2).toPx()) {
                                                this@drawWithContent.drawContent()
                                            }
                                        }
                                        .then(if (shelf.id == homeShelves.firstOrNull()?.id) Modifier.focusRequester(focusRequester) else Modifier)
                                        .onPreviewKeyEvent { event ->
                                            val key = event.nativeKeyEvent
                                            val direction = when (key.keyCode) {
                                                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> 1
                                                android.view.KeyEvent.KEYCODE_DPAD_UP -> -1
                                                else -> 0
                                            }
                                            val current = homeShelves.indexOfFirst { it.id == shelf.id }
                                            val target = current + direction
                                            if (direction == 0 || (target < 0 && direction < 0)) {
                                                false // Let Up on the first shelf return to navigation.
                                            } else {
                                                if (key.action == android.view.KeyEvent.ACTION_DOWN &&
                                                    target in homeShelves.indices && shelfNavigationJob?.isActive != true) {
                                                    shelfNavigationJob = scope.launch {
                                                        // The next row's cards are outside the viewport. Place it
                                                        // before requesting focus, instead of searching invisible cards.
                                                        horizontalShelfNavigation = false
                                                        shelfTransitionFocus.requestFocus()
                                                        if (disableUIAnimations.value) homeLazyListState.scrollToItem(target)
                                                        else homeLazyListState.animateScrollToItem(target)
                                                        pendingShelfFocus = homeShelves[target].id
                                                    }
                                                }
                                                true
                                            }
                                        }
                                        .then(if (debugOutlinesEnabled) Modifier.border(2.dp, Color.Magenta) else Modifier)
                                ) {
                                    items(items = shelf.items, key = { it.Id }, contentType = { if (shelf.progress) "progress" else "poster" }) { item ->
                                        val onFocused: (Boolean) -> Unit = { focused ->
                                            if (focused) {
                                                focusedCardByShelf[shelf.id] = item.Id
                                                instantHighlightedItem = item
                                                originalEpisodeItem = item.takeIf { it.Type == "Episode" }
                                                backgroundChangeJob?.cancel()
                                                backgroundChangeJob = scope.launch {
                                                    delay(400)
                                                    highlightedItem = item
                                                }
                                            }
                                        }
                                        val cardEntryFocus = remember { FocusRequester() }
                                        val initialDrawerCard = drawerEnabled && !initialDrawerCardFocused &&
                                            shelf.id == homeShelves.firstOrNull()?.id && item.Id == shelf.items.firstOrNull()?.Id
                                        LaunchedEffect(initialDrawerCard) {
                                            if (initialDrawerCard) {
                                                androidx.compose.runtime.withFrameNanos { }
                                                cardEntryFocus.requestFocus()
                                                initialDrawerCardFocused = true
                                            }
                                        }
                                        val shouldEnterShelf = entryCard == item.Id
                                        LaunchedEffect(shouldEnterShelf) {
                                            if (shouldEnterShelf) {
                                                androidx.compose.runtime.withFrameNanos { }
                                                cardEntryFocus.requestFocus()
                                                pendingShelfFocus = null
                                            }
                                        }
                                        // Focus is requested by a composed card, never an off-screen requester.
                                        Box(shelfUpdateModifier(!disableUIAnimations.value && !lowPowerMode.value).focusRequester(cardEntryFocus)) {
                                        if (shelf.progress) {
                                            JellyfinHorizontalCardWithProgress(
                                                item = item, apiService = apiService,
                                                onClick = { onItemClick(item, (item.UserData?.PositionTicks ?: 0L) / 10_000) },
                                                onFocusChanged = onFocused,
                                                disableAnimations = disableUIAnimations.value,
                                                useSimpleCards = useSimpleCards.value, useGoogleTvCards = useGoogleTvCards.value,
                                                lowPowerMode = lowPowerMode.value, imageRefreshKey = imageRefreshKey
                                            )
                                        } else {
                                            JellyfinHorizontalCard(
                                                item = item, apiService = apiService,
                                                onClick = { onItemClick(item, if (shelf.resume) (item.UserData?.PositionTicks ?: 0L) / 10_000 else 0L) },
                                                onFocusChanged = onFocused, enableCaching = cacheLibraryImages,
                                                reducePosterResolution = reducePosterResolution,
                                                useSeriesPosterForEpisodes = true,
                                                unwatchedEpisodeCount = if (item.Type == "Series") item.UserData?.UnplayedItemCount else null,
                                                disableAnimations = disableUIAnimations.value,
                                                useSimpleCards = useSimpleCards.value, useGoogleTvCards = useGoogleTvCards.value,
                                                lowPowerMode = lowPowerMode.value, imageRefreshKey = imageRefreshKey
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
            }
                }
}
        }
} // TvBringIntoViewProvider
    
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
                // Check if UI animations setting changed and refresh UI if needed
                val animationsChanged = settings.disableUIAnimations != disableUIAnimationsWhenSettingsOpened
                if (animationsChanged) {
                    disableUIAnimations.value = settings.disableUIAnimations
                }
                // Check if 24-hour time setting changed
                val use24HourTimeChanged = settings.use24HourTime != use24HourTimeWhenSettingsOpened
                if (use24HourTimeChanged) {
                    use24HourTime = settings.use24HourTime
                }
                // Check if low power mode changed
                val lowPowerModeChanged = settings.lowPowerMode != lowPowerModeWhenSettingsOpened
                if (lowPowerModeChanged) {
                    lowPowerMode.value = settings.lowPowerMode
                }
                // Check if simple cards or Google TV cards settings changed and refresh UI if needed
                // Always refresh both since they're mutually exclusive (enabling one disables the other)
                val simpleCardsChanged = settings.useSimpleCards != useSimpleCardsWhenSettingsOpened
                val googleTvCardsChanged = settings.useGoogleTvCards != useGoogleTvCardsWhenSettingsOpened
                if (simpleCardsChanged || googleTvCardsChanged || lowPowerModeChanged) {
                    useSimpleCards.value = settings.useSimpleCards
                    useGoogleTvCards.value = settings.useGoogleTvCards
                }
                
                // Check if row card count changed and refresh data if needed
                val rowCardCountChanged = settings.rowCardCount != rowCardCountWhenSettingsOpened
                if (rowCardCountChanged) {
                    scope.launch {
                        repository?.fetchContinueWatching()
                        repository?.fetchNextUp()
                        repository?.fetchRecentlyAddedMovies()
                        repository?.fetchRecentlyAddedShows()
                        repository?.fetchRecentlyAddedEpisodes()
                        
                        // Also refresh library items if one is selected
                        if (selectedLibraryId != null) {
                            val library = libraries.find { it.Id == selectedLibraryId }
                            if (library != null) {
                                repository?.fetchLibraryItems(selectedLibraryId!!)
                            }
                        }
                    }
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
                            // Check if UI animations setting changed and refresh UI if needed
                            val animationsChanged = settings.disableUIAnimations != disableUIAnimationsWhenSettingsOpened
                            if (animationsChanged) {
                                disableUIAnimations.value = settings.disableUIAnimations
                            }
                            // Check if 24-hour time setting changed
                            val use24HourTimeChanged = settings.use24HourTime != use24HourTimeWhenSettingsOpened
                            if (use24HourTimeChanged) {
                                use24HourTime = settings.use24HourTime
                            }
                            // Check if low power mode changed
                            val lowPowerModeChanged = settings.lowPowerMode != lowPowerModeWhenSettingsOpened
                            if (lowPowerModeChanged) {
                                lowPowerMode.value = settings.lowPowerMode
                            }
                            // Check if simple cards or Google TV cards settings changed and refresh UI if needed
                            // Always refresh both since they're mutually exclusive (enabling one disables the other)
                            val simpleCardsChanged = settings.useSimpleCards != useSimpleCardsWhenSettingsOpened
                            val googleTvCardsChanged = settings.useGoogleTvCards != useGoogleTvCardsWhenSettingsOpened
                            if (simpleCardsChanged || googleTvCardsChanged || lowPowerModeChanged) {
                                useSimpleCards.value = settings.useSimpleCards
                                useGoogleTvCards.value = settings.useGoogleTvCards
                            }
                            
                            // Check if row card count changed and refresh data if needed
                            val rowCardCountChanged = settings.rowCardCount != rowCardCountWhenSettingsOpened
                            if (rowCardCountChanged) {
                                scope.launch {
                                    repository?.fetchContinueWatching()
                                    repository?.fetchNextUp()
                                    repository?.fetchRecentlyAddedMovies()
                                    repository?.fetchRecentlyAddedShows()
                                    repository?.fetchRecentlyAddedEpisodes()
                                    
                                    // Also refresh library items if one is selected
                                    if (selectedLibraryId != null) {
                                        val library = libraries.find { it.Id == selectedLibraryId }
                                        if (library != null) {
                                            repository?.fetchLibraryItems(selectedLibraryId!!)
                                        }
                                    }
                                }
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
                    // Create Jellyseerr API service if enabled
                    // Create Jellyseerr API service if enabled
                    val jellyseerrApiService = remember(
                        settings.jellyseerrUrl, 
                        settings.jellyseerrApiKey, 
                        settings.jellyseerrSessionCookie,
                        settings.jellyseerrAuthType,
                        settings.jellyseerrEnabled
                    ) {
                        if (settings.isJellyseerrConfigured) {
                            try {
                                if (settings.jellyseerrAuthType == "credentials" && settings.jellyseerrSessionCookie.isNotBlank()) {
                                    com.flex.elefin.jellyseerr.JellyseerrApiService.withCookie(
                                        baseUrl = settings.jellyseerrUrl,
                                        cookie = settings.jellyseerrSessionCookie
                                    )
                                } else {
                                    com.flex.elefin.jellyseerr.JellyseerrApiService.withApiKey(
                                        baseUrl = settings.jellyseerrUrl,
                                        apiKey = settings.jellyseerrApiKey
                                    )
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("JellyfinHomeScreen", "Error creating Jellyseerr API service", e)
                                null
                            }
                        } else {
                            null
                        }
                    }

                    SearchScreen(
                        apiService = apiService,
                        jellyseerrApiService = jellyseerrApiService,
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
    onSortSelected: (SortType) -> Unit,
    availableGenres: List<String> = emptyList(),
    selectedGenre: String? = null,
    onGenreSelected: ((String?) -> Unit)? = null
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
                    .fillMaxHeight() // Fill height to allow full scrolling
                    .padding(top = 20.dp, bottom = 20.dp, end = 54.dp), // Adjusted padding
                shape = RoundedCornerShape(16.dp),
                colors = androidx.tv.material3.SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Title Area
                    Text(
                        text = "Sort & Filter",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.7f
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 12.dp)
                    )

                    // Scrollable Content
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // SECTION: Sort By
                        item {
                             Text(
                                text = "Sort By",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                            )
                        }

                        item {
                            ListItem(
                                selected = currentSortType == SortType.Alphabetically,
                                onClick = { onSortSelected(SortType.Alphabetically) },
                                headlineContent = {
                                    Text(
                                        text = "Alphabetically",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                        )
                                    )
                                }
                            )
                        }
                        
                        item {
                            ListItem(
                                selected = currentSortType == SortType.DateAdded,
                                onClick = { onSortSelected(SortType.DateAdded) },
                                headlineContent = {
                                    Text(
                                        text = "Date Added",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                        )
                                    )
                                }
                            )
                        }
                        
                        item {
                            ListItem(
                                selected = currentSortType == SortType.DateReleased,
                                onClick = { onSortSelected(SortType.DateReleased) },
                                headlineContent = {
                                    Text(
                                        text = "Date Released",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                        )
                                    )
                                }
                            )
                        }

                        // SECTION: FILTER GENRES (Optional)
                        if (availableGenres.isNotEmpty() && onGenreSelected != null) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    text = "Filter Genre",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                                )
                            }
                            
                            // All Genres Option
                            item {
                                ListItem(
                                    selected = selectedGenre == null,
                                    onClick = { onGenreSelected(null) },
                                    headlineContent = {
                                        Text(
                                            text = "All Genres",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                            )
                                        )
                                    }
                                )
                            }
                            
                            // Individual Genres
                            items(availableGenres) { genre ->
                                ListItem(
                                    selected = selectedGenre == genre,
                                    onClick = { onGenreSelected(genre) },
                                    headlineContent = {
                                        Text(
                                            text = genre,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                            )
                                        )
                                    }
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
    reducePosterResolution: Boolean = false,
    disableAnimations: Boolean = false
) {
    val imageUrl = apiService?.getImageUrl(item.Id, "Primary", null,
        maxWidth = 1080, maxHeight = 1620, quality = 90) ?: ""

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
    reducePosterResolution: Boolean = false,
    disableAnimations: Boolean = false
) {
    val imageUrl = apiService?.getImageUrl(item.Id, "Primary", null,
        maxWidth = 1080, maxHeight = 1620, quality = 90) ?: ""

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
    unwatchedEpisodeCount: Int? = null,
    disableAnimations: Boolean = false,
    useSimpleCards: Boolean = false,
    useGoogleTvCards: Boolean = false,
    lowPowerMode: Boolean = false,
    imageRefreshKey: Long = 0L,
    externalImageUrl: String? = null // New parameter for external images (e.g., Jellyseerr)
) {
    // For episodes, use series poster (Primary) if requested; otherwise use poster (Primary) for movies/shows
    // Request high-resolution sources; AsyncImage decodes to the actual card dimensions.
    // Animation/card-style preferences should not reduce the source artwork quality.
    val imageUrl = remember(apiService, item.Id, item.Type, item.SeriesId, useSeriesPosterForEpisodes, imageRefreshKey, externalImageUrl) {
        if (!externalImageUrl.isNullOrBlank()) return@remember externalImageUrl
        val maxWidth = 1080
        val maxHeight = 1620
        val quality = 90

        if (item.Type == "Episode" && useSeriesPosterForEpisodes && item.SeriesId != null) {
            // Use series poster (Primary) for episodes
            apiService?.getImageUrl(item.SeriesId, "Primary", null, maxWidth = maxWidth, maxHeight = maxHeight, quality = quality) ?: ""
        } else {
            // Use poster (Primary) for movies and shows
            apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = maxWidth, maxHeight = maxHeight, quality = quality) ?: ""
        }
    }
    val context = LocalContext.current
    
    // Use Google TV style card - lightweight with subtle 10% scale animation and glow border
    if (useGoogleTvCards) {
        
        // Google TV style: use TV Card with minimal scale for proper D-pad navigation
        Card(
            onClick = onClick,
            modifier = Modifier
                .width(105.dp)
                .onFocusChanged { focusState ->
                    onFocusChanged?.invoke(focusState.isFocused)
                }.homeCardFocus(disableAnimations || lowPowerMode),
            scale = CardDefaults.scale(focusedScale = 1f),
            border = CardDefaults.border(focusedBorder = androidx.tv.material3.Border.None),

            colors = CardDefaults.colors(containerColor = Color.Transparent),
            shape = CardDefaults.shape(HomeStyle.cardShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            ) {
                if (imageUrl.isNotEmpty() && apiService != null) {
                    // Request headers are built only when the remembered request changes.
                    val imageRequest = remember(item.Id, imageUrl, apiService, enableCaching, imageRefreshKey) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .headers(apiService.getImageRequestHeaders())
                            .crossfade(false) // Disable crossfade for Google TV cards (performance)
                            .memoryCachePolicy(CachePolicy.ENABLED) // Always cache for Google TV cards
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = item.Name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                
                // Watched indicator
                val isWatched = (item.UserData?.Played == true) || (item.UserData?.PlayedPercentage == 100.0)
                if (isWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(HomeStyle.badgeBackground, HomeStyle.badgeShape)
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
                
                // Unwatched episodes badge
                if (item.Type == "Series" && !isWatched && unwatchedEpisodeCount != null && unwatchedEpisodeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(HomeStyle.badgeBackground, HomeStyle.badgeShape)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = unwatchedEpisodeCount.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    } else if (useSimpleCards) {
        // Use simple card without zoom animation for low-spec devices
        
        // Simple card using Card without StandardCardContainer (no zoom animation)
        Card(
            onClick = onClick,
            modifier = Modifier
                .width(105.dp)
                .onFocusChanged { focusState ->
                    onFocusChanged?.invoke(focusState.isFocused)
                }
                .homeCardFocus(true),
            scale = CardDefaults.scale(focusedScale = 1f),
                    border = CardDefaults.border(focusedBorder = androidx.tv.material3.Border.None),
            colors = CardDefaults.colors(containerColor = Color.Transparent),
            shape = CardDefaults.shape(HomeStyle.cardShape)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (imageUrl.isNotEmpty() && apiService != null) {
                    // Request headers are built only when the remembered request changes.
                    val imageRequest = remember(item.Id, imageUrl, apiService, enableCaching, imageRefreshKey) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .headers(apiService.getImageRequestHeaders())
                            .crossfade(false) // Disable crossfade for simple cards (performance)
                            .memoryCachePolicy(if (enableCaching) CachePolicy.ENABLED else CachePolicy.DISABLED)
                            .diskCachePolicy(if (enableCaching) CachePolicy.ENABLED else CachePolicy.DISABLED)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = item.Name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(HomeStyle.cardShape),
                        contentScale = ContentScale.Crop,
                        placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(HomeStyle.cardShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                
                // Watched indicator
                val isWatched = (item.UserData?.Played == true) || (item.UserData?.PlayedPercentage == 100.0)
                if (isWatched) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(HomeStyle.badgeBackground, HomeStyle.badgeShape)
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
                
                // Unwatched episodes badge
                if (item.Type == "Series" && !isWatched && unwatchedEpisodeCount != null && unwatchedEpisodeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(HomeStyle.badgeBackground, HomeStyle.badgeShape)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = unwatchedEpisodeCount.toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    } else {
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
                    modifier = Modifier.homeCardFocus(disableAnimations || lowPowerMode),
                    scale = CardDefaults.scale(focusedScale = 1f),
                    border = CardDefaults.border(focusedBorder = androidx.tv.material3.Border.None),
                    shape = CardDefaults.shape(HomeStyle.cardShape),
                    colors = CardDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                ) {
                    if (imageUrl.isNotEmpty() && apiService != null) {
                        // Request headers are built only when the remembered request changes.
                        // Use stable ImageRequest based on item ID to ensure proper caching
                        // Coil will cache based on the URL, but using remember ensures we don't recreate the request on recomposition
                        // imageRefreshKey forces cache invalidation when auto-refresh detects new content
                        val imageRequest = remember(item.Id, imageUrl, apiService, enableCaching, imageRefreshKey) {
                            ImageRequest.Builder(context)
                                .data(imageUrl)
                                .headers(apiService.getImageRequestHeaders())
                                .crossfade(false) // Reuse cached artwork without an additional image fade
                                .memoryCachePolicy(if (enableCaching) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                .diskCachePolicy(if (enableCaching) CachePolicy.ENABLED else CachePolicy.DISABLED)
                                .networkCachePolicy(CachePolicy.ENABLED) // Allow network caching but Coil will retry on error
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
                                placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
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
                                        .background(HomeStyle.badgeBackground, HomeStyle.badgeShape)
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
                                        .background(HomeStyle.badgeBackground, HomeStyle.badgeShape)
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
}

@Composable
fun JellyfinEpisodeCard(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    onClick: () -> Unit,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    disableAnimations: Boolean = false
) {
    // For episodes, use series backdrop if available, otherwise use episode backdrop or primary
    // When animations disabled, force reduced resolution for better performance
    val imageUrl = remember(item.SeriesId, item.Id, disableAnimations) {
        val maxWidth = if (disableAnimations) 600 else 3840
        val maxHeight = if (disableAnimations) 900 else 5760
        val quality = 90
        
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
                    apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = maxWidth, maxHeight = maxHeight, quality = quality) ?: ""
                }
            }
            } else {
                // No series ID, use episode images
                val episodeBackdrop = apiService?.getImageUrl(item.Id, "Backdrop") ?: ""
                if (episodeBackdrop.isNotEmpty()) {
                    episodeBackdrop
                } else {
                    apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = maxWidth, maxHeight = maxHeight, quality = quality) ?: ""
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
    onFocusChanged: ((Boolean) -> Unit)? = null,
    disableAnimations: Boolean = false,
    useSimpleCards: Boolean = false,
    useGoogleTvCards: Boolean = false,
    lowPowerMode: Boolean = false,
    imageRefreshKey: Long = 0L
) {
    // Full-HD landscape sources, decoded at the displayed card size by AsyncImage.
    val maxImageWidth = 1920
    val maxImageHeight = 1080
    val imageQuality = 90
    
    // A generated URL is not proof that artwork exists. Try each candidate
    // once, advancing on a failed image load rather than leaving a blank card.
    val imageCandidates = remember(item, apiService, maxImageWidth, maxImageHeight, imageQuality, imageRefreshKey) {
        buildList {
            fun candidate(id: String, type: String, tag: String? = null) {
                apiService?.getImageUrl(id, type, tag, maxWidth = maxImageWidth,
                    maxHeight = maxImageHeight, quality = imageQuality)?.let { add(it) }
            }
            if (item.Type == "Episode" && item.SeriesId != null) {
                candidate(item.SeriesId, "Thumb")
                candidate(item.Id, "Thumb", item.ImageTags?.get("Thumb"))
                candidate(item.Id, "Primary", item.ImageTags?.get("Primary"))
                candidate(item.SeriesId, "Backdrop")
                candidate(item.SeriesId, "Primary")
            } else {
                candidate(item.Id, "Thumb", item.ImageTags?.get("Thumb"))
                candidate(item.Id, "Backdrop")
                candidate(item.Id, "Primary", item.ImageTags?.get("Primary"))
            }
        }.distinct()
    }
    var imageCandidateIndex by remember(imageCandidates, imageRefreshKey) { mutableStateOf(0) }
    val imageUrl = imageCandidates.getOrNull(imageCandidateIndex).orEmpty()
    val advanceImage: (coil.compose.AsyncImagePainter.State.Error) -> Unit = {
        if (imageCandidates.getOrNull(imageCandidateIndex) == imageUrl &&
            imageCandidateIndex < imageCandidates.lastIndex) {
            imageCandidateIndex++
        }
    }

    // Calculate progress percentage
    val progress = item.UserData?.PlayedPercentage?.toFloat()?.div(100f) ?: 0f
    val context = LocalContext.current
    
    // Google TV style card - lightweight with subtle scale animation
    if (useGoogleTvCards) {
        
        // Google TV style: use TV Card with minimal scale for proper D-pad navigation
        Card(
            onClick = onClick,
            modifier = Modifier
                .width(161.dp)
                .onFocusChanged { focusState ->
                    onFocusChanged?.invoke(focusState.isFocused)
                }.homeCardFocus(disableAnimations || lowPowerMode),
            scale = CardDefaults.scale(focusedScale = 1f),
            border = CardDefaults.border(focusedBorder = androidx.tv.material3.Border.None),

            colors = CardDefaults.colors(containerColor = Color.Transparent),
            shape = CardDefaults.shape(HomeStyle.cardShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                if (imageUrl.isNotEmpty() && apiService != null) {
                    // Request headers are built only when the remembered request changes.
                    val imageRequest = remember(item.Id, imageUrl, apiService, imageRefreshKey) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .headers(apiService.getImageRequestHeaders())
                            .crossfade(false) // Disable crossfade for Google TV cards (performance)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        onError = advanceImage,
                        contentDescription = item.Name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                
                // Progress bar at the bottom
                if (progress > 0f && progress < 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .height(HomeStyle.progressHeight)
                            .align(Alignment.BottomCenter)
                            .offset(y = (-6).dp)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(HomeStyle.progressHeight)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    } else if (useSimpleCards) {
        // Simple card - flat with border highlight on focus
        
        Card(
            onClick = onClick,
            modifier = Modifier
                .width(161.dp)
                .onFocusChanged { focusState ->
                    onFocusChanged?.invoke(focusState.isFocused)
                }
                .homeCardFocus(true),
            scale = CardDefaults.scale(focusedScale = 1f),
                    border = CardDefaults.border(focusedBorder = androidx.tv.material3.Border.None),
            colors = CardDefaults.colors(containerColor = Color.Transparent),
            shape = CardDefaults.shape(HomeStyle.cardShape)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (imageUrl.isNotEmpty() && apiService != null) {
                    // Request headers are built only when the remembered request changes.
                    val imageRequest = remember(item.Id, imageUrl, apiService, imageRefreshKey) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .headers(apiService.getImageRequestHeaders())
                            .crossfade(false) // Disable crossfade for simple cards (performance)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        onError = advanceImage,
                        contentDescription = item.Name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(HomeStyle.cardShape),
                        contentScale = ContentScale.Crop,
                        placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(HomeStyle.cardShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                
                // Progress bar at the bottom
                if (progress > 0f && progress < 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .height(HomeStyle.progressHeight)
                            .align(Alignment.BottomCenter)
                            .offset(y = (-6).dp)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(HomeStyle.progressHeight)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    } else {
        // Default: Horizontal card with 16:9 aspect ratio using StandardCardContainer
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
                    modifier = Modifier.homeCardFocus(disableAnimations || lowPowerMode),
                    scale = CardDefaults.scale(focusedScale = 1f),
                    border = CardDefaults.border(focusedBorder = androidx.tv.material3.Border.None),
                    shape = CardDefaults.shape(HomeStyle.cardShape),
                    colors = CardDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                ) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .clip(HomeStyle.cardShape)) {
                        if (imageUrl.isNotEmpty() && apiService != null) {
                            // Request headers are built only when the remembered request changes.
                            val imageRequest = remember(item.Id, imageUrl, apiService, imageRefreshKey) {
                                ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .headers(apiService.getImageRequestHeaders())
                                    .crossfade(false)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            }
                            AsyncImage(
                                model = imageRequest,
                        onError = advanceImage,
                                contentDescription = item.Name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f) // 16:9 landscape aspect ratio
                                    .clip(HomeStyle.cardShape),
                                contentScale = ContentScale.Crop,
                                placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        } else {
                            // Fallback to primary image if no thumbnail/backdrop
                            val primaryUrl = apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 1080, maxHeight = 1620, quality = 90) ?: ""
                            if (primaryUrl.isNotEmpty() && apiService != null) {
                                // Request headers are built only when the remembered request changes.
                                val primaryImageRequest = remember(item.Id, primaryUrl, apiService, imageRefreshKey) {
                                    ImageRequest.Builder(context)
                                        .data(primaryUrl)
                                        .headers(apiService.getImageRequestHeaders())
                                        .crossfade(false)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .build()
                                }
                                AsyncImage(
                                    model = primaryImageRequest,
                                    contentDescription = item.Name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(HomeStyle.cardShape),
                                    contentScale = ContentScale.Crop,
                                    placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                    error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            } else {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(HomeStyle.cardShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                        }
                        
                        // Progress bar at the bottom
                        if (progress > 0f && progress < 1f) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .height(HomeStyle.progressHeight)
                                    .align(Alignment.BottomCenter)
                                    .offset(y = (-6).dp)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .height(HomeStyle.progressHeight)
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
}

fun Modifier.carouselGradient(): Modifier = composed {
    val color = MaterialTheme.colorScheme.surface

    // Stronger left-side gradient for navigation drawer readability
    // Left side is fully opaque, fading to transparent on the right (30% darker than before)
    // INCREASED INTENSITY: Changed to 1.0f start, and 0.7f mid for better readability
    val colorAlphaList = listOf(1.0f, 0.7f, 0.0f)
    val colorStopList = listOf(0.0f, 0.35f, 0.7f)

    // INCREASED INTENSITY: Changed to 1.0f start, and 0.5f mid
    val colorAlphaList2 = listOf(1.0f, 0.5f, 0.0f)
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
            .background(HomeStyle.badgeBackground, HomeStyle.badgeShape)
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



// =============================================================================
// A-Z ALPHABET INDEX BAR (Plex-style jump navigation)
// =============================================================================

/**
 * Builds a map of first letter -> row index for alphabetically sorted items.
 * Used to jump to specific letters in the library grid.
 * 
 * @param items The list of items (must be sorted alphabetically)
 * @param columns Number of columns in the grid (items per row)
 * @return Map of letter to row index
 */
private fun buildLetterIndexMap(items: List<JellyfinItem>, columns: Int = 6): Map<Char, Int> {
    val map = mutableMapOf<Char, Int>()
    
    items.forEachIndexed { index, item ->
        val name = item.Name ?: return@forEachIndexed
        val first = name.firstOrNull()?.uppercaseChar() ?: return@forEachIndexed
        
        // Only map A-Z letters, and only the first occurrence
        if (first in 'A'..'Z' && first !in map) {
            // Calculate row index (items are chunked into rows)
            val rowIndex = index / columns
            map[first] = rowIndex
        }
    }
    
    return map
}

/**
 * A-Z Alphabet Index Bar for TV navigation.
 * Displays a vertical column of letters that users can navigate with DPAD.
 * When a letter is focused, it triggers a callback to scroll to that section.
 * 
 * @param modifier Modifier for the column
 * @param letters List of letters to display (defaults to A-Z)
 * @param availableLetters Set of letters that have items (others are dimmed)
 * @param selectedLetter Currently selected/highlighted letter
 * @param onLetterFocused Callback when a letter gains focus
 * @param onLetterSelected Callback when a letter is clicked/selected
 */
@Composable
private fun AlphabetIndexBar(
    modifier: Modifier = Modifier,
    letters: List<Char> = ('A'..'Z').toList(),
    availableLetters: Set<Char> = emptySet(),
    selectedLetter: Char?,
    onLetterFocused: (Char) -> Unit,
    onLetterSelected: (Char) -> Unit
) {
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    
    // Scroll to selected letter when it changes
    LaunchedEffect(selectedLetter) {
        if (selectedLetter != null) {
            val index = letters.indexOf(selectedLetter)
            if (index >= 0) {
                lazyListState.animateScrollToItem(
                    index = maxOf(0, index - 3), // Show a few letters above
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
                    .size(28.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .then(
                        if (isFocused) {
                            Modifier.background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                RoundedCornerShape(6.dp)
                            )
                        } else if (isSelected) {
                            Modifier.background(
                                Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(6.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .focusable(interactionSource = interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        if (isAvailable) {
                            onLetterSelected(letter)
                        }
                    }
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && isAvailable) {
                            onLetterFocused(letter)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = when {
                            isFocused -> 18.sp
                            isSelected -> 16.sp
                            else -> 14.sp
                        },
                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = when {
                        isFocused -> Color.White
                        isSelected -> Color.White
                        isAvailable -> Color.White.copy(alpha = 0.7f)
                        else -> Color.White.copy(alpha = 0.25f) // Dimmed for unavailable letters
                    }
                )
            }
        }
    }
}

/**
 * Large letter overlay shown when navigating the A-Z index.
 * Provides visual feedback of the currently selected letter.
 * 
 * @param letter The letter to display (null to hide)
 * @param visible Whether the overlay should be visible
 */
@Composable
private fun LetterOverlay(
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

/**
 * Metadata & Synopsis Section for the Home Screen.
 * Leverages state-read deferral via lambdas to prevent recomposing parent container.
 */
@Composable
private fun HomeHero(
    itemProvider: () -> JellyfinItem?,
    isScrolling: () -> Boolean,
    episodeProvider: () -> JellyfinItem?,
    apiService: JellyfinApiService?,
    imageRefreshKey: Long,
    disableAnimations: Boolean
) {
    var selected by remember { mutableStateOf<Pair<JellyfinItem?, JellyfinItem?>>(null to null) }
    var details by remember { mutableStateOf<JellyfinItem?>(null) }
    val cache = remember(apiService, imageRefreshKey) { android.util.LruCache<String, JellyfinItem>(64) }
    // Observe focus without subscribing the parent screen to every remote key press.
    LaunchedEffect(apiService, imageRefreshKey) {
        snapshotFlow { Triple(itemProvider(), episodeProvider(), isScrolling()) }.collectLatest { focused ->
            if (focused.third) return@collectLatest
            val item = focused.first ?: return@collectLatest
            delay(250)
            selected = focused.first to focused.second
            details = cache.get(item.Id) ?: item
            if (apiService != null && cache.get(item.Id) == null) {
                try {
                    details = apiService.getItemDetails(item.Id).also { cache.put(item.Id, it) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Basic metadata remains available if the server cannot return details.
                }
            }
        }
    }
    MetadataSection({ selected.first }, { details }, { selected.second }, apiService, disableAnimations)
}

@Composable
private fun MetadataSection(
    itemProvider: () -> JellyfinItem?,
    detailsProvider: () -> JellyfinItem?,
    originalEpisodeItemProvider: () -> JellyfinItem?,
    apiService: JellyfinApiService?,
    disableAnimations: Boolean,
) {
    val debouncedHighlightedItem = itemProvider()
    val debouncedHighlightedItemDetails = detailsProvider()
    val debouncedOriginalEpisodeItem = originalEpisodeItemProvider()
    
    // Retain outgoing content, and never show a previous item's details under the new title.
    val metadataFrame = Triple(debouncedHighlightedItem,
        debouncedHighlightedItemDetails?.takeIf { it.Id == debouncedHighlightedItem?.Id },
        debouncedOriginalEpisodeItem)
    
    Crossfade(
        targetState = metadataFrame,
        animationSpec = tween(durationMillis = if (disableAnimations) 0 else 160),
        label = "metadata_fade"
    ) { frame ->
        // Only render if we have a valid item
        val item = frame.first
        val debouncedOriginalEpisodeItem = frame.third
        if (item != null) {
            val details = frame.second ?: item
            val runtimeText = formatRuntime(details.RunTimeTicks)
            
            // For episodes from Continue Watching, Next Up, or Recently Added Episodes, show air date instead of ProductionYear
            val yearText = if (debouncedOriginalEpisodeItem != null && debouncedOriginalEpisodeItem?.Type == "Episode") {
                // Show episode air date formatted like on season info screen
                formatDate(debouncedOriginalEpisodeItem?.PremiereDate ?: debouncedOriginalEpisodeItem?.DateCreated)
            } else {
                // For movies and series, show ProductionYear
                details.ProductionYear?.toString() ?: ""
            }
            
            val genreText = details.Genres?.take(3)?.joinToString(", ") ?: ""
            // Show episode info if:
            // 1. debouncedOriginalEpisodeItem is set and item is Series (from Recently Added Episodes where we fetch series)
            // 2. item itself is an Episode (from Continue Watching, Next Up where we keep the episode as highlighted)
            val isEpisodeHighlight = (debouncedOriginalEpisodeItem != null && item.Type == "Series") || item.Type == "Episode"
            val isSeriesItem = item.Type == "Series" && debouncedOriginalEpisodeItem == null
            
            // Get the episode to use for metadata (either debouncedOriginalEpisodeItem or the item itself if it's an episode)
            val episodeForMetadata = if (item.Type == "Episode") item else debouncedOriginalEpisodeItem
            
            // Get season and episode number for episodes
            val seasonEpisodeText = if (isEpisodeHighlight && episodeForMetadata != null && !isSeriesItem) {
                val seasonNumber = episodeForMetadata.ParentIndexNumber
                val episodeNumber = episodeForMetadata.IndexNumber
                if (seasonNumber != null && episodeNumber != null) {
                    "S${seasonNumber} E${episodeNumber}"
                } else null
            } else null
            
            Column(
                modifier = Modifier
                    .padding(start = 54.dp, top = 77.dp, end = 38.dp) // Increased by 10% (70 * 1.1 = 77)
                    .fillMaxWidth(0.72f)
                    .height(170.dp).clip(RoundedCornerShape(0.dp))
            ) {
                // Title (Series name for episodes, item name for others) or Logo
                // For episodes, we need to show the series name as the title, not the episode name
                val titleText = if (isEpisodeHighlight && episodeForMetadata != null && !isSeriesItem) {
                    // For episodes, use SeriesName as the title
                    episodeForMetadata.SeriesName ?: details.Name
                } else {
                    details.Name
                }
                
                // Create a modified item for TitleOrLogo that has the correct name
                // This ensures the title shows series name for episodes
                val titleItem = if (isEpisodeHighlight && episodeForMetadata != null && !isSeriesItem && episodeForMetadata.SeriesName != null) {
                    // Use series info for logo/title display
                    details.copy(Name = episodeForMetadata.SeriesName!!)
                } else {
                    details
                }
                
                TitleOrLogo(
                    item = titleItem,
                    apiService = apiService,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 22.sp, fontWeight = FontWeight.SemiBold
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Episode name below title (for episodes from Continue Watching, Next Up, or Recently Added Episodes)
                if (isEpisodeHighlight && episodeForMetadata != null && !isSeriesItem) {
                    Text(
                        text = episodeForMetadata.Name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f // Same size as synopsis
                        ),
                        color = HomeStyle.secondaryText,
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
                                    color = HomeStyle.secondaryText
                                )
                            }
                            if (yearText.isNotEmpty()) {
                                Text(
                                    text = yearText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                                    ),
                                    color = HomeStyle.secondaryText
                                )
                            }
                            // Don't show runtime for Series items (shows)
                            if (runtimeText.isNotEmpty() && !isSeriesItem) {
                                Text(
                                    text = runtimeText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                                    ),
                                    color = HomeStyle.secondaryText
                                )
                            }
                            if (genreText.isNotEmpty()) {
                                Text(
                                    text = genreText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                                    ),
                                    color = HomeStyle.secondaryText
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
                val synopsisText = if (isEpisodeHighlight && episodeForMetadata != null && !isSeriesItem) {
                    episodeForMetadata.Overview ?: details.Overview
                } else {
                    details.Overview
                }
                
                synopsisText?.let { synopsis ->
                    if (synopsis.isNotEmpty()) {
                        Text(
                            text = synopsis,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f,
                                lineHeight = 18.sp
                            ),
                            color = HomeStyle.secondaryText,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvHomeBackground(
    itemProvider: () -> JellyfinItem?,
    isScrolling: () -> Boolean,
    apiService: JellyfinApiService?,
    darkModeEnabled: Boolean,
    lowPowerMode: Boolean,
    use4KBackgrounds: Boolean,
    selectedLibraryId: String?,
    selectedCollectionId: String?,
    disableAnimations: Boolean
) {
    HomeBackdrop(itemProvider(), apiService, darkModeEnabled, lowPowerMode,
        use4KBackgrounds, disableAnimations, isScrolling)
}
