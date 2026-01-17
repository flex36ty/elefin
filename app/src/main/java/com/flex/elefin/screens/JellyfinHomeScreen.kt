package com.flex.elefin.screens

import android.app.Activity
import android.content.Context
import android.util.Log
import com.flex.elefin.ui.TvBringIntoViewProvider
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
import kotlinx.coroutines.withContext
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.JellyfinLibrary
import com.flex.elefin.jellyfin.JellyfinRepository
import java.text.SimpleDateFormat
import java.util.Locale
import com.flex.elefin.ui.ArtworkPalette
import com.flex.elefin.ui.PlexPaletteExtractor
import com.flex.elefin.ui.PlexBackdropGradient
import android.graphics.drawable.BitmapDrawable
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers

enum class SortType {
    Alphabetically,
    DateAdded,
    DateReleased
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun JellyfinHomeScreen(
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
    
    // Note: Unwatched episode counts are now provided directly by Jellyfin API via UserData.UnplayedItemCount
    // No need to manually track them - just use item.UserData?.UnplayedItemCount
    
    var selectedLibraryId by remember { mutableStateOf<String?>(null) }
    var selectedCollectionId by remember { mutableStateOf<String?>(null) }
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
            // Small delay to allow UI to render first and prevent ANR
            delay(150)
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
                    repository?.fetchContinueWatching()
                    repository?.fetchNextUp()
                    repository?.fetchRecentlyAddedMovies()
                    repository?.fetchRecentlyReleasedMovies()
                    repository?.fetchLibraries()
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
    
    // Auto-refresh: Periodically check for new media and refresh rows if new content is detected
    var autoRefreshEnabled by remember { mutableStateOf(settings.autoRefreshEnabled) }
    var autoRefreshIntervalMinutes by remember { mutableStateOf(settings.autoRefreshIntervalMinutes) }
    
    // Image refresh key - incremented when new content is detected to force image reload
    // This ensures Coil re-fetches images that may have failed to load initially
    var imageRefreshKey by remember { mutableStateOf(0L) }
    
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
                        // Increment image refresh key to force Coil to reload images
                        // This fixes issue where new content appears but posters don't load
                        imageRefreshKey = System.currentTimeMillis()
                        android.util.Log.d("JellyfinHomeScreen", "Auto-refresh: Image cache invalidated, forcing reload")
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
    // highlightedItem is used for background image (debounced)
    var highlightedItem by remember { mutableStateOf<JellyfinItem?>(null) }
    var highlightedItemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    // instantHighlightedItem is used for metadata text (updates immediately)
    var instantHighlightedItem by remember { mutableStateOf<JellyfinItem?>(null) }
    var instantHighlightedItemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    // Track the original episode item when highlighting a series from an episode
    var originalEpisodeItem by remember { mutableStateOf<JellyfinItem?>(null) }
    
    // Job to track debounced background image changes
    var backgroundChangeJob by remember { mutableStateOf<Job?>(null) }
    
    // Plex-style dynamic background palette
    var currentArtworkPalette by remember { mutableStateOf<ArtworkPalette?>(null) }
    
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
    
    // Fetch details for highlighted item (for background)
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
    
    // For instant metadata and synopsis: show item data immediately, fetch full details instantly
    LaunchedEffect(instantHighlightedItem?.Id, apiService) {
        // Show item data immediately for instant response
        instantHighlightedItemDetails = instantHighlightedItem
        
        // Fetch full details immediately for instant synopsis/metadata display
        instantHighlightedItem?.Id?.let { itemId ->
            if (apiService != null) {
                try {
                    val details = apiService.getItemDetails(itemId)
                    // Only update if we're still showing the same item
                    if (instantHighlightedItem?.Id == itemId) {
                        instantHighlightedItemDetails = details
                    }
                } catch (e: Exception) {
                    // Silently fail - keep using basic item info
                }
            }
        }
    }
    
    // Main content (navigation drawer removed due to performance issues - using tab bar instead)
    // Wrap with TV-optimized bring-into-view behavior for better focus handling
    TvBringIntoViewProvider {
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
                // Low power mode uses 720p, normal mode uses 1080p, 4K mode uses 2160p
                val bgMaxWidth = if (lowPowerMode.value) 1280 else if (settings.use4KBackgrounds) 3840 else 1920
                val bgMaxHeight = if (lowPowerMode.value) 720 else if (settings.use4KBackgrounds) 2160 else 1080
                val bgQuality = if (lowPowerMode.value) 75 else if (settings.use4KBackgrounds) 95 else 90
                
                val backdropUrl = apiService?.getImageUrl(itemId, "Backdrop", null, maxWidth = bgMaxWidth, maxHeight = bgMaxHeight, quality = bgQuality) ?: ""
                if (backdropUrl.isNotEmpty()) {
                    backdropUrl
                } else {
                    // Fall back to primary image if no backdrop
                    apiService?.getImageUrl(itemId, "Primary", null, maxWidth = bgMaxWidth, maxHeight = bgMaxHeight, quality = bgQuality) ?: ""
                }
            } ?: ""
            
            // Extract palette from the current image URL
            LaunchedEffect(imageUrl) {
                if (imageUrl.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val loader = coil.ImageLoader(context)
                            val request = ImageRequest.Builder(context)
                                .data(imageUrl)
                                .allowHardware(false) // Required for Palette
                                .build()
                            
                            val result = loader.execute(request)
                            if (result is SuccessResult) {
                                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                                if (bitmap != null) {
                                    val palette = PlexPaletteExtractor.extract(context, bitmap)
                                    withContext(Dispatchers.Main) {
                                        currentArtworkPalette = palette
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("JellyfinHomeScreen", "Error extracting palette", e)
                        }
                    }
                } else {
                    currentArtworkPalette = null
                }
            }
            
            // Use Crossfade for smooth fade in/out animation
            // In dark mode, don't show background image - use Material dark background instead
            if (!darkModeEnabled) {
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
                                .crossfade(300) // Smooth 300ms crossfade when loading
                                .allowHardware(true) // Use GPU memory for faster rendering
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
                // Dark mode: use Material dark background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
            
            // Dark overlay and scrim - different opacity based on view mode
            // Skip overlay in dark mode since we're using a dark background
            if ((selectedLibraryId == null && selectedCollectionId == null) && !darkModeEnabled) {
                // Default home view: 20% darkness + gradient scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.1f))
                )
                
                // Scrim gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .carouselGradient()
                )
            } else if (!darkModeEnabled) {
                // Library or Collections view: 20% darkness + gradient scrim (same as home)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.1f))
                )
                
                // Scrim gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .carouselGradient()
                )
            }
            
            // Apply Plex-style dynamic gradient overlay
            if (currentArtworkPalette != null && !darkModeEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PlexBackdropGradient(currentArtworkPalette!!))
                )
            }
        }
        
        // Top row with home button and library buttons - positioned absolutely on top of carousel
        Box(
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
                    onClick = {
                        darkModeWhenSettingsOpened = settings.darkModeEnabled
                        debugOutlinesWhenSettingsOpened = settings.showDebugOutlines
                        disableUIAnimationsWhenSettingsOpened = settings.disableUIAnimations
                        lowPowerModeWhenSettingsOpened = settings.lowPowerMode
                        useSimpleCardsWhenSettingsOpened = settings.useSimpleCards
                        useGoogleTvCardsWhenSettingsOpened = settings.useGoogleTvCards
                        use24HourTimeWhenSettingsOpened = settings.use24HourTime
                        rowCardCountWhenSettingsOpened = settings.rowCardCount
                        showSettings = true
                    },
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
                    onClick = {
                        if (isLibrarySelected) {
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
                    },
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
                val allTabs = remember(libraries, collections) {
                    // Debug: Log all libraries and their CollectionType
                    libraries.forEach { lib ->
                        android.util.Log.d("JellyfinHomeScreen", "📚 Library loaded: ${lib.Name}, Type: ${lib.Type}, CollectionType: ${lib.CollectionType}, Id: ${lib.Id}")
                    }
                    
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
                                        android.util.Log.d("JellyfinHomeScreen", "🎵 Music library clicked! Navigating to music screen...")
                                        onMusicClick()
                                        return@Tab
                                    }
                                    
                                    // Handle movies library specially - navigate to movies library screen
                                    if (isMoviesLibrary && library != null) {
                                        android.util.Log.d("JellyfinHomeScreen", "🎬 Movies library clicked! Navigating to movies library screen...")
                                        onMoviesLibraryClick(library.Id, library.Name)
                                        return@Tab
                                    }
                                    
                                    // Handle TV shows library specially - navigate to TV shows library screen
                                    if (isTvShowsLibrary && library != null) {
                                        android.util.Log.d("JellyfinHomeScreen", "📺 TV Shows library clicked! Navigating to TV shows library screen...")
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
                                val scaledFontSize = MaterialTheme.typography.labelLarge.fontSize * 0.90f // Increased by 10% from 0.82f
                                // Add horizontal padding (Reduced by 30% from current enlarged state: 1.2 * 0.7 = 0.84)
                                val horizontalPadding = 16.dp * 0.84f
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
        
        // Item details section - below settings button (only show when not viewing a library)
        // Show highlighted item panel for home screen and collections
        // Use instantHighlightedItem for immediate metadata updates
        // Use Crossfade to animate all metadata together when item changes
        if (selectedLibraryId == null) {
            // Create a stable key for the current item to trigger crossfade
            val metadataKey = instantHighlightedItem?.Id ?: ""
            
            Crossfade(
                targetState = metadataKey,
                animationSpec = tween(durationMillis = 200),
                label = "metadata_fade"
            ) { currentKey ->
                // Only render if we have a valid item
                val item = instantHighlightedItem
                if (item != null && currentKey == item.Id) {
                    val details = instantHighlightedItemDetails ?: item
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
                    // Show episode info if:
                    // 1. originalEpisodeItem is set and item is Series (from Recently Added Episodes where we fetch series)
                    // 2. item itself is an Episode (from Continue Watching, Next Up where we keep the episode as highlighted)
                    val isEpisodeHighlight = (originalEpisodeItem != null && item.Type == "Series") || item.Type == "Episode"
                    val isSeriesItem = item.Type == "Series" && originalEpisodeItem == null
                    
                    // Get the episode to use for metadata (either originalEpisodeItem or the item itself if it's an episode)
                    val episodeForMetadata = if (item.Type == "Episode") item else originalEpisodeItem
                    
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
                            .fillMaxWidth(0.75f) // Increased by 50%: 0.5 * 1.5 = 0.75 (50% wider horizontally)
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
                                fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f // Reduced by 20% (0.8 * 0.8 = 0.64)
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
                val lazyListState = rememberLazyListState()
                
                // Preload images for items that are about to come into view
                LaunchedEffect(items, apiService, selectedLibraryId, preloadLibraryImages, cacheLibraryImages, reducePosterResolution) {
                    if (preloadLibraryImages && apiService != null && items.isNotEmpty()) {
                        // Preload images for the first 6 rows (36 items) - more aggressive preloading
                        val preloadCount = minOf(36, items.size) // First 6 rows (6 columns * 6 rows)
                        
                        items.take(preloadCount).forEach { item ->
                            // Use reduced resolution (300x450) or standard resolution (400x600) based on setting
                            val imageUrl = if (reducePosterResolution) {
                                apiService.getImageUrl(item.Id, "Primary", null, maxWidth = 300, maxHeight = 450, quality = 80)
                            } else {
                                apiService.getImageUrl(item.Id, "Primary", null, maxWidth = 400, maxHeight = 600, quality = 85)
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
                                // Use reduced resolution (300x450) or standard resolution (400x600) based on setting
                                val imageUrl = if (reducePosterResolution) {
                                    apiService.getImageUrl(item.Id, "Primary", null, maxWidth = 300, maxHeight = 450, quality = 80)
                                } else {
                                    apiService.getImageUrl(item.Id, "Primary", null, maxWidth = 400, maxHeight = 600, quality = 85)
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
                            lazyListState.animateScrollToItem(targetRow)
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
                                    state = lazyListState,
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
                                                                    delay(1000)
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
                    // All collection rows in a single item for smoother vertical scrolling
                    // (same pattern as library screens)
                    item {
                        Column(
                            modifier = Modifier
                                .padding(top = 24.dp)
                                .focusRequester(focusRequester)
                        ) {
                            // Iterate through each collection and create a row for it
                            collections.forEachIndexed { index, collection ->
                                val items = collectionItems[collection.Id] ?: emptyList()
                                
                                // Only show collections that have items
                                if (items.isNotEmpty()) {
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
                                        horizontalArrangement = Arrangement.spacedBy(20.dp),
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
                                                            delay(1000)
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
                        // All rows in a single item for smoother vertical scrolling
                        // (same pattern as library screens)
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
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f * 1.2f * 1.3f)),
                                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                                    flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
                                    modifier = if (debugOutlinesEnabled) {
                                        Modifier.border(2.dp, Color.Magenta)
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    items(
                                        items = continueWatchingItems,
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
                                                    originalEpisodeItem = if (item.Type == "Episode") item else null
                                                    backgroundChangeJob?.cancel()
                                                    backgroundChangeJob = scope.launch {
                                                        delay(1000)
                                                        if (item.Type == "Episode" && item.SeriesId != null) {
                                                            val seriesDetails = apiService?.getItemDetails(item.SeriesId)
                                                            if (seriesDetails != null) {
                                                                highlightedItem = seriesDetails
                                                            } else {
                                                                highlightedItem = item
                                                            }
                                                        } else {
                                                            highlightedItem = item
                                                        }
                                                    }
                                                }
                                            },
                                            useSimpleCards = useSimpleCards.value,
                                            useGoogleTvCards = useGoogleTvCards.value,
                                            lowPowerMode = lowPowerMode.value,
                                            imageRefreshKey = imageRefreshKey
                                        )
                                    }
                                }
                                
                                // Next Up row
                                Text(
                                    text = "Next Up",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp)
                                )
                                
                                LazyRow(
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f * 1.2f * 1.3f)),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
                                    modifier = if (debugOutlinesEnabled) {
                                        Modifier.border(2.dp, Color.Magenta)
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    items(
                                        items = nextUpItems,
                                        key = { it.Id },
                                        contentType = { "horizontal_card_progress" }
                                    ) { item ->
                                        JellyfinHorizontalCardWithProgress(
                                            item = item,
                                            apiService = apiService,
                                            onClick = {
                                                onItemClick(item, 0L)
                                            },
                                            onFocusChanged = { isFocused ->
                                                if (isFocused) {
                                                    instantHighlightedItem = item
                                                    originalEpisodeItem = if (item.Type == "Episode") item else null
                                                    backgroundChangeJob?.cancel()
                                                    backgroundChangeJob = scope.launch {
                                                        delay(1000)
                                                        if (item.Type == "Episode" && item.SeriesId != null) {
                                                            val seriesDetails = apiService?.getItemDetails(item.SeriesId)
                                                            if (seriesDetails != null) {
                                                                highlightedItem = seriesDetails
                                                            } else {
                                                                highlightedItem = item
                                                            }
                                                        } else {
                                                            highlightedItem = item
                                                        }
                                                    }
                                                }
                                            },
                                            useSimpleCards = useSimpleCards.value,
                                            useGoogleTvCards = useGoogleTvCards.value,
                                            lowPowerMode = lowPowerMode.value,
                                            imageRefreshKey = imageRefreshKey
                                        )
                                    }
                                }
                                
                                // Recently Added Movies rows - one per movie library
                                movieLibraries.forEachIndexed { index, library ->
                                    val libraryMovies = recentlyAddedMoviesByLibrary[library.Id] ?: emptyList()
                                    if (libraryMovies.isNotEmpty()) {
                                        val rowTitle = if (index == 0) "Recently Added Movies" else "Recently Added ${library.Name}"
                                        
                                        Text(
                                            text = rowTitle,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                            ),
                                            modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp)
                                        )
                                        
                                        LazyRow(
                                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f * 1.2f * 1.3f)),
                                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                                            flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
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
                                                    onClick = { onItemClick(item, 0L) },
                                                    onFocusChanged = { isFocused ->
                                                        if (isFocused) {
                                                            instantHighlightedItem = item
                                                            originalEpisodeItem = null
                                                            backgroundChangeJob?.cancel()
                                                            backgroundChangeJob = scope.launch {
                                                                delay(1000)
                                                                highlightedItem = item
                                                            }
                                                        }
                                                    },
                                                    enableCaching = cacheLibraryImages,
                                                    reducePosterResolution = reducePosterResolution,
                                                    useSimpleCards = useSimpleCards.value,
                                                    useGoogleTvCards = useGoogleTvCards.value,
                                                    lowPowerMode = lowPowerMode.value,
                                                    imageRefreshKey = imageRefreshKey
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
                                    modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp)
                                )
                                
                                LazyRow(
                                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f * 1.2f * 1.3f)),
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
                                            onClick = { onItemClick(item, 0L) },
                                            onFocusChanged = { isFocused ->
                                                if (isFocused) {
                                                    instantHighlightedItem = item
                                                    originalEpisodeItem = null
                                                    backgroundChangeJob?.cancel()
                                                    backgroundChangeJob = scope.launch {
                                                        delay(1000)
                                                        highlightedItem = item
                                                    }
                                                }
                                            },
                                            enableCaching = cacheLibraryImages,
                                            reducePosterResolution = reducePosterResolution,
                                            useSimpleCards = useSimpleCards.value,
                                            useGoogleTvCards = useGoogleTvCards.value,
                                            lowPowerMode = lowPowerMode.value,
                                            imageRefreshKey = imageRefreshKey
                                        )
                                    }
                                }
                                
                                // Recently Added Shows rows - one per TV show library
                                tvShowLibraries.forEachIndexed { libraryIndex, library ->
                                    val libraryShows = recentlyAddedShowsByLibrary[library.Id]?.let { shows ->
                                        if (hideShowsWithZeroEpisodes) {
                                            shows.filter { item ->
                                                if (item.Type != "Series") {
                                                    true
                                                } else {
                                                    val episodeCount = item.RecursiveItemCount ?: item.ChildCount ?: 0
                                                    episodeCount > 0
                                                }
                                            }
                                        } else {
                                            shows
                                        }
                                    } ?: emptyList()
                                    
                                    if (libraryShows.isNotEmpty()) {
                                        val rowTitle = if (libraryIndex == 0) "Recently Added Shows" else "Recently Added Shows in ${library.Name}"
                                        
                                        Text(
                                            text = rowTitle,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                            ),
                                            modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp)
                                        )
                                        
                                        LazyRow(
                                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f * 1.2f * 1.3f)),
                                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                                            flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
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
                                                    onClick = { onItemClick(item, 0L) },
                                                    onFocusChanged = { isFocused ->
                                                        if (isFocused) {
                                                            instantHighlightedItem = item
                                                            originalEpisodeItem = null
                                                            backgroundChangeJob?.cancel()
                                                            backgroundChangeJob = scope.launch {
                                                                delay(1000)
                                                                highlightedItem = item
                                                            }
                                                        }
                                                    },
                                                    enableCaching = cacheLibraryImages,
                                                    reducePosterResolution = reducePosterResolution,
                                                    unwatchedEpisodeCount = if (item.Type == "Series") item.UserData?.UnplayedItemCount else null,
                                                    useSimpleCards = useSimpleCards.value,
                                                    useGoogleTvCards = useGoogleTvCards.value,
                                                    lowPowerMode = lowPowerMode.value,
                                                    imageRefreshKey = imageRefreshKey
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                // Recently Added Episodes rows - one per TV show library
                                tvShowLibraries.forEachIndexed { libraryIndex, library ->
                                    val libraryEpisodes = recentlyAddedEpisodesByLibrary[library.Id] ?: emptyList()
                                    
                                    if (libraryEpisodes.isNotEmpty()) {
                                        val rowTitle = if (libraryIndex == 0) "Recently Added Episodes" else "Recently Added Episodes in ${library.Name}"
                                        
                                        Text(
                                            text = rowTitle,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                            ),
                                            modifier = Modifier.padding(bottom = 12.dp, top = 30.36.dp)
                                        )
                                        
                                        LazyRow(
                                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f * 1.4f * 1.3f)),
                                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                                            flingBehavior = if (disableUIAnimations.value) noFlingBehavior else ScrollableDefaults.flingBehavior(),
                                            modifier = if (debugOutlinesEnabled) {
                                                Modifier.border(2.dp, Color.Magenta)
                                            } else {
                                                Modifier
                                            }
                                        ) {
                                            items(libraryEpisodes) { item ->
                                                JellyfinHorizontalCard(
                                                    item = item,
                                                    apiService = apiService,
                                                    onClick = {
                                                        val resumePositionMs = item.UserData?.PositionTicks?.let { it / 10_000 } ?: 0L
                                                        onItemClick(item, resumePositionMs)
                                                    },
                                                    onFocusChanged = { isFocused ->
                                                        if (isFocused) {
                                                            instantHighlightedItem = item
                                                            originalEpisodeItem = if (item.Type == "Episode") item else null
                                                            backgroundChangeJob?.cancel()
                                                            backgroundChangeJob = scope.launch {
                                                                delay(1000)
                                                                if (item.Type == "Episode" && item.SeriesId != null) {
                                                                    val seriesDetails = apiService?.getItemDetails(item.SeriesId)
                                                                    if (seriesDetails != null) {
                                                                        highlightedItem = seriesDetails
                                                                    } else {
                                                                        highlightedItem = item
                                                                    }
                                                                } else {
                                                                    highlightedItem = item
                                                                }
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
    // Use reduced resolution (300x450) or standard resolution (400x600) based on setting
    // When animations disabled, force reduced resolution for better performance
    val effectiveReduceResolution = reducePosterResolution || disableAnimations
    val imageUrl = if (effectiveReduceResolution) {
        apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 300, maxHeight = 450, quality = 80) ?: ""
    } else {
        apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 400, maxHeight = 600, quality = 85) ?: ""
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
    reducePosterResolution: Boolean = false,
    disableAnimations: Boolean = false
) {
    // Use reduced resolution (300x450) or standard resolution (400x600) based on setting
    val imageUrl = if (reducePosterResolution) {
        apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 300, maxHeight = 450, quality = 80) ?: ""
    } else {
        apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 400, maxHeight = 600, quality = 85) ?: ""
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
    unwatchedEpisodeCount: Int? = null,
    disableAnimations: Boolean = false,
    useSimpleCards: Boolean = false,
    useGoogleTvCards: Boolean = false,
    lowPowerMode: Boolean = false,
    imageRefreshKey: Long = 0L,
    externalImageUrl: String? = null // New parameter for external images (e.g., Jellyseerr)
) {
    // For episodes, use series poster (Primary) if requested; otherwise use poster (Primary) for movies/shows
    // When animations disabled, simple cards, or Google TV cards enabled, force reduced resolution for better performance
    // Low power mode uses even smaller images (300x450) for budget devices
    val effectiveReduceResolution = reducePosterResolution || disableAnimations || useSimpleCards || useGoogleTvCards
    val imageUrl = remember(item.Id, item.Type, item.SeriesId, useSeriesPosterForEpisodes, effectiveReduceResolution, lowPowerMode, imageRefreshKey, externalImageUrl) {
        if (!externalImageUrl.isNullOrBlank()) {
            return@remember externalImageUrl
        }
        
        // Low power mode: 300x450, Reduced: 300x450, Standard: 400x600
        val maxWidth = if (lowPowerMode) 300 else if (effectiveReduceResolution) 300 else 400
        val maxHeight = if (lowPowerMode) 450 else if (effectiveReduceResolution) 450 else 600
        val quality = if (lowPowerMode) 80 else 85
        
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
        var isFocused by remember { mutableStateOf(false) }
        
        // Google TV style: use TV Card with minimal scale for proper D-pad navigation
        Card(
            onClick = onClick,
            modifier = Modifier
                .width(105.dp)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    onFocusChanged?.invoke(focusState.isFocused)
                }
                .graphicsLayer {
                    // Subtle 10% scale on focus (Google TV uses 1.08-1.12x)
                    scaleX = if (isFocused) 1.10f else 1.0f
                    scaleY = if (isFocused) 1.10f else 1.0f
                    // Subtle shadow on focus
                    shadowElevation = if (isFocused) 16f else 0f
                }
                .then(
                    if (isFocused) {
                        // Google TV glow border
                        Modifier.border(
                            width = 3.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        Modifier
                    }
                ),
            scale = CardDefaults.scale(focusedScale = 1.0f), // Disable default scale (we use graphicsLayer)
            colors = CardDefaults.colors(containerColor = Color.Transparent),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            ) {
                if (imageUrl.isNotEmpty() && apiService != null) {
                    val headerMap = apiService.getImageRequestHeaders()
                    val imageRequest = remember(item.Id, imageUrl, enableCaching, imageRefreshKey) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .headers(headerMap)
                            .size(300)
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
                
                // Unwatched episodes badge
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
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    } else if (useSimpleCards) {
        // Use simple card without zoom animation for low-spec devices
        var isFocused by remember { mutableStateOf(false) }
        
        // Simple card using Card without StandardCardContainer (no zoom animation)
        Card(
            onClick = onClick,
            modifier = Modifier
                .width(105.dp)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    onFocusChanged?.invoke(focusState.isFocused)
                }
                .then(
                    if (isFocused) {
                        Modifier.border(3.dp, Color.White, RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    }
                ),
            colors = CardDefaults.colors(containerColor = Color.Transparent),
            shape = CardDefaults.shape(RoundedCornerShape(8.dp))
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (imageUrl.isNotEmpty() && apiService != null) {
                    val headerMap = apiService.getImageRequestHeaders()
                    val imageRequest = remember(item.Id, imageUrl, enableCaching, imageRefreshKey) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .headers(headerMap)
                            .size(300)
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
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                        placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
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
                
                // Unwatched episodes badge
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
                    colors = CardDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                ) {
                    if (imageUrl.isNotEmpty() && apiService != null) {
                        val headerMap = apiService.getImageRequestHeaders()
                        // Use stable ImageRequest based on item ID to ensure proper caching
                        // Coil will cache based on the URL, but using remember ensures we don't recreate the request on recomposition
                        // imageRefreshKey forces cache invalidation when auto-refresh detects new content
                        val imageRequest = remember(item.Id, imageUrl, enableCaching, imageRefreshKey) {
                            ImageRequest.Builder(context)
                                .data(imageUrl)
                                .headers(headerMap)
                                .size(300) // Hint to Coil about target size for optimization
                                .crossfade(true) // Smooth fade-in when image loads
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
    // Use reduced resolution for simple/Google TV cards for better performance
    // Low power mode uses even smaller images (320x180) for budget devices
    val useReducedResolution = useSimpleCards || useGoogleTvCards
    val maxImageWidth = if (lowPowerMode) 320 else if (useReducedResolution) 640 else 1920
    val maxImageHeight = if (lowPowerMode) 180 else if (useReducedResolution) 360 else 1080
    val imageQuality = if (lowPowerMode) 80 else 90
    
    // Use thumbnail (Thumb) images for both episodes and movies
    // For episodes, prioritize series/parent thumb image (like official Jellyfin Android TV app)
    // For movies, use item's own thumb if available
    val imageUrl = when {
        // For episodes, try series thumb first (official app's preferParentThumb behavior)
        item.Type == "Episode" && item.SeriesId != null -> {
            // Try to get series thumb image (most common for episodes)
            val seriesThumb = apiService?.getImageUrl(item.SeriesId, "Thumb", null, maxWidth = maxImageWidth, maxHeight = maxImageHeight, quality = imageQuality) ?: ""
            if (seriesThumb.isNotEmpty()) {
                seriesThumb
            } else {
                // Fall back to episode's own thumb
                val episodeThumb = item.ImageTags?.get("Thumb")?.let { thumbTag ->
                    apiService?.getImageUrl(item.Id, "Thumb", thumbTag, maxWidth = maxImageWidth, maxHeight = maxImageHeight, quality = imageQuality) ?: ""
                } ?: ""
                if (episodeThumb.isNotEmpty()) {
                    episodeThumb
                } else {
                    // Last resort: series backdrop, then episode backdrop, then primary
                    val seriesBackdrop = apiService?.getImageUrl(item.SeriesId, "Backdrop", null, maxWidth = maxImageWidth, maxHeight = maxImageHeight, quality = imageQuality) ?: ""
                    seriesBackdrop.ifEmpty {
                        val episodeBackdrop = apiService?.getImageUrl(item.Id, "Backdrop", null, maxWidth = maxImageWidth, maxHeight = maxImageHeight, quality = imageQuality) ?: ""
                        episodeBackdrop.ifEmpty {
                            apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = maxImageWidth, maxHeight = maxImageHeight, quality = imageQuality) ?: ""
                        }
                    }
                }
            }
        }
        // For movies or other items, use item's own thumb if available
        item.ImageTags?.containsKey("Thumb") == true -> {
            item.ImageTags?.get("Thumb")?.let { thumbTag ->
                apiService?.getImageUrl(item.Id, "Thumb", thumbTag, maxWidth = maxImageWidth, maxHeight = maxImageHeight, quality = imageQuality) ?: ""
            } ?: ""
        }
        // Fallback for movies: backdrop, then primary
        else -> {
            val backdrop = apiService?.getImageUrl(item.Id, "Backdrop", null, maxWidth = maxImageWidth, maxHeight = maxImageHeight, quality = imageQuality) ?: ""
            backdrop.ifEmpty {
                apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = maxImageWidth, maxHeight = maxImageHeight, quality = imageQuality) ?: ""
            }
        }
    }
    
    // Calculate progress percentage
    val progress = item.UserData?.PlayedPercentage?.toFloat()?.div(100f) ?: 0f
    val context = LocalContext.current
    
    // Google TV style card - lightweight with subtle scale animation
    if (useGoogleTvCards) {
        var isFocused by remember { mutableStateOf(false) }
        
        // Google TV style: use TV Card with minimal scale for proper D-pad navigation
        Card(
            onClick = onClick,
            modifier = Modifier
                .width(161.dp)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    onFocusChanged?.invoke(focusState.isFocused)
                }
                .graphicsLayer {
                    scaleX = if (isFocused) 1.10f else 1.0f
                    scaleY = if (isFocused) 1.10f else 1.0f
                    shadowElevation = if (isFocused) 16f else 0f
                }
                .then(
                    if (isFocused) {
                        Modifier.border(
                            width = 3.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        Modifier
                    }
                ),
            scale = CardDefaults.scale(focusedScale = 1.0f), // Disable default scale (we use graphicsLayer)
            colors = CardDefaults.colors(containerColor = Color.Transparent),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                if (imageUrl.isNotEmpty() && apiService != null) {
                    val headerMap = apiService.getImageRequestHeaders()
                    val imageRequest = remember(item.Id, imageUrl, imageRefreshKey) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .headers(headerMap)
                            .crossfade(false) // Disable crossfade for Google TV cards (performance)
                            .memoryCachePolicy(CachePolicy.ENABLED)
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
                
                // Progress bar at the bottom
                if (progress > 0f && progress < 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    } else if (useSimpleCards) {
        // Simple card - flat with border highlight on focus
        var isFocused by remember { mutableStateOf(false) }
        
        Card(
            onClick = onClick,
            modifier = Modifier
                .width(161.dp)
                .onFocusChanged { focusState ->
                    isFocused = focusState.isFocused
                    onFocusChanged?.invoke(focusState.isFocused)
                }
                .then(
                    if (isFocused) {
                        Modifier.border(3.dp, Color.White, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    }
                ),
            colors = CardDefaults.colors(containerColor = Color.Transparent),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp))
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (imageUrl.isNotEmpty() && apiService != null) {
                    val headerMap = apiService.getImageRequestHeaders()
                    val imageRequest = remember(item.Id, imageUrl, imageRefreshKey) {
                        ImageRequest.Builder(context)
                            .data(imageUrl)
                            .headers(headerMap)
                            .crossfade(false) // Disable crossfade for simple cards (performance)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = item.Name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                        placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                
                // Progress bar at the bottom
                if (progress > 0f && progress < 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
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
                    colors = CardDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                ) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))) {
                        if (imageUrl.isNotEmpty() && apiService != null) {
                            val headerMap = apiService.getImageRequestHeaders()
                            val imageRequest = remember(item.Id, imageUrl, imageRefreshKey) {
                                ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .headers(headerMap)
                                    .crossfade(true)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            }
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = item.Name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f) // 16:9 landscape aspect ratio
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                                placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        } else {
                            // Fallback to primary image if no thumbnail/backdrop
                            val primaryUrl = apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = 400, maxHeight = 600, quality = 85) ?: ""
                            if (primaryUrl.isNotEmpty() && apiService != null) {
                                val headerMap = apiService.getImageRequestHeaders()
                                val primaryImageRequest = remember(item.Id, primaryUrl, imageRefreshKey) {
                                    ImageRequest.Builder(context)
                                        .data(primaryUrl)
                                        .headers(headerMap)
                                        .crossfade(true)
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
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                    placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                    error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
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
