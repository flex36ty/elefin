package com.flex.elefin.screens

import android.util.Log
import com.flex.elefin.ui.TvBringIntoViewProvider
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flex.elefin.components.DigitalClock
import com.flex.elefin.components.DiscoverViewMoreCard
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
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Border
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.JellyfinRepository
import com.flex.elefin.jellyseerr.JellyseerrApiService
import com.flex.elefin.jellyseerr.JellyseerrMovie
import com.flex.elefin.jellyseerr.JellyseerrImageUrl
import com.flex.elefin.jellyseerr.JellyseerrGenres
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import com.flex.elefin.ui.ArtworkPalette
import com.flex.elefin.ui.PlexPaletteExtractor
import com.flex.elefin.ui.PlexBackdropGradient
import android.graphics.drawable.BitmapDrawable
import coil.request.SuccessResult
import coil.annotation.ExperimentalCoilApi

/**
 * Movies Library Screen - A dedicated screen for the Movies library
 * that is a 1:1 copy of the home screen layout but focused only on movie content.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun MoviesLibraryScreen(
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
    
    // 24-hour time format setting - read from settings
    var use24HourTime by remember { mutableStateOf(settings.use24HourTime) }
    
    // Dialog states
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var darkModeWhenSettingsOpened by remember { mutableStateOf(false) }
    var debugOutlinesWhenSettingsOpened by remember { mutableStateOf(false) }
    var disableUIAnimationsWhenSettingsOpened by remember { mutableStateOf(false) }
    var lowPowerModeWhenSettingsOpened by remember { mutableStateOf(false) }
    var useSimpleCardsWhenSettingsOpened by remember { mutableStateOf(false) }
    var useGoogleTvCardsWhenSettingsOpened by remember { mutableStateOf(false) }
    var use24HourTimeWhenSettingsOpened by remember { mutableStateOf(false) }
    
    // Tab state: "recommendations", "library", or "discover"
    var selectedTab by remember { mutableStateOf("recommendations") }
    
    // Jellyseerr API Service for discover content
    val jellyseerrApiService = remember(
        settings.jellyseerrUrl, 
        settings.jellyseerrAuthType, 
        settings.jellyseerrApiKey, 
        settings.jellyseerrSessionCookie
    ) {
        if (settings.jellyseerrUrl.isBlank()) return@remember null
        
        when (settings.jellyseerrAuthType) {
            "api_key" -> {
                if (settings.jellyseerrApiKey.isNotBlank()) {
                    JellyseerrApiService.withApiKey(settings.jellyseerrUrl, settings.jellyseerrApiKey)
                } else null
            }
            "credentials" -> {
                if (settings.jellyseerrSessionCookie.isNotBlank()) {
                    JellyseerrApiService.withCookie(settings.jellyseerrUrl, settings.jellyseerrSessionCookie)
                } else null
            }
            else -> null
        }
    }
    
    // Jellyseerr Discover data states (each category in its own row)
    var discoverMoviesByCategory by remember { mutableStateOf<Map<String, List<JellyseerrMovie>>>(emptyMap()) }
    var isDiscoverLoading by remember { mutableStateOf(false) }
    var discoverHighlightedMovie by remember { mutableStateOf<JellyseerrMovie?>(null) }
    var instantDiscoverHighlightedMovie by remember { mutableStateOf<JellyseerrMovie?>(null) }
    var discoverMoviePages by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    
    // Movie request screen state
    var movieToRequest by remember { mutableStateOf<JellyseerrMovie?>(null) }
    
    // Refresh state
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Sort state for library grid
    var sortType by remember { mutableStateOf(SortType.Alphabetically) }
    var selectedGenreFilter by remember { mutableStateOf<String?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }
    
    // Data states for recommendations
    var continueWatchingMovies by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var recentlyReleasedMovies by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var recentlyAddedMovies by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var topUnwatchedMovies by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var recentlyWatchedMovies by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var favoriteMovies by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    
    // Genre-based movie rows
    var genreMovies1 by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var genreMovies2 by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var selectedGenre1 by remember { mutableStateOf("") }
    var selectedGenre2 by remember { mutableStateOf("") }
    var availableGenres by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Common movie genres to prefer
    val movieGenres = listOf("Action", "Comedy", "Drama", "Horror", "Thriller", "Sci-Fi", "Romance", "Adventure", "Animation", "Documentary", "Crime", "Fantasy", "Mystery", "Family")
    
    // Data states for library grid
    var libraryItems by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    
    var isLoading by remember { mutableStateOf(true) }
    
    // Highlighted item for background (same as home screen)
    var highlightedItem by remember { mutableStateOf<JellyfinItem?>(null) }
    var instantHighlightedItem by remember { mutableStateOf<JellyfinItem?>(null) }
    var instantHighlightedItemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var backgroundChangeJob by remember { mutableStateOf<Job?>(null) }
    
    // Plex-style dynamic background palette
    var currentArtworkPalette by remember { mutableStateOf<ArtworkPalette?>(null) }
    
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
    
    // Fetch recommendations data - using library-specific API methods
    // This ensures "Movies" and "Movies 4K" (and other movie libraries) are treated separately
    LaunchedEffect(apiService, libraryId) {
        if (apiService != null && libraryId.isNotEmpty()) {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    Log.d("MoviesLibraryScreen", "Fetching movies for library: $libraryName (ID: $libraryId)")
                    
                    // First fetch available genres from this library
                    val genres = apiService.getMovieGenresFromLibrary(libraryId)
                    availableGenres = genres
                    
                    // Pick 2 random unique genres from available genres
                    val shuffledGenres = if (genres.isNotEmpty()) {
                        genres.filter { it in movieGenres }.shuffled().take(2).ifEmpty { 
                            genres.shuffled().take(2)
                        }
                    } else {
                        movieGenres.shuffled().take(2)
                    }
                    
                    val genre1 = shuffledGenres.getOrNull(0) ?: movieGenres.random()
                    val genre2 = shuffledGenres.getOrNull(1) ?: movieGenres.random()
                    
                    selectedGenre1 = genre1
                    selectedGenre2 = genre2
                    
                    // Fetch all movie data in parallel using coroutineScope
                    // Using library-specific methods with ParentId filter
                    coroutineScope {
                        val continueWatchingDeferred = async { apiService.getContinueWatchingMoviesFromLibrary(libraryId, 20) }
                        val recentlyReleasedDeferred = async { apiService.getRecentlyReleasedMoviesFromLibrary(libraryId, 20) }
                        val recentlyAddedDeferred = async { apiService.getRecentlyAddedMoviesFromLibrary(libraryId, 20) }
                        val topUnwatchedDeferred = async { apiService.getTopUnwatchedMoviesFromLibrary(libraryId, 20) }
                        val recentlyWatchedDeferred = async { apiService.getRecentlyWatchedMoviesFromLibrary(libraryId, 20) }
                        val favoritesDeferred = async { apiService.getFavoriteMoviesFromLibrary(libraryId, 20) }
                        val genre1Deferred = async { apiService.getMoviesByGenreFromLibrary(libraryId, genre1, 20) }
                        val genre2Deferred = async { apiService.getMoviesByGenreFromLibrary(libraryId, genre2, 20) }
                        val libraryDeferred = async { apiService.getAllLibraryItems(libraryId) }
                        
                        continueWatchingMovies = continueWatchingDeferred.await()
                        recentlyReleasedMovies = recentlyReleasedDeferred.await()
                        recentlyAddedMovies = recentlyAddedDeferred.await()
                        topUnwatchedMovies = topUnwatchedDeferred.await()
                        recentlyWatchedMovies = recentlyWatchedDeferred.await()
                        favoriteMovies = favoritesDeferred.await()
                        genreMovies1 = genre1Deferred.await()
                        genreMovies2 = genre2Deferred.await()
                        libraryItems = libraryDeferred.await()
                    }
                    
                    Log.d("MoviesLibraryScreen", "Loaded movies for '$libraryName': " +
                        "continueWatching=${continueWatchingMovies.size}, " +
                        "recentlyReleased=${recentlyReleasedMovies.size}, " +
                        "recentlyAdded=${recentlyAddedMovies.size}, " +
                        "topUnwatched=${topUnwatchedMovies.size}, " +
                        "recentlyWatched=${recentlyWatchedMovies.size}, " +
                        "favorites=${favoriteMovies.size}, " +
                        "genre1($selectedGenre1)=${genreMovies1.size}, " +
                        "genre2($selectedGenre2)=${genreMovies2.size}, " +
                        "library=${libraryItems.size}")
                    
                    // Set initial highlighted item
                    val firstItem = continueWatchingMovies.firstOrNull() 
                        ?: recentlyReleasedMovies.firstOrNull()
                        ?: recentlyAddedMovies.firstOrNull()
                    if (firstItem != null) {
                        highlightedItem = firstItem
                        instantHighlightedItem = firstItem
                    }
                } catch (e: Exception) {
                    Log.e("MoviesLibraryScreen", "Error loading movies for library $libraryId", e)
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
                    Log.e("MoviesLibraryScreen", "Error fetching item details", e)
                }
            }
        }
    }
    
    // Fetch Jellyseerr discover movies when tab is selected
    LaunchedEffect(selectedTab, jellyseerrApiService) {
        if (selectedTab == "discover" && jellyseerrApiService != null && discoverMoviesByCategory.isEmpty()) {
            isDiscoverLoading = true
            withContext(Dispatchers.IO) {
                try {
                    Log.d("MoviesLibraryScreen", "Fetching Jellyseerr discover movies...")
                    val discoverData = jellyseerrApiService.getAllMovieCategories()
                    discoverMoviesByCategory = discoverData
                    
                    // Set initial highlighted discover movie
                    val firstMovie = discoverData["🔥 Trending"]?.firstOrNull()
                        ?: discoverData["Popular"]?.firstOrNull()
                    if (firstMovie != null) {
                        discoverHighlightedMovie = firstMovie
                        instantDiscoverHighlightedMovie = firstMovie
                    }
                    
                    Log.d("MoviesLibraryScreen", "Loaded ${discoverData.size} discover movie categories")
                } catch (e: Exception) {
                    Log.e("MoviesLibraryScreen", "Error loading Jellyseerr discover movies", e)
                }
            }
            isDiscoverLoading = false
        }
    }
    
    // Function to load more discover movies
    val loadMoreDiscoverMovies: (String) -> Unit = { categoryName ->
        if (jellyseerrApiService != null) {
            scope.launch {
                val currentPage = discoverMoviePages[categoryName] ?: 1
                val nextPage = currentPage + 1
                val newMovies = when {
                    categoryName.startsWith("🔥") -> {
                        val trending = jellyseerrApiService.getTrending(nextPage)
                        trending.filter { it.mediaType == "movie" }.map { item ->
                            JellyseerrMovie(
                                id = item.id,
                                mediaType = "movie",
                                popularity = item.popularity,
                                posterPath = item.posterPath,
                                backdropPath = item.backdropPath,
                                voteCount = item.voteCount,
                                voteAverage = item.voteAverage,
                                genreIds = item.genreIds,
                                overview = item.overview,
                                originalLanguage = item.originalLanguage,
                                title = item.title,
                                originalTitle = item.originalTitle,
                                releaseDate = item.releaseDate,
                                adult = item.adult,
                                video = item.video,
                                mediaInfo = item.mediaInfo
                            )
                        }
                    }
                    categoryName == "Popular" -> jellyseerrApiService.getPopularMovies(nextPage)
                    categoryName == "Upcoming" -> jellyseerrApiService.getUpcomingMovies(nextPage)
                    else -> emptyList()
                }
                
                if (newMovies.isNotEmpty()) {
                    val currentMovies = discoverMoviesByCategory[categoryName] ?: emptyList()
                    // Filter out any duplicates
                    val currentIds = currentMovies.map { it.id }.toSet()
                    val filteredNewMovies = newMovies.filter { it.id !in currentIds }
                    
                    if (filteredNewMovies.isNotEmpty()) {
                        discoverMoviesByCategory = discoverMoviesByCategory + (categoryName to (currentMovies + filteredNewMovies))
                        discoverMoviePages = discoverMoviePages + (categoryName to nextPage)
                        Log.d("MoviesLibraryScreen", "Loaded ${filteredNewMovies.size} more $categoryName movies (page $nextPage)")
                    }
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
    
    // Sort and Filter library items
    val sortedLibraryItems = remember(libraryItems, sortType, selectedGenreFilter) {
        val filteredItems = if (selectedGenreFilter != null) {
            libraryItems.filter { it.Genres?.contains(selectedGenreFilter) == true }
        } else {
            libraryItems
        }

        when (sortType) {
            SortType.Alphabetically -> filteredItems.sortedBy { it.Name?.lowercase() }
            SortType.DateAdded -> {
                filteredItems.sortedByDescending { 
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
                filteredItems.sortedByDescending { 
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
    // Wrap with TV-optimized bring-into-view behavior for better focus handling
    TvBringIntoViewProvider {
    Box(Modifier.fillMaxSize()) {
        // Featured carousel with backdrop - extends behind bottom container
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Get image URL for current highlighted item - use backdrop photo
            // For discover tab, use TMDB backdrop (via Jellyseerr); for recommendations, use Jellyfin backdrop
            val imageUrl = if (selectedTab == "discover") {
                discoverHighlightedMovie?.backdropPath?.let { JellyseerrImageUrl.backdrop(it) } ?: ""
            } else {
                highlightedItem?.let { item ->
                    // Low power mode uses 720p, normal mode uses 1080p, 4K mode uses 2160p
                    val bgMaxWidth = if (lowPowerMode.value) 1280 else if (settings.use4KBackgrounds) 3840 else 1920
                    val bgMaxHeight = if (lowPowerMode.value) 720 else if (settings.use4KBackgrounds) 2160 else 1080
                    val bgQuality = if (lowPowerMode.value) 75 else if (settings.use4KBackgrounds) 95 else 90
                    
                    val backdropUrl = apiService?.getImageUrl(item.Id, "Backdrop", null, maxWidth = bgMaxWidth, maxHeight = bgMaxHeight, quality = bgQuality) ?: ""
                    if (backdropUrl.isNotEmpty()) {
                        backdropUrl
                    } else {
                        // Fall back to primary image if no backdrop
                        apiService?.getImageUrl(item.Id, "Primary", null, maxWidth = bgMaxWidth, maxHeight = bgMaxHeight, quality = bgQuality) ?: ""
                    }
                } ?: ""
            }
            
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
                            Log.e("MoviesLibraryScreen", "Error extracting palette", e)
                        }
                    }
                } else {
                    currentArtworkPalette = null
                }
            }
            
            // Use Crossfade for smooth fade in/out animation (same as home screen)
            if (!darkModeEnabled && (selectedTab == "recommendations" || selectedTab == "discover")) {
                Crossfade(
                    targetState = imageUrl,
                    animationSpec = tween(durationMillis = 500),
                    label = "background_fade"
                ) { currentUrl ->
                    if (currentUrl.isNotEmpty()) {
                        // For recommendations, use Jellyfin headers; for discover, no headers needed
                        val headerMap = if (selectedTab == "recommendations" && apiService != null) {
                            apiService.getImageRequestHeaders()
                        } else {
                            okhttp3.Headers.Builder().build()
                        }
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(currentUrl)
                                .headers(headerMap)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(300)
                                .allowHardware(true)
                                .build(),
                            contentDescription = if (selectedTab == "discover") discoverHighlightedMovie?.title ?: "" else highlightedItem?.Name ?: "",
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
            if (!darkModeEnabled && (selectedTab == "recommendations" || selectedTab == "discover")) {
                // Default view: 5% darkness (reduced from 10% for vibrancy)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.05f))
                )
                
                // Scrim gradient overlay - only show if NOT using dynamic Plex background
                if (currentArtworkPalette == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .carouselGradient()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            }
            
            // Apply Plex-style dynamic gradient overlay
            if (currentArtworkPalette != null && !darkModeEnabled && (selectedTab == "recommendations" || selectedTab == "discover")) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PlexBackdropGradient(currentArtworkPalette!!))
                )
            }
        }
        
        // Top row with buttons and tabs (same as home screen)
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
                    // Settings button
                    IconButton(
                        onClick = {
                            darkModeWhenSettingsOpened = settings.darkModeEnabled
                            debugOutlinesWhenSettingsOpened = settings.showDebugOutlines
                            disableUIAnimationsWhenSettingsOpened = settings.disableUIAnimations
                            lowPowerModeWhenSettingsOpened = settings.lowPowerMode
                            useSimpleCardsWhenSettingsOpened = settings.useSimpleCards
                            useGoogleTvCardsWhenSettingsOpened = settings.useGoogleTvCards
                            use24HourTimeWhenSettingsOpened = settings.use24HourTime
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
                    
                    // Search button
                    IconButton(
                        onClick = { showSearch = true },
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
                                            
                                            // Pick 2 new random unique genres
                                            val shuffledGenres = if (availableGenres.isNotEmpty()) {
                                                availableGenres.filter { it in movieGenres }.shuffled().take(2).ifEmpty { 
                                                    availableGenres.shuffled().take(2)
                                                }
                                            } else {
                                                movieGenres.shuffled().take(2)
                                            }
                                            
                                            val genre1 = shuffledGenres.getOrNull(0) ?: movieGenres.random()
                                            val genre2 = shuffledGenres.getOrNull(1) ?: movieGenres.random()
                                            
                                            selectedGenre1 = genre1
                                            selectedGenre2 = genre2
                                            
                                            // Refresh data - using library-specific methods
                                            withContext(Dispatchers.IO) {
                                                coroutineScope {
                                                    val continueWatchingDeferred = async { apiService.getContinueWatchingMoviesFromLibrary(libraryId, 20) }
                                                    val recentlyReleasedDeferred = async { apiService.getRecentlyReleasedMoviesFromLibrary(libraryId, 20) }
                                                    val recentlyAddedDeferred = async { apiService.getRecentlyAddedMoviesFromLibrary(libraryId, 20) }
                                                    val topUnwatchedDeferred = async { apiService.getTopUnwatchedMoviesFromLibrary(libraryId, 20) }
                                                    val recentlyWatchedDeferred = async { apiService.getRecentlyWatchedMoviesFromLibrary(libraryId, 20) }
                                                    val favoritesDeferred = async { apiService.getFavoriteMoviesFromLibrary(libraryId, 20) }
                                                    val genre1Deferred = async { apiService.getMoviesByGenreFromLibrary(libraryId, genre1, 20) }
                                                    val genre2Deferred = async { apiService.getMoviesByGenreFromLibrary(libraryId, genre2, 20) }
                                                    val libraryDeferred = async { apiService.getAllLibraryItems(libraryId) }
                                                    
                                                    continueWatchingMovies = continueWatchingDeferred.await()
                                                    recentlyReleasedMovies = recentlyReleasedDeferred.await()
                                                    recentlyAddedMovies = recentlyAddedDeferred.await()
                                                    topUnwatchedMovies = topUnwatchedDeferred.await()
                                                    recentlyWatchedMovies = recentlyWatchedDeferred.await()
                                                    favoriteMovies = favoritesDeferred.await()
                                                    genreMovies1 = genre1Deferred.await()
                                                    genreMovies2 = genre2Deferred.await()
                                                    libraryItems = libraryDeferred.await()
                                                }
                                            }
                                            
                                            Log.d("MoviesLibraryScreen", "Manual refresh completed")
                                        } catch (e: Exception) {
                                            Log.e("MoviesLibraryScreen", "Manual refresh error", e)
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
                            .padding(end = 14.dp) // Reduced from 20 to 14
                            .size(34.dp) // Same size as settings button (34dp)
                    ) {
                        if (isLibraryTab) {
                            // Show sort icon when library tab is selected
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Sort",
                                modifier = Modifier.size(14.dp) // Reduced from 20dp to 14dp
                            )
                        } else {
                            // Show refresh icon on recommendations tab
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
                    
                    // Home button - navigates back to home screen
                    var homeFocused by remember { mutableStateOf(false) }
                    
                    TabRow(
                        modifier = Modifier.padding(end = 14.dp), // Reduced from 20 to 14
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
                                    .padding(horizontal = 8.dp, vertical = 4.dp) // Reduced from (12, 6) to (8, 4)
                                    .size(20.dp) // Reduced from 28dp to 20dp
                            )
                        }
                    }
                    
                    // Recommendations, Library, and Discover tabs
                    val hasJellyseerr = settings.isJellyseerrConfigured
                    val tabs = buildList {
                        add("Recommendations" to "recommendations")
                        add("$libraryName Library" to "library")
                        if (hasJellyseerr) {
                            add("Discover" to "discover")
                        }
                    }
                    val selectedTabIndex = tabs.indexOfFirst { it.second == selectedTab }.takeIf { it >= 0 } ?: 0
                    
                    TabRow(
                        modifier = Modifier.fillMaxWidth(),
                        selectedTabIndex = selectedTabIndex,
                        separator = { Spacer(modifier = Modifier.width(11.dp)) }, // Reduced from 16dp to 11dp (30% smaller)
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
                                val scaledFontSize = MaterialTheme.typography.labelLarge.fontSize * 0.90f // Increased by 10% from 0.82f
                                val horizontalPadding = 16.dp * 0.84f // Reduced by 30% from 1.2f
                                Text(
                                    text = tabName,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = scaledFontSize
                                    ),
                                    color = if (isFocused) Color.Black else Color.White,
                                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 4.dp) // Reduced vertical from 6 to 4
                                )
                            }
                        }
                    }
                }
                
                // Digital clock on the far right
                DigitalClock(use24HourFormat = use24HourTime)
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
                    
                    Column(
                        modifier = Modifier
                            .padding(start = 54.dp, top = 77.dp, end = 38.dp)
                            .fillMaxWidth(0.75f)
                    ) {
                        // Title (same styling as home screen)
                        TitleOrLogo(
                            item = details,
                            apiService = apiService,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
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
                            
                            // MetadataBox components (same as home screen with Rotten Tomatoes support)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val audioStream = details.MediaSources?.firstOrNull()?.MediaStreams?.firstOrNull { it.Type == "Audio" }
                                
                                // Maturity Rating
                                details.OfficialRating?.let { rating ->
                                    MovieMetadataBox(text = rating)
                                }
                                
                                // Review Rating with Rotten Tomatoes icons support (same as home screen)
                                MovieRatingDisplay(
                                    item = details,
                                    communityRating = details.CommunityRating,
                                    criticRating = details.CriticRating
                                )
                                
                                // Language
                                audioStream?.Language?.let { lang ->
                                    MovieMetadataBox(text = lang.uppercase())
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
                                .padding(top = 24.dp) // Increased to ensure "Continue Watching" title is visible
                                .focusRequester(focusRequester)
                        ) {
                            // Continue Watching row - using vertical poster cards
                            if (continueWatchingMovies.isNotEmpty()) {
                                Text(
                                    text = "Continue Watching",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                    ),
                                    modifier = Modifier.padding(bottom = 12.dp, top = 12.dp)
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
                                    items(continueWatchingMovies) { item ->
                                        // Use vertical poster card for movies
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
                            
                            // Recently Released row (same styling as home screen)
                            if (recentlyReleasedMovies.isNotEmpty()) {
                                Text(
                                    text = "Recently Released in $libraryName",
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
                                    items(recentlyReleasedMovies) { item ->
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
                            
                            // Recently Added row (same styling as home screen)
                            if (recentlyAddedMovies.isNotEmpty()) {
                                Text(
                                    text = "Recently Added in $libraryName",
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
                                    items(recentlyAddedMovies) { item ->
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
                            
                            // Top Unwatched row (same styling as home screen)
                            if (topUnwatchedMovies.isNotEmpty()) {
                                Text(
                                    text = "Top Unwatched in $libraryName",
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
                                    items(topUnwatchedMovies) { item ->
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
                            
                            // Recently Watched row (same styling as home screen)
                            if (recentlyWatchedMovies.isNotEmpty()) {
                                Text(
                                    text = "Recently Watched in $libraryName",
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
                                    items(recentlyWatchedMovies) { item ->
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
                            
                            // Favorites row (same styling as home screen)
                            if (favoriteMovies.isNotEmpty()) {
                                Text(
                                    text = "Favorites in $libraryName",
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
                                    items(favoriteMovies) { item ->
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
                            
                            // Top Movies in Genre 1 row
                            if (genreMovies1.isNotEmpty() && selectedGenre1.isNotEmpty()) {
                                Text(
                                    text = "Top Movies in $selectedGenre1",
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
                                    items(genreMovies1) { item ->
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
                            
                            // Top Movies in Genre 2 row
                            if (genreMovies2.isNotEmpty() && selectedGenre2.isNotEmpty()) {
                                Text(
                                    text = "Top Movies in $selectedGenre2",
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
                                    items(genreMovies2) { item ->
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
        } else if (selectedTab == "library") {
            // Library grid view (same as home screen library view)
            val columns = 6
            val lazyListState = rememberLazyListState()
            
            // A-Z Index state - only show when sorted alphabetically AND not in low power mode
            val showAlphabetIndex = sortType == SortType.Alphabetically && !lowPowerMode.value
            val letterIndexMap = remember(sortedLibraryItems, columns) {
                if (showAlphabetIndex) buildMovieLetterIndexMap(sortedLibraryItems, columns) else emptyMap()
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
                                MovieAlphabetIndexBar(
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
                        MovieLetterOverlay(
                            letter = selectedLetter,
                            visible = showLetterOverlay
                        )
                    }
                }
            }
        } else if (selectedTab == "discover") {
            // Discover tab content - Jellyseerr trending/popular/upcoming movies
            if (jellyseerrApiService == null) {
                // No Jellyseerr configured
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Jellyseerr Not Configured",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Add your Jellyseerr URL and API key in Settings to discover movies",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            } else if (isDiscoverLoading) {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading discover movies...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            } else {
                // Discover content with rows (same layout as recommendations)
                // Background is already handled at the top level
                
                // Discover movie details section with Crossfade animation (like recommendations)
                val discoverMetadataKey = instantDiscoverHighlightedMovie?.id?.toString() ?: ""
                
                Crossfade(
                    targetState = discoverMetadataKey,
                    animationSpec = tween(durationMillis = 200),
                    label = "discover_metadata_fade"
                ) { currentKey ->
                    val discoverMovie = instantDiscoverHighlightedMovie
                    if (discoverMovie != null && currentKey == discoverMovie.id.toString()) {
                        Column(
                            modifier = Modifier
                                .padding(start = 54.dp, top = 77.dp, end = 38.dp)
                                .fillMaxWidth(0.75f)
                        ) {
                            // Title
                            Text(
                                text = discoverMovie.title ?: "Unknown",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Metadata row
                            Row(
                                modifier = Modifier.padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Rating
                                discoverMovie.voteAverage?.let { rating ->
                                    Text(
                                        text = "★ ${String.format("%.1f", rating)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFFFD700)
                                    )
                                }
                                
                                // Release year
                                discoverMovie.releaseDate?.take(4)?.let { year ->
                                    Text(
                                        text = year,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                                
                                // Genre (from genre IDs)
                                val genreNames = discoverMovie.genreIds.take(2).mapNotNull { 
                                    JellyseerrGenres.MOVIE_GENRES[it] 
                                }
                                if (genreNames.isNotEmpty()) {
                                    Text(
                                        text = genreNames.joinToString(", "),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                                
                                // Availability badge
                                if (discoverMovie.mediaInfo?.isAvailable == true) {
                                    Text(
                                        text = "In Library",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier
                                            .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            
                            // Overview/Synopsis
                            discoverMovie.overview?.let { overview ->
                                if (overview.isNotEmpty()) {
                                    Text(
                                        text = overview,
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
                
                // Bottom container with discover rows
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
                    // Spacer to push content down (40% for details)
                    Spacer(modifier = Modifier.weight(0.4f))
                    
                    // Content rows (60% for rows)
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
                                    .padding(top = 24.dp)
                                    .focusRequester(focusRequester)
                            ) {
                                // Sort categories: "🔥 Trending" first, then "Popular", then "Upcoming"
                                val sortedCategories = discoverMoviesByCategory.keys.sortedWith(
                                    compareBy<String> { 
                                        when {
                                            it.startsWith("🔥") -> 0
                                            it == "Popular" -> 1
                                            it == "Upcoming" -> 2
                                            else -> 3
                                        }
                                    }
                                )
                                
                                sortedCategories.forEachIndexed { index, categoryName ->
                                    val movies = discoverMoviesByCategory[categoryName] ?: return@forEachIndexed
                                    if (movies.isEmpty()) return@forEachIndexed
                                    
                                    MovieCategoryRow(
                                        categoryName = categoryName,
                                        movies = movies,
                                        titleTopPadding = if (index == 0) 12.dp else 30.36.dp,
                                        onViewMoreClick = { loadMoreDiscoverMovies(categoryName) },
                                        onMovieClick = { movie ->
                                            // Search for this movie in Jellyfin library and navigate to details
                                            scope.launch {
                                                Log.d("MoviesLibraryScreen", "Searching for movie: ${movie.title} (ID: ${movie.id})")
                                                
                                                // Check if Jellyseerr knows it's in the library
                                                val jellyfinId = movie.mediaInfo?.jellyfinId
                                                if (jellyfinId != null) {
                                                    // Directly fetch from Jellyfin using the known ID
                                                    val jellyfinItem = apiService?.getItemDetails(jellyfinId)
                                                    if (jellyfinItem != null) {
                                                        Log.d("MoviesLibraryScreen", "Found movie in library via Jellyseerr: ${jellyfinItem.Name}")
                                                        onItemClick(jellyfinItem, 0L)
                                                        return@launch
                                                    }
                                                }
                                                
                                                // Try to find by TMDB ID
                                                var jellyfinItem = apiService?.findItemByTmdbId(movie.id, "Movie")
                                                
                                                // Fall back to title search if TMDB ID not found
                                                if (jellyfinItem == null) {
                                                    val year = movie.releaseDate?.take(4)
                                                    jellyfinItem = apiService?.findItemByTitle(movie.title ?: "", year, "Movie")
                                                }
                                                
                                                if (jellyfinItem != null) {
                                                    Log.d("MoviesLibraryScreen", "Found movie in library: ${jellyfinItem.Name} (ID: ${jellyfinItem.Id})")
                                                    onItemClick(jellyfinItem, 0L)
                                                } else {
                                                    Log.d("MoviesLibraryScreen", "Movie not found in library, showing request screen: ${movie.title}")
                                                    // Show movie request screen
                                                    movieToRequest = movie
                                                }
                                            }
                                        },
                                        onMovieFocused = { movie ->
                                            instantDiscoverHighlightedMovie = movie
                                            backgroundChangeJob?.cancel()
                                            backgroundChangeJob = scope.launch {
                                                delay(500)
                                                discoverHighlightedMovie = movie
                                            }
                                        },
                                        disableUIAnimations = disableUIAnimations.value,
                                        debugOutlinesEnabled = debugOutlinesEnabled,
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
                    // Check if 24-hour time setting changed
                    val use24HourTimeChanged = settings.use24HourTime != use24HourTimeWhenSettingsOpened
                    if (use24HourTimeChanged) {
                        use24HourTime = settings.use24HourTime
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
                                // Check if 24-hour time setting changed
                                val use24HourTimeChanged = settings.use24HourTime != use24HourTimeWhenSettingsOpened
                                if (use24HourTimeChanged) {
                                    use24HourTime = settings.use24HourTime
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
    } // TvBringIntoViewProvider
        
        // Sort dialog
        if (showSortDialog) {
            SortDialog(
                currentSortType = sortType,
                onSortSelected = { newSortType ->
                    sortType = newSortType
                    showSortDialog = false
                },
                onDismiss = { showSortDialog = false },
                availableGenres = availableGenres,
                selectedGenre = selectedGenreFilter,
                onGenreSelected = { genre ->
                    selectedGenreFilter = genre
                    showSortDialog = false
                }
            )
        }
        
        // Movie Request Screen Overlay - Use Dialog for absolute focus isolation
        movieToRequest?.let { movie ->
            Dialog(
                onDismissRequest = { movieToRequest = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    MovieRequestScreen(
                        movie = movie,
                        jellyseerrApiService = jellyseerrApiService,
                        onBackPressed = {
                            movieToRequest = null
                        }
                    )
                }
            }
        }
    }
}

/**
 * Simple metadata box for displaying movie metadata (same as home screen MetadataBox)
 */
