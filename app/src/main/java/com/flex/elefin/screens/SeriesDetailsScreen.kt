package com.flex.elefin.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.Image
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    
    // Handle back button press
    if (onBackPressed != null) {
        BackHandler(onBack = onBackPressed)
    }
    
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var seasons by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var episodes by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var selectedSeasonIndex by remember { mutableStateOf(0) }
    var focusedEpisode by remember { mutableStateOf<JellyfinItem?>(null) }
    var focusedSeason by remember { mutableStateOf<JellyfinItem?>(null) }
    var isLoadingSeasons by remember { mutableStateOf(true) }
    var isLoadingEpisodes by remember { mutableStateOf(true) }
    
    // FocusRequester map for episodes (used for initial focus)
    val episodeFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    // Fetch full series details
    LaunchedEffect(item.Id, apiService) {
        if (apiService != null) {
            withContext(Dispatchers.IO) {
                try {
                    val details = apiService.getItemDetails(item.Id)
                    itemDetails = details
                    val seriesSeasons = apiService.getSeasons(item.Id)
                    seasons = seriesSeasons
                    isLoadingSeasons = false
                    // Load first season's episodes
                    if (seriesSeasons.isNotEmpty()) {
                        val seasonEpisodes = apiService.getEpisodes(item.Id, seriesSeasons[0].Id)
                        episodes = seasonEpisodes
                        isLoadingEpisodes = false
                    }
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

    // Fetch episodes when selected season changes
    LaunchedEffect(selectedSeasonIndex, seasons, apiService) {
        if (apiService != null && seasons.isNotEmpty() && selectedSeasonIndex < seasons.size) {
            withContext(Dispatchers.IO) {
                try {
                    isLoadingEpisodes = true
                    val seasonEpisodes = apiService.getEpisodes(item.Id, seasons[selectedSeasonIndex].Id)
                    episodes = seasonEpisodes
                    isLoadingEpisodes = false
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Ignore cancellation exceptions - they're expected when composition changes
                    throw e // Re-throw to properly handle cancellation
                } catch (e: Exception) {
                    Log.e("SeriesDetailsScreen", "Error fetching episodes", e)
                    isLoadingEpisodes = false
                }
            }
        }
    }
    
    // Track if we've already done the initial focus to prevent refocusing when seasons change
    var hasPerformedInitialFocus by remember { mutableStateOf(false) }
    
    // Handle initial episode focus - find the season containing the episode and focus on it
    // Only run once when initialEpisodeId is set, not when episodes change
    LaunchedEffect(initialEpisodeId, seasons, apiService, isLoadingEpisodes) {
        if (initialEpisodeId != null && !hasPerformedInitialFocus && apiService != null && seasons.isNotEmpty() && !isLoadingEpisodes) {
            // Check if the episode is in the currently loaded episodes
            val targetEpisode = episodes.find { it.Id == initialEpisodeId }
            if (targetEpisode != null) {
                // Episode is in current season - focus on it
                withContext(Dispatchers.Main) {
                    focusedEpisode = targetEpisode
                    // Request focus on the episode card with retries to ensure composable is ready
                    val focusRequester = episodeFocusRequesters.getOrPut(targetEpisode.Id) { FocusRequester() }
                    delay(1000) // Longer delay to ensure UI is ready and LazyColumn has composed
                    // Try to request focus with retries
                    var retries = 0
                    val maxRetries = 20
                    var success = false
                    while (retries < maxRetries && !success) {
                        try {
                            focusRequester.requestFocus()
                            Log.d("SeriesDetailsScreen", "Focused on initial episode: ${targetEpisode.Name}")
                            success = true
                        } catch (e: IllegalStateException) {
                            retries++
                            if (retries < maxRetries) {
                                delay(300)
                            } else {
                                Log.w("SeriesDetailsScreen", "Failed to request focus on initial episode after $maxRetries retries: ${e.message}")
                            }
                        } catch (e: Exception) {
                            Log.e("SeriesDetailsScreen", "Unexpected error requesting focus: ${e.message}", e)
                            break
                        }
                    }
                    hasPerformedInitialFocus = true
                }
            } else {
                // Episode not in current season - search through all seasons to find it
                withContext(Dispatchers.IO) {
                    try {
                        var found = false
                        for ((index, season) in seasons.withIndex()) {
                            val seasonEpisodes = apiService.getEpisodes(item.Id, season.Id)
                            val episodeInSeason = seasonEpisodes.find { it.Id == initialEpisodeId }
                            if (episodeInSeason != null) {
                                // Found the episode in this season
                                withContext(Dispatchers.Main) {
                                    selectedSeasonIndex = index
                                    episodes = seasonEpisodes
                                    focusedEpisode = episodeInSeason
                                    // Request focus on the episode card with retries to ensure composable is ready
                                    val focusRequester = episodeFocusRequesters.getOrPut(episodeInSeason.Id) { FocusRequester() }
                                    delay(1200) // Longer delay to ensure episodes are loaded and UI is ready
                                    // Try to request focus with retries
                                    var retries = 0
                                    val maxRetries = 20
                                    var success = false
                                    while (retries < maxRetries && !success) {
                                        try {
                                            focusRequester.requestFocus()
                                            Log.d("SeriesDetailsScreen", "Found initial episode in season ${index + 1}, focused: ${episodeInSeason.Name}")
                                            success = true
                                        } catch (e: IllegalStateException) {
                                            retries++
                                            if (retries < maxRetries) {
                                                delay(300)
                                            } else {
                                                Log.w("SeriesDetailsScreen", "Failed to request focus on initial episode after $maxRetries retries: ${e.message}")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("SeriesDetailsScreen", "Unexpected error requesting focus: ${e.message}", e)
                                            break
                                        }
                                    }
                                    hasPerformedInitialFocus = true
                                }
                                found = true
                                break
                            }
                        }
                        if (!found) {
                            Log.w("SeriesDetailsScreen", "Could not find episode with ID: $initialEpisodeId")
                            hasPerformedInitialFocus = true // Mark as done even if not found to prevent retrying
                        }
                    } catch (e: Exception) {
                        Log.e("SeriesDetailsScreen", "Error finding initial episode", e)
                        hasPerformedInitialFocus = true // Mark as done on error to prevent retrying
                    }
                }
            }
        }
    }
    
    // State to trigger episodes refresh after marking as watched
    var refreshEpisodesAfterWatched by remember { mutableStateOf(false) }
    
    // Refresh episodes list when an episode is marked as watched
    LaunchedEffect(refreshEpisodesAfterWatched, apiService, seasons.size, selectedSeasonIndex, item.Id) {
        if (refreshEpisodesAfterWatched && apiService != null && seasons.isNotEmpty() && selectedSeasonIndex < seasons.size) {
            val currentApiService = apiService
            val currentSeasons = seasons
            val currentSeasonIndex = selectedSeasonIndex
            val currentItemId = item.Id
            
            withContext(Dispatchers.IO) {
                try {
                    // Add a small delay to ensure server has processed the watched status
                    delay(300)
                    val season = currentSeasons[currentSeasonIndex]
                    val refreshedEpisodes = currentApiService.getEpisodes(currentItemId, season.Id)
                    Log.d("SeriesDetails", "Refreshed ${refreshedEpisodes.size} episodes after marking as watched")
                    // Log the watched status of episodes
                    refreshedEpisodes.forEach { ep ->
                        Log.d("SeriesDetails", "Episode ${ep.Name}: PlayedPercentage=${ep.UserData?.PlayedPercentage}")
                    }
                    withContext(Dispatchers.Main) {
                        // Store the currently focused episode ID before refreshing
                        val currentFocusedEpisodeId = focusedEpisode?.Id
                        
                        // Create a new list to ensure Compose detects the change
                        episodes = refreshedEpisodes.toList()
                        Log.d("SeriesDetails", "Episodes list updated with ${episodes.size} episodes, triggering recomposition")
                        
                        // Log watched status of episodes for debugging
                        refreshedEpisodes.forEach { ep ->
                            Log.d("SeriesDetails", "Episode ${ep.Name} (${ep.Id}): PlayedPercentage=${ep.UserData?.PlayedPercentage}")
                        }
                        
                        // Update focusedEpisode to point to the refreshed episode if it exists
                        if (currentFocusedEpisodeId != null) {
                            val refreshedFocusedEpisode = refreshedEpisodes.find { it.Id == currentFocusedEpisodeId }
                            if (refreshedFocusedEpisode != null) {
                                focusedEpisode = refreshedFocusedEpisode
                                Log.d("SeriesDetails", "Updated focusedEpisode with refreshed data: PlayedPercentage=${refreshedFocusedEpisode.UserData?.PlayedPercentage}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SeriesDetails", "Error refreshing episodes after marking as watched", e)
                }
            }
            refreshEpisodesAfterWatched = false // Reset flag
        }
    }

    val displayItem = itemDetails ?: item
    val selectedSeason = if (seasons.isNotEmpty() && selectedSeasonIndex < seasons.size) {
        seasons[selectedSeasonIndex]
    } else null
    
    // Get backdrop URL - same pattern as MovieDetailsScreen
    val backdropUrl = remember(displayItem) {
        apiService?.getImageUrl(displayItem.Id, "Backdrop") ?: ""
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop background - absolutely positioned to fill entire screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        ) {
            val imageUrl = if (backdropUrl.isNotEmpty()) {
                backdropUrl
            } else {
                apiService?.getImageUrl(displayItem.Id, "Primary") ?: ""
            }
            
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
                            model = ImageRequest.Builder(context)
                                .data(currentUrl)
                                .headers(headerMap)
                                .build(),
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
                        .background(Color.Black.copy(alpha = 0.5f))
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
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top container with logo, synopsis, and metadata
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.286875f) // Fixed at 28.6875% of screen height (decreased by another 10%)
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
                    .weight(1f) // Fill remaining space between top and bottom
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
                    onEpisodesRefreshRequested = {
                        refreshEpisodesAfterWatched = true
                    },
                    initialEpisodeId = initialEpisodeId,
                    episodeFocusRequesters = episodeFocusRequesters,
                    showDebugOutlines = showDebugOutlines,
                    seasons = seasons,
                    selectedSeasonIndex = selectedSeasonIndex,
                    onSeasonSelected = { index ->
                        selectedSeasonIndex = index
                    }
                )
            }
            
            // New bottom container - takes up 30% of the bottom of the screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.3f) // Fixed at 30% of screen height
                    .then(
                        if (showDebugOutlines) {
                            Modifier.border(4.dp, Color.Green)
                        } else {
                            Modifier
                        }
                    )
            ) {
                // Cast row for TV show
                val castMembers = displayItem.People?.filter { it.Type == "Actor" } ?: emptyList()
                if (castMembers.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 33.6.dp, end = 33.6.dp, top = 0.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Cast",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) {
                            items(castMembers) { person ->
                                CastMemberCard(
                                    person = person,
                                    apiService = apiService
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
fun SeriesTopContainer(
    item: JellyfinItem,
    itemDetails: JellyfinItem?,
    focusedEpisode: JellyfinItem?,
    focusedSeason: JellyfinItem?,
    selectedSeasonIndex: Int,
    apiService: JellyfinApiService?,
    showDebugOutlines: Boolean = false
) {
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
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f
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
                        seasonNumber = selectedSeasonIndex + 1,
                        apiService = apiService,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Synopsis (from episode) - reduced padding
                focusedEpisode.Overview?.let { synopsis ->
                    if (synopsis.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 180.dp) // Increased height to allow more synopsis text to be visible
                                .then(
                                    if (showDebugOutlines) {
                                        Modifier.border(1.dp, Color.Magenta)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Text(
                                text = synopsis,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f,
                                    lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 0.8f * 1.1f // Reduced line spacing (10% of font size)
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = Int.MAX_VALUE,
                                modifier = Modifier
                                    .padding(top = 4.dp) // Only top padding, no bottom padding
                            )
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
                        Box(
                            modifier = Modifier
                                .heightIn(min = 180.dp) // Increased height to allow more synopsis text to be visible
                                .then(
                                    if (showDebugOutlines) {
                                        Modifier.border(1.dp, Color.Magenta)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Text(
                                text = synopsis,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f,
                                    lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 0.8f * 1.1f // Reduced line spacing (10% of font size)
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = Int.MAX_VALUE,
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                            )
                        }
                    }
                }
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
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp) // Left aligned, no padding
                ) {
                    items(seasons.size) { index ->
                        val season = seasons[index]
                        var isFocused by remember { mutableStateOf(false) }
                        val isSelected = index == selectedSeasonIndex
                        val seasonNumber = index + 1
                        
                        Button(
                            onClick = { onSeasonSelected(index) },
                            modifier = Modifier
                                .then(
                                    if (isFocused) {
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
                            if (!isFocused) {
                                // Show just the number when unfocused
                                Text(
                                    text = seasonNumber.toString(),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                                    )
                                )
                            } else {
                                // Show "Season X" when focused
                                Text(
                                    text = "Season $seasonNumber",
                                    style = MaterialTheme.typography.labelLarge.copy(
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
    onEpisodesRefreshRequested: () -> Unit,
    initialEpisodeId: String? = null,
    episodeFocusRequesters: MutableMap<String, FocusRequester>? = null,
    showDebugOutlines: Boolean = false,
    seasons: List<JellyfinItem> = emptyList(),
    selectedSeasonIndex: Int = 0,
    onSeasonSelected: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val settings = remember { com.flex.elefin.jellyfin.AppSettings(context) }
    var showResumeDialog by remember { mutableStateOf<JellyfinItem?>(null) }
    var focusedEpisode by remember { mutableStateOf<JellyfinItem?>(null) }
    var lastFocusedEpisode by remember { mutableStateOf<JellyfinItem?>(null) } // Track last focused episode for buttons
    var showLongPressMenu by remember { mutableStateOf<JellyfinItem?>(null) }
    
    
    // FocusRequester for the last focused episode (to restore focus when pressing up)
    val lastFocusedEpisodeRequester = episodeFocusRequesters ?: remember { mutableMapOf<String, FocusRequester>() }
    
    // Track if we've already done the initial focus to prevent refocusing when episodes change
    var hasPerformedInitialFocus by remember { mutableStateOf(false) }
    
    // Handle initial episode focus when episodes are loaded
    // Only run once when initialEpisodeId is set, not when episodes change
    LaunchedEffect(initialEpisodeId, isLoadingEpisodes, episodes.size) {
        if (initialEpisodeId != null && !hasPerformedInitialFocus && episodes.isNotEmpty() && !isLoadingEpisodes) {
            val targetEpisode = episodes.find { it.Id == initialEpisodeId }
            if (targetEpisode != null) {
                focusedEpisode = targetEpisode
                lastFocusedEpisode = targetEpisode
                onEpisodeFocused(targetEpisode)
                // Request focus on the episode card with retries to ensure composable is ready
                // Get or create the focusRequester - it will be attached when EpisodeCard composes
                val focusRequester = lastFocusedEpisodeRequester.getOrPut(targetEpisode.Id) { FocusRequester() }
                // Wait longer for composition to complete and UI to be ready
                // LazyColumn needs time to compose all items
                kotlinx.coroutines.delay(1000)
                // Try to request focus with retries
                var retries = 0
                val maxRetries = 20
                var success = false
                while (retries < maxRetries && !success) {
                    try {
                        focusRequester.requestFocus()
                        Log.d("SeriesBottomContainer", "Focused on initial episode: ${targetEpisode.Name}")
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
                // Episode not found, mark as performed to prevent retrying
                hasPerformedInitialFocus = true
            }
        }
    }
    
    // Initialize lastFocusedEpisode to first episode when episodes are loaded
    LaunchedEffect(episodes.isNotEmpty(), isLoadingEpisodes) {
        if (episodes.isNotEmpty() && !isLoadingEpisodes && lastFocusedEpisode == null && initialEpisodeId == null) {
            lastFocusedEpisode = episodes.first()
        }
    }
    
    // Update lastFocusedEpisode when episodes list refreshes (to get updated UserData)
    LaunchedEffect(episodes) {
        if (lastFocusedEpisode != null && episodes.isNotEmpty()) {
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
        LazyColumn(
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
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
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
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp) // Left aligned, no padding
                        ) {
                            items(seasons.size) { index ->
                                val season = seasons[index]
                                var isFocused by remember { mutableStateOf(false) }
                                val isSelected = index == selectedSeasonIndex
                                val seasonNumber = index + 1
                                
                                Button(
                                    onClick = { onSeasonSelected(index) },
                                    modifier = Modifier
                                        .then(
                                            if (isFocused) {
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
                                        .onFocusChanged { focusState ->
                                            isFocused = focusState.isFocused
                                        }
                                        .clip(CircleShape),
                                    colors = ButtonDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    contentPadding = PaddingValues(8.dp)
                                ) {
                                    if (!isFocused) {
                                        // Show just the number when unfocused
                                        Text(
                                            text = seasonNumber.toString(),
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                                            )
                                        )
                                    } else {
                                        // Show "Season X" when focused
                                        Text(
                                            text = "Season $seasonNumber",
                                            style = MaterialTheme.typography.labelLarge.copy(
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
            
            // Episodes row and Play buttons combined in one item to prevent scrolling between them
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Episodes row
                    if (isLoadingEpisodes) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading episodes...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
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
                            val lazyListState = rememberLazyListState()
                            
                            LazyRow(
                                state = lazyListState,
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
                                contentPadding = PaddingValues(horizontal = 16.dp)
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
                                apiService = apiService,
                                modifier = Modifier.fillMaxWidth(),
                                onEpisodeUpdated = { updatedEpisode ->
                                    // Trigger a full refresh of the episodes list from the API
                                    onEpisodesRefreshRequested()
                                }
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
    val imageUrl = remember(episode) {
        val hasThumb = episode.ImageTags?.containsKey("Thumb") == true
        if (hasThumb) {
            apiService?.getImageUrl(episode.Id, "Thumb", episode.ImageTags?.get("Thumb"))
        } else {
            apiService?.getImageUrl(episode.Id, "Primary", episode.ImageTags?.get("Primary"))
        } ?: ""
    }
    
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
                            val headerMap = apiService.getImageRequestHeaders()
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageUrl)
                                    .headers(headerMap)
                                    .build(),
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
    apiService: JellyfinApiService?,
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
                                subtitleStreamIndex = storedSubtitleIndex,
                                audioStreamIndex = storedAudioIndex
                            )
                            context.startActivity(intent)
                        },
                        label = "Resume",
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    var resumeFocused by remember { mutableStateOf(false) }
                    
                    Button(
                        onClick = {
                            val intent = JellyfinVideoPlayerActivity.createIntent(
                                context = context,
                                itemId = displayEpisode.Id,
                                resumePositionMs = resumePositionMs,
                                subtitleStreamIndex = storedSubtitleIndex,
                                audioStreamIndex = storedAudioIndex
                            )
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .then(
                                if (resumeFocused) {
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
                            .onFocusChanged { resumeFocused = it.isFocused }
                            .clip(CircleShape),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Resume",
                            modifier = Modifier.size(14.3.dp)
                        )
                        if (resumeFocused) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Resume",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
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
                                subtitleStreamIndex = storedSubtitleIndex
                            )
                            context.startActivity(intent)
                        },
                    label = "Play",
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            } else {
                var playFocused by remember { mutableStateOf(false) }
                
                Button(
                    onClick = {
                        val intent = JellyfinVideoPlayerActivity.createIntent(
                            context = context,
                            itemId = displayEpisode.Id,
                            resumePositionMs = 0L,
                            subtitleStreamIndex = storedSubtitleIndex
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .then(
                            if (playFocused) {
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
                        .onFocusChanged { playFocused = it.isFocused }
                        .clip(CircleShape),
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(14.3.dp)
                    )
                    if (playFocused) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Play",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            }
            
            // Audio track button
            var audioFocused by remember { mutableStateOf(false) }
            
            Button(
                onClick = {
                    showAudioDialog = true
                },
                modifier = Modifier
                    .then(
                        if (audioFocused) {
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
                    .onFocusChanged { audioFocused = it.isFocused }
                    .clip(CircleShape),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Audio Track",
                    modifier = Modifier.size(14.3.dp)
                )
                if (audioFocused) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Audio",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
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
                    .then(
                        if (subtitleFocused) {
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
                    .onFocusChanged { subtitleFocused = it.isFocused }
                    .clip(CircleShape),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Subtitles",
                    modifier = Modifier.size(14.3.dp)
                )
                if (subtitleFocused) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Subtitles",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
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
                    .then(
                        if (watchedFocused) {
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
                    .onFocusChanged { watchedFocused = it.isFocused }
                    .clip(CircleShape),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = if (isAlreadyWatched) "Mark As Unwatched" else "Mark As Watched",
                    modifier = Modifier.size(14.3.dp)
                )
                if (watchedFocused) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAlreadyWatched) "Mark As Unwatched" else "Mark As Watched",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                        )
                    )
                }
            }
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
                settings.setAudioPreference(episode.Id, audioIndex)
                showAudioDialog = false
            }
        )
    }
}

// Rating display with Rotten Tomatoes icon support
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
    val criticRatingType = if (criticRating != null) {
        // Pass null for communityRating to focus on critic rating
        determineRatingType(item.ProviderIds, null, criticRating, preferCommunity = false)
    } else {
        null
    }
    
    // Determine community rating type and display if available (as audience rating)
    val communityRatingType = if (communityRating != null) {
        // Pass null for criticRating to focus on community rating
        determineRatingType(item.ProviderIds, communityRating, null, preferCommunity = true)
    } else {
        null
    }
    
    // Show critic rating (RT Fresh/Rotten or generic)
    if (criticRating != null) {
        val percentage = calculatePercentage(criticRating)
        when (criticRatingType) {
            RatingType.RottenTomatoesFresh -> {
                RatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = R.drawable.ic_rt_fresh,
                    label = "RT"
                )
            }
            RatingType.RottenTomatoesRotten -> {
                RatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = R.drawable.ic_rt_rotten,
                    label = "RT"
                )
            }
            RatingType.IMDb -> {
                RatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = R.drawable.ic_imdb,
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
            RatingType.RottenTomatoesAudience -> {
                RatingBoxWithIcon(
                    percentage = percentage,
                    iconRes = R.drawable.ic_rt_popcorn,
                    label = "RT"
                )
            }
            RatingType.IMDb -> {
                // Only show IMDb if we didn't already show it for critic
                if (criticRatingType != RatingType.IMDb) {
                    RatingBoxWithIcon(
                        percentage = percentage,
                        iconRes = R.drawable.ic_imdb,
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
// Note: RatingType enum is defined in MovieDetailsScreen.kt (same package, internal visibility)
// Overloaded versions to handle critic-only or community-only scenarios
private fun determineRatingType(
    providerIds: Map<String, String>?,
    communityRating: Float?,
    criticRating: Float?
): RatingType {
    return determineRatingType(providerIds, communityRating, criticRating, false)
}

private fun determineRatingType(
    providerIds: Map<String, String>?,
    communityRating: Float?,
    criticRating: Float?,
    preferCommunity: Boolean
): RatingType {
    // Check for Rotten Tomatoes provider IDs first
    val rtId = providerIds?.get("RottenTomatoes") ?: providerIds?.get("rottentomatoes") ?: 
               providerIds?.get("Rotten Tomatoes") ?: providerIds?.get("RottenTomatoes.tomato") ?:
               providerIds?.get("RottenTomatoes.audience")
    
    // If preferCommunity is true and CommunityRating exists with RT ID, return Audience
    if (preferCommunity && rtId != null && communityRating != null) {
        return RatingType.RottenTomatoesAudience
    }
    
    // If CriticRating exists, it's likely RT Fresh/Rotten rating
    if (criticRating != null && !preferCommunity) {
        // RT Fresh = 60%+ (6.0/10), RT Rotten = <60%
        // Show RT icons even if ProviderIds don't explicitly say RT, as CriticRating is typically RT
        return if (criticRating >= 6.0f) {
            RatingType.RottenTomatoesFresh
        } else {
            RatingType.RottenTomatoesRotten
        }
    }
    
    // If we have RT provider ID and CommunityRating, it might be RT Audience
    if (rtId != null && communityRating != null) {
        return RatingType.RottenTomatoesAudience
    }
    
    // Check for IMDb
    if (providerIds != null) {
        val imdbId = providerIds["Imdb"] ?: providerIds["imdb"] ?: providerIds["IMDb"] ?:
                     providerIds["imdbid"]
        if (imdbId != null) {
            return RatingType.IMDb
        }
    }
    
    return RatingType.Generic
}

// Rating box with icon
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
    onSubtitleSelected: (subtitleStreamIndex: Int?) -> Unit
) {
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var isLoadingSubtitles by remember { mutableStateOf(true) }
    
    // Fetch full item details to get MediaSources with subtitle streams
    LaunchedEffect(item.Id, apiService) {
        if (apiService != null) {
            withContext(Dispatchers.IO) {
                try {
                    val details = apiService.getItemDetails(item.Id)
                    itemDetails = details
                    isLoadingSubtitles = false
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
    
    val context = LocalContext.current
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
                                        onSubtitleSelected(null)
                                        onDismiss()
                                    },
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
                                            onSubtitleSelected(index)
                                            onDismiss()
                                        }
                                    },
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
                            if (subtitleStreams.isEmpty()) {
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
                        }
                    }
                }
            }
        }
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
        if (apiService != null) {
            withContext(Dispatchers.IO) {
                try {
                    val details = apiService.getItemDetails(item.Id)
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
            isLoadingAudio = false
        }
    }
    
    // Get audio streams from MediaSources
    val audioStreams = remember(itemDetails?.MediaSources) {
        itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
            ?.filter { it.Type == "Audio" }
            ?.sortedBy { it.Index ?: 0 } ?: emptyList()
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
                                
                                ListItem(
                                    selected = isSelected,
                                    onClick = {
                                        stream.Index?.let { index ->
                                            onAudioSelected(index)
                                            onDismiss()
                                        }
                                    },
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
