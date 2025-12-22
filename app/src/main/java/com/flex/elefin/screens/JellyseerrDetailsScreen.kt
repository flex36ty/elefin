package com.flex.elefin.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flex.elefin.jellyseerr.JellyseerrApiService
import com.flex.elefin.jellyseerr.JellyseerrImageUrl
import com.flex.elefin.jellyseerr.JellyseerrMovie
import com.flex.elefin.jellyseerr.JellyseerrTvShow
import kotlinx.coroutines.launch

@Composable
fun JellyseerrDetailsScreen(
    tmdbId: Int,
    mediaType: String,
    apiService: JellyseerrApiService?,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var movieDetails by remember { mutableStateOf<JellyseerrMovie?>(null) }
    var tvDetails by remember { mutableStateOf<JellyseerrTvShow?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isRequesting by remember { mutableStateOf(false) }
    
    // Fetch details
    LaunchedEffect(tmdbId, mediaType, apiService) {
        if (apiService == null) {
            error = "Jellyseerr service not available"
            isLoading = false
            return@LaunchedEffect
        }
        
        isLoading = true
        error = null
        
        try {
            if (mediaType == "movie") {
                val details = apiService.getMovieDetails(tmdbId)
                if (details != null) {
                    movieDetails = details
                } else {
                    error = "Could not fetch movie details"
                }
            } else {
                val details = apiService.getTvShowDetails(tmdbId)
                if (details != null) {
                    tvDetails = details
                } else {
                    error = "Could not fetch TV show details"
                }
            }
        } catch (e: Exception) {
            error = "Error: ${e.message}"
        } finally {
            isLoading = false
        }
    }
    
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading details...", color = Color.White)
        }
        return
    }
    
    if (error != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = error ?: "Unknown error", color = Color.Red)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBackPressed) {
                    Text("Go Back")
                }
            }
        }
        return
    }
    
    // Extract display data
    val title = movieDetails?.title ?: tvDetails?.name ?: "Unknown"
    val overview = movieDetails?.overview ?: tvDetails?.overview ?: "No overview available."
    val year = (movieDetails?.releaseDate ?: tvDetails?.firstAirDate)?.take(4) ?: ""
    val backdropPath = movieDetails?.backdropPath ?: tvDetails?.backdropPath
    val posterPath = movieDetails?.posterPath ?: tvDetails?.posterPath
    val mediaInfo = movieDetails?.mediaInfo ?: tvDetails?.mediaInfo
    
    // Status logic
    // 1=Pending, 2=Approved, 3=Declined, 4=Partially Available, 5=Available
    val status = mediaInfo?.status ?: 0 // 0 = Unknown/Not Requested
    val isAvailable = status == 5 || status == 4
    val isPending = status == 1 || status == 2 // 2 is Approved but maybe not processed? Jellyseerr status codes: 2 is APPROVED.
    // Actually: 1=PENDING APPROVAL, 2=APPROVED, 3=DECLINED.
    // 4=PARTIALLY AVAILABLE, 5=AVAILABLE are separate media availability statuses usually, but Jellyseerr mixes them in MediaInfo sometimes depending on context.
    // Let's rely on mediaInfo.status generally.
    
    val backdropUrl = JellyseerrImageUrl.backdrop(backdropPath)
    val posterUrl = JellyseerrImageUrl.poster(posterPath)
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Background Image
        if (backdropUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(backdropUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.4f }
            )
            
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.9f),
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray)
            )
        }
        
        // Content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster
            if (posterUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(300.dp)
                        .height(450.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                )
                
                Spacer(modifier = Modifier.width(32.dp))
            }
            
            // Text Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                if (year.isNotEmpty()) {
                    Text(
                        text = year,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // Action Button
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (isAvailable) {
                        Button(
                            onClick = { /* Could launch Jellyfin details/play if we can map it back */ },
                            colors = ButtonDefaults.colors(containerColor = Color(0xFF4CAF50)), // Green
                            enabled = false // For now, just show it's available
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Available on Jellyfin")
                        }
                    } else if (isPending) {
                        Button(
                            onClick = { },
                            colors = ButtonDefaults.colors(containerColor = Color(0xFFFFC107)), // Amber
                            enabled = false
                        ) {
                            Icon(Icons.Default.HourglassEmpty, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Request Pending")
                        }
                    } else {
                        // Request Button
                        Button(
                            onClick = {
                                if (!isRequesting && apiService != null) {
                                    isRequesting = true
                                    scope.launch {
                                        val result = if (mediaType == "movie") {
                                            apiService.requestMovie(tmdbId)
                                        } else {
                                            apiService.requestTvShow(tmdbId) // Request all seasons by default
                                        }
                                        
                                        if (result.isSuccess) {
                                            Toast.makeText(context, "Request sent successfully!", Toast.LENGTH_SHORT).show()
                                            // Refresh details to update status
                                            if (mediaType == "movie") {
                                                movieDetails = apiService.getMovieDetails(tmdbId)
                                            } else {
                                                tvDetails = apiService.getTvShowDetails(tmdbId)
                                            }
                                        } else {
                                            Toast.makeText(context, "Request failed", Toast.LENGTH_SHORT).show()
                                        }
                                        isRequesting = false
                                    }
                                }
                            },
                            enabled = !isRequesting,
                            colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isRequesting) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Requesting...")
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Request on Jellyseerr")
                            }
                        }
                    }
                }
            }
        }
        
        // Back Button overlay
        Box(
            modifier = Modifier
                .padding(32.dp)
                .align(Alignment.TopStart)
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }
    }
}