@Composable
private fun MovieMetadataBox(text: String) {
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

// Rating display with Rotten Tomatoes icon support - matching home screen
@Composable
private fun MovieRatingDisplay(
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
    val criticRatingType: RatingType? = if (criticRating != null) {
        determineMovieRatingType(item.ProviderIds, null, criticRating, preferCommunity = false)
    } else {
        null
    }
    
    // Determine community rating type and display if available (as audience rating)
    val communityRatingType: RatingType? = if (communityRating != null) {
        determineMovieRatingType(item.ProviderIds, communityRating, null, preferCommunity = true)
    } else {
        null
    }
    
    // Show critic rating (RT Fresh/Rotten or generic)
    if (criticRating != null) {
        val percentage = calculatePercentage(criticRating)
        when (criticRatingType) {
            RatingType.RottenTomatoesFresh -> {
                MovieRatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = com.flex.elefin.R.drawable.ic_rt_fresh,
                    label = "RT"
                )
            }
            RatingType.RottenTomatoesRotten -> {
                MovieRatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = com.flex.elefin.R.drawable.ic_rt_rotten,
                    label = "RT"
                )
            }
            RatingType.IMDb -> {
                MovieRatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = com.flex.elefin.R.drawable.ic_imdb,
                    label = "IMDb"
                )
            }
            else -> {
                MovieMetadataBox(text = "${percentage}%")
            }
        }
    }
    
    // Show audience rating (RT Popcorn or generic) if available and different from critic
    if (communityRating != null && (criticRating == null || communityRating != criticRating)) {
        val percentage = calculatePercentage(communityRating)
        when (communityRatingType) {
            RatingType.RottenTomatoesAudience -> {
                MovieRatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = com.flex.elefin.R.drawable.ic_rt_popcorn,
                    label = "RT"
                )
            }
            RatingType.IMDb -> {
                // Only show IMDb if we didn't already show it for critic
                if (criticRatingType != RatingType.IMDb) {
                    MovieRatingBoxWithIcon(
                        percentage = percentage,
                        iconRes = com.flex.elefin.R.drawable.ic_imdb,
                        label = "IMDb"
                    )
                }
            }
            else -> {
                // Show generic community rating only if we didn't show critic rating
                if (criticRating == null) {
                    MovieMetadataBox(text = "${percentage}%")
                }
            }
        }
    }
}

