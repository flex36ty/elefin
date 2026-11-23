package com.flex.elefin.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import com.flex.elefin.MainActivity
import com.flex.elefin.components.RatingDisplay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.flex.elefin.R
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.JellyfinRepository
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.theme.*
import com.flex.elefin.TrailerLauncher
import com.flex.elefin.tmdb.TmdbApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import com.flex.elefin.MovieDetailsActivity


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinMobileDetailsScreen(
    item: JellyfinItem,
    repository: JellyfinRepository,
    onPlayClick: (JellyfinItem, Long) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Use ~33% of screen height for the hero header
    val screenHeightDp = configuration.screenHeightDp.dp
    val headerHeight = if (isLandscape) (screenHeightDp * 0.40f) else (screenHeightDp * 0.33f)

    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var isLoadingDetails by remember { mutableStateOf(true) }
    val displayItem = itemDetails ?: item

    // Check if this is a series
    val isSeries = remember(displayItem.Type, item.Type) {
        displayItem.Type == "Series" || item.Type == "Series"
    }

    // State for seasons and episodes
    var seasons by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var selectedSeasonIndex by remember { mutableStateOf(0) }
    var episodes by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }

    var nextUpEpisode by remember { mutableStateOf<JellyfinItem?>(null) }
    var trailerKey by remember { mutableStateOf<String?>(null) }
    var isTogglingWatched by remember { mutableStateOf(false) }

    val settings = remember { com.flex.elefin.jellyfin.AppSettings(context) }

    // Fetch details and seasons/episodes
    LaunchedEffect(item.Id) {
        isLoadingDetails = true
        try {
            // Fetch details for all item types
            val details = repository.apiService.getItemDetails(item.Id)
            itemDetails = details

            val isSeriesShow = details?.Type == "Series" || item.Type == "Series"
            if (isSeriesShow) {
                // Fetch next up episode for TV show
                try {
                    val nextUp = repository.apiService.getNextUpForSeries(item.Id)
                    nextUpEpisode = nextUp
                } catch (e: Exception) {
                    // Ignore
                }

                val fetchedSeasons = repository.apiService.getSeasons(item.Id)
                seasons = fetchedSeasons
                if (fetchedSeasons.isNotEmpty()) {
                    isLoadingEpisodes = true
                    episodes = repository.apiService.getEpisodes(item.Id, fetchedSeasons[0].Id)
                    isLoadingEpisodes = false
                }
            }
        } catch (e: Exception) {
            // handle error
        } finally {
            isLoadingDetails = false
        }
    }

    // Load episodes when season changes
    LaunchedEffect(selectedSeasonIndex, seasons) {
        if (isSeries && seasons.isNotEmpty() && selectedSeasonIndex < seasons.size) {
            isLoadingEpisodes = true
            try {
                episodes = repository.apiService.getEpisodes(item.Id, seasons[selectedSeasonIndex].Id)
            } catch (e: Exception) {
                // handle error
            } finally {
                isLoadingEpisodes = false
            }
        }
    }

    // --- TV-style Similar and Cast Recommendations ---
    val castMembers = remember(displayItem) { displayItem.People?.filter { it.Type == "Actor" } ?: emptyList() }
    val firstGenre = remember(displayItem) { displayItem.Genres?.firstOrNull() }
    val firstCastMember = remember(castMembers) { castMembers.firstOrNull() }

    var similarItems by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var itemsWithCast by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var isLoadingSimilar by remember { mutableStateOf(false) }
    var isLoadingCastMovies by remember { mutableStateOf(false) }

    // Fetch similar movies/shows by genre
    LaunchedEffect(firstGenre, item.Id) {
        if (firstGenre != null) {
            isLoadingSimilar = true
            try {
                withContext(Dispatchers.IO) {
                    val items = repository.apiService.getMoviesByGenre(firstGenre, excludeItemId = item.Id, limit = 20)
                    similarItems = items
                }
            } catch (e: Exception) {
                Log.e("MobileDetails", "Error fetching similar items", e)
            } finally {
                isLoadingSimilar = false
            }
        } else {
            similarItems = emptyList()
        }
    }

    // Fetch movies/shows with first cast member
    LaunchedEffect(firstCastMember?.Id, item.Id) {
        if (firstCastMember?.Id != null) {
            isLoadingCastMovies = true
            try {
                withContext(Dispatchers.IO) {
                    val items = repository.apiService.getMoviesByPerson(firstCastMember.Id, excludeItemId = item.Id, limit = 20)
                    itemsWithCast = items
                }
            } catch (e: Exception) {
                Log.e("MobileDetails", "Error fetching items with cast", e)
            } finally {
                isLoadingCastMovies = false
            }
        } else {
            itemsWithCast = emptyList()
        }
    }

    // Fetch trailer (TMDB Direct Fallback)
    LaunchedEffect(displayItem, settings.tmdbApiKey) {
        trailerKey = null // Reset

        if (settings.tmdbApiKey.isNotBlank()) {
            val tmdbId = displayItem.ProviderIds?.get("Tmdb")
                ?: displayItem.ProviderIds?.get("tmdb")
                ?: displayItem.ProviderIds?.get("TMDB")

            if (tmdbId != null) {
                try {
                    withContext(Dispatchers.IO) {
                        val audioLang = displayItem.MediaSources?.firstOrNull()?.MediaStreams
                            ?.firstOrNull { it.Type == "Audio" && it.IsDefault == true }?.Language
                            ?: displayItem.MediaSources?.firstOrNull()?.MediaStreams
                                ?.firstOrNull { it.Type == "Audio" }?.Language

                        var iso639Code: String? = null
                        if (audioLang != null) {
                            try {
                                iso639Code = java.util.Locale(audioLang).language
                                if (iso639Code == audioLang && audioLang.length == 3) {
                                    iso639Code = java.util.Locale.getAvailableLocales()
                                        .find { try { it.getISO3Language() == audioLang } catch (e: Exception) { false } }?.language ?: audioLang.take(2)
                                }
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }

                        val videos = TmdbApiService.getVideos(
                            tmdbId = tmdbId.toInt(),
                            type = if (displayItem.Type == "Series" || displayItem.Type == "Season" || displayItem.Type == "Episode") "tv" else "movie",
                            apiKey = settings.tmdbApiKey,
                            language = iso639Code
                        )
                        val trailer = videos.firstOrNull { it.site == "YouTube" && it.type == "Trailer" && it.official && (iso639Code == null || it.iso6391 == iso639Code) }
                            ?: videos.firstOrNull { it.site == "YouTube" && it.type == "Trailer" && it.official }
                            ?: videos.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }
                            ?: videos.firstOrNull { it.site == "YouTube" }

                        if (trailer != null) {
                            trailerKey = trailer.key
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    val isAlreadyWatched = remember(displayItem.UserData) {
        (displayItem.UserData?.Played == true) || (displayItem.UserData?.PlayedPercentage == 100.0)
    }

    fun toggleWatched() {
        if (isTogglingWatched) return
        val service = repository.apiService
        scope.launch(Dispatchers.IO) {
            isTogglingWatched = true
            try {
                val success = if (isAlreadyWatched) {
                    service.markAsUnwatched(displayItem.Id)
                } else {
                    service.markAsWatched(displayItem.Id)
                }
                if (success) {
                    delay(500)
                    val refreshedDetails = service.getItemDetails(displayItem.Id)
                    if (refreshedDetails != null) {
                        withContext(Dispatchers.Main) {
                            itemDetails = refreshedDetails
                            if (isSeries) {
                                launch(Dispatchers.IO) {
                                    val nextUp = service.getNextUpForSeries(displayItem.Id)
                                    withContext(Dispatchers.Main) {
                                        nextUpEpisode = nextUp
                                    }
                                }
                                if (seasons.isNotEmpty() && selectedSeasonIndex < seasons.size) {
                                    launch(Dispatchers.IO) {
                                        val refreshedEpisodes = service.getEpisodes(displayItem.Id, seasons[selectedSeasonIndex].Id)
                                        withContext(Dispatchers.Main) {
                                            episodes = refreshedEpisodes
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // handle error
            } finally {
                isTogglingWatched = false
            }
        }
    }

    val backdropUrl = remember(displayItem.Id, displayItem.ImageTags) {
        val tag = displayItem.ImageTags?.get("Backdrop") ?: displayItem.ImageTags?.get("Primary")
        if (tag != null) {
            repository.apiService.getImageUrl(
                itemId = displayItem.Id,
                imageType = if (displayItem.ImageTags?.get("Backdrop") != null) "Backdrop" else "Primary",
                imageTag = tag,
                maxWidth = 1280,
                quality = 80
            )
        } else ""
    }

    // Logo URL for the header
    val logoItemId = remember(displayItem.Id, displayItem.ImageTags, displayItem.SeriesId) {
        when {
            displayItem.ImageTags?.containsKey("Logo") == true -> displayItem.Id
            displayItem.Type == "Episode" && displayItem.SeriesId != null -> displayItem.SeriesId!!
            else -> displayItem.Id
        }
    }
    val hasLogo = remember(displayItem.ImageTags, displayItem.Type, displayItem.SeriesId) {
        displayItem.ImageTags?.containsKey("Logo") == true ||
        (displayItem.Type == "Episode" && displayItem.SeriesId != null)
    }
    val logoTag = remember(displayItem.ImageTags) { displayItem.ImageTags?.get("Logo") }
    val logoUrl = remember(logoItemId, logoTag) {
        repository.apiService.getImageUrl(
            itemId = logoItemId,
            imageType = "Logo",
            imageTag = logoTag
        )
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
                            text = displayItem.Name ?: "",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        // Home Button (exits back to home screen's Suggestions)
                        IconButton(onClick = {
                            val intent = android.content.Intent(context, MainActivity::class.java).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra("select_tab_action", "home")
                            }
                            context.startActivity(intent)
                            if (context is android.app.Activity) context.finish()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = Color.White
                            )
                        }

                        // Search Button (exits back to home screen's Search tab)
                        IconButton(onClick = {
                            val intent = android.content.Intent(context, MainActivity::class.java).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra("select_tab_action", "search")
                            }
                            context.startActivity(intent)
                            if (context is android.app.Activity) context.finish()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White
                            )
                        }

                        // Settings Button (exits back to home screen's Settings tab)
                        IconButton(onClick = {
                            val intent = android.content.Intent(context, MainActivity::class.java).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra("select_tab_action", "settings")
                            }
                            context.startActivity(intent)
                            if (context is android.app.Activity) context.finish()
                        }) {
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Hero header: thumbnail fills top ~1/3, darkened with logo overlaid
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(headerHeight)
                            .background(JetcasterSurfaceVariant)
                    ) {
                        // Backdrop/thumbnail image
                        if (backdropUrl.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(backdropUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter
                            )
                        }
                        // Dark scrim overlay for readability
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        )
                        // Gradient fade to background at the bottom edge
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            JetcasterBackground.copy(alpha = 0.6f),
                                            JetcasterBackground
                                        )
                                    )
                                )
                        )
                        // Logo / title pinned to the bottom of the header
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 24.dp, vertical = 20.dp)
                        ) {
                            // Logo image (always attempted), falls back to text
                            MobileLogoOrTitle(
                                displayItem = displayItem,
                                hasLogo = hasLogo,
                                logoUrl = logoUrl,
                                apiService = repository.apiService
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val mediaType = if (isSeries) "TV Show" else "Movie"
                                Text(
                                    text = mediaType,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = JetcasterPrimary
                                )
                                if (displayItem.ProductionYear != null) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = displayItem.ProductionYear.toString(),
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Info Section & Actions (title/type now in header, so start with actions)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))

                        val playableItem = if (isSeries) (nextUpEpisode ?: episodes.firstOrNull()) else displayItem
                        val isPlayableResumable = playableItem?.UserData?.PositionTicks != null && playableItem.UserData.PositionTicks > 0
                        val playableResumePositionMs = playableItem?.UserData?.PositionTicks?.let { it / 10_000 } ?: 0L

                        val playButtonText = remember(playableItem, isPlayableResumable) {
                            if (playableItem == null) "Play"
                            else if (isPlayableResumable) {
                                if (isSeries) "Resume: S${playableItem.ParentIndexNumber ?: 1}E${playableItem.IndexNumber ?: 1}"
                                else "Resume"
                            } else {
                                if (isSeries) "Play: S${playableItem.ParentIndexNumber ?: 1}E${playableItem.IndexNumber ?: 1}"
                                else "Play Now"
                            }
                        }

                        // Unified 4-Action Buttons Row (Play, Trailer, Subtitles, Mark as Watched)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Play Button
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = playableItem != null) {
                                        playableItem?.let { onPlayClick(it, playableResumePositionMs) }
                                    }
                                    .padding(4.dp)
                                    .weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(if (playableItem != null) JetcasterPrimary else JetcasterOnBackground.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = if (playableItem != null) Color(0xFF313002) else JetcasterOnBackground.copy(alpha = 0.3f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isPlayableResumable) "Resume" else "Play",
                                    fontSize = 11.sp,
                                    color = JetcasterOnSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 2. Trailer Button
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        TrailerLauncher.launchTrailer(
                                            context, repository.apiService, displayItem.Id,
                                            displayItem.Name, trailerKey, scope
                                        )
                                    }
                                    .padding(4.dp)
                                    .weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = "Trailer",
                                        tint = JetcasterPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Trailer",
                                    fontSize = 11.sp,
                                    color = JetcasterOnSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 3. Subtitles Button
                            var showSubtitleDialog by remember { mutableStateOf(false) }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = playableItem != null) {
                                        showSubtitleDialog = true
                                    }
                                    .padding(4.dp)
                                    .weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(if (playableItem != null) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.02f), CircleShape)
                                        .border(0.5.dp, if (playableItem != null) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "Subtitles",
                                        tint = if (playableItem != null) JetcasterPrimary else JetcasterOnBackground.copy(alpha = 0.3f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Subtitles",
                                    fontSize = 11.sp,
                                    color = if (playableItem != null) JetcasterOnSurface else JetcasterOnBackground.copy(alpha = 0.3f),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (showSubtitleDialog && playableItem != null) {
                                MobileSubtitleSelectionDialog(
                                    item = playableItem,
                                    apiService = repository.apiService,
                                    onDismiss = { showSubtitleDialog = false },
                                    onSubtitleSelected = { index ->
                                        settings.setSubtitlePreference(playableItem.Id, index)
                                        showSubtitleDialog = false
                                    },
                                    settings = settings
                                )
                            }

                            // 4. Mark as Watched Button
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        toggleWatched()
                                    }
                                    .padding(4.dp)
                                    .weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Mark watched",
                                        tint = if (isAlreadyWatched) JetcasterPrimary else JetcasterOnBackground.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isAlreadyWatched) "Watched" else "Watch",
                                    fontSize = 11.sp,
                                    color = JetcasterOnSurface,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Video Metadata Row (Resolution, Audio, TV Rating, Community Rating, etc.)
                        val videoStream = displayItem.MediaSources?.firstOrNull()?.MediaStreams?.firstOrNull { it.Type == "Video" }
                        val audioStream = displayItem.MediaSources?.firstOrNull()?.MediaStreams?.firstOrNull { it.Type == "Audio" }
                        val hasMetadata = displayItem.OfficialRating != null ||
                                          displayItem.CommunityRating != null ||
                                          displayItem.CriticRating != null ||
                                          videoStream != null ||
                                          audioStream != null ||
                                          isAlreadyWatched

                        if (hasMetadata) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Maturity Rating (OfficialRating)
                                displayItem.OfficialRating?.let { rating ->
                                    if (rating.isNotBlank()) {
                                        MetadataBox(text = rating)
                                    }
                                }

                                // 2. Community & Critic Ratings (RatingDisplay)
                                RatingDisplay(
                                    item = displayItem,
                                    communityRating = displayItem.CommunityRating,
                                    criticRating = displayItem.CriticRating
                                )

                                // 3. Resolution
                                videoStream?.let { stream ->
                                    formatResolution(stream.Width, stream.Height)?.let { res ->
                                        MetadataBox(text = res)
                                    }
                                }

                                // 4. HDR/SDR
                                videoStream?.let { stream ->
                                    val is4K = (stream.Width != null && stream.Width!! >= 3840) || (stream.Height != null && stream.Height!! >= 2160)
                                    val isHEVC = stream.Codec?.contains("hevc", ignoreCase = true) == true ||
                                                stream.Codec?.contains("h265", ignoreCase = true) == true
                                    val hdrStatus = if (is4K && isHEVC) "HDR" else "SDR"
                                    MetadataBox(text = hdrStatus)
                                }

                                // 5. Audio Details (Language + Codec)
                                audioStream?.let { stream ->
                                    val language = stream.Language?.uppercase() ?: ""
                                    val codec = stream.Codec?.uppercase() ?: ""
                                    val channelLayout = stream.ChannelLayout ?: ""

                                    val audioText = when {
                                        codec.isNotEmpty() && channelLayout.isNotEmpty() && language.isNotEmpty() -> {
                                            "$language ($codec $channelLayout)"
                                        }
                                        codec.isNotEmpty() && language.isNotEmpty() -> {
                                            "$language ($codec)"
                                        }
                                        language.isNotEmpty() -> language
                                        codec.isNotEmpty() && channelLayout.isNotEmpty() -> "$codec $channelLayout"
                                        codec.isNotEmpty() -> codec
                                        else -> null
                                    }

                                    audioText?.let {
                                        MetadataBox(text = it)
                                    }
                                }
                            }
                        }

                        // Description
                        if (!displayItem.Overview.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Overview",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = JetcasterOnSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = displayItem.Overview,
                                fontSize = 14.sp,
                                color = JetcasterOnBackground.copy(alpha = 0.7f),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // TV-style recommendations and Cast row (on mobile, shown under overview for movies/shows)
                if (castMembers.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Cast",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnSurface,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(castMembers) { person ->
                                val personImageUrl = person.Id?.let { personId ->
                                    person.PrimaryImageTag?.let { tag ->
                                        repository.apiService.getImageUrl(personId, "Primary", tag)
                                    }
                                } ?: ""
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .width(76.dp)
                                        .clickable {
                                            if (person.Id != null) {
                                                val intent = com.flex.elefin.CastInfoActivity.createIntent(
                                                    context,
                                                    person.Id,
                                                    person.Name,
                                                    person.Type
                                                )
                                                context.startActivity(intent)
                                            }
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(JetcasterSurfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (personImageUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(personImageUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = person.Name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = JetcasterPrimary.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    Text(
                                        text = person.Name,
                                        fontSize = 11.sp,
                                        color = JetcasterOnSurface,
                                        maxLines = 2,
                                        textAlign = TextAlign.Center,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Similar Movies / Shows Row
                if (similarItems.isNotEmpty() && !isSeries) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        val headerTitle = if (isSeries) "Similar Shows" else "Similar Movies"
                        Text(
                            text = headerTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnSurface,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(similarItems) { movie ->
                                val posterTag = movie.ImageTags?.get("Primary")
                                val posterUrl = remember(movie.Id, posterTag) {
                                    if (posterTag != null) {
                                        repository.apiService.getImageUrl(movie.Id, "Primary", posterTag, maxWidth = 200, quality = 70)
                                    } else ""
                                }
                                Column(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .clickable {
                                            val intent = MovieDetailsActivity.createIntent(context, movie)
                                            context.startActivity(intent)
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(150.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(JetcasterSurfaceVariant)
                                    ) {
                                        if (posterUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(posterUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = movie.Name,
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
                                                    tint = JetcasterPrimary.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = movie.Name,
                                        fontSize = 12.sp,
                                        color = JetcasterOnSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // More with [Actor] Row
                if (firstCastMember != null && itemsWithCast.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "More with ${firstCastMember.Name}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnSurface,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(itemsWithCast) { movie ->
                                val posterTag = movie.ImageTags?.get("Primary")
                                val posterUrl = remember(movie.Id, posterTag) {
                                    if (posterTag != null) {
                                        repository.apiService.getImageUrl(movie.Id, "Primary", posterTag, maxWidth = 200, quality = 70)
                                    } else ""
                                }
                                Column(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .clickable {
                                            val intent = MovieDetailsActivity.createIntent(context, movie)
                                            context.startActivity(intent)
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(150.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(JetcasterSurfaceVariant)
                                    ) {
                                        if (posterUrl.isNotEmpty()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(posterUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = movie.Name,
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
                                                    tint = JetcasterPrimary.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = movie.Name,
                                        fontSize = 12.sp,
                                        color = JetcasterOnSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // TV Show Specific: Seasons Selector & Episodes List
                if (isSeries) {
                    // Seasons Horizontal Carousel Selector
                    if (seasons.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = "Seasons",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = JetcasterOnSurface,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    count = seasons.size,
                                    key = { index -> seasons[index].Id },
                                    contentType = { "season_poster" }
                                ) { index ->
                                    val season = seasons[index]
                                    val isSelected = index == selectedSeasonIndex

                                    val seasonTag = season.ImageTags?.get("Primary")
                                    val seasonImageUrl = remember(season.Id, seasonTag) {
                                        if (seasonTag != null) {
                                            repository.apiService.getImageUrl(
                                                itemId = season.Id,
                                                imageType = "Primary",
                                                imageTag = seasonTag,
                                                maxWidth = 200,
                                                quality = 70
                                            )
                                        } else ""
                                    }

                                    val selectSeasonCallback = remember(index) { { selectedSeasonIndex = index } }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(180.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(100.dp)
                                                .height(150.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(JetcasterSurfaceVariant)
                                                .then(
                                                    if (isSelected) {
                                                        Modifier.border(3.dp, JetcasterPrimary, RoundedCornerShape(12.dp))
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .clickable(onClick = selectSeasonCallback)
                                        ) {
                                            if (seasonImageUrl.isNotEmpty()) {
                                                val imageRequest = remember(seasonImageUrl) {
                                                    ImageRequest.Builder(context)
                                                        .data(seasonImageUrl)
                                                        .crossfade(true)
                                                        .build()
                                                }
                                                AsyncImage(
                                                    model = imageRequest,
                                                    contentDescription = season.Name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Tv,
                                                        contentDescription = null,
                                                        tint = JetcasterPrimary.copy(alpha = 0.5f),
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = season.Name,
                                            color = if (isSelected) JetcasterPrimary else JetcasterOnSurface,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Episodes Vertical List
                    item {
                        Text(
                            text = "Episodes",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnSurface,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }

                    if (isLoadingEpisodes) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = JetcasterPrimary)
                            }
                        }
                    } else if (episodes.isNotEmpty()) {
                        items(
                            items = episodes,
                            key = { it.Id },
                            contentType = { "episode_row" }
                        ) { episode ->
                            val episodePlayResumeMs = episode.UserData?.PositionTicks?.let { it / 10_000 } ?: 0L
                            val clickCallback = remember(episode, episodePlayResumeMs) { { onPlayClick(episode, episodePlayResumeMs) } }
                            EpisodeRowItem(
                                episode = episode,
                                onClick = clickCallback,
                                repository = repository
                            )
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No episodes found for this season", color = JetcasterOnBackground.copy(alpha = 0.5f))
                            }
                        }
                    }

                    // Similar Shows Row (Moved below the list of episodes)
                    if (similarItems.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Similar Shows",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = JetcasterOnSurface,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(similarItems) { movie ->
                                    val posterTag = movie.ImageTags?.get("Primary")
                                    val posterUrl = remember(movie.Id, posterTag) {
                                        if (posterTag != null) {
                                            repository.apiService.getImageUrl(movie.Id, "Primary", posterTag, maxWidth = 200, quality = 70)
                                        } else ""
                                    }
                                    Column(
                                        modifier = Modifier
                                            .width(100.dp)
                                            .clickable {
                                                val intent = MovieDetailsActivity.createIntent(context, movie)
                                                context.startActivity(intent)
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(100.dp)
                                                .height(150.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(JetcasterSurfaceVariant)
                                        ) {
                                            if (posterUrl.isNotEmpty()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(posterUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = movie.Name,
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
                                                        tint = JetcasterPrimary.copy(alpha = 0.5f)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = movie.Name,
                                            fontSize = 12.sp,
                                            color = JetcasterOnSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 15.sp
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

@Composable
private fun EpisodeRowItem(
    episode: JellyfinItem,
    onClick: () -> Unit,
    repository: JellyfinRepository
) {
    val context = LocalContext.current
    val thumbUrl = remember(episode.Id) {
        val tag = episode.ImageTags?.get("Primary") ?: episode.ImageTags?.get("Backdrop")
        if (tag != null) {
            repository.apiService.getImageUrl(
                itemId = episode.Id,
                imageType = "Primary",
                imageTag = tag,
                maxWidth = 220,
                quality = 70
            )
        } else ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = JetcasterSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 70.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(JetcasterSurfaceVariant)
                ) {
                    if (thumbUrl.isNotEmpty()) {
                        val imageRequest = remember(thumbUrl) {
                            ImageRequest.Builder(context)
                                .data(thumbUrl)
                                .crossfade(true)
                                .build()
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = JetcasterPrimary.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val title = if (episode.IndexNumber != null) {
                        "${episode.IndexNumber}. ${episode.Name}"
                    } else {
                        episode.Name
                    }
                    Text(
                        text = title,
                        color = JetcasterOnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!episode.Overview.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = episode.Overview,
                    color = JetcasterOnBackground.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun MobileSubtitleSelectionDialog(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    onDismiss: () -> Unit,
    onSubtitleSelected: (Int?) -> Unit,
    settings: com.flex.elefin.jellyfin.AppSettings
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var isLoadingSubtitles by remember { mutableStateOf(true) }

    LaunchedEffect(item.Id, apiService) {
        if (apiService != null) {
            try {
                withContext(Dispatchers.IO) {
                    apiService.refreshItemMetadata(item.Id)
                    delay(300)
                    itemDetails = apiService.getItemDetails(item.Id)
                }
            } catch (e: Exception) {
                // handle error
            } finally {
                isLoadingSubtitles = false
            }
        } else {
            isLoadingSubtitles = false
        }
    }

    val displayItem = itemDetails ?: item
    val subtitleStreams = remember(displayItem.MediaSources) {
        displayItem.MediaSources?.firstOrNull()?.MediaStreams
            ?.filter { it.Type == "Subtitle" }
            ?.sortedBy { it.Index ?: 0 } ?: emptyList()
    }

    val selectedIndex = remember(item.Id) { settings.getSubtitlePreference(item.Id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Select Subtitles", fontWeight = FontWeight.Bold, color = JetcasterOnSurface) },
        text = {
            if (isLoadingSubtitles) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = JetcasterPrimary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option: None
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedIndex == null) JetcasterPrimary.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    onSubtitleSelected(null)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedIndex == null,
                                onClick = { onSubtitleSelected(null) },
                                colors = RadioButtonDefaults.colors(selectedColor = JetcasterPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("None", color = JetcasterOnSurface)
                        }
                    }
                    // Option for each subtitle
                    items(subtitleStreams) { stream ->
                        val index = stream.Index
                        val isSelected = selectedIndex == index
                        val title = stream.DisplayTitle ?: stream.Language ?: "Unknown Subtitle"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) JetcasterPrimary.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    onSubtitleSelected(index)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSubtitleSelected(index) },
                                colors = RadioButtonDefaults.colors(selectedColor = JetcasterPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(title, color = JetcasterOnSurface)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = JetcasterPrimary)
            }
        },
        containerColor = JetcasterSurfaceVariant,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun MetadataBox(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .border(0.5.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = JetcasterPrimary
                )
            }
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = JetcasterOnSurface
            )
        }
    }
}

@Composable
private fun RatingBoxWithIcon(
    percentage: Int,
    iconRes: Int,
    label: String
) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.height(12.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = "${percentage}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = JetcasterOnSurface
            )
        }
    }
}



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

/**
 * Always attempts to show the logo image for the item on the mobile details screen.
 * Falls back to the title text if the logo is unavailable or fails to load.
 */
@Composable
private fun MobileLogoOrTitle(
    displayItem: JellyfinItem,
    hasLogo: Boolean,
    logoUrl: String,
    apiService: com.flex.elefin.jellyfin.JellyfinApiService?
) {
    val context = LocalContext.current
    var logoFailed by remember(displayItem.Id) { mutableStateOf(false) }

    if (hasLogo && apiService != null && !logoFailed) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(logoUrl)
                .headers(apiService.getImageRequestHeaders())
                .crossfade(true)
                .build(),
            contentDescription = displayItem.Name,
            modifier = Modifier
                .heightIn(max = 64.dp)
                .wrapContentWidth(),
            contentScale = ContentScale.Fit,
            onError = { logoFailed = true }
        )
    } else {
        Text(
            text = displayItem.Name,
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
