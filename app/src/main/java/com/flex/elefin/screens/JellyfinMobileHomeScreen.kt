package com.flex.elefin.screens

import com.flex.elefin.components.RatingDisplay

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.jellyfin.JellyfinConfig
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.JellyfinLibrary
import com.flex.elefin.jellyfin.JellyfinRepository
import com.flex.elefin.theme.*
import com.flex.elefin.music.ui.mobile.JellyfinMobileMusicScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.flex.elefin.jellyseerr.JellyseerrApiService
import com.flex.elefin.jellyseerr.JellyseerrMovie
import com.flex.elefin.jellyseerr.JellyseerrTvShow
import com.flex.elefin.jellyseerr.JellyseerrImageUrl
import com.flex.elefin.jellyseerr.JellyseerrGenres
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler

@Composable
fun JellyfinMobileHomeScreen(
    initialTabAction: String? = null,
    onTabActionHandled: () -> Unit = {},
    repository: JellyfinRepository,
    onItemClick: (JellyfinItem, Long) -> Unit,
    onMusicClick: () -> Unit,
    onMoviesLibraryClick: (String, String) -> Unit,
    onTvShowsLibraryClick: (String, String) -> Unit,
    preloadLibraryImages: Boolean = false,
    cacheLibraryImages: Boolean = true,
    reducePosterResolution: Boolean = false
) {
    val context = LocalContext.current
    val config = remember { JellyfinConfig(context) }
    var selectedTab by remember { mutableStateOf(0) }
    var activeSubTab by remember { mutableStateOf("suggestions") }

    // Back press: if not on home tab, go back to home instead of closing the app
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    LaunchedEffect(selectedTab) {
        activeSubTab = "suggestions"
    }

    // Collect States from JellyfinRepository
    val continueWatchingItems by repository.continueWatchingItems.collectAsState(initial = emptyList())
    val nextUpItems by repository.nextUpItems.collectAsState(initial = emptyList())
    val libraries by repository.libraries.collectAsState(initial = emptyList())
    val recentlyReleasedMovies by repository.recentlyReleasedMovies.collectAsState(initial = emptyList())
    val collections by repository.collections.collectAsState(initial = emptyList())

    // Grouped library states
    val recentlyAddedMoviesByLibrary by repository.recentlyAddedMoviesByLibrary.collectAsState(initial = emptyMap())
    val recentlyAddedShowsByLibrary by repository.recentlyAddedShowsByLibrary.collectAsState(initial = emptyMap())
    val recentlyAddedEpisodesByLibrary by repository.recentlyAddedEpisodesByLibrary.collectAsState(initial = emptyMap())

    // Backward compatibility lists (for initial featured carousel random items)
    val recentlyAddedMovies by repository.recentlyAddedMovies.collectAsState(initial = emptyList())
    val recentlyAddedShows by repository.recentlyAddedShows.collectAsState(initial = emptyList())

    val maxNavLibraries = 4
    val showMusicLibraries = remember { AppSettings(context).showMusicLibraries }
    val navLibraries = remember(libraries, showMusicLibraries) {
        libraries
            .filter { lib -> showMusicLibraries || lib.CollectionType != "music" }
            .take(maxNavLibraries)
    }
    val searchTabIndex = navLibraries.size + 1
    val settingsTabIndex = navLibraries.size + 2

    LaunchedEffect(initialTabAction, navLibraries) {
        if (initialTabAction != null) {
            when (initialTabAction) {
                "home" -> selectedTab = 0
                "search" -> selectedTab = searchTabIndex
                "settings" -> selectedTab = settingsTabIndex
            }
            onTabActionHandled()
        }
    }

    // Load initial data
    LaunchedEffect(Unit) {
        if (config.isConfigured()) {
            repository.fetchContinueWatching()
            repository.fetchNextUp()
            repository.fetchRecentlyAddedMovies()
            repository.fetchRecentlyReleasedMovies()
            repository.fetchLibraries()
            repository.fetchCollections()
            repository.fetchRecentlyAddedShows()
            repository.fetchRecentlyAddedEpisodes()
        }
    }

    JetcasterMobileTheme {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                // Show only on libraries, suggestions, discover screens (i.e. tab 0 or tab 1..navLibraries.size)
                // Discover screen renders its own unified top bar, so hide home top bar if activeSubTab is "discover"
                if (selectedTab <= navLibraries.size && activeSubTab != "discover") {
                    Column(
                        modifier = Modifier.statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .height(56.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentTitle = remember(selectedTab, navLibraries) {
                                if (selectedTab == 0) {
                                    "Home"
                                } else {
                                    val libraryIndex = selectedTab - 1
                                    if (libraryIndex >= 0 && libraryIndex < navLibraries.size) {
                                        navLibraries[libraryIndex].Name
                                    } else {
                                        "Library"
                                    }
                                }
                            }
                            Text(
                                text = currentTitle,
                                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )

                            // Home Button (acts as suggestions / home content selector)
                            IconButton(onClick = { selectedTab = 0 }) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Home",
                                    tint = if (selectedTab == 0) JetcasterPrimary else Color.White
                                )
                            }

                            // Search Button
                            IconButton(onClick = { selectedTab = searchTabIndex }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White
                                )
                            }

                            // Settings Button
                            IconButton(onClick = { selectedTab = settingsTabIndex }) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White
                                )
                            }
                        }
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 0.5.dp
                        )
                    }
                }
            },
            bottomBar = {
                Column {
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        thickness = 0.5.dp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 2. Dynamic Library Tabs
                        navLibraries.forEachIndexed { index, library ->
                            val tabIndex = index + 1
                            val isMovies = library.CollectionType == "movies"
                            val isTv = library.CollectionType == "tvshows"
                            val isMusic = library.CollectionType == "music"
                            val icon = when {
                                isMovies -> Icons.Default.Movie
                                isTv -> Icons.Default.Tv
                                isMusic -> Icons.Default.MusicNote
                                else -> Icons.Default.Folder
                            }

                            ScrollableNavigationItem(
                                selected = selectedTab == tabIndex,
                                onClick = { selectedTab = tabIndex },
                                icon = icon,
                                label = library.Name
                            )
                        }
                    }
                }
            },
            containerColor = JetcasterBackground
    ) { paddingValues ->
        val bottomPadding = paddingValues.calculateBottomPadding()
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topPadding = when {
            selectedTab > navLibraries.size -> statusBarPadding
            else -> paddingValues.calculateTopPadding()
        }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                JetcasterBackground,
                                JetcasterBackground.copy(alpha = 0.95f)
                            )
                        )
                    )
            ) {
                Crossfade(targetState = selectedTab, label = "TabTransition") { tab ->
                    when {
                        tab == 0 -> MobileHomeContent(
                            continueWatchingItems = continueWatchingItems,
                            nextUpItems = nextUpItems,
                            libraries = libraries,
                            recentlyAddedMovies = recentlyAddedMovies,
                            recentlyAddedShows = recentlyAddedShows,
                            recentlyReleasedMovies = recentlyReleasedMovies,
                            recentlyAddedMoviesByLibrary = recentlyAddedMoviesByLibrary,
                            recentlyAddedShowsByLibrary = recentlyAddedShowsByLibrary,
                            recentlyAddedEpisodesByLibrary = recentlyAddedEpisodesByLibrary,
                            collections = collections,
                            repository = repository,
                            onItemClick = onItemClick,
                            onMusicClick = onMusicClick,
                            onMoviesLibraryClick = onMoviesLibraryClick,
                            onTvShowsLibraryClick = onTvShowsLibraryClick,
                            reducePosterResolution = reducePosterResolution,
                            cacheLibraryImages = cacheLibraryImages,
                            bottomPadding = bottomPadding
                        )
                        tab == searchTabIndex -> MobileSearchTab(
                            repository = repository,
                            onItemClick = onItemClick,
                            bottomPadding = bottomPadding
                        )
                        tab == settingsTabIndex -> SettingsScreen(
                            onBack = { selectedTab = 0 }
                        )
                        else -> {
                            val libraryIndex = tab - 1
                            if (libraryIndex >= 0 && libraryIndex < navLibraries.size) {
                                val library = navLibraries[libraryIndex]
                                val isMusic = library.CollectionType == "music"
                                if (isMusic) {
                                    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                    val cleanBottomPadding = (bottomPadding - systemBottom).coerceAtLeast(0.dp)
                                    JellyfinMobileMusicScreen(
                                        bottomPadding = cleanBottomPadding,
                                        showFullHeader = false,
                                        onBackPress = { selectedTab = 0 }
                                    )
                                } else {
                                     MobileLibraryCategoryTabContent(
                                         libraryId = library.Id,
                                         libraryName = library.Name,
                                         collectionType = library.CollectionType ?: "movies",
                                         repository = repository,
                                         onItemClick = onItemClick,
                                         bottomPadding = bottomPadding,
                                         activeSubTab = activeSubTab,
                                         onActiveSubTabChange = { activeSubTab = it },
                                         onHomeClick = { selectedTab = 0 },
                                         onSearchClick = { selectedTab = searchTabIndex },
                                         onSettingsClick = { selectedTab = settingsTabIndex }
                                     )
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MobileHomeContent(
    continueWatchingItems: List<JellyfinItem>,
    nextUpItems: List<JellyfinItem>,
    libraries: List<JellyfinLibrary>,
    recentlyAddedMovies: List<JellyfinItem>,
    recentlyAddedShows: List<JellyfinItem>,
    recentlyReleasedMovies: List<JellyfinItem>,
    recentlyAddedMoviesByLibrary: Map<String, List<JellyfinItem>>,
    recentlyAddedShowsByLibrary: Map<String, List<JellyfinItem>>,
    recentlyAddedEpisodesByLibrary: Map<String, List<JellyfinItem>>,
    collections: List<JellyfinItem>,
    repository: JellyfinRepository,
    onItemClick: (JellyfinItem, Long) -> Unit,
    onMusicClick: () -> Unit,
    onMoviesLibraryClick: (String, String) -> Unit,
    onTvShowsLibraryClick: (String, String) -> Unit,
    reducePosterResolution: Boolean,
    cacheLibraryImages: Boolean,
    bottomPadding: Dp
) {
    val context = LocalContext.current

    // Choose 5 random movies/shows to display in the featured top carousel, and keep it stable once loaded
    var featuredItems by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    LaunchedEffect(recentlyAddedMovies, recentlyAddedShows) {
        if (featuredItems.isEmpty()) {
            val combined = recentlyAddedMovies + recentlyAddedShows
            if (combined.isNotEmpty()) {
                featuredItems = combined.shuffled().take(5)
            }
        }
    }

    // Filter movie and TV show libraries
    val movieLibraries = remember(libraries, recentlyAddedMoviesByLibrary) {
        libraries.filter { library ->
            recentlyAddedMoviesByLibrary.containsKey(library.Id)
        }.sortedBy { it.Name }
    }
    val tvShowLibraries = remember(libraries, recentlyAddedShowsByLibrary, recentlyAddedEpisodesByLibrary) {
        libraries.filter { library ->
            recentlyAddedShowsByLibrary.containsKey(library.Id) || recentlyAddedEpisodesByLibrary.containsKey(library.Id)
        }.sortedBy { it.Name }
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 24.dp + bottomPadding)
    ) {
        // Random TV Show/Movie Featured Poster Carousel (Tall and gorgeous with customized poster size)
        if (featuredItems.isNotEmpty()) {
            FeaturedCarousel(
                featuredItems = featuredItems,
                repository = repository,
                onItemClick = { item -> onItemClick(item, 0) }
            )
        }



        // 1. Continue Watching Row
        if (continueWatchingItems.isNotEmpty()) {
            Text(
                text = "Continue Watching",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = JetcasterOnBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = continueWatchingItems,
                    key = { it.Id },
                    contentType = { "media_card" }
                ) { item ->
                    val clickCallback = remember(item) { { onItemClick(item, (item.UserData?.PositionTicks ?: 0L) / 10000) } }
                    MobileMediaCard(
                        item = item,
                        onClick = clickCallback,
                        showProgress = true,
                        reduceResolution = reducePosterResolution,
                        enableCaching = cacheLibraryImages,
                        repository = repository
                    )
                }
            }
        }

        // 2. Next Up Row
        if (nextUpItems.isNotEmpty()) {
            Text(
                text = "Next Up",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = JetcasterOnBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = nextUpItems,
                    key = { it.Id },
                    contentType = { "media_card" }
                ) { item ->
                    val clickCallback = remember(item) { { onItemClick(item, 0) } }
                    MobileMediaCard(
                        item = item,
                        onClick = clickCallback,
                        showProgress = false,
                        reduceResolution = reducePosterResolution,
                        enableCaching = cacheLibraryImages,
                        repository = repository
                    )
                }
            }
        }

        // 3. Recently Added Movies rows (One per movie library)
        movieLibraries.forEachIndexed { index, library ->
            val libraryMovies = recentlyAddedMoviesByLibrary[library.Id] ?: emptyList()
            if (libraryMovies.isNotEmpty()) {
                val rowTitle = if (index == 0) "Recently Added Movies" else "Recently Added ${library.Name}"
                Text(
                    text = rowTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = JetcasterOnBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = libraryMovies,
                        key = { it.Id },
                        contentType = { "media_card" }
                    ) { item ->
                        val clickCallback = remember(item) { { onItemClick(item, 0) } }
                        MobileMediaCard(
                            item = item,
                            onClick = clickCallback,
                            showProgress = false,
                            reduceResolution = reducePosterResolution,
                            enableCaching = cacheLibraryImages,
                            repository = repository
                        )
                    }
                }
            }
        }

        // 4. Recently Released Movies Row
        if (recentlyReleasedMovies.isNotEmpty()) {
            Text(
                text = "Recently Released Movies",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = JetcasterOnBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = recentlyReleasedMovies,
                    key = { it.Id },
                    contentType = { "media_card" }
                ) { item ->
                    val clickCallback = remember(item) { { onItemClick(item, 0) } }
                    MobileMediaCard(
                        item = item,
                        onClick = clickCallback,
                        showProgress = false,
                        reduceResolution = reducePosterResolution,
                        repository = repository
                    )
                }
            }
        }

        // 5. Recently Added TV Shows rows (One per TV library)
        tvShowLibraries.forEachIndexed { libraryIndex, library ->
            val libraryShows = recentlyAddedShowsByLibrary[library.Id] ?: emptyList()
            if (libraryShows.isNotEmpty()) {
                val rowTitle = if (libraryIndex == 0) "Recently Added Shows" else "Recently Added Shows in ${library.Name}"
                Text(
                    text = rowTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = JetcasterOnBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = libraryShows,
                        key = { it.Id },
                        contentType = { "media_card" }
                    ) { item ->
                        val clickCallback = remember(item) { { onItemClick(item, 0) } }
                        MobileMediaCard(
                            item = item,
                            onClick = clickCallback,
                            showProgress = false,
                            reduceResolution = reducePosterResolution,
                            enableCaching = cacheLibraryImages,
                            repository = repository
                        )
                    }
                }
            }
        }

        // 6. Recently Added Episodes rows (One per TV library)
        tvShowLibraries.forEachIndexed { libraryIndex, library ->
            val libraryEpisodes = recentlyAddedEpisodesByLibrary[library.Id] ?: emptyList()
            if (libraryEpisodes.isNotEmpty()) {
                val rowTitle = if (libraryIndex == 0) "Recently Added Episodes" else "Recently Added Episodes in ${library.Name}"
                Text(
                    text = rowTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = JetcasterOnBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = libraryEpisodes,
                        key = { it.Id },
                        contentType = { "media_card" }
                    ) { item ->
                        val clickCallback = remember(item) { { onItemClick(item, 0) } }
                        MobileMediaCard(
                            item = item,
                            onClick = clickCallback,
                            showProgress = false,
                            reduceResolution = reducePosterResolution,
                            enableCaching = cacheLibraryImages,
                            repository = repository
                        )
                    }
                }
            }
        }

        // 7. Collections/BoxSets Row
        if (collections.isNotEmpty()) {
            Text(
                text = "Collections",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = JetcasterOnBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(250.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = collections,
                    key = { it.Id },
                    contentType = { "media_card" }
                ) { item ->
                    val clickCallback = remember(item) { { onItemClick(item, 0) } }
                    MobileMediaCard(
                        item = item,
                        onClick = clickCallback,
                        showProgress = false,
                        reduceResolution = reducePosterResolution,
                        repository = repository
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedCarousel(
    featuredItems: List<JellyfinItem>,
    repository: JellyfinRepository,
    onItemClick: (JellyfinItem) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { featuredItems.size })
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    // Dynamically calculate height to maintain a perfect 16:9 aspect ratio based on device width (handles phone vs tablet)
    val carouselHeight = remember(screenWidth) { (screenWidth * 9 / 16).dp }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(carouselHeight)
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(JetcasterSurfaceVariant)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = featuredItems[page]
            val posterUrl = remember(item.Id) {
                val hasThumb = item.ImageTags?.containsKey("Thumb") == true
                val hasBackdrop = item.ImageTags?.containsKey("Backdrop") == true
                val tag = item.ImageTags?.get("Thumb")
                    ?: item.ImageTags?.get("Backdrop")
                    ?: item.ImageTags?.get("Primary")

                if (tag != null) {
                    val imageType = when {
                        hasThumb -> "Thumb"
                        hasBackdrop -> "Backdrop"
                        else -> "Primary"
                    }
                    repository.apiService.getImageUrl(
                        itemId = item.Id,
                        imageType = imageType,
                        imageTag = tag,
                        maxWidth = 1080,
                        quality = 80
                    )
                } else ""
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onItemClick(item) }
            ) {
                if (posterUrl.isNotEmpty()) {
                    val imageRequest = remember(posterUrl) {
                        ImageRequest.Builder(context)
                            .data(posterUrl)
                            .crossfade(true)
                            .build()
                    }
                    // Blurred background to fill container beautifully without cropping the main poster
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(20.dp),
                        contentScale = ContentScale.Crop,
                        alpha = 0.3f
                    )
                    // Foreground poster matching 16:9 layout perfectly
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = item.Name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Dark glassmorphism linear gradient overlay matching eggplant theme
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.4f),
                                    JetcasterBackground
                                )
                            )
                        )
                )
                // Left edge scrim — blends into background
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(48.dp)
                        .align(Alignment.CenterStart)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    JetcasterBackground,
                                    Color.Transparent
                                )
                            )
                        )
                )
                // Right edge scrim — blends into background
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(48.dp)
                        .align(Alignment.CenterEnd)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    JetcasterBackground
                                )
                            )
                        )
                )

                // Title and labels overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    val label = if (item.Type == "Series") "Featured TV Show" else "Featured Movie"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(JetcasterPrimary.copy(alpha = 0.15f))
                            .border(1.dp, JetcasterPrimary.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = label.uppercase(),
                            color = JetcasterPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.Name,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.CommunityRating != null || item.CriticRating != null || item.OfficialRating != null || item.ProductionYear != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.ProductionYear != null) {
                                Text(
                                    text = item.ProductionYear.toString(),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            item.OfficialRating?.let { rating ->
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = rating,
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            RatingDisplay(
                                item = item,
                                communityRating = item.CommunityRating,
                                criticRating = item.CriticRating
                            )
                        }
                    }
                }
            }
        }

        // Custom dot pager indicators with active sliding bar style
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(featuredItems.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(width = if (isSelected) 16.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) JetcasterPrimary else JetcasterOnBackground.copy(alpha = 0.3f))
                )
            }
        }
    }
}