// Determine rating type based on provider IDs - matching home screen
private fun determineMovieRatingType(
    providerIds: Map<String, String>?,
    communityRating: Float?,
    criticRating: Float?,
    preferCommunity: Boolean = false
): RatingType {
    val hasRottenTomatoes = providerIds?.containsKey("TmdbId") == true || 
                           providerIds?.containsKey("Imdb") == true
    val hasIMDb = providerIds?.containsKey("Imdb") == true
    
    // Determine which rating to display based on what's available
    if (preferCommunity && communityRating != null) {
        // For community/audience ratings, prefer RT Popcorn if RT is available
        if (hasRottenTomatoes) {
            return RatingType.RottenTomatoesAudience
        }
        if (hasIMDb) {
            return RatingType.IMDb
        }
    } else if (!preferCommunity && criticRating != null) {
        // For critic ratings, use RT Fresh/Rotten based on percentage
        if (hasRottenTomatoes) {
            val percentage = if (criticRating > 10) criticRating.toInt() else (criticRating * 10).toInt()
            return if (percentage >= 60) {
                RatingType.RottenTomatoesFresh
            } else {
                RatingType.RottenTomatoesRotten
            }
        }
        if (hasIMDb) {
            return RatingType.IMDb
        }
    }
    
    return RatingType.Generic
}

