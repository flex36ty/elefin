package com.flex.elefin.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.flex.elefin.jellyseerr.JellyseerrApiService
import com.flex.elefin.jellyseerr.JellyseerrGenres
import com.flex.elefin.jellyseerr.JellyseerrImageUrl
import com.flex.elefin.jellyseerr.JellyseerrTvShow
import com.flex.elefin.jellyseerr.JellyseerrSeason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TV Show Request Screen - Displays TV show details from Jellyseerr with options to request specific seasons
 * 
 * @param show The JellyseerrTvShow to display
 * @param jellyseerrApiService The Jellyseerr API service for making requests
 * @param onBackPressed Callback when back is pressed
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvShowRequestScreen(
    show: JellyseerrTvShow,
    jellyseerrApiService: JellyseerrApiService?,
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // State for full show details (which includes seasons)
    var fullShowDetails by remember { mutableStateOf<JellyseerrTvShow?>(null) }
    
    // Fetch full details when screen loads
    LaunchedEffect(show.id) {
        if (jellyseerrApiService != null) {
            withContext(Dispatchers.IO) {
                fullShowDetails = jellyseerrApiService.getTvShowDetails(show.id)
            }
        }
    }
    
    // Use full details if available, otherwise fallback to passed show
    val displayShow = fullShowDetails ?: show
    
    // Focus requester for the first available season button
    val seasonListFocusRequester = remember { FocusRequester() }
    
    // Request focus on the list when screen appears
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100) // Small delay to ensure layout is ready
        try {
            seasonListFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Ignore focus errors
        }
    }
    
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
            val backdropUrl = JellyseerrImageUrl.backdrop(displayShow.backdropPath, "w1280")
            if (backdropUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(backdropUrl)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = displayShow.name,
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
        Row(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Left Side: Synopsis and Metadata (50% of screen)
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .padding(33.6.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = displayShow.name ?: "Unknown Title",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * 0.8f
                    ),
                    color = Color.White
                )
                
                // Metadata Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Year
                    displayShow.firstAirDate?.take(4)?.let { year ->
                        Text(
                            text = year,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    
                    // Genres
                    val genreNames = displayShow.genreIds.take(3).mapNotNull { 
                        JellyseerrGenres.TV_GENRES[it] 
                    }
                    if (genreNames.isNotEmpty()) {
                        Text(
                            text = genreNames.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    
                    // Rating Box
                    displayShow.voteAverage?.let { rating ->
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
                displayShow.overview?.let { overview ->
                    if (overview.isNotEmpty()) {
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
            
            // Right Side: Seasons List (50% of screen)
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.3f)) // Slight contrast for list area
                    .padding(vertical = 33.6.dp, horizontal = 24.dp)
            ) {
                Text(
                    text = "Request Seasons",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                if (displayShow.seasons.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(seasonListFocusRequester),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(displayShow.seasons.filter { it.seasonNumber > 0 }) { season ->
                            SeasonRequestItem(
                                season = season,
                                showId = displayShow.id,
                                jellyseerrApiService = jellyseerrApiService,
                                context = context
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (fullShowDetails == null) {
                            CircularProgressIndicator(color = Color.White)
                        } else {
                            Text("No seasons found", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SeasonRequestItem(
    season: JellyseerrSeason,
    showId: Int,
    jellyseerrApiService: JellyseerrApiService?,
    context: android.content.Context
) {
    val scope = rememberCoroutineScope()
    var isRequesting by remember { mutableStateOf(false) }
    var requestSuccess by remember { mutableStateOf(false) }
    var requestError by remember { mutableStateOf<String?>(null) }
    
    // Status logic would ideally come from API, but for now we track local request success
    // In a real app, we'd check if the season is already available or requested based on show details
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Poster
            season.posterPath?.let { path ->
                val posterUrl = JellyseerrImageUrl.poster(path, "w185")
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .height(60.dp)
                        .width(40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Column {
                Text(
                    text = "Season ${season.seasonNumber}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                season.episodeCount?.let { count ->
                    Text(
                        text = "$count Episodes",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        Button(
            onClick = {
                if (!isRequesting && !requestSuccess) {
                    isRequesting = true
                    requestError = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            jellyseerrApiService?.requestTvShow(showId, listOf(season.seasonNumber))
                        }
                        
                        isRequesting = false
                        
                        result?.fold(
                            onSuccess = {
                                requestSuccess = true
                                Toast.makeText(
                                    context,
                                    "Season ${season.seasonNumber} requested!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onFailure = { error ->
                                requestError = error.message
                                Toast.makeText(
                                    context,
                                    "Failed: ${error.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) ?: run {
                            requestError = "API Error"
                        }
                    }
                }
            },
            enabled = !isRequesting && !requestSuccess,
            colors = ButtonDefaults.colors(
                containerColor = if (requestSuccess) Color(0xFF2196F3) else MaterialTheme.colorScheme.surface,
                contentColor = if (requestSuccess) Color.White else MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.width(120.dp)
        ) {
            if (isRequesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    strokeWidth = 2.dp
                )
            } else {
                Text(if (requestSuccess) "Requested" else "Request")
            }
        }
    }
}