@Composable
private fun MobileMediaCard(
    item: JellyfinItem,
    onClick: () -> Unit,
    showProgress: Boolean,
    reduceResolution: Boolean,
    enableCaching: Boolean = true,
    repository: JellyfinRepository
) {
    val context = LocalContext.current
    val reducePosterResolution = remember(repository) { repository.settings.reducePosterResolution }
    val effectiveReduceResolution = reduceResolution || reducePosterResolution
    val posterUrl = remember(item.Id, effectiveReduceResolution) {
        if (item.Type == "Episode" && !item.SeriesId.isNullOrEmpty()) {
            // Use parent series primary poster instead of episode screenshot
            repository.apiService.getImageUrl(
                itemId = item.SeriesId,
                imageType = "Primary",
                imageTag = null,
                maxWidth = if (effectiveReduceResolution) 200 else 340,
                quality = if (effectiveReduceResolution) 70 else 75
            )
        } else {
            val tag = item.ImageTags?.get("Primary") ?: item.ImageTags?.get("Backdrop")
            if (tag != null) {
                repository.apiService.getImageUrl(
                    itemId = item.Id,
                    imageType = "Primary",
                    imageTag = tag,
                    maxWidth = if (effectiveReduceResolution) 200 else 340,
                    quality = if (effectiveReduceResolution) 70 else 75
                )
            } else ""
        }
    }

    Column(
        modifier = Modifier
            .width(140.dp)
            .height(250.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(JetcasterSurfaceVariant)
        ) {
            if (posterUrl.isNotEmpty()) {
                val cachePolicy = if (enableCaching) CachePolicy.ENABLED else CachePolicy.DISABLED
                val imageRequest = remember(posterUrl, enableCaching) {
                    ImageRequest.Builder(context)
                        .data(posterUrl)
                        .crossfade(true)
                        .memoryCachePolicy(cachePolicy)
                        .diskCachePolicy(cachePolicy)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.Name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = JetcasterPrimary.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Progress Bar Overlay
            if (showProgress && item.RunTimeTicks != null && item.RunTimeTicks > 0) {
                val progress = (item.UserData?.PositionTicks ?: 0L).toFloat() / item.RunTimeTicks.toFloat()
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .align(Alignment.BottomCenter),
                    color = JetcasterSecondary,
                    trackColor = Color.Black.copy(alpha = 0.4f)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.Name,
            color = JetcasterOnSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!item.SeriesName.isNullOrBlank()) {
            Text(
                text = item.SeriesName,
                color = JetcasterOnBackground.copy(alpha = 0.6f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MobileLibrariesGrid(
    libraries: List<JellyfinLibrary>,
    onMoviesLibraryClick: (String, String) -> Unit,
    onTvShowsLibraryClick: (String, String) -> Unit,
    onMusicClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Your Libraries",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = JetcasterPrimary,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(libraries) { library ->
                val isMovies = library.CollectionType == "movies"
                val isTv = library.CollectionType == "tvshows"
                val isMusic = library.CollectionType == "music"

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.2f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(JetcasterSurface)
                        .clickable {
                            when {
                                isMovies -> onMoviesLibraryClick(library.Id, library.Name)
                                isTv -> onTvShowsLibraryClick(library.Id, library.Name)
                                isMusic -> onMusicClick()
                            }
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val icon = when {
                            isMovies -> Icons.Default.Movie
                            isTv -> Icons.Default.Tv
                            isMusic -> Icons.Default.MusicNote
                            else -> Icons.Default.Folder
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = library.Name,
                            tint = JetcasterPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = library.Name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileSearchTab(
    repository: JellyfinRepository,
    onItemClick: (JellyfinItem, Long) -> Unit,
    bottomPadding: Dp
) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Trigger search on query change
    LaunchedEffect(query) {
        if (query.length >= 2) {
            try {
                // Jellyfin search API call
                val items = repository.apiService.searchItems(query, limit = 20)
                searchResults = items
            } catch (e: Exception) {
                searchResults = emptyList()
            }
        } else {
            searchResults = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 20.dp, start = 20.dp, end = 20.dp)
    ) {
        Text(
            text = "Search Content",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = JetcasterPrimary,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search movies, shows...", color = JetcasterOnBackground.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JetcasterPrimary,
                unfocusedBorderColor = JetcasterOnBackground.copy(alpha = 0.3f),
                focusedLabelColor = JetcasterPrimary,
                cursorColor = JetcasterPrimary,
                focusedTextColor = JetcasterOnBackground,
                unfocusedTextColor = JetcasterOnBackground
            ),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = JetcasterPrimary) },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (searchResults.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = bottomPadding + 20.dp)
            ) {
                items(searchResults) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item, 0) },
                        colors = CardDefaults.cardColors(containerColor = JetcasterSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.Type == "Series") Icons.Default.Tv else Icons.Default.Movie,
                                contentDescription = null,
                                tint = JetcasterPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = item.Name,
                                    color = JetcasterOnSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = item.Type ?: "Media",
                                    color = JetcasterOnBackground.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        } else if (query.length >= 2) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching results found.", color = JetcasterOnBackground.copy(alpha = 0.6f))
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Type at least 2 characters to search.", color = JetcasterOnBackground.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun MobileLibraryCategoryTabContent(
    libraryId: String,
    libraryName: String,
    collectionType: String,
    repository: JellyfinRepository,
    onItemClick: (JellyfinItem, Long) -> Unit,
    bottomPadding: Dp,
    activeSubTab: String,
    onActiveSubTabChange: (String) -> Unit,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    val reducePosterResolution = remember(repository) { repository.settings.reducePosterResolution }

    val settings = remember { AppSettings(context) }
    val cacheLibraryImages = remember(settings) { settings.cacheLibraryImages }
    val jellyseerrEnabled = settings.jellyseerrEnabled && settings.jellyseerrUrl.isNotBlank()
    val jellyseerrApiService = remember(jellyseerrEnabled, settings.jellyseerrUrl, settings.jellyseerrApiKey, settings.jellyseerrSessionCookie, settings.jellyseerrAuthType) {
        if (jellyseerrEnabled) {
            if (settings.jellyseerrAuthType == "api_key" && settings.jellyseerrApiKey.isNotBlank()) {
                JellyseerrApiService.withApiKey(settings.jellyseerrUrl, settings.jellyseerrApiKey)
            } else if (settings.jellyseerrAuthType == "cookie" && settings.jellyseerrSessionCookie.isNotBlank()) {
                JellyseerrApiService.withCookie(settings.jellyseerrUrl, settings.jellyseerrSessionCookie)
            } else {
                null
            }
        } else {
            null
        }
    }

    val subTabs = remember(jellyseerrEnabled) {
        if (jellyseerrEnabled) {
            listOf(
                "Suggestions" to "suggestions",
                "Library" to "library",
                "Discover" to "discover"
            )
        } else {
            listOf(
                "Suggestions" to "suggestions",
                "Library" to "library"
            )
        }
    }

    // Discover data states
    var discoverMoviesByCategory by remember { mutableStateOf<Map<String, List<JellyseerrMovie>>>(emptyMap()) }
    var discoverTvShowsByCategory by remember { mutableStateOf<Map<String, List<JellyseerrTvShow>>>(emptyMap()) }
    var isDiscoverLoading by remember { mutableStateOf(false) }
    var movieToRequest by remember { mutableStateOf<JellyseerrMovie?>(null) }
    var tvShowToRequest by remember { mutableStateOf<JellyseerrTvShow?>(null) }
    val scope = rememberCoroutineScope()

    // Fetch Discover Content
    LaunchedEffect(activeSubTab, jellyseerrApiService) {
        if (activeSubTab == "discover" && jellyseerrApiService != null) {
            isDiscoverLoading = true
            withContext(Dispatchers.IO) {
                try {
                    if (collectionType == "movies") {
                        val data = jellyseerrApiService.getAllMovieCategories()
                        discoverMoviesByCategory = data
                    } else if (collectionType == "tvshows") {
                        val data = jellyseerrApiService.getAllTvCategories()
                        discoverTvShowsByCategory = data
                    }
                } catch (e: Exception) {
                    android.util.Log.e("JellyfinMobileHomeScreen", "Error loading discover items", e)
                } finally {
                    isDiscoverLoading = false
                }
            }
        }
    }

    // Movie recommendation states
    var continueWatchingMovies by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var recentlyReleasedMovies by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var recentlyAddedMovies by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var topUnwatchedMovies by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var favoriteMovies by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var genreMovies1 by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var genreMovies2 by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var genreMovies3 by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var selectedGenre1 by remember { mutableStateOf("") }
    var selectedGenre2 by remember { mutableStateOf("") }
    var selectedGenre3 by remember { mutableStateOf("") }

    // TV show recommendation states
    var continueWatchingEpisodes by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var nextUpEpisodes by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var recentlyReleasedEpisodes by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var recentlyAddedShows by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var startWatchingShows by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var topRatedShows by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var genreShows1 by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var genreShows2 by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var genreShows3 by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var selectedGenre1Tv by remember { mutableStateOf("") }
    var selectedGenre2Tv by remember { mutableStateOf("") }
    var selectedGenre3Tv by remember { mutableStateOf("") }

    // Grid Library items state (using same repository mapping to avoid duplicate fetch calls)
    val libraryItemsMap by repository.libraryItems.collectAsState(initial = emptyMap())
    val items = remember(libraryItemsMap, libraryId) { libraryItemsMap[libraryId] ?: emptyList() }

    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var showGenreDialog by remember { mutableStateOf(false) }

    LaunchedEffect(libraryId) {
        selectedGenre = null
    }

    val availableGenres = remember(items) {
        items.flatMap { it.Genres ?: emptyList() }.distinct().sorted()
    }

    // Sort states
    var sortBy by remember { mutableStateOf("Alphabetical") }

    val filteredItems = remember(items, selectedGenre) {
        if (selectedGenre == null) {
            items
        } else {
            items.filter { it.Genres?.contains(selectedGenre) == true }
        }
    }

    val sortedItems = remember(filteredItems, sortBy) {
        when (sortBy) {
            "Alphabetical" -> filteredItems.sortedBy { it.Name }
            "Recently Added" -> filteredItems.sortedByDescending { it.DateCreated ?: "" }
            "Release Date" -> filteredItems.sortedByDescending { it.PremiereDate ?: "" }
            else -> filteredItems
        }
    }
    var showSortDialog by remember { mutableStateOf(false) }

    val hasSuggestions = collectionType == "movies" || collectionType == "tvshows"

    // If suggestions are not supported for this type of library, go straight to the grid
    LaunchedEffect(collectionType) {
        if (!hasSuggestions) {
            onActiveSubTabChange("library")
        }
    }

    LaunchedEffect(libraryId, collectionType) {
        isLoading = true
        // Fetch all library items for the Library tab
        repository.fetchLibraryItems(libraryId)

        withContext(Dispatchers.IO) {
            try {
                val apiService = repository.apiService
                val limit = 15

                if (collectionType == "movies") {
                    val genres = try { apiService.getMovieGenresFromLibrary(libraryId) } catch (e: Exception) { emptyList() }
                    val movieGenres = listOf("Action", "Comedy", "Drama", "Horror", "Thriller", "Sci-Fi", "Romance", "Adventure", "Animation", "Documentary")
                    val shuffledGenres = if (genres.isNotEmpty()) {
                        genres.filter { it in movieGenres }.shuffled().take(3).ifEmpty { genres.shuffled().take(3) }
                    } else {
                        movieGenres.shuffled().take(3)
                    }
                    val g1 = shuffledGenres.getOrNull(0) ?: "Action"
                    val g2 = shuffledGenres.getOrNull(1) ?: "Comedy"
                    val g3 = shuffledGenres.getOrNull(2) ?: "Drama"

                    selectedGenre1 = g1
                    selectedGenre2 = g2
                    selectedGenre3 = g3

                    coroutineScope {
                        val cwDef = async { try { apiService.getContinueWatchingMoviesFromLibrary(libraryId, limit) } catch (e: Exception) { emptyList() } }
                        val rrDef = async { try { apiService.getRecentlyReleasedMoviesFromLibrary(libraryId, limit) } catch (e: Exception) { emptyList() } }
                        val raDef = async { try { apiService.getRecentlyAddedMoviesFromLibrary(libraryId, limit) } catch (e: Exception) { emptyList() } }
                        val tuDef = async { try { apiService.getTopUnwatchedMoviesFromLibrary(libraryId, limit) } catch (e: Exception) { emptyList() } }
                        val favDef = async { try { apiService.getFavoriteMoviesFromLibrary(libraryId, limit) } catch (e: Exception) { emptyList() } }
                        val g1Def = async { try { apiService.getMoviesByGenreFromLibrary(libraryId, g1, limit) } catch (e: Exception) { emptyList() } }
                        val g2Def = async { try { apiService.getMoviesByGenreFromLibrary(libraryId, g2, limit) } catch (e: Exception) { emptyList() } }
                        val g3Def = async { try { apiService.getMoviesByGenreFromLibrary(libraryId, g3, limit) } catch (e: Exception) { emptyList() } }

                        continueWatchingMovies = cwDef.await()
                        recentlyReleasedMovies = rrDef.await()
                        recentlyAddedMovies = raDef.await()
                        topUnwatchedMovies = tuDef.await()
                        favoriteMovies = favDef.await()
                        genreMovies1 = g1Def.await()
                        genreMovies2 = g2Def.await()
                        genreMovies3 = g3Def.await()
                    }
                } else if (collectionType == "tvshows") {
                    val genres = try { apiService.getGenresFromLibrary(libraryId) } catch (e: Exception) { emptyList() }
                    val tvGenres = listOf("Drama", "Comedy", "Action", "Adventure", "Sci-Fi", "Fantasy", "Horror", "Thriller", "Mystery", "Animation")
                    val shuffledGenres = if (genres.isNotEmpty()) {
                        genres.filter { it in tvGenres }.shuffled().take(3).ifEmpty { genres.shuffled().take(3) }
                    } else {
                        tvGenres.shuffled().take(3)
                    }
                    val g1 = shuffledGenres.getOrNull(0) ?: "Drama"
                    val g2 = shuffledGenres.getOrNull(1) ?: "Comedy"
                    val g3 = shuffledGenres.getOrNull(2) ?: "Action"

                    selectedGenre1Tv = g1
                    selectedGenre2Tv = g2
                    selectedGenre3Tv = g3

                    coroutineScope {
                        val cwDef = async { try { apiService.getContinueWatchingEpisodesFromLibrary(libraryId, limit) } catch (e: Exception) { emptyList() } }
                        val nuDef = async { try { apiService.getNextUpEpisodesFromLibrary(libraryId, limit) } catch (e: Exception) { emptyList() } }
                        val rrDef = async { try { apiService.getRecentlyReleasedEpisodesFromLibrary(libraryId, limit) } catch (e: Exception) { emptyList() } }
                        val raDef = async { try { apiService.getRecentlyAddedShowsFromLibrary(libraryId, limit) } catch (e: Exception) { emptyList() } }
                        val swDef = async { try { apiService.getRandomUnwatchedShowsFromLibrary(libraryId, limit) } catch (e: Exception) { emptyList() } }
                        val trDef = async { try { apiService.getTopRatedShowsFromLibrary(libraryId, limit) } catch (e: Exception) { emptyList() } }
                        val g1Def = async { try { apiService.getShowsByGenreFromLibrary(libraryId, g1, limit) } catch (e: Exception) { emptyList() } }
                        val g2Def = async { try { apiService.getShowsByGenreFromLibrary(libraryId, g2, limit) } catch (e: Exception) { emptyList() } }
                        val g3Def = async { try { apiService.getShowsByGenreFromLibrary(libraryId, g3, limit) } catch (e: Exception) { emptyList() } }

                        continueWatchingEpisodes = cwDef.await()
                        nextUpEpisodes = nuDef.await()
                        recentlyReleasedEpisodes = rrDef.await()
                        recentlyAddedShows = raDef.await()
                        startWatchingShows = swDef.await()
                        topRatedShows = trDef.await()
                        genreShows1 = g1Def.await()
                        genreShows2 = g2Def.await()
                        genreShows3 = g3Def.await()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    val configuration = LocalConfiguration.current
    val columns = remember(configuration.screenWidthDp) {
        val availableWidth = configuration.screenWidthDp - 40 // padding
        val cols = (availableWidth + 16) / 126
        cols.coerceAtLeast(2)
    }
    val chunkedItems = remember(sortedItems, columns) { sortedItems.chunked(columns) }

    if (activeSubTab == "suggestions") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp, bottom = 32.dp + bottomPadding)
        ) {

            // 2. Suggestions vs Library dual pill selector
            if (hasSuggestions) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    subTabs.forEach { (label, id) ->
                        val isSelected = activeSubTab == id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) JetcasterPrimary.copy(alpha = 0.25f) else Color.Transparent)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(1.dp, JetcasterPrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { onActiveSubTabChange(id) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) JetcasterPrimary else JetcasterOnBackground.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = JetcasterPrimary)
                }
            } else {
                if (collectionType == "movies") {
                    // Suggestions: Movies content
                    // 1. Continue Watching
                    if (continueWatchingMovies.isNotEmpty()) {
                        Text(
                            text = "Continue Watching",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = continueWatchingMovies,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, (item.UserData?.PositionTicks ?: 0L) / 10000) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = true,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // 2. Recently Released
                    if (recentlyReleasedMovies.isNotEmpty()) {
                        Text(
                            text = "Recently Released",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = recentlyReleasedMovies,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // 3. Recently Added
                    if (recentlyAddedMovies.isNotEmpty()) {
                        Text(
                            text = "Recently Added",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = recentlyAddedMovies,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // 4. Top Unwatched
                    if (topUnwatchedMovies.isNotEmpty()) {
                        Text(
                            text = "Top Unwatched",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = topUnwatchedMovies,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // 5. Favorites
                    if (favoriteMovies.isNotEmpty()) {
                        Text(
                            text = "Favorites",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = favoriteMovies,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // Genre 1 Row
                    if (genreMovies1.isNotEmpty() && selectedGenre1.isNotEmpty()) {
                        Text(
                            text = selectedGenre1,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = genreMovies1,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // Genre 2 Row
                    if (genreMovies2.isNotEmpty() && selectedGenre2.isNotEmpty()) {
                        Text(
                            text = selectedGenre2,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = genreMovies2,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // Genre 3 Row
                    if (genreMovies3.isNotEmpty() && selectedGenre3.isNotEmpty()) {
                        Text(
                            text = selectedGenre3,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = genreMovies3,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }
                } else if (collectionType == "tvshows") {
                    // Suggestions: TV Shows content
                    // 1. Continue Watching
                    if (continueWatchingEpisodes.isNotEmpty()) {
                        Text(
                            text = "Continue Watching",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = continueWatchingEpisodes,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, (item.UserData?.PositionTicks ?: 0L) / 10000) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = true,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // 2. Next Up
                    if (nextUpEpisodes.isNotEmpty()) {
                        Text(
                            text = "Next Up",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = nextUpEpisodes,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // 3. Recently Released Episodes
                    if (recentlyReleasedEpisodes.isNotEmpty()) {
                        Text(
                            text = "Recently Released Episodes",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = recentlyReleasedEpisodes,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // 4. Recently Added Shows
                    if (recentlyAddedShows.isNotEmpty()) {
                        Text(
                            text = "Recently Added Shows",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = recentlyAddedShows,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // 5. Start Watching
                    if (startWatchingShows.isNotEmpty()) {
                        Text(
                            text = "Start Watching",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = startWatchingShows,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // 6. Top Rated Shows
                    if (topRatedShows.isNotEmpty()) {
                        Text(
                            text = "Top Rated Shows",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = topRatedShows,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // Genre 1 Row
                    if (genreShows1.isNotEmpty() && selectedGenre1Tv.isNotEmpty()) {
                        Text(
                            text = selectedGenre1Tv,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = genreShows1,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // Genre 2 Row
                    if (genreShows2.isNotEmpty() && selectedGenre2Tv.isNotEmpty()) {
                        Text(
                            text = selectedGenre2Tv,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = genreShows2,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }

                    // Genre 3 Row
                    if (genreShows3.isNotEmpty() && selectedGenre3Tv.isNotEmpty()) {
                        Text(
                            text = selectedGenre3Tv,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = genreShows3,
                                key = { it.Id },
                                contentType = { "media_card" }
                            ) { item ->
                                val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                MobileMediaCard(
                                    item = item,
                                    onClick = clickCallback,
                                    showProgress = false,
                                    reduceResolution = reducePosterResolution,
                                    enableCaching = cacheLibraryImages,
                                    repository = repository
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No suggestions available for this library type.", color = JetcasterOnBackground.copy(alpha = 0.6f))
                    }
                }
            }
        }
    } else if (activeSubTab == "discover") {
        JellyfinMobileDiscoverScreen(
            libraryName = libraryName,
            collectionType = collectionType,
            repository = repository,
            jellyseerrApiService = jellyseerrApiService,
            onItemClick = onItemClick,
            onBackClick = { onActiveSubTabChange("suggestions") },
            onHomeClick = onHomeClick,
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp + bottomPadding)
        ) {
            // 1. Library Title and Sort Icon
            item(key = "library_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (availableGenres.isNotEmpty()) {
                        TextButton(onClick = { showGenreDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filter by Genre",
                                tint = if (selectedGenre != null) JetcasterPrimary else JetcasterPrimary.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = selectedGenre ?: "Genre",
                                color = if (selectedGenre != null) JetcasterPrimary else JetcasterPrimary.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    TextButton(onClick = { showSortDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort",
                            tint = JetcasterPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = sortBy,
                            color = JetcasterPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. Suggestions vs Library dual pill selector
            if (hasSuggestions) {
                item(key = "sub_tabs") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        subTabs.forEach { (label, id) ->
                            val isSelected = activeSubTab == id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) JetcasterPrimary.copy(alpha = 0.25f) else Color.Transparent)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(1.dp, JetcasterPrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { onActiveSubTabChange(id) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) JetcasterPrimary else JetcasterOnBackground.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else {
                item(key = "sub_tabs_spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (isLoading) {
                item(key = "loading_spinner") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillParentMaxHeight(0.7f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = JetcasterPrimary)
                    }
                }
            } else {
                if (sortedItems.isNotEmpty()) {
                    items(
                        count = chunkedItems.size,
                        key = { index -> "library_grid_row_${chunkedItems[index].first().Id}" },
                        contentType = { "library_grid_row" }
                    ) { rowIndex ->
                        val rowItems = chunkedItems[rowIndex]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            rowItems.forEach { item ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                ) {
                                    val clickCallback = remember(item) { { onItemClick(item, 0) } }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = clickCallback)
                                    ) {
                                        val posterUrl = remember(item.Id, reducePosterResolution) {
                                            val tag = item.ImageTags?.get("Primary") ?: item.ImageTags?.get("Backdrop")
                                            if (tag != null) {
                                                repository.apiService.getImageUrl(
                                                    itemId = item.Id,
                                                    imageType = "Primary",
                                                    imageTag = tag,
                                                    maxWidth = if (reducePosterResolution) 200 else 340,
                                                    quality = if (reducePosterResolution) 70 else 75
                                                )
                                            } else ""
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(2f / 3f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(JetcasterSurfaceVariant)
                                        ) {
                                            if (posterUrl.isNotEmpty()) {
                                                val imageRequest = remember(posterUrl) {
                                                    ImageRequest.Builder(context)
                                                        .data(posterUrl)
                                                        .crossfade(true)
                                                        .build()
                                                }
                                                AsyncImage(
                                                    model = imageRequest,
                                                    contentDescription = item.Name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Movie,
                                                        contentDescription = null,
                                                        tint = JetcasterPrimary.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = item.Name,
                                            color = JetcasterOnSurface,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (rowItems.size < columns) {
                                repeat(columns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    item(key = "empty_library_placeholder") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillParentMaxHeight(0.7f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("This library is empty.", color = JetcasterOnBackground.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }

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
                    onBackPressed = { movieToRequest = null }
                )
            }
        }
    }

    tvShowToRequest?.let { show ->
        Dialog(
            onDismissRequest = { tvShowToRequest = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                TvShowRequestScreen(
                    show = show,
                    jellyseerrApiService = jellyseerrApiService,
                    onBackPressed = { tvShowToRequest = null }
                )
            }
        }
    }

    if (showGenreDialog) {
        AlertDialog(
            onDismissRequest = { showGenreDialog = false },
            title = { Text("Filter by Genre", color = JetcasterOnSurface, fontWeight = FontWeight.Bold) },
            containerColor = JetcasterSurface,
            confirmButton = {
                TextButton(onClick = { showGenreDialog = false }) {
                    Text("Close", color = JetcasterPrimary)
                }
            },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedGenre = null
                                    showGenreDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedGenre == null,
                                onClick = {
                                    selectedGenre = null
                                    showGenreDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = JetcasterPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("All Genres", color = JetcasterOnBackground)
                        }
                    }
                    items(availableGenres) { genre ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedGenre = genre
                                    showGenreDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedGenre == genre,
                                onClick = {
                                    selectedGenre = genre
                                    showGenreDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = JetcasterPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(genre, color = JetcasterOnBackground)
                        }
                    }
                }
            }
        )
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort By", color = JetcasterOnSurface, fontWeight = FontWeight.Bold) },
            containerColor = JetcasterSurface,
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("Cancel", color = JetcasterPrimary)
                }
            },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortBy = "Alphabetical"
                                showSortDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = sortBy == "Alphabetical",
                            onClick = {
                                sortBy = "Alphabetical"
                                showSortDialog = false
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = JetcasterPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Alphabetical", color = JetcasterOnBackground)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortBy = "Recently Added"
                                showSortDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = sortBy == "Recently Added",
                            onClick = {
                                sortBy = "Recently Added"
                                showSortDialog = false
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = JetcasterPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Recently Added", color = JetcasterOnBackground)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortBy = "Release Date"
                                showSortDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = sortBy == "Release Date",
                            onClick = {
                                sortBy = "Release Date"
                                showSortDialog = false
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = JetcasterPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Release Date", color = JetcasterOnBackground)
                    }
                }
            }
        )
    }
}

@Composable
private fun MobileDiscoverCard(
    posterPath: String?,
    name: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val posterUrl = remember(posterPath) {
        JellyseerrImageUrl.poster(posterPath, "w342")
    }

    Column(
        modifier = Modifier
            .width(140.dp)
            .height(250.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(JetcasterSurfaceVariant)
        ) {
            if (!posterUrl.isNullOrEmpty()) {
                val imageRequest = remember(posterUrl) {
                    ImageRequest.Builder(context)
                        .data(posterUrl)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = JetcasterPrimary.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            color = JetcasterOnSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ScrollableNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    val contentColor = if (selected) JetcasterPrimary else JetcasterOnBackground.copy(alpha = 0.6f)
    val indicatorColor = if (selected) JetcasterPrimary.copy(alpha = 0.2f) else Color.Transparent

    Column(
        modifier = Modifier
            .width(76.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp, 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(indicatorColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