// Rating box with icon - matching home screen
@Composable
private fun MovieRatingBoxWithIcon(
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
 */
private fun buildMovieLetterIndexMap(items: List<JellyfinItem>, columns: Int = 6): Map<Char, Int> {
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
private fun MovieAlphabetIndexBar(
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
private fun MovieLetterOverlay(
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
 * Jellyseerr Movie Card - displays a movie from Jellyseerr discover data
 * Styled to match JellyfinHorizontalCard for visual consistency
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun JellyseerrMovieCard(
    movie: JellyseerrMovie,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    useSimpleCards: Boolean = false,
    useGoogleTvCards: Boolean = false,
    lowPowerMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Width matching JellyfinHorizontalCard
    val cardWidth = 105.dp
    
    val posterUrl = JellyseerrImageUrl.poster(movie.posterPath, "w342")
    
    Card(
        onClick = onClick,
        modifier = modifier
            .width(cardWidth)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                onFocusChanged(focusState.isFocused)
            },
        scale = CardDefaults.scale(focusedScale = 1.1f),
        colors = CardDefaults.colors(
            containerColor = Color.DarkGray.copy(alpha = 0.3f),
            focusedContainerColor = Color.DarkGray.copy(alpha = 0.5f)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(8.dp)),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                shape = RoundedCornerShape(8.dp)
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            // Poster image
            if (posterUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(posterUrl)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Placeholder when no poster
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (movie.title ?: "?").take(1),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
            
            // Rating badge in top-right corner
            movie.voteAverage?.let { rating ->
                if (rating > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "★ ${String.format("%.1f", rating)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFD700)
                        )
                    }
                }
            }
            
            // Availability badge in bottom-left corner (shows if in Jellyfin library)
            if (movie.mediaInfo?.isAvailable == true) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color(0xFF4CAF50), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * A row of movies for a specific category with focus memory.
 * Remembers the last focused item index and restores it when focus enters the row.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MovieCategoryRow(
    categoryName: String,
    movies: List<JellyseerrMovie>,
    onMovieClick: (JellyseerrMovie) -> Unit,
    onMovieFocused: (JellyseerrMovie) -> Unit,
    onViewMoreClick: () -> Unit,
    titleTopPadding: androidx.compose.ui.unit.Dp = 12.dp,
    disableUIAnimations: Boolean,
    debugOutlinesEnabled: Boolean,
    useSimpleCards: Boolean,
    useGoogleTvCards: Boolean,
    lowPowerMode: Boolean,
    modifier: Modifier = Modifier
) {
    // Remember the last focused item index for this specific category
    var lastFocusedIndex by androidx.compose.runtime.saveable.rememberSaveable(categoryName) { 
        androidx.compose.runtime.mutableIntStateOf(0) 
    }
    
    // Map of FocusRequesters for visible items
    val focusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    
    // Title
    Text(
        text = categoryName,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
        ),
        modifier = Modifier.padding(bottom = 12.dp, top = titleTopPadding)
    )
    
    LazyRow(
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = (15.87.dp * 1.4553f * 1.2f * 1.3f)),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        flingBehavior = if (disableUIAnimations) ScrollableDefaults.flingBehavior() else ScrollableDefaults.flingBehavior(),
        modifier = modifier
            .focusProperties {
                // When focus enters the row, redirect to the last focused item
                enter = {
                    focusRequesters[lastFocusedIndex] ?: FocusRequester.Default
                }
            }
            .then(if (debugOutlinesEnabled) Modifier.border(2.dp, Color.Magenta) else Modifier)
    ) {
        items(
            count = movies.size,
            key = { index -> movies[index].id }
        ) { index ->
            val movie = movies[index]
            val focusRequester = remember { FocusRequester() }
            
            // Register focus requester
            DisposableEffect(index) {
                focusRequesters[index] = focusRequester
                onDispose { focusRequesters.remove(index) }
            }
            
            JellyseerrMovieCard(
                movie = movie,
                onClick = { onMovieClick(movie) },
                onFocusChanged = { isFocused ->
                    if (isFocused) {
                        lastFocusedIndex = index
                        onMovieFocused(movie)
                    }
                },
                useSimpleCards = useSimpleCards,
                useGoogleTvCards = useGoogleTvCards,
                lowPowerMode = lowPowerMode,
                modifier = Modifier.focusRequester(focusRequester)
            )
        }
        
        item(key = "view_more") {
            DiscoverViewMoreCard(
                onClick = onViewMoreClick,
                lowPowerMode = lowPowerMode
            )
        }
    }
}
