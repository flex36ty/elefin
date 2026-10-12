package com.flex.elefin.screens

import com.flex.elefin.jellyfin.JellyfinRepository
import com.flex.elefin.components.RatingDisplay
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import com.flex.elefin.ui.expandingActionButton
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.foundation.Image
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Icon
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.ListItem
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.width
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.flex.elefin.JellyfinVideoPlayerActivity
import com.flex.elefin.SeriesDetailsActivity
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.R
import com.flex.elefin.screens.ItemDetailsSection
import com.flex.elefin.screens.AnimatedPlayButton
import com.flex.elefin.screens.JellyfinHorizontalCard
import com.flex.elefin.screens.CastMemberCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.flex.elefin.TrailerLauncher
import com.flex.elefin.tmdb.TmdbApiService
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.foundation.focusable

@Composable
fun SeriesDetailsScreen(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    showDebugOutlines: Boolean = false,
    initialEpisodeId: String? = null,
    onBackPressed: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val settings = remember { com.flex.elefin.jellyfin.AppSettings(context) }
    var darkModeEnabled by remember { mutableStateOf(settings.darkModeEnabled) }
    
    val repository = remember(apiService) {
        apiService?.let { JellyfinRepository(it, settings) }
    }

    val isTv = remember(context) { com.flex.elefin.ui.DeviceUtils.isTvDevice(context) }
    if (!isTv && repository != null) {
        JellyfinMobileDetailsScreen(
            item = item,
            repository = repository,
            onPlayClick = { playItem, resumeMs ->
                val intent = JellyfinVideoPlayerActivity.createIntent(context, playItem.Id, resumeMs)
                context.startActivity(intent)
            },
            onBackClick = { onBackPressed?.invoke() }
        )
        return
    }
    
    // GL Pipeline warmup for NVIDIA Shield - prevents initial frame stutter and ANR
    LaunchedEffect(Unit) {
        delay(100) // 100ms delay to prevent ANR and warm up GL pipeline
    }
    
    // Handle back button press
    if (onBackPressed != null) {
        BackHandler(onBack = onBackPressed)
    }
    
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var seasons by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var episodes by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var selectedSeasonId by androidx.compose.runtime.saveable.rememberSaveable(item.Id) {
        mutableStateOf<String?>(null)
    }
    val selectedSeasonIndex = seasons.indexOfFirst { it.Id == selectedSeasonId }.coerceAtLeast(0)
    val activeSeasonId = seasons.getOrNull(selectedSeasonIndex)?.Id
    var focusedEpisode by remember { mutableStateOf<JellyfinItem?>(null) }
    var focusedSeason by remember { mutableStateOf<JellyfinItem?>(null) }
    var isLoadingSeasons by remember { mutableStateOf(true) }
    var isLoadingEpisodes by remember { mutableStateOf(true) }
    
    // FocusRequester map for episodes (used for initial focus)
    val episodeFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    
    // FocusRequester for first season button (used when no initial episode is provided)
    val firstSeasonFocusRequester = remember { FocusRequester() }
    
    // Lifecycle observer to trigger refresh when returning from player
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var refreshTrigger by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Increment trigger to reload data
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Fetch full series details - load in parallel for faster loading
    LaunchedEffect(item.Id, apiService, refreshTrigger) {
        if (apiService != null) {
            withContext(Dispatchers.IO) {
                try {
                    // Load details and seasons in parallel
                    val detailsDeferred = async { apiService.getItemDetails(item.Id) }
                    val seasonsDeferred = async { apiService.getSeasons(item.Id) }
                    
                    val details = detailsDeferred.await()
                    itemDetails = details
                    
                    val seriesSeasons = seasonsDeferred.await()
                    if (seriesSeasons.none { it.Id == selectedSeasonId }) {
                        selectedSeasonId = seriesSeasons.firstOrNull()?.Id
                    }
                    seasons = seriesSeasons
                    isLoadingSeasons = false
                    
                    // Episode loading belongs exclusively to the selected-season effect below.
                    // A resume refresh must not overwrite it with the first season's episodes.
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Ignore cancellation exceptions - they're expected when composition changes
                    throw e // Re-throw to properly handle cancellation
                } catch (e: Exception) {
                    Log.e("SeriesDetailsScreen", "Error fetching series details", e)
                    isLoadingSeasons = false
                    isLoadingEpisodes = false
                }
            }
        } else {
            isLoadingSeasons = false
            isLoadingEpisodes = false
        }
    }
    
    // One loader owns the episode list. Capture the season ID for this request and
    // commit on the main thread only after the cancellable fetch completes.
    LaunchedEffect(item.Id, activeSeasonId, apiService, refreshTrigger, isLoadingSeasons) {
        if (apiService != null && activeSeasonId != null) {
            isLoadingEpisodes = true
            try {
                val seasonEpisodes = withContext(Dispatchers.IO) {
                    apiService.getEpisodes(item.Id, activeSeasonId)
                }
                episodes = seasonEpisodes
                isLoadingEpisodes = false
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("SeriesDetailsScreen", "Error fetching episodes for $activeSeasonId", e)
                isLoadingEpisodes = false
            }
        } else if (!isLoadingSeasons) {
            episodes = emptyList()
            isLoadingEpisodes = false
        }
    }
    // Track if we've already done the initial focus to prevent refocusing when seasons change
    var hasPerformedInitialFocus by androidx.compose.runtime.saveable.rememberSaveable(item.Id) { mutableStateOf(false) }

    // Sync focusedEpisode with updated episodes list (to get fresh UserData/streams after refresh)
    LaunchedEffect(episodes) {
        if (focusedEpisode != null) {
            val freshEpisode = episodes.find { it.Id == focusedEpisode?.Id }
            if (freshEpisode != null && freshEpisode != focusedEpisode) {
                Log.d("SeriesDetailsScreen", "Updating focusedEpisode with fresh data from new episodes list")
                focusedEpisode = freshEpisode
            }
        }
    }
    
    // Handle initial episode focus - find the season containing the episode and switch to it
    // The actual focus is handled by SeriesBottomContainer once the correct episodes are loaded
    LaunchedEffect(initialEpisodeId, seasons, apiService, isLoadingEpisodes) {
        if (initialEpisodeId != null && !hasPerformedInitialFocus && apiService != null && seasons.isNotEmpty() && !isLoadingEpisodes) {
            Log.d("SeriesDetailsScreen", "🔍 Looking for episode ID: $initialEpisodeId in ${seasons.size} seasons")
            
            // Check if the episode is in the currently loaded episodes
            val targetEpisode = episodes.find { it.Id == initialEpisodeId }
            if (targetEpisode != null) {
                // Episode is in current season - SeriesBottomContainer will handle focus
                Log.d("SeriesDetailsScreen", "✅ Episode found in current season: ${targetEpisode.Name} (S${targetEpisode.ParentIndexNumber}E${targetEpisode.IndexNumber})")
                focusedEpisode = targetEpisode
                hasPerformedInitialFocus = true
            } else {
                // Episode not in current season - search through all seasons to find it
                Log.d("SeriesDetailsScreen", "Episode not in current season (${episodes.size} episodes loaded), searching all ${seasons.size} seasons...")
                withContext(Dispatchers.IO) {
                    try {
                        var found = false
                        for ((index, season) in seasons.withIndex()) {
                            Log.d("SeriesDetailsScreen", "Searching season ${season.IndexNumber ?: index} (index $index, ID: ${season.Id})...")
                            val seasonEpisodes = apiService.getEpisodes(item.Id, season.Id)
                            Log.d("SeriesDetailsScreen", "  - Found ${seasonEpisodes.size} episodes in this season")
                            val episodeInSeason = seasonEpisodes.find { it.Id == initialEpisodeId }
                            if (episodeInSeason != null) {
                                // Found the episode in this season - switch to it
                                Log.d("SeriesDetailsScreen", "✅ Found episode in season ${season.IndexNumber ?: index}: ${episodeInSeason.Name}")
                                withContext(Dispatchers.Main) {
                                    selectedSeasonId = season.Id
                                    // The selected-season loader publishes episodes.
                                    focusedEpisode = episodeInSeason
                                    Log.d("SeriesDetailsScreen", "Switched to season index=$index with ${seasonEpisodes.size} episodes, SeriesBottomContainer will handle focus")
                                    hasPerformedInitialFocus = true
                                }
                                found = true
                                break
                            }
                        }
                        if (!found) {
                            Log.w("SeriesDetailsScreen", "❌ Could not find episode with ID: $initialEpisodeId in any of ${seasons.size} seasons")
                            withContext(Dispatchers.Main) {
                                hasPerformedInitialFocus = true // Mark as done even if not found to prevent retrying
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SeriesDetailsScreen", "Error finding initial episode", e)
                        withContext(Dispatchers.Main) {
                            hasPerformedInitialFocus = true // Mark as done on error to prevent retrying
                        }
                    }
                }
            }
        }
    }
    
    // Focus on Season 1 button when no initial episode is provided
    // This runs when the screen loads from "Recently Added" or similar without a specific episode
    LaunchedEffect(initialEpisodeId, seasons.isNotEmpty(), isLoadingSeasons) {
        if (initialEpisodeId == null && seasons.isNotEmpty() && !isLoadingSeasons) {
            // Wait for UI to be ready
            delay(300)
            try {
                firstSeasonFocusRequester.requestFocus()
                Log.d("SeriesDetailsScreen", "Focused on Season 1 button (no initial episode)")
            } catch (e: Exception) {
                Log.w("SeriesDetailsScreen", "Could not focus on Season 1 button: ${e.message}")
            }
        }
    }
    
    // State to trigger episodes refresh after marking as watched
    var refreshEpisodesAfterWatched by remember { mutableStateOf(false) }
    
    // Route watched-state refreshes through the same loader instead of racing it.
    LaunchedEffect(refreshEpisodesAfterWatched) {
        if (refreshEpisodesAfterWatched) {
            delay(300)
            refreshTrigger++
            refreshEpisodesAfterWatched = false
        }
    }
    val displayItem = itemDetails ?: item
    val selectedSeason = if (seasons.isNotEmpty() && selectedSeasonIndex < seasons.size) {
        seasons[selectedSeasonIndex]
    } else null
    
    // Determine which item to use for the backdrop
    // We prefer the focused season, then the selected season, then the series details
    // BUT only if they actually have a Backdrop image tag to avoid showing a blank background
    val backgroundItem = remember(focusedSeason, selectedSeason, itemDetails, item) {
        val focusedWithBackdrop = focusedSeason?.takeIf { !it.BackdropImageTags.isNullOrEmpty() || it.ImageTags?.containsKey("Backdrop") == true }
        val selectedWithBackdrop = selectedSeason?.takeIf { !it.BackdropImageTags.isNullOrEmpty() || it.ImageTags?.containsKey("Backdrop") == true }
        val detailsWithBackdrop = itemDetails?.takeIf { !it.BackdropImageTags.isNullOrEmpty() || it.ImageTags?.containsKey("Backdrop") == true }
        
        focusedWithBackdrop ?: selectedWithBackdrop ?: detailsWithBackdrop ?: item
    }

    val backdropUrls = remember(backgroundItem.Id, displayItem.Id, apiService) {
        listOf(backgroundItem.Id, displayItem.Id).distinct().flatMap { id ->
            listOf("Backdrop", "Thumb", "Primary").mapNotNull { type ->
                apiService?.getImageUrl(id, type, null, maxWidth = 1920, maxHeight = 1080, quality = 90)
            }
        }
    }
    var backdropIndex by remember(backdropUrls) { mutableStateOf(0) }
    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop background - absolutely positioned to fill entire screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }
                .align(Alignment.Center)
        ) {
            val imageUrl = backdropUrls.getOrNull(backdropIndex).orEmpty()

            // In dark mode, don't show background image - use Material dark background instead
            if (!darkModeEnabled) {
                Crossfade(
                    targetState = imageUrl,
                    animationSpec = tween(durationMillis = 500),
                    label = "background_fade"
                ) { currentUrl ->
                    if (currentUrl.isNotEmpty() && apiService != null) {
                        val backdropRequest = remember(context, currentUrl, apiService) {
                            ImageRequest.Builder(context)
                                .data(currentUrl)
                                .headers(apiService.getImageRequestHeaders())
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .crossfade(false)
                                .allowHardware(true)
                                .build()
                        }
                        AsyncImage(
                            model = backdropRequest,
                            onError = {
                                if (currentUrl == backdropUrls.getOrNull(backdropIndex) && backdropIndex < backdropUrls.lastIndex) backdropIndex++
                            },
                            contentDescription = displayItem.Name,
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
                
                // 50% darkness overlay - skip in dark mode
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.1f))
                )

                // Scrim gradient overlay (matching home screen)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .carouselGradient()
                )
            } else {
                // Dark mode: use Material dark background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        }
        
        // Content on top of backdrop
        // Check if logo is being used (takes more vertical space than text)
        val settings = remember { com.flex.elefin.jellyfin.AppSettings(context) }
        val useLogo = settings.useLogoForTitle
        // Increase container height when logo is used to prevent synopsis cropping
        val topContainerHeight = if (useLogo) 0.30f else 0.2581875f

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top container with logo, synopsis, and metadata
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(topContainerHeight)
                    .then(
                        if (showDebugOutlines) {
                            Modifier.border(4.dp, Color.Red)
                        } else {
                            Modifier
                        }
                    )
            ) {
                SeriesTopContainer(
                    item = displayItem,
                    itemDetails = itemDetails,
                    focusedEpisode = focusedEpisode,
                    focusedSeason = focusedSeason,
                    selectedSeasonIndex = selectedSeasonIndex,
                    apiService = apiService,
                    showDebugOutlines = showDebugOutlines
                )
            }

            // Middle container with season selector, episodes and play buttons
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(
                        if (showDebugOutlines) {
                            Modifier.border(4.dp, Color.Blue)
                        } else {
                            Modifier
                        }
                    )
            ) {
                SeriesBottomContainer(
                    item = displayItem,
                    episodes = episodes,
                    apiService = apiService,
                    isLoadingEpisodes = isLoadingEpisodes,
                    onEpisodeFocused = { episode ->
                        focusedEpisode = episode
                    },
                    onSeasonFocused = { season ->
                        focusedSeason = season
                    },
                    onEpisodesRefreshRequested = {
                        refreshEpisodesAfterWatched = true
                    },
                    initialEpisodeId = initialEpisodeId,
                    episodeFocusRequesters = episodeFocusRequesters,
                    showDebugOutlines = showDebugOutlines,
                    seasons = seasons,
                    selectedSeasonIndex = selectedSeasonIndex,
                    onSeasonSelected = { index ->
                        selectedSeasonId = seasons.getOrNull(index)?.Id
                    },
                    firstSeasonFocusRequester = firstSeasonFocusRequester,
                    onShowSettings = { showSettings = true }
                )
            }

        }
    }

    // Settings dialog (for TMDB key configuration) - moved to top level for full screen
    if (showSettings) {
        SettingsScreen(
            onBack = { showSettings = false },
            initialCategory = com.flex.elefin.screens.SettingsCategory.TRAILERS
        )
    }
}

