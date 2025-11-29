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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min
import kotlin.math.PI
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flex.elefin.JellyfinVideoPlayerActivity
import com.flex.elefin.MovieDetailsActivity
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.R
import com.flex.elefin.screens.ItemDetailsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun MovieDetailsScreen(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    showDebugOutlines: Boolean = false,
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
    var isLoading by remember { mutableStateOf(true) }

    // Fetch full item details
    LaunchedEffect(item.Id, apiService) {
        if (apiService != null) {
            withContext(Dispatchers.IO) {
                try {
                    Log.d("MovieDetailsScreen", "Fetching item details for: ${item.Id} (${item.Name})")
                    Log.d("MovieDetailsScreen", "Initial item UserData: ${item.UserData}")
                    val details = apiService.getItemDetails(item.Id)
                    itemDetails = details
                    Log.d("MovieDetailsScreen", "Fetched item details UserData: ${details?.UserData}")
                    Log.d("MovieDetailsScreen", "Fetched item PositionTicks: ${details?.UserData?.PositionTicks}")
                    isLoading = false
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Ignore cancellation exceptions - they're expected when composition changes
                    throw e // Re-throw to properly handle cancellation
                } catch (e: Exception) {
                    Log.e("MovieDetailsScreen", "Error fetching item details", e)
                    isLoading = false
                }
            }
        } else {
            isLoading = false
        }
    }

    val displayItem = itemDetails ?: item
    
    // Log the displayItem UserData to see what's being passed to ActionButtonsRow
    LaunchedEffect(itemDetails) {
        val itemToCheck = itemDetails ?: item
        Log.d("MovieDetailsScreen", "DisplayItem UserData updated: ${itemToCheck.UserData}")
        Log.d("MovieDetailsScreen", "DisplayItem PositionTicks: ${itemToCheck.UserData?.PositionTicks}")
        val isResumable = itemToCheck.UserData?.PositionTicks != null && itemToCheck.UserData?.PositionTicks!! > 0
        Log.d("MovieDetailsScreen", "DisplayItem isResumable: $isResumable")
    }
    
    // For episodes, try to get backdrop from the episode first, then fallback to series backdrop
    val backdropUrl = remember(displayItem) {
        // First, check if the item itself has a backdrop
        val itemBackdrop = apiService?.getImageUrl(displayItem.Id, "Backdrop")
        if (itemBackdrop?.isNotEmpty() == true) {
            itemBackdrop
        } else if (displayItem.Type == "Episode" && displayItem.SeriesId != null) {
            // For episodes, try to get backdrop from parent series
            apiService?.getImageUrl(displayItem.SeriesId, "Backdrop") ?: ""
        } else {
            ""
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Backdrop background - absolutely positioned to fill entire screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        ) {
            // Get backdrop URL or fallback to primary
            val imageUrl = if (backdropUrl.isNotEmpty()) {
                backdropUrl
            } else {
                // Fallback to primary image (from item or series)
                val primaryUrl = apiService?.getImageUrl(displayItem.Id, "Primary")
                if (primaryUrl?.isNotEmpty() == true) {
                    primaryUrl
                } else if (displayItem.Type == "Episode" && displayItem.SeriesId != null) {
                    // For episodes, try primary from series as last resort
                    apiService?.getImageUrl(displayItem.SeriesId, "Primary") ?: ""
                } else {
                    ""
                }
            }
            
            // Use Crossfade for smooth fade animation
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
                
                // 50% darkness overlay (same as library view) - skip in dark mode
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
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Top container with synopsis and metadata (50% of screen, fixed)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .then(
                        if (showDebugOutlines) {
                            Modifier.border(4.dp, Color.Red)
                        } else {
                            Modifier
                        }
                    )
            ) {
                TopContainer(
                    item = displayItem,
                    apiService = apiService,
                    showDebugOutlines = showDebugOutlines
                )
            }

            // Bottom container with cast and similar movies (50% of screen, scrollable)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .then(
                        if (showDebugOutlines) {
                            Modifier.border(4.dp, Color.Blue)
                        } else {
                            Modifier
                        }
                    )
            ) {
                BottomContainer(
                    item = displayItem,
                    apiService = apiService,
                    showDebugOutlines = showDebugOutlines
                )
            }
        }
    }
}

