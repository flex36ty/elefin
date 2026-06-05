package com.flex.elefin.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.StandardCardContainer
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.flex.elefin.jellyseerr.JellyseerrApiService
import com.flex.elefin.jellyseerr.JellyseerrGenres
import com.flex.elefin.jellyseerr.JellyseerrImageUrl
import com.flex.elefin.jellyseerr.JellyseerrMovie
import com.flex.elefin.jellyfin.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Movie Request Screen - Displays movie details from Jellyseerr with a Request button
 * Uses the same layout as MovieDetailsScreen but shows content from TMDB via Jellyseerr
 * 
 * @param movie The JellyseerrMovie to display
 * @param jellyseerrApiService The Jellyseerr API service for making requests
 * @param onBackPressed Callback when back is pressed
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MovieRequestScreen(
    movie: JellyseerrMovie,
    jellyseerrApiService: JellyseerrApiService?,
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTv = remember(context) { com.flex.elefin.ui.DeviceUtils.isTvDevice(context) }
    // State for full movie details (which includes cast/credits)
    var fullMovieDetails by remember { mutableStateOf<JellyseerrMovie?>(null) }
    
    // Fetch full details when screen loads
    LaunchedEffect(movie.id) {
        if (jellyseerrApiService != null) {
            withContext(Dispatchers.IO) {
                fullMovieDetails = jellyseerrApiService.getMovieDetails(movie.id)
            }
        }
    }
    
    // Use full details if available, otherwise fallback to passed movie
    val displayMovie = fullMovieDetails ?: movie
    
    // Focus requester for the request button
    val requestButtonFocusRequester = remember { FocusRequester() }
    
    // Request state
    var isRequesting by remember { mutableStateOf(false) }
    var requestSuccess by remember { mutableStateOf(false) }
    var requestError by remember { mutableStateOf<String?>(null) }
    var requestStatus by remember { mutableStateOf<Int?>(displayMovie.mediaInfo?.status) }
    var buttonFocused by remember { mutableStateOf(false) }
    
    // Update request status when full details load
    LaunchedEffect(fullMovieDetails) {
        fullMovieDetails?.mediaInfo?.status?.let {
            requestStatus = it
        }
    }
    
    // Request focus on the button when screen appears
    LaunchedEffect(Unit) {
        if (isTv) {
            kotlinx.coroutines.delay(100) // Small delay to ensure layout is ready
            try {
                requestButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus errors
            }
        }
    }
    
    // Back handling is now done via onPreviewKeyEvent on the main Box
    // to ensure reliability inside a Dialog wrapper
    
    // Check if already requested/available
    val isAlreadyRequested = requestStatus == 2 || requestStatus == 3 // Pending or Processing
    val isAvailable = requestStatus == 4 || requestStatus == 5 // Partially Available or Available
    
    // Main Container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                    onBackPressed()
                    true
                } else {
                    false
                }
            }
    ) {
        if (isTv) {
            // Backdrop background - absolutely positioned to fill entire screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            ) {
                val backdropUrl = JellyseerrImageUrl.backdrop(displayMovie.backdropPath, "w1280")
                if (backdropUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(backdropUrl)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = displayMovie.title,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                // 50% darkness overlay (same as library view)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
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
                ) {
                    // Content: Synopsis and metadata (no poster)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(33.6.dp), // Match MovieDetailsScreen padding
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Content area (Title, Metadata, Synopsis)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .focusable(false),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                            // Title
                            Text(
                                text = displayMovie.title ?: "Unknown Title",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.64f
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            // Metadata Row
                            Row(
                                modifier = Modifier.padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Year
                                displayMovie.releaseDate?.take(4)?.let { year ->
                                    Text(
                                        text = year,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                                        ),
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                                
                                // Genres
                                val genreNames = displayMovie.genreIds.take(3).mapNotNull { 
                                    JellyseerrGenres.MOVIE_GENRES[it] 
                                }
                                if (genreNames.isNotEmpty()) {
                                    Text(
                                        text = genreNames.joinToString(", "),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.8f
                                        ),
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                // Rating Box
                                displayMovie.voteAverage?.let { rating ->
                                    if (rating > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "★",
                                                color = Color(0xFFFFD700),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = String.format("%.1f", rating),
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Synopsis
                             displayMovie.overview?.let { overview ->
                                if (overview.isNotEmpty()) {
                                    Text(
                                        text = overview,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.8f,
                                            lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 0.8f * 1.1f
                                        ),
                                        color = Color.White.copy(alpha = 0.9f),
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        // Action Buttons Row (Request Button)
                        Row(
                            modifier = Modifier.padding(top = 5.6.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(11.2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val onClickAction = {
                                if (!isRequesting && !requestSuccess && !isAlreadyRequested && !isAvailable) {
                                    isRequesting = true
                                    requestError = null
                                    
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            jellyseerrApiService?.requestMovie(displayMovie.id)
                                        }
                                        
                                        isRequesting = false
                                        
                                        result?.fold(
                                            onSuccess = { request ->
                                                requestSuccess = true
                                                requestStatus = request.status
                                                Toast.makeText(
                                                    context,
                                                    "${displayMovie.title} has been requested!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            onFailure = { error ->
                                                requestError = error.message ?: "Request failed"
                                                Toast.makeText(
                                                    context,
                                                    "Failed to request: ${error.message}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        ) ?: run {
                                            requestError = "Jellyseerr not configured"
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = onClickAction,
                                enabled = !isRequesting && !isAvailable,
                                colors = ButtonDefaults.colors(
                                    containerColor = when {
                                        isAvailable -> Color(0xFF4CAF50) // Green for available
                                        requestSuccess || isAlreadyRequested -> Color(0xFF2196F3) // Blue for requested
                                        else -> MaterialTheme.colorScheme.surface // Standard surface color for action button
                                    },
                                    contentColor = when {
                                        isAvailable || requestSuccess || isAlreadyRequested -> Color.White
                                        else -> MaterialTheme.colorScheme.onSurface 
                                    }
                                ),
                                modifier = Modifier
                                    .focusRequester(requestButtonFocusRequester)
                                    .then(
                                        if (buttonFocused) {
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
                                    .onFocusChanged { buttonFocused = it.isFocused }
                                    .clip(CircleShape)
                                    .focusProperties {
                                        up = FocusRequester.Cancel
                                        down = FocusRequester.Cancel
                                        left = FocusRequester.Cancel
                                        right = FocusRequester.Cancel
                                        exit = { FocusRequester.Cancel }
                                    },
                                contentPadding = PaddingValues(8.dp)
                            ) {
                                if (isRequesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.3.dp), // Match icon size
                                        color = MaterialTheme.colorScheme.onSurface,
                                        strokeWidth = 2.dp
                                    )
                                    if (buttonFocused) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Requesting...",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                                            ),
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = when {
                                            isAvailable -> Icons.Filled.Check
                                            requestSuccess || isAlreadyRequested -> Icons.Filled.HourglassEmpty
                                            else -> Icons.Filled.Add // Add icon for request
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(14.3.dp) // Match Play button icon size
                                    )
                                    if (buttonFocused) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = when {
                                                isAvailable -> "Available"
                                                requestSuccess -> "Requested"
                                                isAlreadyRequested -> "Pending"
                                                else -> "Request"
                                            },
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.7f
                                            ),
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        )
                                    }
                                }
                            }
                            
                            // Error message next to button
                            requestError?.let { error ->
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
                
                // Bottom container (50% of screen) - Cast Members
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f)
                ) {
                   val castMembers = displayMovie.credits?.cast ?: emptyList()
                   
                   if (castMembers.isNotEmpty()) {
                       Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 48.dp)
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                       ) {
                           Text(
                               text = "Cast",
                               style = MaterialTheme.typography.titleMedium,
                               color = MaterialTheme.colorScheme.onSurface
                           )
                           
                           LazyRow(
                               horizontalArrangement = Arrangement.spacedBy(16.dp),
                               contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 48.dp)
                           ) {
                               items(castMembers) { person ->
                                   JellyseerrCastCard(person)
                               }
                           }
                       }
                   }
                }
            }
        } else {
            // Mobile Details Layout
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val screenHeightDp = configuration.screenHeightDp.dp
            val headerHeight = if (isLandscape) (screenHeightDp * 0.40f) else (screenHeightDp * 0.33f)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(com.flex.elefin.theme.JetcasterBackground),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // 1. Hero Backdrop Header
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(headerHeight)
                            .background(com.flex.elefin.theme.JetcasterSurfaceVariant)
                    ) {
                        val backdropUrl = JellyseerrImageUrl.backdrop(displayMovie.backdropPath, "w1280")
                        if (backdropUrl != null) {
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

                        // Scrim overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        )
                        // Gradient fade
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            com.flex.elefin.theme.JetcasterBackground.copy(alpha = 0.6f),
                                            com.flex.elefin.theme.JetcasterBackground
                                        )
                                    )
                                )
                        )

                        // Top Bar back navigation button
                        IconButton(
                            onClick = onBackPressed,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .statusBarsPadding()
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        // Title & Metadata pinned to the bottom of the header
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 24.dp, vertical = 20.dp)
                        ) {
                            androidx.compose.material3.Text(
                                text = displayMovie.title ?: "Unknown Title",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Text(
                                    text = "Movie",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = com.flex.elefin.theme.JetcasterPrimary
                                )
                                displayMovie.releaseDate?.take(4)?.let { year ->
                                    Spacer(modifier = Modifier.width(12.dp))
                                    androidx.compose.material3.Text(
                                        text = year,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                }
                                displayMovie.voteAverage?.let { rating ->
                                    if (rating > 0) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        androidx.compose.material3.Text(
                                            text = "★ " + String.format("%.1f", rating),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFD700)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Action Buttons Row (Request Button)
                item {
                    val onClickAction = {
                        if (!isRequesting && !requestSuccess && !isAlreadyRequested && !isAvailable) {
                            isRequesting = true
                            requestError = null
                            
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    jellyseerrApiService?.requestMovie(displayMovie.id)
                                }
                                
                                isRequesting = false
                                
                                result?.fold(
                                    onSuccess = { request ->
                                        requestSuccess = true
                                        requestStatus = request.status
                                        Toast.makeText(
                                            context,
                                            "${displayMovie.title} has been requested!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onFailure = { error ->
                                        requestError = error.message ?: "Request failed"
                                        Toast.makeText(
                                            context,
                                            "Failed to request: ${error.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                ) ?: run {
                                    requestError = "Jellyseerr not configured"
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Button(
                            onClick = onClickAction,
                            enabled = !isRequesting && !isAvailable,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = when {
                                    isAvailable -> Color(0xFF4CAF50)
                                    requestSuccess || isAlreadyRequested -> Color(0xFF2196F3)
                                    else -> androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = when {
                                    isAvailable || requestSuccess || isAlreadyRequested -> Color.White
                                    else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            if (isRequesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Text("Requesting...", fontSize = 14.sp)
                            } else {
                                androidx.compose.material3.Icon(
                                    imageVector = when {
                                        isAvailable -> Icons.Filled.Check
                                        requestSuccess || isAlreadyRequested -> Icons.Filled.HourglassEmpty
                                        else -> Icons.Filled.Add
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Text(
                                    text = when {
                                        isAvailable -> "Available"
                                        requestSuccess -> "Requested"
                                        isAlreadyRequested -> "Pending"
                                        else -> "Request"
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Error message next to button
                        requestError?.let { error ->
                            androidx.compose.material3.Text(
                                text = error,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                // 3. Overview section
                displayMovie.overview?.let { overview ->
                    if (overview.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 16.dp)
                            ) {
                                androidx.compose.material3.Text(
                                    text = "Overview",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = com.flex.elefin.theme.JetcasterOnSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                androidx.compose.material3.Text(
                                    text = overview,
                                    fontSize = 14.sp,
                                    color = com.flex.elefin.theme.JetcasterOnBackground.copy(alpha = 0.7f),
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }

                // 4. Cast Section
                val castMembers = displayMovie.credits?.cast ?: emptyList()
                if (castMembers.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        ) {
                            androidx.compose.material3.Text(
                                text = "Cast",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.flex.elefin.theme.JetcasterOnSurface,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(castMembers) { person ->
                                    val personImageUrl = JellyseerrImageUrl.poster(person.profilePath, "w185")
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.width(76.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(com.flex.elefin.theme.JetcasterSurfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!personImageUrl.isNullOrEmpty()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(personImageUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = person.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                androidx.compose.material3.Icon(
                                                    imageVector = Icons.Filled.Person,
                                                    contentDescription = null,
                                                    tint = com.flex.elefin.theme.JetcasterPrimary.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                        androidx.compose.material3.Text(
                                            text = person.name,
                                            fontSize = 11.sp,
                                            color = com.flex.elefin.theme.JetcasterOnSurface,
                                            maxLines = 2,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 14.sp
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun JellyseerrCastCard(
    person: com.flex.elefin.jellyseerr.JellyseerrCast
) {
    val context = LocalContext.current
    val imageUrl = JellyseerrImageUrl.poster(person.profilePath, "w185") // Use poster helper for profile images too
    
    // Card size - 30% smaller (96.dp * 0.7 = 67.2.dp) - same as MovieDetailsScreen
    val cardSize = 67.dp
    
    Column(
        modifier = Modifier
            .width(cardSize),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Explicitly non-focusable image area
        Box(
            modifier = Modifier
                .size(cardSize)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = person.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(androidx.compose.foundation.shape.CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
        
        // Cast member name below the card
        Text(
            text = person.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize * 0.7f
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        // Character name
        person.character?.let { character ->
             Text(
                text = character,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.6f
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