@Composable
fun SeriesTopContainer(
    item: JellyfinItem,
    itemDetails: JellyfinItem?,
    focusedEpisode: JellyfinItem?,
    focusedSeason: JellyfinItem?,
    selectedSeasonIndex: Int,
    apiService: JellyfinApiService?,
    showDebugOutlines: Boolean = false
) {
    val context = LocalContext.current
    // Use itemDetails if available, otherwise fall back to item
    val displayItem = itemDetails ?: item
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (showDebugOutlines) {
                    Modifier.border(3.dp, Color.Red)
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 30.24.dp, end = 30.24.dp, top = 18.dp, bottom = 16.2.dp) // Reduced all padding by 10%
                .then(
                    if (showDebugOutlines) {
                        Modifier.border(2.dp, Color.Magenta)
                    } else {
                        Modifier
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title and Metadata - using home screen style for uniformity
            // When season is focused, show: Series Title, Series metadata, Series Synopsis
            // When episode is focused, show: Series Title, Episode Name, S1 E1 metadata, Episode Synopsis
            // When no episode/season focused, show: Series Title, Series metadata, Series Synopsis
            
            if (focusedEpisode != null) {
                // Episode-focused layout: Series Title, Episode Name, S1 E1 metadata, Synopsis
                // Title (Series name) or Logo
                TitleOrLogo(
                    item = item,
                    apiService = apiService,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                    ),
                    color = Color.White,
                    logoHeightDp = 31.5f, // 30% smaller than default (45 * 0.7 = 31.5)
                    modifier = Modifier
                        .padding(bottom = 0.dp) // Remove padding between title and episode name
                        .then(
                            if (showDebugOutlines) {
                                Modifier.border(1.dp, Color.Cyan)
                            } else {
                                Modifier
                            }
                        )
                )
                
                // Episode name below title
                Text(
                    text = focusedEpisode.Name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f // Match home screen size
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .padding(bottom = 0.dp) // Remove padding between episode name and metadata
                        .then(
                            if (showDebugOutlines) {
                                Modifier.border(1.dp, Color.Green)
                            } else {
                                Modifier
                            }
                        )
                )
                
                // Episode metadata - matching MovieDetailsScreen layout
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 0.dp) // Remove padding between metadata and synopsis
                        .then(
                            if (showDebugOutlines) {
                                Modifier.border(1.dp, Color.Yellow)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    EpisodeMetadataRow(
                        episode = focusedEpisode,
                        // Use episode's ParentIndexNumber (actual season number from API)
                        seasonNumber = focusedEpisode.ParentIndexNumber ?: (selectedSeasonIndex + 1),
                        apiService = apiService,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Synopsis (from episode) - single line with ellipsis, clickable to show full text
                focusedEpisode.Overview?.let { synopsis ->
                    if (synopsis.isNotEmpty()) {
                        var showSynopsisDialog by remember { mutableStateOf(false) }
                        
                            Text(
                                text = synopsis,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { showSynopsisDialog = true }
                        )
                        
                        // Synopsis dialog popup
                        if (showSynopsisDialog) {
                            Dialog(
                                onDismissRequest = { showSynopsisDialog = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.85f))
                                        .clickable { showSynopsisDialog = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .fillMaxHeight(0.6f),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = androidx.tv.material3.SurfaceDefaults.colors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(32.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Text(
                                                text = "Synopsis",
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                                            Text(
                                                text = synopsis,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                                lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.5f
                                            )
                                        }
                                    }
                                }
                                
                                BackHandler(enabled = showSynopsisDialog) {
                                    showSynopsisDialog = false
                                }
                            }
                        }
                    }
                }
            } else {
                // Series-focused layout: Series Title, Series metadata, Synopsis
                // Title or Logo
                TitleOrLogo(
                    item = displayItem,
                    apiService = apiService,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                    ),
                    color = Color.White,
                    logoHeightDp = 31.5f, // 30% smaller than default (45 * 0.7 = 31.5)
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .then(
                            if (showDebugOutlines) {
                                Modifier.border(1.dp, Color.Cyan)
                            } else {
                                Modifier
                            }
                        )
                )
                
                // Series metadata (Year, Runtime, Genre)
                val runtimeText = formatRuntime(displayItem.RunTimeTicks)
                val yearText = displayItem.ProductionYear?.toString() ?: ""
                val genreText = displayItem.Genres?.take(3)?.joinToString(", ") ?: ""
                
                Row(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .then(
                            if (showDebugOutlines) {
                                Modifier.border(1.dp, Color.Yellow)
                            } else {
                                Modifier
                            }
                        ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                
                // Synopsis (from series)
                displayItem.Overview?.let { synopsis ->
                    if (synopsis.isNotEmpty()) {
                        var showSeriesSynopsisDialog by remember { mutableStateOf(false) }
                        
                        Text(
                            text = synopsis,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f,
                                lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 0.8f * 1.25f
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clickable { showSeriesSynopsisDialog = true }
                        )
                        
                        if (showSeriesSynopsisDialog) {
                            Dialog(
                                onDismissRequest = { showSeriesSynopsisDialog = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.85f))
                                        .clickable { showSeriesSynopsisDialog = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .fillMaxHeight(0.6f),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = androidx.tv.material3.SurfaceDefaults.colors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(32.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Text(
                                                text = "Series Synopsis",
                                                style = MaterialTheme.typography.headlineMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(bottom = 16.dp)
                                            )
                                            Text(
                                                text = synopsis,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                                lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.5f
                                            )
                                        }
                                    }
                                }
                                
                                BackHandler(enabled = showSeriesSynopsisDialog) {
                                    showSeriesSynopsisDialog = false
                                }
                            }
                        }
                    }
                }
                
                // Extra spacer at the bottom to ensure last line of synopsis is not cropped
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SeriesSeasonSelectorContainer(
    seasons: List<JellyfinItem>,
    selectedSeasonIndex: Int,
    onSeasonSelected: (Int) -> Unit,
    showDebugOutlines: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (showDebugOutlines) {
                    Modifier.border(3.dp, Color.Yellow)
                } else {
                    Modifier
                }
            )
    ) {
        if (seasons.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 33.6.dp, vertical = 8.dp)
                    .then(
                        if (showDebugOutlines) {
                            Modifier.border(2.dp, Color(0xFFFFA500)) // Orange color
                        } else {
                            Modifier
                        }
                    )
            ) {
                // "Seasons" title
                Text(
                    text = "Seasons",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // Season buttons - left aligned
                // The selected season always shows full "Season X" text so users know which season they're browsing
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp) // Add padding for focus scale
                ) {
                    items(seasons.size) { index ->
                        val season = seasons[index]
                        var isFocused by remember { mutableStateOf(false) }
                        val isSelected = index == selectedSeasonIndex
                        // Use the actual season number from API, fallback to index+1 if not available
                        val seasonNumber = season.IndexNumber ?: (index + 1)
                        
                        // Show expanded button if focused OR if this is the selected season
                        val showExpanded = isFocused || isSelected
                        
                        Button(
                            onClick = { onSeasonSelected(index) },
                            modifier = Modifier
                                .then(
                                    if (showExpanded) {
                                        Modifier
                                            .wrapContentWidth()
                                            .height(28.dp)
                                    } else {
                                        Modifier.size(28.dp)
                                    }
                                )
                                .animateContentSize(
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                                .onFocusChanged { isFocused = it.isFocused }
                                .clip(CircleShape),
                            colors = ButtonDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            if (!showExpanded) {
                                // Show just the number when unfocused and not selected
                                Text(
                                    text = seasonNumber.toString(),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                                    )
                                )
                            } else {
                                // Show "Season X" when focused or selected
                                Text(
                                    text = "Season $seasonNumber",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp)
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
fun SeriesBottomContainer(
    item: JellyfinItem,
    episodes: List<JellyfinItem>,
    apiService: JellyfinApiService?,
    isLoadingEpisodes: Boolean,
    onEpisodeFocused: (JellyfinItem?) -> Unit,
    onSeasonFocused: (JellyfinItem?) -> Unit,
    onEpisodesRefreshRequested: () -> Unit,
    initialEpisodeId: String? = null,
    episodeFocusRequesters: MutableMap<String, FocusRequester>? = null,
    showDebugOutlines: Boolean = false,
    seasons: List<JellyfinItem> = emptyList(),
    selectedSeasonIndex: Int = 0,
    onSeasonSelected: (Int) -> Unit = {},
    firstSeasonFocusRequester: FocusRequester? = null,
    onShowSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { com.flex.elefin.jellyfin.AppSettings(context) }
    var showResumeDialog by remember { mutableStateOf<JellyfinItem?>(null) }
    var focusedEpisode by remember { mutableStateOf<JellyfinItem?>(null) }
    var lastFocusedEpisode by remember { mutableStateOf<JellyfinItem?>(null) } // Track last focused episode for buttons
    var showLongPressMenu by remember { mutableStateOf<JellyfinItem?>(null) }
    
    
    // FocusRequester for the last focused episode (to restore focus when pressing up)
    val lastFocusedEpisodeRequester = episodeFocusRequesters ?: remember { mutableMapOf<String, FocusRequester>() }
    
    // LazyListState for episodes row - needed to scroll to target episode before focusing
    val episodesLazyListState = rememberLazyListState()
    
    // Track if we've already done the initial focus to prevent refocusing when episodes change
    var hasPerformedInitialFocus by remember { mutableStateOf(false) }
    
    // Handle initial episode focus when episodes are loaded
    // Retries when episodes change (e.g., when season switches to the correct one)
    LaunchedEffect(initialEpisodeId, isLoadingEpisodes, episodes) {
        if (initialEpisodeId != null && !hasPerformedInitialFocus && episodes.isNotEmpty() && !isLoadingEpisodes) {
            val targetEpisode = episodes.find { it.Id == initialEpisodeId }
            if (targetEpisode != null) {
                // Find the index of the target episode
                val targetIndex = episodes.indexOfFirst { it.Id == initialEpisodeId }
                Log.d("SeriesBottomContainer", "Found target episode at index $targetIndex: ${targetEpisode.Name} (S${targetEpisode.ParentIndexNumber}E${targetEpisode.IndexNumber})")
                
                focusedEpisode = targetEpisode
                lastFocusedEpisode = targetEpisode
                onEpisodeFocused(targetEpisode)
                
                // First, scroll to the target episode to ensure it's composed
                if (targetIndex >= 0) {
                    Log.d("SeriesBottomContainer", "Scrolling to episode index $targetIndex...")
                    episodesLazyListState.scrollToItem(targetIndex)
                    // Wait for scroll and composition to complete
                    kotlinx.coroutines.delay(500)
                }
                
                // Request focus on the episode card with retries to ensure composable is ready
                // Get or create the focusRequester - it will be attached when EpisodeCard composes
                val focusRequester = lastFocusedEpisodeRequester.getOrPut(targetEpisode.Id) { FocusRequester() }
                // Wait for composition to complete
                kotlinx.coroutines.delay(500)
                
                // Try to request focus with retries
                var retries = 0
                val maxRetries = 20
                var success = false
                while (retries < maxRetries && !success) {
                    try {
                        focusRequester.requestFocus()
                        Log.d("SeriesBottomContainer", "✅ Successfully focused on initial episode: ${targetEpisode.Name}")
                        success = true
                    } catch (e: IllegalStateException) {
                        retries++
                        if (retries < maxRetries) {
                            // Wait longer between retries to ensure UI is ready
                            kotlinx.coroutines.delay(300)
                        } else {
                            Log.w("SeriesBottomContainer", "Failed to request focus on initial episode after $maxRetries retries: ${e.message}")
                        }
                    } catch (e: Exception) {
                        // Catch any other exceptions
                        Log.e("SeriesBottomContainer", "Unexpected error requesting focus: ${e.message}", e)
                        break
                    }
                }
                hasPerformedInitialFocus = true
            } else {
                // Episode not found in current episodes list - don't mark as performed
                // The parent LaunchedEffect in SeriesDetailsScreen will switch to the correct season
                // and episodes will update, triggering this LaunchedEffect again
                Log.d("SeriesBottomContainer", "Episode $initialEpisodeId not found in current episodes (${episodes.size} items), waiting for correct season to load...")
            }
        }
    }
    
    // Update lastFocusedEpisode and scroll position when episodes list changes or season changes
    LaunchedEffect(episodes, isLoadingEpisodes) {
        if (episodes.isNotEmpty() && !isLoadingEpisodes) {
            if (lastFocusedEpisode != null) {
                // Find the refreshed version of the currently focused episode
                val refreshedEpisode = episodes.find { it.Id == lastFocusedEpisode!!.Id }
                if (refreshedEpisode != null) {
                    lastFocusedEpisode = refreshedEpisode
                    // Also update focusedEpisode if it matches
                    if (focusedEpisode != null && focusedEpisode!!.Id == refreshedEpisode.Id) {
                        focusedEpisode = refreshedEpisode
                        onEpisodeFocused(refreshedEpisode)
                        Log.d("SeriesBottomContainer", "Updated focusedEpisode with refreshed data: PlayedPercentage=${refreshedEpisode.UserData?.PlayedPercentage}")
                    }
                    Log.d("SeriesBottomContainer", "Updated lastFocusedEpisode with refreshed data: PlayedPercentage=${refreshedEpisode.UserData?.PlayedPercentage}")
                } else if (initialEpisodeId == null) {
                    // Season changed (previous focused episode not in new episodes list)
                    val firstEp = episodes.first()
                    lastFocusedEpisode = firstEp
                    focusedEpisode = null
                    episodesLazyListState.scrollToItem(0)
                    Log.d("SeriesBottomContainer", "Season changed: reset focus to first episode (${firstEp.Name}) and scrolled to start")
                }
            } else if (initialEpisodeId == null) {
                // Initial load
                val firstEp = episodes.first()
                lastFocusedEpisode = firstEp
                episodesLazyListState.scrollToItem(0)
                Log.d("SeriesBottomContainer", "Initial load: set lastFocusedEpisode to first episode (${firstEp.Name})")
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (showDebugOutlines) {
                    Modifier.border(3.dp, Color.Blue)
                } else {
                    Modifier
                }
            )
    ) {
        // Keep seasons, episodes, actions, and cast fixed within the lower panel.
        // Scrolling this column caused D-pad focus on the actions to push the
        // Seasons heading underneath the static series header.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 33.6.dp, vertical = 8.dp) // Reduced from 16dp to move content up
                .then(
                    if (showDebugOutlines) {
                        Modifier.border(2.dp, Color.DarkGray)
                    } else {
                        Modifier
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(0.dp) // Remove spacing between items (episodes and buttons)
        ) {
            // Season selector row
            if (seasons.isNotEmpty()) {
                TabRow(
                    modifier = Modifier.fillMaxWidth(),
                    selectedTabIndex = selectedSeasonIndex,
                    separator = { Spacer(modifier = Modifier.width(11.dp)) },
                    indicator = { tabPositions, doesTabRowHaveFocus ->
                        if (selectedSeasonIndex in tabPositions.indices) {
                            TabRowDefaults.UnderlinedIndicator(
                                currentTabPosition = tabPositions[selectedSeasonIndex],
                                doesTabRowHaveFocus = doesTabRowHaveFocus
                            )
                        }
                    }
                ) {
                    seasons.forEachIndexed { index, season ->
                        var isFocused by remember { mutableStateOf(false) }
                        val seasonNumber = season.IndexNumber ?: (index + 1)

                        Tab(
                            selected = index == selectedSeasonIndex,
                            onFocus = { },
                            onClick = { onSeasonSelected(index) },
                            colors = TabDefaults.underlinedIndicatorTabColors(),
                            modifier = Modifier
                                .then(
                                    if (index == 0 && firstSeasonFocusRequester != null) {
                                        Modifier.focusRequester(firstSeasonFocusRequester)
                                    } else {
                                        Modifier
                                    }
                                )
                                .onFocusChanged { focusState ->
                                    isFocused = focusState.isFocused || focusState.hasFocus
                                    if (isFocused) onSeasonFocused(season) else onSeasonFocused(null)
                                }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                        val episodeToFocus = lastFocusedEpisode ?: episodes.firstOrNull()
                                        if (episodeToFocus != null) {
                                            try {
                                                lastFocusedEpisodeRequester[episodeToFocus.Id]?.requestFocus()
                                                true
                                            } catch (e: Exception) {
                                                Log.w("SeriesBottomContainer", "Failed to restore focus to episode: ${e.message}")
                                                false
                                            }
                                        } else false
                                    } else false
                                }
                                .then(
                                    if (isFocused) {
                                        Modifier.background(Color.White, RoundedCornerShape(4.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Text(
                                text = "Season $seasonNumber",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.90f
                                ),
                                color = if (isFocused) Color.Black else Color.White,
                                modifier = Modifier.padding(horizontal = 13.44.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
            
            // Episodes row and Play buttons remain together in the static layout.
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Episodes row
                if (isLoadingEpisodes) {
                    // Show empty space while loading (no text)
                    Spacer(modifier = Modifier.height(200.dp))
                } else if (episodes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No episodes available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .then(
                                if (showDebugOutlines) {
                                    Modifier.border(2.dp, Color.Green)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        LazyRow(
                            state = episodesLazyListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (showDebugOutlines) {
                                        Modifier.border(1.dp, Color.LightGray)
                                    } else {
                                        Modifier
                                    }
                                ),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp) // Add padding for focus scale on edge cards
                        ) {
                        items(
                            items = episodes,
                            key = { episode -> episode.Id }
                        ) { episode ->
                            EpisodeCard(
                                episode = episode,
                                apiService = apiService,
                                onClick = {
                                    // Get stored subtitle preference for this episode
                                    val subtitlePreference = settings.getSubtitlePreference(episode.Id)
                                    
                                    // Check if episode is resumable
                                    val isResumable = episode.UserData?.PositionTicks != null && episode.UserData?.PositionTicks!! > 0
                                    if (isResumable) {
                                        // Show resume dialog
                                        showResumeDialog = episode
                                    } else {
                                        // Play from start
                                    // Launch video player - keep SeriesDetailsActivity in back stack so back button returns here
                                    val intent = JellyfinVideoPlayerActivity.createIntent(
                                        context,
                                        episode.Id,
                                        0L,
                                        subtitlePreference
                                    )
                                    context.startActivity(intent)
                                    // Don't finish - let back button return to series details screen
                                    }
                                },
                                onFocusChanged = { isFocused ->
                                    if (isFocused) {
                                        focusedEpisode = episode
                                        lastFocusedEpisode = episode // Update last focused episode
                                        onEpisodeFocused(episode)
                                    } else {
                                        focusedEpisode = null
                                        // Don't clear lastFocusedEpisode - keep it for the buttons
                                            // Don't call onEpisodeFocused(null) - keep showing the last focused episode's synopsis
                                    }
                                },
                                onLongPress = {
                                    showLongPressMenu = episode
                                },
                                focusRequester = lastFocusedEpisodeRequester.getOrPut(episode.Id) { FocusRequester() },
                                showDebugOutlines = showDebugOutlines
                            )
                        }
                        }
                        
                        // Don't automatically request focus on first episode - let user navigate manually
                }
            }
            
            // Episode Action Buttons - always show for last focused episode
            // If no episode has been focused yet, use the first episode
            val episodeForButtons = lastFocusedEpisode ?: episodes.firstOrNull()
            episodeForButtons?.let { currentEpisode ->
                    // Wrap in Box to handle up key event without interfering with button focus
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                    // Restore focus to last focused episode
                                    try {
                                        lastFocusedEpisodeRequester[currentEpisode.Id]?.requestFocus()
                                    } catch (e: IllegalStateException) {
                                        Log.w("SeriesBottomContainer", "Failed to restore focus to episode: ${e.message}")
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                            .then(
                                if (showDebugOutlines) {
                                    Modifier.border(2.dp, Color.Cyan)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        EpisodeActionButtonsRow(
                            episode = currentEpisode,
                            seriesItem = item,
                            apiService = apiService,
                            onShowSettings = onShowSettings,
                            modifier = Modifier.fillMaxWidth(),
                            onEpisodeUpdated = { updatedEpisode ->
                                // Trigger a full refresh of the episodes list from the API
                                onEpisodesRefreshRequested()
                            }
                        )
                        }
                    }
            }

            // Cast follows the episode actions in the same bounded scroll area, so
            // the action row is never clipped by a competing fixed-height section.
            val castMembers = item.People?.filter { it.Type == "Actor" } ?: emptyList()
            if (castMembers.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Cast",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        items(castMembers) { person ->
                            CastMemberCard(
                                person = person,
                                apiService = apiService,
                                cardSize = 54.dp
                            )
                        }
                    }
                }
            }
        }
        
        // Long press menu dialog
        showLongPressMenu?.let { episode ->
            EpisodeLongPressMenu(
                episode = episode,
                apiService = apiService,
                settings = settings,
                onDismiss = { 
                    showLongPressMenu = null
                },
                onSubtitleSelected = { subtitleIndex ->
                    settings.setSubtitlePreference(episode.Id, subtitleIndex)
                }
            )
        }
        
        // Resume dialog
        showResumeDialog?.let { episode ->
            val storedSubtitlePreference = settings.getSubtitlePreference(episode.Id)
            ResumeEpisodeDialog(
                episode = episode,
                onDismiss = { showResumeDialog = null },
                onResume = {
                    val resumePositionMs = episode.UserData?.PositionTicks?.let { it / 10_000 } ?: 0L
                    val intent = JellyfinVideoPlayerActivity.createIntent(
                        context,
                        episode.Id,
                        resumePositionMs,
                        storedSubtitlePreference
                    )
                    context.startActivity(intent)
                    showResumeDialog = null
                },
                onPlayFromStart = {
                    val storedSubtitlePreference = settings.getSubtitlePreference(episode.Id)
                    val intent = JellyfinVideoPlayerActivity.createIntent(
                        context,
                        episode.Id,
                        0L,
                        storedSubtitlePreference
                    )
                    context.startActivity(intent)
                    showResumeDialog = null
                }
            )
        }
    }
}

@Composable
fun EpisodeCard(
    episode: JellyfinItem,
    apiService: JellyfinApiService?,
    onClick: () -> Unit,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    showDebugOutlines: Boolean = false
) {
    val context = LocalContext.current
    val settings = remember { com.flex.elefin.jellyfin.AppSettings(context) }
    val scope = rememberCoroutineScope()
    var keyDownTime by remember { mutableStateOf<Long?>(null) }
    var longPressJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val longPressDuration = remember(settings.longPressDurationSeconds) {
        settings.longPressDurationSeconds * 1000L // Convert seconds to milliseconds
    }
    val imageCandidates = remember(episode, apiService) {
        buildList {
            fun candidate(id: String, type: String, tag: String? = null) {
                apiService?.getImageUrl(id, type, tag, maxWidth = 640, maxHeight = 360)?.let { add(it) }
            }
            // Tags can be omitted from episode lists; try the endpoints as well as tagged artwork.
            candidate(episode.Id, "Thumb", episode.ImageTags?.get("Thumb"))
            candidate(episode.Id, "Primary", episode.ImageTags?.get("Primary"))
            episode.SeriesId?.let { seriesId ->
                candidate(seriesId, "Thumb")
                candidate(seriesId, "Backdrop")
                candidate(seriesId, "Primary")
            }
        }.distinct()
    }
    var imageCandidateIndex by remember(imageCandidates) { mutableStateOf(0) }
    val imageUrl = imageCandidates.getOrNull(imageCandidateIndex).orEmpty()
    
    StandardCardContainer(
        modifier = Modifier
            .width(185.dp) // 20% bigger (154 * 1.20 = 184.8, rounded to 185)
            .then(
                if (showDebugOutlines) {
                    Modifier.border(2.dp, Color.Cyan)
                } else {
                    Modifier
                }
            )
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { focusState ->
                onFocusChanged?.invoke(focusState.isFocused)
            }
            .onKeyEvent { keyEvent ->
                when {
                    // Key down - start long press timer
                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter -> {
                        keyDownTime = System.currentTimeMillis()
                        // Start long press job
                        longPressJob?.cancel()
                        longPressJob = scope.launch {
                            delay(longPressDuration)
                            // Check if key is still down
                            if (keyDownTime != null) {
                                onLongPress?.invoke()
                            }
                        }
                        false // Don't consume the event, let normal click work
                    }
                    // Key up - cancel long press if not long enough
                    keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Enter -> {
                        val duration = keyDownTime?.let { System.currentTimeMillis() - it }
                        keyDownTime = null
                        longPressJob?.cancel()
                        longPressJob = null
                        // If duration was less than long press, don't prevent normal click
                        if (duration != null && duration < longPressDuration) {
                            false // Allow normal click
                        } else {
                            true // Consume event if long press was triggered
                        }
                    }
                    else -> false
                }
            },
        imageCard = {
            Card(
                onClick = onClick,
                interactionSource = it,
                colors = CardDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            ) {
                Box(modifier = Modifier.aspectRatio(16f / 9f)) {
                    if (imageUrl.isNotEmpty() && apiService != null) {
                            val imageContext = LocalContext.current
                            val episodeImageRequest = remember(imageContext, imageUrl, apiService) {
                                ImageRequest.Builder(imageContext)
                                    .data(imageUrl)
                                    .headers(apiService.getImageRequestHeaders())
                                    .crossfade(false)
                                    .build()
                            }
                            AsyncImage(
                                model = episodeImageRequest,
                                onError = {
                                    if (imageCandidates.getOrNull(imageCandidateIndex) == imageUrl && imageCandidateIndex < imageCandidates.lastIndex) {
                                        imageCandidateIndex++
                                    }
                                },
                                placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                                contentDescription = episode.Name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                    }
                    
                    // Watched indicator - checkmark in black box (top-left corner)
                    // Check Played boolean first, then PlayedPercentage as fallback
                    val isWatched = (episode.UserData?.Played == true) ||
                                   (episode.UserData?.PlayedPercentage == 100.0)
                    val progress = episode.UserData?.PlayedPercentage?.toFloat()?.div(100f) ?: 0f
                    
                    if (isWatched) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Watched",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    // Episode number badge in top right corner
                    val episodeNumber = episode.IndexNumber ?: 0
                    if (episodeNumber > 0) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "E$episodeNumber",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = MaterialTheme.typography.labelMedium.fontSize * 0.9f
                                ),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Progress bar at the bottom
                    if (progress > 0f && progress <= 1f) {
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
        },
        title = { }
    )
}

@Composable
fun ResumeEpisodeDialog(
    episode: JellyfinItem,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onPlayFromStart: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .wrapContentSize()
                .widthIn(min = 250.dp, max = 400.dp),
            shape = RoundedCornerShape(12.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = episode.Name ?: "Episode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                Row(
                    modifier = Modifier.wrapContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val resumeFocusRequester = remember { FocusRequester() }
                    
                    // Request focus on Resume button by default
                    LaunchedEffect(Unit) {
                        resumeFocusRequester.requestFocus()
                    }
                    
                    Button(
                        onClick = onResume,
                        modifier = Modifier
                            .focusRequester(resumeFocusRequester)
                            .widthIn(min = 120.dp, max = 150.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "Resume",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.6f
                            )
                        )
                    }
                    
                    Button(
                        onClick = onPlayFromStart,
                        modifier = Modifier.widthIn(min = 120.dp, max = 180.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "Play from Start",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.6f
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeMetadataRow(
    episode: JellyfinItem,
    seasonNumber: Int,
    apiService: JellyfinApiService?,
    modifier: Modifier = Modifier
) {
    // Fetch episode details to get MediaSources for complete metadata
    var episodeDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    
    LaunchedEffect(episode.Id, episode.UserData?.PlayedPercentage, apiService) {
        // Always start with the episode prop (which has the latest UserData from refreshed episodes list)
        episodeDetails = episode
        
        // Then fetch full details in background for MediaSources, but preserve UserData from episode prop
        if (apiService != null) {
            withContext(Dispatchers.IO) {
                try {
                    val fetchedDetails = apiService.getItemDetails(episode.Id)
                    // Use fetched details but preserve the episode prop's UserData (which is fresher after refresh)
                    if (fetchedDetails != null && episode.UserData != null) {
                        episodeDetails = fetchedDetails.copy(
                            UserData = episode.UserData
                        )
                    } else {
                        episodeDetails = fetchedDetails ?: episode
                    }
                    Log.d("EpisodeMetadataRow", "Fetched episode details for ${episode.Name}, using UserData PlayedPercentage=${episode.UserData?.PlayedPercentage}")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Normal cancellation when composable leaves composition - don't log as error
                    throw e // Re-throw to respect cancellation
                } catch (e: Exception) {
                    Log.e("EpisodeMetadataRow", "Error fetching episode details", e)
                    // Use episode prop directly (has latest UserData)
                    episodeDetails = episode
                }
            }
        } else {
            episodeDetails = episode
        }
    }
    
    // Use fetched details if available, otherwise use provided episode
    // Always prioritize episode prop's UserData for watched status (it's fresher after refresh)
    val displayEpisode = episodeDetails ?: episode
    
    // Get media information from fetched details
    val videoStream = displayEpisode.MediaSources?.firstOrNull()?.MediaStreams?.firstOrNull { it.Type == "Video" }
    val audioStream = displayEpisode.MediaSources?.firstOrNull()?.MediaStreams?.firstOrNull { it.Type == "Audio" }
    
    // Prepare metadata values
    val episodeNumber = episode.IndexNumber ?: 0
    val runtimeText = formatRuntime(displayEpisode.RunTimeTicks)
    val dateText = formatDate(displayEpisode.PremiereDate ?: displayEpisode.DateCreated)
    
    // HDR/SDR detection
    val hdrStatus = videoStream?.let { stream ->
        val is4K = (stream.Width != null && stream.Width!! >= 3840) || (stream.Height != null && stream.Height!! >= 2160)
        val isHEVC = stream.Codec?.contains("hevc", ignoreCase = true) == true || 
                    stream.Codec?.contains("h265", ignoreCase = true) == true
        if (is4K && isHEVC) "HDR" else "SDR"
    }
    
    // Text metadata line: S1 E1 Nov 1, 2025 45m with metadata boxes
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Text metadata (Season/Episode, Date, Runtime)
        // Season and Episode
        Text(
            text = "S${seasonNumber} E${episodeNumber}",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
            ),
            color = Color.White.copy(alpha = 0.9f)
        )
        
        // Date
        if (dateText.isNotEmpty()) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                ),
                color = Color.White.copy(alpha = 0.9f)
            )
        }
        
        // Runtime
        if (runtimeText.isNotEmpty()) {
            Text(
                text = runtimeText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                ),
                color = Color.White.copy(alpha = 0.9f)
            )
        }
        
        // Metadata boxes: Maturity Rating, IMDb Rating, Resolution, SDR/HDR, Language
        // Nested Row with 8.dp spacing between boxes (parent Row has 12.dp spacing for text items)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Maturity Rating
            displayEpisode.OfficialRating?.let { rating ->
                MetadataBox(text = rating)
            }
            
            // Review Rating with Rotten Tomatoes icons support
            RatingDisplay(
                item = displayEpisode,
                communityRating = displayEpisode.CommunityRating,
                criticRating = displayEpisode.CriticRating
            )
            
            // Resolution
            videoStream?.let { stream ->
                formatResolution(stream.Width, stream.Height)?.let {
                    MetadataBox(text = it)
                }
            }
            
            // HDR/SDR
            hdrStatus?.let {
                MetadataBox(text = it)
            }
            
            // Language with Audio Codec and Channel Layout
            audioStream?.let { stream ->
                val language = stream.Language?.uppercase() ?: ""
                val codec = stream.Codec?.uppercase() ?: ""
                val channelLayout = stream.ChannelLayout ?: ""
                
                // Format as "Language (CODEC CHANNEL)" or "Language (CODEC)" or just "Language"
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
}

@Composable
fun EpisodeActionButtonsRow(
    episode: JellyfinItem,
    seriesItem: JellyfinItem,
    apiService: JellyfinApiService?,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onEpisodeUpdated: ((JellyfinItem) -> Unit)? = null
) {
    // Store the latest episode to watch for updates
    var currentEpisode by remember { mutableStateOf(episode) }
    
    // Update current episode when episode prop changes (check UserData to detect watched status changes)
    LaunchedEffect(episode.Id, episode.UserData?.PlayedPercentage) {
        currentEpisode = episode
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { com.flex.elefin.jellyfin.AppSettings(context) }
    val useAnimatedButton = settings.useAnimatedPlayButton
    
    // Use currentEpisode instead of episode for dynamic updates
    val displayEpisode = currentEpisode
    val isResumable = displayEpisode.UserData?.PositionTicks != null && displayEpisode.UserData?.PositionTicks!! > 0
    val resumePositionMs = displayEpisode.UserData?.PositionTicks?.let { it / 10_000 } ?: 0L
    
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var storedSubtitleIndex by remember { mutableStateOf<Int?>(settings.getSubtitlePreference(displayEpisode.Id)) }
    var storedAudioIndex by remember { mutableStateOf<Int?>(settings.getAudioPreference(displayEpisode.Id)) }
    
    // Refresh subtitle and audio preferences when returning to this screen
    LaunchedEffect(displayEpisode.Id) {
        storedSubtitleIndex = settings.getSubtitlePreference(displayEpisode.Id)
        storedAudioIndex = settings.getAudioPreference(displayEpisode.Id)
    }
    
    // Fetch episode details to get MediaSources and UserData for time remaining
    var episodeDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    
    LaunchedEffect(episode.Id, apiService) {
        if (apiService != null) {
            withContext(Dispatchers.IO) {
                try {
                    episodeDetails = apiService.getItemDetails(episode.Id)
                } catch (e: Exception) {
                    Log.e("EpisodeActionButtons", "Error fetching episode details", e)
                }
            }
        }
    }
    
    // Check if media has multiple audio tracks
    val audioStreamCount = remember(episodeDetails?.MediaSources) {
        episodeDetails?.MediaSources?.firstOrNull()?.MediaStreams
            ?.count { it.Type == "Audio" } ?: 0
    }
    val hasMultiAudio = audioStreamCount > 1
    
    // Calculate default subtitle/audio indices if none selected
    val defaultSubtitleIndex = null
    
    val defaultAudioIndex = remember(episodeDetails?.MediaSources) {
        episodeDetails?.MediaSources?.firstOrNull()?.MediaStreams?.let { streams ->
             com.flex.elefin.player.preferredAudioIndex(streams)
        }
    }

    // Trailer state
    var trailerKey by remember { mutableStateOf<String?>(null) }
    
    // Fetch trailer (TMDB Direct Fallback)
    LaunchedEffect(seriesItem.Id, settings.tmdbApiKey) {
        trailerKey = null // Reset
        
        // Fallback to TMDB directly if key configured
        if (settings.tmdbApiKey.isNotBlank()) {
             val tmdbId = seriesItem.ProviderIds?.get("Tmdb") ?: seriesItem.ProviderIds?.get("tmdb")
             
             if (tmdbId != null) {
                  try {
                      withContext(Dispatchers.IO) {
                          // Determine audio language from the current episode to request localized trailers for the series
                          val audioLang = displayEpisode.MediaSources?.firstOrNull()?.MediaStreams
                              ?.firstOrNull { it.Type == "Audio" && it.IsDefault == true }?.Language
                              ?: displayEpisode.MediaSources?.firstOrNull()?.MediaStreams
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
                                  Log.w("EpisodeActionButtons", "Could not parse language: $audioLang")
                              }
                          }

                          val videos = TmdbApiService.getVideos(
                              tmdbId = tmdbId.toInt(),
                              type = "tv",
                              apiKey = settings.tmdbApiKey,
                              language = iso639Code
                          )
                          // Prefer official trailers, matching language
                          val trailer = videos.firstOrNull { it.site == "YouTube" && it.type == "Trailer" && it.official && (iso639Code == null || it.iso6391 == iso639Code) }
                              ?: videos.firstOrNull { it.site == "YouTube" && it.type == "Trailer" && it.official }
                              ?: videos.firstOrNull { it.site == "YouTube" && it.type == "Trailer" }
                              ?: videos.firstOrNull { it.site == "YouTube" }
                          
                          if (trailer != null) {
                              trailerKey = trailer.key
                          }
                      }
                  } catch (e: Exception) {
                      Log.e("EpisodeActionButtons", "Error fetching TMDB trailer", e)
                  }
             }
        }
    }
    
    val playButtonLabel = if (isResumable) "Play From Start" else "Play"
    
    Box(
        modifier = modifier
            .padding(top = 12.dp, bottom = 0.dp) // 25% less top padding (16 * 0.75 = 12), no bottom padding
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.6.dp, bottom = 8.dp), // 30% less top padding (8 * 0.7 = 5.6)
            horizontalArrangement = Arrangement.spacedBy(11.2.dp), // 30% less spacing (16 * 0.7 = 11.2)
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Play buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(11.2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            // Resume button (only show if resumable, on the left)
            if (isResumable) {
                if (useAnimatedButton) {
                    AnimatedPlayButton(
                        onClick = {
                            val intent = JellyfinVideoPlayerActivity.createIntent(
                                context = context,
                                itemId = displayEpisode.Id,
                                resumePositionMs = resumePositionMs,
                                subtitleStreamIndex = storedSubtitleIndex ?: defaultSubtitleIndex,
                                audioStreamIndex = storedAudioIndex ?: defaultAudioIndex
                            )
                            context.startActivity(intent)
                        },
                        label = "Resume",
                        containerColor = androidx.compose.ui.graphics.Color.White,
                        contentColor = androidx.compose.ui.graphics.Color.Black
                    )
                } else {
                    var resumeFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            val intent = JellyfinVideoPlayerActivity.createIntent(
                                context = context,
                                itemId = displayEpisode.Id,
                                resumePositionMs = resumePositionMs,
                                subtitleStreamIndex = storedSubtitleIndex ?: defaultSubtitleIndex,
                                audioStreamIndex = storedAudioIndex ?: defaultAudioIndex
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .expandingActionButton(focused = resumeFocused)
                    .onFocusChanged { resumeFocused = it.isFocused },
                        colors = ButtonDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = androidx.compose.ui.graphics.Color.White,
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedContentColor = androidx.compose.ui.graphics.Color.Black
                        ),
                        shape = ButtonDefaults.shape(CircleShape),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Resume", modifier = Modifier.size(14.3.dp))
                        if (resumeFocused) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume", style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f), modifier = Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            }

            // Play button - always shows, plays from beginning
            if (useAnimatedButton) {
                AnimatedPlayButton(
                    onClick = {
                        val intent = JellyfinVideoPlayerActivity.createIntent(
                            context = context,
                            itemId = displayEpisode.Id,
                            resumePositionMs = 0L,
                            subtitleStreamIndex = storedSubtitleIndex ?: defaultSubtitleIndex,
                            audioStreamIndex = storedAudioIndex ?: defaultAudioIndex
                        )
                        context.startActivity(intent)
                    },
                    label = playButtonLabel,
                    containerColor = androidx.compose.ui.graphics.Color.White,
                    contentColor = androidx.compose.ui.graphics.Color.Black
                )
            } else {
                var playFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        val intent = JellyfinVideoPlayerActivity.createIntent(
                            context = context,
                            itemId = displayEpisode.Id,
                            resumePositionMs = 0L,
                            subtitleStreamIndex = storedSubtitleIndex ?: defaultSubtitleIndex,
                            audioStreamIndex = storedAudioIndex ?: defaultAudioIndex
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .expandingActionButton(focused = playFocused)
                    .onFocusChanged { playFocused = it.isFocused },
                    colors = ButtonDefaults.colors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = androidx.compose.ui.graphics.Color.White,
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContentColor = androidx.compose.ui.graphics.Color.Black
                    ),
                    shape = ButtonDefaults.shape(CircleShape),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(14.3.dp))
                    if (playFocused) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(playButtonLabel, style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f), modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
            

        // Audio track button (only show if media has multiple audio tracks)
            if (hasMultiAudio) {
            var audioFocused by remember { mutableStateOf(false) }
            
            Button(
                onClick = {
                    showAudioDialog = true
                },
                modifier = Modifier
                    .expandingActionButton(focused = audioFocused, expandLeft = true)
                    .onFocusChanged { audioFocused = it.isFocused },
                colors = ButtonDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContentColor = androidx.compose.ui.graphics.Color.Black
                ),
                shape = ButtonDefaults.shape(CircleShape),
                contentPadding = PaddingValues(8.dp)
            ) {
                if (audioFocused) {
                    Text(
                        text = "Audio",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                        Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Audio Track",
                    modifier = Modifier.size(14.3.dp)
                )
                }
            }
            
            // Subtitles button
            var subtitleFocused by remember { mutableStateOf(false) }
            
            Button(
                onClick = {
                    showSubtitleDialog = true
                },
                modifier = Modifier
                    .expandingActionButton(focused = subtitleFocused, expandLeft = true)
                    .onFocusChanged { subtitleFocused = it.isFocused },
                colors = ButtonDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContentColor = androidx.compose.ui.graphics.Color.Black
                ),
                shape = ButtonDefaults.shape(CircleShape),
                contentPadding = PaddingValues(8.dp)
            ) {
                if (subtitleFocused) {
                    Text(
                        text = "Subtitles",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Subtitles",
                    modifier = Modifier.size(14.3.dp)
                )
            }
            
            // Trailer button - always shows
            var trailerFocused by remember { mutableStateOf(false) }
            
            Button(
                onClick = {
                    TrailerLauncher.launchTrailer(
                        context, apiService, seriesItem.Id, seriesItem.Name ?: "", trailerKey, scope
                    )
                },
                modifier = Modifier
                    .expandingActionButton(focused = trailerFocused, expandLeft = true)
                    .onFocusChanged { trailerFocused = it.isFocused },
                colors = ButtonDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContentColor = androidx.compose.ui.graphics.Color.Black
                ),
                shape = ButtonDefaults.shape(CircleShape),
                contentPadding = PaddingValues(8.dp)
            ) {
                if (trailerFocused) {
                    Text(
                        text = "Watch Trailer",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = "Watch Trailer",
                    modifier = Modifier.size(14.3.dp)
                )
            }

            // Mark as Watched/Unwatched button
            val isAlreadyWatched = (episode.UserData?.Played == true) ||
                                  (episodeDetails?.UserData?.Played == true) ||
                                  (displayEpisode.UserData?.Played == true) ||
                                  (episode.UserData?.PlayedPercentage == 100.0) ||
                                  (episodeDetails?.UserData?.PlayedPercentage == 100.0) ||
                                  (displayEpisode.UserData?.PlayedPercentage == 100.0)
            
            var watchedFocused by remember { mutableStateOf(false) }
            
            Button(
                onClick = {
                    apiService?.let { service ->
                        scope.launch(Dispatchers.IO) {
                            try {
                                val success = if (isAlreadyWatched) {
                                    // Mark as unwatched
                                    service.markAsUnwatched(displayEpisode.Id)
                                } else {
                                    // Mark as watched
                                    service.markAsWatched(displayEpisode.Id)
                                }
                                
                                if (success) {
                                    val action = if (isAlreadyWatched) "unwatched" else "watched"
                                    Log.d("SeriesDetails", "Episode ${displayEpisode.Id} marked as $action")
                                    // Add a small delay to let the server process the status change
                                    delay(800) // Wait 800ms for server to update
                                    
                                    val refreshedEpisode = service.getItemDetails(displayEpisode.Id)
                                    if (refreshedEpisode != null) {
                                        Log.d("SeriesDetails", "Refreshed episode UserData: Played=${refreshedEpisode.UserData?.Played}, PlayedPercentage=${refreshedEpisode.UserData?.PlayedPercentage}")
                                        // Update local state immediately with refreshed data
                                        currentEpisode = refreshedEpisode
                                        // Also update episodeDetails for metadata display
                                        episodeDetails = refreshedEpisode
                                        // Notify parent to trigger a full episodes list refresh
                                        withContext(Dispatchers.Main) {
                                            onEpisodeUpdated?.invoke(refreshedEpisode)
                                        }
                                        Log.d("SeriesDetails", "Episode marked as $action, triggering episodes list refresh")
                                    }
                                } else {
                                    val action = if (isAlreadyWatched) "unwatched" else "watched"
                                    Log.w("SeriesDetails", "Failed to mark episode as $action")
                                }
                            } catch (e: Exception) {
                                val action = if (isAlreadyWatched) "unwatched" else "watched"
                                Log.e("SeriesDetails", "Error marking episode as $action", e)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .expandingActionButton(focused = watchedFocused, expandLeft = true)
                    .onFocusChanged { watchedFocused = it.isFocused },
                colors = ButtonDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContentColor = androidx.compose.ui.graphics.Color.Black
                ),
                shape = ButtonDefaults.shape(CircleShape),
                contentPadding = PaddingValues(8.dp)
            ) {
                if (watchedFocused) {
                    Text(
                        text = if (isAlreadyWatched) "Mark As Unwatched" else "Mark As Watched",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = if (isAlreadyWatched) "Mark As Unwatched" else "Mark As Watched",
                    modifier = Modifier.size(14.3.dp)
                )
            }
            
            // Right side: Selected subtitle and Watched indicator
            Spacer(modifier = Modifier.weight(1f))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Get selected subtitle stream
                val subtitleStream = episodeDetails?.MediaSources?.firstOrNull()?.MediaStreams?.firstOrNull { 
                    it.Type == "Subtitle" && it.Index == storedSubtitleIndex 
                }
                
                // Selected Subtitles
                subtitleStream?.let { stream ->
                    val subtitleName = stream.DisplayTitle ?: stream.Language ?: "Unknown"
                    MetadataBox(text = subtitleName, icon = Icons.Default.Language)
                }
            }
        }
    }
    
    // Subtitle selection dialog
    if (showSubtitleDialog) {
        EpisodeSubtitleSelectionDialog(
            item = episode,
            apiService = apiService,
            onDismiss = { showSubtitleDialog = false },
            onSubtitleSelected = { subtitleIndex ->
                storedSubtitleIndex = subtitleIndex  // ⭐ Update state immediately for UI refresh
                settings.setSubtitlePreference(episode.Id, subtitleIndex)
                showSubtitleDialog = false
                
                // ⭐ Pre-download subtitle when selected (before playback starts)
                if (subtitleIndex != null && apiService != null) {
                    scope.launch {
                        try {
                            val mediaSource = episode.MediaSources?.firstOrNull()
                            val mediaSourceId = mediaSource?.Id ?: episode.Id
                            val subtitleStream = mediaSource?.MediaStreams
                                ?.find { it.Type == "Subtitle" && it.Index == subtitleIndex }
                            
                            if (subtitleStream != null) {
                                android.util.Log.d("SeriesDetails", "Pre-downloading selected subtitle: ${subtitleStream.DisplayTitle}")
                                com.flex.elefin.player.SubtitleDownloader.downloadSubtitle(
                                    context = context,
                                    apiService = apiService,
                                    itemId = episode.Id,
                                    mediaSourceId = mediaSourceId,
                                    stream = subtitleStream
                                )
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SeriesDetails", "Error pre-downloading subtitle", e)
                        }
                    }
                }
            }
        )
    }
    
    // Audio track selection dialog
    if (showAudioDialog) {
        EpisodeAudioSelectionDialog(
            item = episode,
            apiService = apiService,
            onDismiss = { showAudioDialog = false },
            onAudioSelected = { audioIndex ->
                Log.d("EpisodeAudioDialog", "onAudioSelected callback: saving audioIndex=$audioIndex for episode=${episode.Id}")
                settings.setAudioPreference(episode.Id, audioIndex)
                storedAudioIndex = audioIndex
                showAudioDialog = false
            }
        )
    }
}



// Metadata box component
@Composable
private fun MetadataBox(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Box(
        modifier = Modifier
            .background(Color.Black, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.White
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

// Time remaining indicator with circular progress bar
@Composable
private fun TimeRemainingIndicator(item: JellyfinItem) {
    // Get runtime and current position
    val runtimeTicks = item.RunTimeTicks ?: return
    val positionTicks = item.UserData?.PositionTicks ?: 0L
    
    // Don't show if not started or runtime is invalid
    if (runtimeTicks <= 0 || positionTicks <= 0) return
    
    // Calculate time remaining (10,000,000 ticks = 1 second)
    val remainingTicks = runtimeTicks - positionTicks
    if (remainingTicks <= 0) return // Don't show if completed
    
    // Convert ticks to milliseconds for accurate calculation
    val runtimeMs = runtimeTicks / 10_000
    val positionMs = positionTicks / 10_000
    val remainingMs = remainingTicks / 10_000
    
    // Calculate progress percentage (0.0 to 1.0)
    val progress = if (runtimeMs > 0) {
        min(1.0f, positionMs.toFloat() / runtimeMs.toFloat())
    } else {
        0.0f
    }
    
    // Convert remaining time to hours, minutes, and seconds
    val totalSeconds = (remainingMs / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    // Format time remaining text with better precision
    val timeText = when {
        hours > 0 -> {
            if (minutes > 0) {
                "${hours}hr ${minutes}m left"
            } else {
                "${hours}hr left"
            }
        }
        minutes > 0 -> {
            if (minutes >= 5) {
                "${minutes}m left"
            } else {
                // Show seconds for less than 5 minutes
                "${minutes}m ${seconds}s left"
            }
        }
        seconds > 0 -> "${seconds}s left"
        else -> return // Don't show if less than a second
    }
    
    // Circular progress bar dimensions - match metadata item height
    val progressSize = 14.dp
    val strokeWidth = 1.5.dp
    
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular progress bar
            Box(
                modifier = Modifier.size(progressSize),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidthPx = strokeWidth.toPx()
                    val radius = (size.minDimension - strokeWidthPx) / 2
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius2 = radius * 2
                    
                    // Background circle (semi-transparent white)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                    
                    // Progress arc (white) - shows how much has been watched
                    if (progress > 0f && progress < 1f) {
                        val sweepAngle = 360f * progress
                        drawArc(
                            color = Color.White,
                            startAngle = -90f, // Start from top (12 o'clock)
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius2, radius2),
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        )
                    } else if (progress >= 1f) {
                        // Fully watched - draw complete circle
                        drawCircle(
                            color = Color.White,
                            radius = radius,
                            center = center,
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        )
                    }
                }
            }
            
            // Time remaining text
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = MaterialTheme.typography.labelSmall.fontSize
                ),
                color = Color.White
            )
        }
    }
}

@Composable
fun EpisodeSubtitleSelectionDialog(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    onDismiss: () -> Unit,
    onSubtitleSelected: (subtitleStreamIndex: Int?) -> Unit,
    onDownloadedSubtitleSelected: ((String) -> Unit)? = null // File path of downloaded subtitle
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var isLoadingSubtitles by remember { mutableStateOf(true) }
    
    var showServerSubtitleSearch by remember { mutableStateOf(false) }
    var downloadedSubtitles by remember { mutableStateOf<List<com.flex.elefin.subtitles.DownloadedSubtitle>>(emptyList()) }

    // Load downloaded subtitles
    LaunchedEffect(item.Id) {
        downloadedSubtitles = com.flex.elefin.subtitles.OpenSubtitlesApi.getDownloadedSubtitles(context, item.Id)
    }
    
    // Fetch full item details to get MediaSources with subtitle streams
    // First refresh the item on the server to detect any newly added external subtitles
    LaunchedEffect(item.Id, apiService) {
        if (apiService != null) {
            withContext(Dispatchers.IO) {
                try {
                    // Refresh item metadata on server to detect new external subtitle files
                    Log.d("EpisodeSubtitleDialog", "Refreshing item metadata to detect new subtitles...")
                    apiService.refreshItemMetadata(item.Id)
                    
                    // Small delay to allow server to process the refresh
                    kotlinx.coroutines.delay(500)
                    
                    // Now fetch the updated item details
                    val details = apiService.getItemDetails(item.Id)
                    itemDetails = details
                    isLoadingSubtitles = false
                    
                    val subtitleCount = details?.MediaSources?.firstOrNull()?.MediaStreams
                        ?.count { it.Type == "Subtitle" } ?: 0
                    Log.d("EpisodeSubtitleDialog", "Loaded $subtitleCount subtitle streams after refresh")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Normal cancellation when composable leaves composition - don't log as error
                    throw e // Re-throw to respect cancellation
                } catch (e: Exception) {
                    Log.e("EpisodeSubtitleDialog", "Error fetching item details", e)
                    isLoadingSubtitles = false
                }
            }
        } else {
            isLoadingSubtitles = false
        }
    }
    
    // Get subtitle streams from MediaSources
    val subtitleStreams = remember(itemDetails?.MediaSources) {
        itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
            ?.filter { it.Type == "Subtitle" }
            ?.sortedBy { it.Index ?: 0 } ?: emptyList()
    }
    
    val storedSubtitleIndex = remember(context, item.Id) { 
        com.flex.elefin.jellyfin.AppSettings(context).getSubtitlePreference(item.Id) 
    }
    
    Dialog(
        onDismissRequest = onDismiss,
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
                    .fillMaxWidth(0.3f)
                    .fillMaxHeight(0.5f),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.tv.material3.SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Select Subtitles",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    if (isLoadingSubtitles) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading subtitles...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        // Custom colors for ListItem - purple focus to match toggle switches
                        val listItemColors = androidx.tv.material3.ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            focusedContentColor = Color.White,
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            selectedContentColor = Color.White
                        )
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // "None" option to disable subtitles
                            item {
                                ListItem(
                                    selected = storedSubtitleIndex == null,
                                    onClick = {
                                        apiService?.let { com.flex.elefin.subtitles.clearLocalProviderSubtitle(context, it, item.Id) }
                                        onSubtitleSelected(null)
                                        onDismiss()
                                    },
                                    colors = listItemColors,
                                    headlineContent = {
                                        Text(
                                            text = "None (Off)",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.7f
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            // Subtitle stream options
                            items(subtitleStreams) { stream ->
                                val subtitleTitle = stream.DisplayTitle
                                    ?: stream.Language
                                    ?: "Unknown"
                                val subtitleInfo = buildString {
                                    if (stream.IsDefault == true) append("Default")
                                    if (stream.IsForced == true) {
                                        if (isNotEmpty()) append(", ")
                                        append("Forced")
                                    }
                                    if (stream.IsExternal == true) {
                                        if (isNotEmpty()) append(", ")
                                        append("External")
                                    }
                                }
                                val isSelected = stream.Index != null && stream.Index == storedSubtitleIndex
                                
                                ListItem(
                                    selected = isSelected,
                                    onClick = {
                                        stream.Index?.let { index ->
                                            apiService?.let { com.flex.elefin.subtitles.clearLocalProviderSubtitle(context, it, item.Id) }
                                            onSubtitleSelected(index)
                                            onDismiss()
                                        }
                                    },
                                    colors = listItemColors,
                                    headlineContent = {
                                        Column {
                                            Text(
                                                text = subtitleTitle,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.7f
                                                )
                                            )
                                            if (subtitleInfo.isNotEmpty()) {
                                                Text(
                                                    text = subtitleInfo,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.7f
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            // If no subtitles available
                            if (subtitleStreams.isEmpty() && downloadedSubtitles.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No subtitles available",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                            
                            // Divider before downloaded subtitles
                            if (downloadedSubtitles.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Downloaded Subtitles",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = MaterialTheme.typography.labelMedium.fontSize * 0.8f
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                
                                // Downloaded subtitle options
                                items(downloadedSubtitles) { downloadedSub ->
                                    ListItem(
                                        selected = false,
                                        onClick = {
                                            apiService?.let { com.flex.elefin.subtitles.selectLocalProviderSubtitle(context, it, item.Id, downloadedSub.filePath) }
                                            onSubtitleSelected(null)
                                            onDownloadedSubtitleSelected?.invoke(downloadedSub.filePath)
                                            onDismiss()
                                        },
                                        colors = listItemColors,
                                        headlineContent = {
                                            Column {
                                                Text(
                                                    text = com.flex.elefin.subtitles.SubtitleLanguages.getDisplayName(downloadedSub.language),
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.7f
                                                    )
                                                )
                                                Text(
                                                    text = downloadedSub.release,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.7f
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                    maxLines = 1
                                                )
                                            }
                                        },
                                        leadingContent = {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            
                            // Download Subtitles button
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { showServerSubtitleSearch = true },
                                    enabled = apiService != null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Download Subtitles",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showServerSubtitleSearch && apiService != null) {
        com.flex.elefin.subtitles.ServerSubtitleSearchDialog(
            itemId = item.Id,
            api = apiService,
            onDownloaded = { selection ->
                itemDetails = selection.item
                if (selection.localPath == null) com.flex.elefin.player.SubtitleDownloader.downloadSubtitle(
                    context, apiService, item.Id, selection.sourceId, selection.stream
                )
                onSubtitleSelected(selection.stream.Index)
                onDismiss()
            },
            onDismiss = { showServerSubtitleSearch = false }
        )
    }
}

@Composable
fun EpisodeAudioSelectionDialog(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    onDismiss: () -> Unit,
    onAudioSelected: (audioStreamIndex: Int?) -> Unit
) {
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var isLoadingAudio by remember { mutableStateOf(true) }
    
    // Fetch full item details to get MediaSources with audio streams
    LaunchedEffect(item.Id, apiService) {
        Log.d("EpisodeAudioDialog", "LaunchedEffect triggered for item ${item.Id}, apiService=${apiService != null}")
        if (apiService != null) {
            withContext(Dispatchers.IO) {
                try {
                    val details = apiService.getItemDetails(item.Id)
                    Log.d("EpisodeAudioDialog", "Fetched details: ${details?.Name}, MediaSources: ${details?.MediaSources?.size ?: 0}")
                    details?.MediaSources?.firstOrNull()?.MediaStreams?.let { streams ->
                        val audioStreams = streams.filter { it.Type == "Audio" }
                        Log.d("EpisodeAudioDialog", "Found ${audioStreams.size} audio streams: ${audioStreams.map { "Index=${it.Index}, Lang=${it.Language}, Codec=${it.Codec}" }}")
                    }
                    itemDetails = details
                    isLoadingAudio = false
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("EpisodeAudioDialog", "Error fetching item details", e)
                    isLoadingAudio = false
                }
            }
        } else {
            Log.w("EpisodeAudioDialog", "apiService is null, cannot fetch audio tracks")
            isLoadingAudio = false
        }
    }
    
    // Get audio streams from MediaSources
    val audioStreams = remember(itemDetails?.MediaSources) {
        val streams = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
            ?.filter { it.Type == "Audio" }
            ?.sortedBy { it.Index ?: 0 } ?: emptyList()
        Log.d("EpisodeAudioDialog", "audioStreams remember computed: ${streams.size} streams")
        streams
    }
    
    val context = LocalContext.current
    val storedAudioIndex = remember(context, item.Id) { 
        com.flex.elefin.jellyfin.AppSettings(context).getAudioPreference(item.Id) 
    }
    
    Dialog(
        onDismissRequest = onDismiss,
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
                    .fillMaxWidth(0.3f)
                    .fillMaxHeight(0.5f),
                shape = RoundedCornerShape(16.dp),
                colors = androidx.tv.material3.SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Select Audio Track",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    if (isLoadingAudio) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading audio tracks...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        // Custom colors for ListItem - purple focus to match subtitle selector
                        val listItemColors = androidx.tv.material3.ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            focusedContentColor = Color.White,
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            selectedContentColor = Color.White
                        )
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Audio stream options
                            items(audioStreams) { stream ->
                                val audioTitle = stream.DisplayTitle
                                    ?: stream.Language
                                    ?: "Unknown"
                                val audioInfo = buildString {
                                    stream.Codec?.let { 
                                        append(it)
                                    }
                                }
                                val isSelected = stream.Index != null && stream.Index == storedAudioIndex
                                Log.d("EpisodeAudioDialog", "Rendering audio track: $audioTitle, Index=${stream.Index}, isSelected=$isSelected")
                                
                                ListItem(
                                    selected = isSelected,
                                    onClick = {
                                        Log.d("EpisodeAudioDialog", "Audio track clicked: $audioTitle, Index=${stream.Index}")
                                        stream.Index?.let { index ->
                                            Log.d("EpisodeAudioDialog", "Calling onAudioSelected with index=$index")
                                            onAudioSelected(index)
                                            onDismiss()
                                        }
                                    },
                                    colors = listItemColors,
                                    headlineContent = {
                                        Column {
                                            Text(
                                                text = audioTitle,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.7f
                                                )
                                            )
                                            if (audioInfo.isNotEmpty()) {
                                                Text(
                                                    text = audioInfo,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.7f
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            // If no audio tracks available
                            if (audioStreams.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No audio tracks available",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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

// Helper function to get resolution in 1080P format
// Helper function to format resolution to standard format (1080p, 4K, etc.)
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

private fun getEpisodeResolution(episode: JellyfinItem): String? {
    val videoStream = episode.MediaSources
        ?.firstOrNull()
        ?.MediaStreams
        ?.firstOrNull { it.Type == "Video" }
    
    return if (videoStream?.Height != null) {
        val height = videoStream.Height!!
        when {
            height >= 2160 -> "4K"
            height >= 1440 -> "1440P"
            height >= 1080 -> "1080P"
            height >= 720 -> "720P"
            height >= 480 -> "480P"
            else -> "${height}P"
        }
    } else {
        null
    }
}
