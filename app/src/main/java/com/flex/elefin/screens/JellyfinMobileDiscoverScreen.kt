package com.flex.elefin.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.JellyfinRepository
import com.flex.elefin.jellyseerr.JellyseerrApiService
import com.flex.elefin.jellyseerr.JellyseerrImageUrl
import com.flex.elefin.jellyseerr.JellyseerrMovie
import com.flex.elefin.jellyseerr.JellyseerrTvShow
import com.flex.elefin.jellyseerr.JellyseerrSearchResult
import com.flex.elefin.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinMobileDiscoverScreen(
    libraryName: String,
    collectionType: String, // "movies" or "tvshows"
    repository: JellyfinRepository,
    jellyseerrApiService: JellyseerrApiService?,
    onItemClick: (JellyfinItem, Long) -> Unit,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isDiscoverLoading by remember { mutableStateOf(false) }
    var discoverMoviesByCategory by remember { mutableStateOf<Map<String, List<JellyseerrMovie>>>(emptyMap()) }
    var discoverTvShowsByCategory by remember { mutableStateOf<Map<String, List<JellyseerrTvShow>>>(emptyMap()) }
    var discoverMoviePages by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var discoverTvPages by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var movieToRequest by remember { mutableStateOf<JellyseerrMovie?>(null) }
    var tvShowToRequest by remember { mutableStateOf<JellyseerrTvShow?>(null) }

    val loadMoreDiscoverMovies: (String) -> Unit = { categoryName ->
        if (jellyseerrApiService != null) {
            scope.launch {
                val currentPage = discoverMoviePages[categoryName] ?: 1
                val nextPage = currentPage + 1
                val newMovies = withContext(Dispatchers.IO) {
                    try {
                        when {
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
                    } catch (e: Exception) {
                        android.util.Log.e("JellyfinMobileDiscoverScreen", "Error loading more movies", e)
                        emptyList()
                    }
                }

                if (newMovies.isNotEmpty()) {
                    val currentMovies = discoverMoviesByCategory[categoryName] ?: emptyList()
                    val currentIds = currentMovies.map { it.id }.toSet()
                    val filteredNewMovies = newMovies.filter { it.id !in currentIds }

                    if (filteredNewMovies.isNotEmpty()) {
                        discoverMoviesByCategory = discoverMoviesByCategory + (categoryName to (currentMovies + filteredNewMovies))
                        discoverMoviePages = discoverMoviePages + (categoryName to nextPage)
                    }
                }
            }
        }
    }

    val loadMoreDiscoverTvShows: (String) -> Unit = { categoryName ->
        if (jellyseerrApiService != null) {
            scope.launch {
                val currentPage = discoverTvPages[categoryName] ?: 1
                val nextPage = currentPage + 1
                val newShows = withContext(Dispatchers.IO) {
                    try {
                        when {
                            categoryName.startsWith("🔥") -> {
                                val trending = jellyseerrApiService.getTrending(nextPage)
                                trending.filter { it.mediaType == "tv" }.map { item ->
                                    JellyseerrTvShow(
                                        id = item.id,
                                        mediaType = "tv",
                                        popularity = item.popularity,
                                        posterPath = item.posterPath,
                                        backdropPath = item.backdropPath,
                                        voteCount = item.voteCount,
                                        voteAverage = item.voteAverage,
                                        genreIds = item.genreIds,
                                        overview = item.overview,
                                        originalLanguage = item.originalLanguage,
                                        name = item.name,
                                        originalName = item.originalName,
                                        originCountry = item.originCountry,
                                        firstAirDate = item.firstAirDate,
                                        mediaInfo = item.mediaInfo
                                    )
                                }
                            }
                            categoryName == "Popular" -> jellyseerrApiService.getPopularTvShows(nextPage)
                            categoryName == "Upcoming" -> jellyseerrApiService.getUpcomingTvShows(nextPage)
                            else -> emptyList()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("JellyfinMobileDiscoverScreen", "Error loading more TV shows", e)
                        emptyList()
                    }
                }

                if (newShows.isNotEmpty()) {
                    val currentShows = discoverTvShowsByCategory[categoryName] ?: emptyList()
                    val currentIds = currentShows.map { it.id }.toSet()
                    val filteredNewShows = newShows.filter { it.id !in currentIds }

                    if (filteredNewShows.isNotEmpty()) {
                        discoverTvShowsByCategory = discoverTvShowsByCategory + (categoryName to (currentShows + filteredNewShows))
                        discoverTvPages = discoverTvPages + (categoryName to nextPage)
                    }
                }
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<com.flex.elefin.jellyseerr.JellyseerrSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(collectionType, jellyseerrApiService) {
        if (jellyseerrApiService != null) {
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
                    android.util.Log.e("JellyfinMobileDiscoverScreen", "Error loading discover items", e)
                } finally {
                    isDiscoverLoading = false
                }
            }
        }
    }

    LaunchedEffect(searchQuery, jellyseerrApiService) {
        if (jellyseerrApiService != null && searchQuery.length >= 2) {
            isSearching = true
            // Debounce to prevent immediate API flooding
            kotlinx.coroutines.delay(500)
            withContext(Dispatchers.IO) {
                try {
                    val response = jellyseerrApiService.search(searchQuery)
                    searchResults = response?.results ?: emptyList()
                } catch (e: Exception) {
                    android.util.Log.e("JellyfinMobileDiscoverScreen", "Error searching", e)
                } finally {
                    isSearching = false
                }
            }
        } else {
            searchResults = emptyList()
        }
    }

    JetcasterMobileTheme {
        Scaffold(
            topBar = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .statusBarsPadding()
                            .height(56.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Discover " + if (collectionType == "movies") "Movies" else "TV Shows",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Home Button
                        IconButton(onClick = onHomeClick) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = Color.White
                            )
                        }

                        // Search Button
                        IconButton(onClick = onSearchClick) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White
                            )
                        }

                        // Settings Button
                        IconButton(onClick = onSettingsClick) {
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
            },
            containerColor = JetcasterBackground
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                JetcasterBackground,
                                JetcasterBackground.copy(alpha = 0.95f)
                            )
                        )
                    )
            ) {
                if (jellyseerrApiService == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "Jellyseerr is not configured.",
                                color = JetcasterOnBackground.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Text(
                                text = "Configure Jellyseerr URL and API Key in Settings to discover content.",
                                color = JetcasterOnBackground.copy(alpha = 0.4f),
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = if (collectionType == "movies") "Search Overseer for New Movies" else "Search Overseer for New Shows",
                                    color = JetcasterOnBackground.copy(alpha = 0.5f),
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = JetcasterPrimary
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = JetcasterPrimary
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JetcasterPrimary,
                                unfocusedBorderColor = JetcasterOnBackground.copy(alpha = 0.2f),
                                focusedContainerColor = JetcasterSurfaceVariant,
                                unfocusedContainerColor = JetcasterSurfaceVariant,
                                focusedTextColor = JetcasterOnBackground,
                                unfocusedTextColor = JetcasterOnBackground
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        )

                        if (searchQuery.length >= 2) {
                            if (isSearching) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = JetcasterPrimary)
                                }
                            } else {
                                val filteredResults = searchResults.filter {
                                    if (collectionType == "movies") it.mediaType == "movie" else it.mediaType == "tv"
                                }
                                if (filteredResults.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No results found for \"" + searchQuery + "\"",
                                            color = JetcasterOnBackground.copy(alpha = 0.6f)
                                        )
                                    }
                                } else {
                            val chunkedResults: List<List<JellyseerrSearchResult>> = filteredResults.chunked(3)
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(chunkedResults) { rowItems: List<JellyseerrSearchResult> ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowItems.forEach { result ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                MobileDiscoverCard(
                                                    posterPath = result.posterPath,
                                                    name = result.displayTitle,
                                                    onClick = {
                                                        scope.launch {
                                                            val jellyfinId = result.mediaInfo?.jellyfinId
                                                            if (jellyfinId != null) {
                                                                val jellyfinItem = repository.apiService.getItemDetails(jellyfinId)
                                                                if (jellyfinItem != null) {
                                                                    onItemClick(jellyfinItem, 0L)
                                                                    return@launch
                                                                }
                                                            }
                                                            if (result.mediaType == "movie") {
                                                                var jellyfinItem = repository.apiService.findItemByTmdbId(result.id, "Movie")
                                                                if (jellyfinItem == null) {
                                                                    val year = result.releaseDate?.take(4)
                                                                    jellyfinItem = repository.apiService.findItemByTitle(result.title ?: "", year, "Movie")
                                                                }
                                                                if (jellyfinItem != null) {
                                                                    onItemClick(jellyfinItem, 0L)
                                                                } else {
                                                                    movieToRequest = JellyseerrMovie(
                                                                        id = result.id,
                                                                        mediaType = "movie",
                                                                        popularity = result.popularity,
                                                                        posterPath = result.posterPath,
                                                                        backdropPath = result.backdropPath,
                                                                        voteCount = result.voteCount,
                                                                        voteAverage = result.voteAverage,
                                                                        genreIds = result.genreIds,
                                                                        overview = result.overview,
                                                                        originalLanguage = result.originalLanguage,
                                                                        title = result.title ?: result.displayTitle,
                                                                        originalTitle = result.originalTitle,
                                                                        releaseDate = result.releaseDate,
                                                                        mediaInfo = result.mediaInfo
                                                                    )
                                                                }
                                                            } else {
                                                                var jellyfinItem = repository.apiService.findItemByTmdbId(result.id, "Series")
                                                                if (jellyfinItem == null) {
                                                                    val year = result.firstAirDate?.take(4)
                                                                    jellyfinItem = repository.apiService.findItemByTitle(result.name ?: "", year, "Series")
                                                                }
                                                                if (jellyfinItem != null) {
                                                                    onItemClick(jellyfinItem, 0L)
                                                                } else {
                                                                    tvShowToRequest = JellyseerrTvShow(
                                                                        id = result.id,
                                                                        mediaType = "tv",
                                                                        popularity = result.popularity,
                                                                        posterPath = result.posterPath,
                                                                        backdropPath = result.backdropPath,
                                                                        voteCount = result.voteCount,
                                                                        voteAverage = result.voteAverage,
                                                                        genreIds = result.genreIds,
                                                                        overview = result.overview,
                                                                        originalLanguage = result.originalLanguage,
                                                                        name = result.name ?: result.displayTitle,
                                                                        originalName = result.originalName,
                                                                        firstAirDate = result.firstAirDate,
                                                                        mediaInfo = result.mediaInfo
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        // Fill empty spaces
                                        repeat(3 - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 16.dp, bottom = 32.dp)
                    ) {
                        if (collectionType == "movies") {
                            val sortedCategories = discoverMoviesByCategory.keys.sortedWith(
                                compareBy {
                                    when {
                                        it.startsWith("🔥") -> 0
                                        it == "Popular" -> 1
                                        it == "Upcoming" -> 2
                                        else -> 3
                                    }
                                }
                            )
                            if (sortedCategories.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No discover categories found.", color = JetcasterOnBackground.copy(alpha = 0.6f))
                                }
                            } else {
                                sortedCategories.forEach { categoryName ->
                                    val movies = discoverMoviesByCategory[categoryName] ?: emptyList()
                                    if (movies.isNotEmpty()) {
                                        Text(
                                            text = categoryName,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = JetcasterOnBackground,
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                        )
                                        LazyRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(250.dp),
                                            contentPadding = PaddingValues(horizontal = 20.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(movies) { movie ->
                                                MobileDiscoverCard(
                                                    posterPath = movie.posterPath,
                                                    name = movie.title ?: "Unknown",
                                                    onClick = {
                                                        scope.launch {
                                                            val jellyfinId = movie.mediaInfo?.jellyfinId
                                                            if (jellyfinId != null) {
                                                                val jellyfinItem = repository.apiService.getItemDetails(jellyfinId)
                                                                if (jellyfinItem != null) {
                                                                    onItemClick(jellyfinItem, 0L)
                                                                    return@launch
                                                                }
                                                            }
                                                            var jellyfinItem = repository.apiService.findItemByTmdbId(movie.id, "Movie")
                                                            if (jellyfinItem == null) {
                                                                val year = movie.releaseDate?.take(4)
                                                                jellyfinItem = repository.apiService.findItemByTitle(movie.title ?: "", year, "Movie")
                                                            }
                                                            if (jellyfinItem != null) {
                                                                    onItemClick(jellyfinItem, 0L)
                                                            } else {
                                                                movieToRequest = movie
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                            item {
                                                MobileDiscoverViewMoreCard(
                                                    onClick = { loadMoreDiscoverMovies(categoryName) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val sortedCategories = discoverTvShowsByCategory.keys.sortedWith(
                                compareBy {
                                    when {
                                        it.startsWith("🔥") -> 0
                                        it == "Popular" -> 1
                                        it == "Upcoming" -> 2
                                        else -> 3
                                    }
                                }
                            )
                            if (sortedCategories.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No discover categories found.", color = JetcasterOnBackground.copy(alpha = 0.6f))
                                }
                            } else {
                                sortedCategories.forEach { categoryName ->
                                    val shows = discoverTvShowsByCategory[categoryName] ?: emptyList()
                                    if (shows.isNotEmpty()) {
                                        Text(
                                            text = categoryName,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = JetcasterOnBackground,
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                        )
                                        LazyRow(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(250.dp),
                                            contentPadding = PaddingValues(horizontal = 20.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(shows) { show ->
                                                MobileDiscoverCard(
                                                    posterPath = show.posterPath,
                                                    name = show.name ?: "Unknown",
                                                    onClick = {
                                                        scope.launch {
                                                            val jellyfinId = show.mediaInfo?.jellyfinId
                                                            if (jellyfinId != null) {
                                                                val jellyfinItem = repository.apiService.getItemDetails(jellyfinId)
                                                                if (jellyfinItem != null) {
                                                                    onItemClick(jellyfinItem, 0L)
                                                                    return@launch
                                                                }
                                                            }
                                                            var jellyfinItem = repository.apiService.findItemByTmdbId(show.id, "Series")
                                                            if (jellyfinItem == null) {
                                                                val year = show.firstAirDate?.take(4)
                                                                jellyfinItem = repository.apiService.findItemByTitle(show.name ?: "", year, "Series")
                                                            }
                                                            if (jellyfinItem != null) {
                                                                    onItemClick(jellyfinItem, 0L)
                                                            } else {
                                                                tvShowToRequest = show
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                            item {
                                                MobileDiscoverViewMoreCard(
                                                    onClick = { loadMoreDiscoverTvShows(categoryName) }
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
private fun MobileDiscoverViewMoreCard(
    onClick: () -> Unit
) {
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
                .border(1.dp, JetcasterPrimary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = JetcasterPrimary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "View More",
                    color = JetcasterOnSurface.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "",
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}
