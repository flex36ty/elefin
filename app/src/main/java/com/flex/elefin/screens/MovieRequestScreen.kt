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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
        kotlinx.coroutines.delay(100) // Small delay to ensure layout is ready
        try {
            requestButtonFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore focus errors
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
                        Button(
                            onClick = {
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
                            },
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