@Composable
fun TopContainer(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    showDebugOutlines: Boolean = false
) {
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
        // Content: Synopsis and metadata (no poster)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(33.6.dp) // 30% less padding (48 * 0.7 = 33.6)
                .then(
                    if (showDebugOutlines) {
                        Modifier.border(2.dp, Color.Magenta)
                    } else {
                        Modifier
                    }
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Content area that auto-adjusts to synopsis text size
            // Make scrollable so metadata is always accessible even with long synopsis
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Take available space, synopsis can expand within
                    .verticalScroll(rememberScrollState()) // Allow scrolling if content is too long
                    .then(
                        if (showDebugOutlines) {
                            Modifier.border(2.dp, Color.Green)
                        } else {
                            Modifier
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp) // Space items evenly
            ) {
                // Title, Metadata, and Synopsis - using home screen style for uniformity
                // Fetch item details for metadata
                val context = LocalContext.current
                val settings = remember { com.flex.elefin.jellyfin.AppSettings(context) }
                var itemDetailsForMetadata by remember { mutableStateOf<JellyfinItem?>(null) }
                var selectedSubtitleIndexForMetadata by remember { mutableStateOf<Int?>(settings.getSubtitlePreference(item.Id)) }
                
                LaunchedEffect(item.Id, apiService) {
                    if (apiService != null) {
                        withContext(Dispatchers.IO) {
                            try {
                                itemDetailsForMetadata = apiService.getItemDetails(item.Id)
                                selectedSubtitleIndexForMetadata = settings.getSubtitlePreference(item.Id)
                            } catch (e: Exception) {
                                android.util.Log.e("TopContainer", "Error fetching item details", e)
                            }
                        }
                    }
                }
                
                val displayItemForMetadata = itemDetailsForMetadata ?: item
                
                ItemDetailsSection(
                    item = item,
                    apiService = apiService,
                    modifier = Modifier.fillMaxWidth(),
                    synopsisMaxLines = Int.MAX_VALUE, // Allow full synopsis on detail screen
                    additionalMetadataContent = {
                        // Time remaining indicator (if item has been started)
                        if (displayItemForMetadata.RunTimeTicks != null && displayItemForMetadata.UserData?.PositionTicks != null && displayItemForMetadata.UserData?.PositionTicks!! > 0) {
                            TimeRemainingIndicator(item = displayItemForMetadata)
                        }
                        
                        // Get media information
                        val videoStream = displayItemForMetadata.MediaSources?.firstOrNull()?.MediaStreams?.firstOrNull { it.Type == "Video" }
                        val audioStream = displayItemForMetadata.MediaSources?.firstOrNull()?.MediaStreams?.firstOrNull { it.Type == "Audio" }
                        val subtitleStream = displayItemForMetadata.MediaSources?.firstOrNull()?.MediaStreams?.firstOrNull { 
                            it.Type == "Subtitle" && it.Index == selectedSubtitleIndexForMetadata 
                        }
                        
                        // Maturity Rating
                        displayItemForMetadata.OfficialRating?.let { rating ->
                            MetadataBox(text = rating)
                        }
                        
                        // Review Rating with Rotten Tomatoes icons support
                        RatingDisplay(
                            item = displayItemForMetadata,
                            communityRating = displayItemForMetadata.CommunityRating,
                            criticRating = displayItemForMetadata.CriticRating
                        )
                        
                        // Resolution
                        videoStream?.let { stream ->
                            formatResolution(stream.Width, stream.Height)?.let {
                                MetadataBox(text = it)
                            }
                        }
                        
                        // HDR/SDR - Only show HDR if 4K and HEVC (more accurate detection)
                        videoStream?.let { stream ->
                            val is4K = (stream.Width != null && stream.Width!! >= 3840) || (stream.Height != null && stream.Height!! >= 2160)
                            val isHEVC = stream.Codec?.contains("hevc", ignoreCase = true) == true || 
                                        stream.Codec?.contains("h265", ignoreCase = true) == true
                            val hdrStatus = if (is4K && isHEVC) "HDR" else "SDR"
                            MetadataBox(text = hdrStatus)
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
                        
                        // Watched indicator
                        val isWatched = (displayItemForMetadata.UserData?.Played == true) ||
                                       (displayItemForMetadata.UserData?.PlayedPercentage == 100.0)
                        if (isWatched) {
                            MetadataBox(text = "Watched")
                        }
                    }
                )
            }
            
            // Action buttons row at the bottom of the container
            ActionButtonsRow(
                item = item, // item parameter is displayItem from parent
                apiService = apiService,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (showDebugOutlines) {
                            Modifier.border(2.dp, Color.Yellow)
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

@Composable
fun BottomContainer(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    showDebugOutlines: Boolean = false
) {
    val context = LocalContext.current
    // Get cast members (People with Type == "Actor")
    val castMembers = item.People?.filter { it.Type == "Actor" } ?: emptyList()
    val firstGenre = item.Genres?.firstOrNull()
    val firstCastMember = castMembers.firstOrNull()
    
    // State for similar movies and movies with cast member
    var similarMovies by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var moviesWithCast by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var isLoadingSimilar by remember { mutableStateOf(true) }
    var isLoadingCastMovies by remember { mutableStateOf(true) }
    
    // Fetch similar movies by genre
    LaunchedEffect(firstGenre, apiService, item.Id) {
        if (firstGenre != null && apiService != null) {
            withContext(Dispatchers.IO) {
                try {
                    val movies = apiService.getMoviesByGenre(firstGenre, excludeItemId = item.Id, limit = 20)
                    similarMovies = movies
                    isLoadingSimilar = false
                } catch (e: Exception) {
                    Log.e("MovieDetails", "Error fetching similar movies", e)
                    isLoadingSimilar = false
                }
            }
        } else {
            isLoadingSimilar = false
        }
    }
    
    // Fetch movies with first cast member
    LaunchedEffect(firstCastMember?.Id, apiService, item.Id) {
        if (firstCastMember?.Id != null && apiService != null) {
            withContext(Dispatchers.IO) {
                try {
                    val movies = apiService.getMoviesByPerson(firstCastMember.Id, excludeItemId = item.Id, limit = 20)
                    moviesWithCast = movies
                    isLoadingCastMovies = false
                } catch (e: Exception) {
                    Log.e("MovieDetails", "Error fetching movies with cast member", e)
                    isLoadingCastMovies = false
                }
            }
        } else {
            isLoadingCastMovies = false
        }
    }
    
    // Scrollable container with cast and movie rows
    val scrollState = rememberLazyListState()
    
    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
            .then(
                if (showDebugOutlines) {
                    Modifier.border(3.dp, Color.Cyan)
                } else {
                    Modifier
                }
            ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Cast row - now focusable so selector can navigate to it
        if (castMembers.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Cast",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
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
        
        // Movies similar to row
        if (firstGenre != null && (!isLoadingSimilar && similarMovies.isNotEmpty())) {
            item {
                Column(
                    modifier = Modifier.padding(top = 6.dp), // 25% increase (24dp * 0.25 = 6dp)
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Similar Movies",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(similarMovies) { movie ->
                            JellyfinHorizontalCard(
                                item = movie,
                                apiService = apiService,
                                onClick = {
                                    val intent = MovieDetailsActivity.createIntent(
                                        context = context,
                                        item = movie
                                    )
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // More movies with [cast member] row
        if (firstCastMember != null && (!isLoadingCastMovies && moviesWithCast.isNotEmpty())) {
            item {
                Column(
                    modifier = Modifier.padding(top = 6.dp), // 25% increase (24dp * 0.25 = 6dp)
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "More Movies with ${firstCastMember.Name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(moviesWithCast) { movie ->
                            JellyfinHorizontalCard(
                                item = movie,
                                apiService = apiService,
                                onClick = {
                                    val intent = MovieDetailsActivity.createIntent(
                                        context = context,
                                        item = movie
                                    )
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CastMemberCard(
    person: com.flex.elefin.jellyfin.Person,
    apiService: JellyfinApiService?
) {
    val context = LocalContext.current
    val imageUrl = person.Id?.let { personId ->
        person.PrimaryImageTag?.let { tag ->
            apiService?.getImageUrl(personId, "Primary", tag)
        }
    } ?: ""
    
    // Card size - 30% smaller (96.dp * 0.7 = 67.2.dp)
    val cardSize = 67.dp
    
    Column(
        modifier = Modifier
            .width(cardSize),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Use StandardCardContainer for proper TV focus styling
        StandardCardContainer(
            modifier = Modifier.size(cardSize),
            imageCard = { interactionSource ->
                Card(
                    onClick = { /* Cast cards are not clickable */ },
                    interactionSource = interactionSource,
                    colors = CardDefaults.colors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (imageUrl.isNotEmpty() && apiService != null) {
                            val headerMap = apiService.getImageRequestHeaders()
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .headers(headerMap)
                                    .build(),
                                contentDescription = person.Name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // Placeholder
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            },
            title = { }
        )
        
        // Cast member name below the card
        Text(
            text = person.Name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.7f
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun SubtitleSelectionDialog(
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
                    Log.e("SubtitleDialog", "Error fetching item details", e)
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
                        .padding(16.dp)
                ) {
                    // Dialog title - 30% smaller
                    Text(
                        text = "Select Subtitles",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.7f
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Vertical list of subtitle options using ListItem
                    if (isLoadingSubtitles) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading subtitles...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.7f
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            // "None" option to disable subtitles
                            item {
                                ListItem(
                                    selected = false,
                                    onClick = {
                                        onSubtitleSelected(null)
                                        onDismiss()
                                    },
                                    headlineContent = {
                                        Text(
                                            text = "None (Off)",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.7f
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
                                
                                ListItem(
                                    selected = false,
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
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.7f
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
                                    Text(
                                        text = "No subtitles available",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.7f
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(16.dp)
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

@Composable
fun AudioSelectionDialog(
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
                    Log.e("AudioDialog", "Error fetching item details", e)
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
                        .padding(16.dp)
                ) {
                    // Dialog title
                    Text(
                        text = "Select Audio Track",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.7f
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // Vertical list of audio track options
                    if (isLoadingAudio) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Loading audio tracks...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.7f
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
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
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.7f
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
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.7f
                                            ),
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
fun ActionButtonsRow(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { com.flex.elefin.jellyfin.AppSettings(context) }
    val useAnimatedButton = settings.useAnimatedPlayButton
    
    // Fetch full item details to get MediaSources
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    
    LaunchedEffect(item.Id, apiService) {
        if (apiService != null) {
            withContext(Dispatchers.IO) {
                try {
                    itemDetails = apiService.getItemDetails(item.Id)
                } catch (e: Exception) {
                    Log.e("ActionButtonsRow", "Error fetching item details", e)
                }
            }
        }
    }
    
    val displayItem = itemDetails ?: item
    
    // Log UserData for debugging
    Log.d("ActionButtonsRow", "Checking resume status for item: ${displayItem.Id} (${displayItem.Name})")
    Log.d("ActionButtonsRow", "UserData: ${displayItem.UserData}")
    Log.d("ActionButtonsRow", "UserData.PositionTicks: ${displayItem.UserData?.PositionTicks}")
    Log.d("ActionButtonsRow", "UserData.PlayedPercentage: ${displayItem.UserData?.PlayedPercentage}")
    
    val isResumable = displayItem.UserData?.PositionTicks != null && displayItem.UserData?.PositionTicks!! > 0
    val resumePositionMs = displayItem.UserData?.PositionTicks?.let { it / 10_000 } ?: 0L
    
    Log.d("ActionButtonsRow", "isResumable: $isResumable, resumePositionMs: $resumePositionMs")
    
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    // Load stored subtitle and audio preferences
    var selectedSubtitleIndex by remember { mutableStateOf<Int?>(settings.getSubtitlePreference(item.Id)) }
    var selectedAudioIndex by remember { mutableStateOf<Int?>(settings.getAudioPreference(item.Id)) }
    
    // Change label to "Play From Start" when there's a resume button
    val playButtonLabel = if (isResumable) "Play From Start" else "Play"
    
    Row(
        modifier = modifier
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
            val resumeFocusRequester = remember { FocusRequester() }
            
            // Request focus on Resume button by default when resumable
            // Use Unit as key to request focus when button first appears
            LaunchedEffect(Unit) {
                if (isResumable) {
                    // Small delay to ensure button is fully composed and focusable
                    kotlinx.coroutines.delay(50)
                    resumeFocusRequester.requestFocus()
                }
            }
            
            if (useAnimatedButton) {
                AnimatedPlayButton(
                    onClick = {
                        // Launch video player - keep MovieDetailsActivity in back stack so back button returns here
                        val intent = JellyfinVideoPlayerActivity.createIntent(
                            context = context,
                            itemId = displayItem.Id,
                            resumePositionMs = resumePositionMs,
                            subtitleStreamIndex = selectedSubtitleIndex,
                            audioStreamIndex = selectedAudioIndex
                        )
                        context.startActivity(intent)
                        // Don't finish - let back button return to movie details screen
                    },
                    label = "Resume",
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.focusRequester(resumeFocusRequester)
                )
            } else {
                var resumeFocused by remember { mutableStateOf(false) }
                
                Button(
                    onClick = {
                        // Launch video player - keep MovieDetailsActivity in back stack so back button returns here
                        val intent = JellyfinVideoPlayerActivity.createIntent(
                            context = context,
                            itemId = displayItem.Id,
                            resumePositionMs = resumePositionMs,
                            subtitleStreamIndex = selectedSubtitleIndex,
                            audioStreamIndex = selectedAudioIndex
                        )
                        context.startActivity(intent)
                        // Don't finish - let back button return to movie details screen
                    },
                    modifier = Modifier
                        .focusRequester(resumeFocusRequester)
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
                    // Launch video player - keep MovieDetailsActivity in back stack so back button returns here
                        val intent = JellyfinVideoPlayerActivity.createIntent(
                            context = context,
                            itemId = displayItem.Id,
                            resumePositionMs = 0L,
                            subtitleStreamIndex = selectedSubtitleIndex
                        )
                    context.startActivity(intent)
                    // Don't finish - let back button return to movie details screen
                },
                label = playButtonLabel,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        } else {
            var playFocused by remember { mutableStateOf(false) }
            
            Button(
                onClick = {
                    // Launch video player - keep MovieDetailsActivity in back stack so back button returns here
                        val intent = JellyfinVideoPlayerActivity.createIntent(
                            context = context,
                            itemId = displayItem.Id,
                            resumePositionMs = 0L,
                            subtitleStreamIndex = selectedSubtitleIndex
                        )
                    context.startActivity(intent)
                    // Don't finish - let back button return to movie details screen
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
                        text = playButtonLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
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
        val isAlreadyWatched = (displayItem.UserData?.Played == true) ||
                               (displayItem.UserData?.PlayedPercentage == 100.0)
        
        var watchedFocused by remember { mutableStateOf(false) }
        
        Button(
            onClick = {
                apiService?.let { service ->
                    scope.launch(Dispatchers.IO) {
                        try {
                            val success = if (isAlreadyWatched) {
                                // Mark as unwatched
                                service.markAsUnwatched(displayItem.Id)
                            } else {
                                // Mark as watched
                                service.markAsWatched(displayItem.Id)
                            }
                            
                            if (success) {
                                val action = if (isAlreadyWatched) "unwatched" else "watched"
                                android.util.Log.d("MovieDetails", "Item ${displayItem.Id} marked as $action")
                                // Add a small delay to let the server process the status change
                                delay(800)
                                // Refresh item details to update UserData
                                val refreshedDetails = service.getItemDetails(displayItem.Id)
                                if (refreshedDetails != null) {
                                    withContext(Dispatchers.Main) {
                                        itemDetails = refreshedDetails
                                    }
                                    android.util.Log.d("MovieDetails", "Item details refreshed, Played=${refreshedDetails.UserData?.Played}, PlayedPercentage: ${refreshedDetails.UserData?.PlayedPercentage}")
                                }
                            } else {
                                val action = if (isAlreadyWatched) "unwatched" else "watched"
                                android.util.Log.w("MovieDetails", "Failed to mark item as $action")
                            }
                        } catch (e: Exception) {
                            val action = if (isAlreadyWatched) "unwatched" else "watched"
                            android.util.Log.e("MovieDetails", "Error marking item as $action", e)
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
        
        // Right side: Spacer to push audio/subtitle display to the right
        Spacer(modifier = Modifier.weight(1f))
        
        // Applied subtitle display (right-aligned)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Applied subtitle display
            if (selectedSubtitleIndex != null && itemDetails != null) {
                val subtitleStream = itemDetails?.MediaSources?.firstOrNull()?.MediaStreams
                    ?.find { it.Type == "Subtitle" && it.Index == selectedSubtitleIndex }
                subtitleStream?.let { stream ->
                    val subtitleName = stream.DisplayTitle ?: stream.Language ?: "Unknown"
                    MetadataBox(text = subtitleName, icon = Icons.Default.Language)
                }
            }
        }
    }
    
    // Subtitle selection dialog
    if (showSubtitleDialog) {
        SubtitleSelectionDialog(
            item = displayItem,
            apiService = apiService,
            onDismiss = { showSubtitleDialog = false },
            onSubtitleSelected = { subtitleIndex ->
                selectedSubtitleIndex = subtitleIndex
                settings.setSubtitlePreference(item.Id, subtitleIndex)
            }
        )
    }
    
    // Audio track selection dialog
    if (showAudioDialog) {
        AudioSelectionDialog(
            item = displayItem,
            apiService = apiService,
            onDismiss = { showAudioDialog = false },
            onAudioSelected = { audioIndex ->
                selectedAudioIndex = audioIndex
                settings.setAudioPreference(item.Id, audioIndex)
            }
        )
    }
    
}

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

// Helper function to get resolution from media streams
private fun getResolution(item: JellyfinItem): String? {
    val videoStream = item.MediaSources
        ?.firstOrNull()
        ?.MediaStreams
        ?.firstOrNull { it.Type == "Video" }
    
    return formatResolution(videoStream?.Width, videoStream?.Height)
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

// Rating type enum
internal enum class RatingType {
    RottenTomatoesFresh,
    RottenTomatoesRotten,
    RottenTomatoesAudience,
    IMDb,
    Generic
}

// Determine rating type from ProviderIds and rating values
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

// Helper function to format runtime
private fun getRuntime(item: JellyfinItem): String? {
    val runtimeTicks = item.RunTimeTicks ?: return null
    // Convert ticks to minutes (10,000,000 ticks = 1 second)
    val totalSeconds = runtimeTicks / 10_000_000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> null
    }
}
