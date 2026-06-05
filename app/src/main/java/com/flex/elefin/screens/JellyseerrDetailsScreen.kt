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

import com.flex.elefin.ui.DeviceUtils

@Composable
fun JellyseerrDetailsScreen(
    tmdbId: Int,
    mediaType: String,
    apiService: JellyseerrApiService?,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTv = remember(context) { DeviceUtils.isTvDevice(context) }
    
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
                if (isTv) {
                    Button(onClick = onBackPressed) {
                        Text("Go Back")
                    }
                } else {
                    androidx.compose.material3.Button(onClick = onBackPressed) {
                        androidx.compose.material3.Text("Go Back")
                    }
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
    val isPending = status == 1 || status == 2
    
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
                .padding(if (isTv) 48.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster
            if (posterUrl != null && isTv) {
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
                    style = if (isTv) MaterialTheme.typography.displayMedium else androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                if (year.isNotEmpty()) {
                    Text(
                        text = year,
                        style = if (isTv) MaterialTheme.typography.titleLarge else androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = overview,
                    style = if (isTv) MaterialTheme.typography.bodyLarge else androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // Action Button
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (isAvailable) {
                        if (isTv) {
                            Button(
                                onClick = { },
                                colors = ButtonDefaults.colors(containerColor = Color(0xFF4CAF50)), // Green
                                enabled = false
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Available on Jellyfin")
                            }
                        } else {
                            androidx.compose.material3.Button(
                                onClick = { },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                enabled = false
                            ) {
                                androidx.compose.material3.Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Text("Available on Jellyfin")
                            }
                        }
                    } else if (isPending) {
                        if (isTv) {
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
                            androidx.compose.material3.Button(
                                onClick = { },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                                enabled = false
                            ) {
                                androidx.compose.material3.Icon(Icons.Default.HourglassEmpty, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Text("Request Pending")
                            }
                        }
                    } else {
                        val requestAction = {
                            if (!isRequesting && apiService != null) {
                                isRequesting = true
                                scope.launch {
                                    val result = if (mediaType == "movie") {
                                        apiService.requestMovie(tmdbId)
                                    } else {
                                        apiService.requestTvShow(tmdbId)
                                    }
                                    
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Request sent successfully!", Toast.LENGTH_SHORT).show()
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
                        }

                        if (isTv) {
                            Button(
                                onClick = requestAction,
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
                        } else {
                            androidx.compose.material3.Button(
                                onClick = requestAction,
                                enabled = !isRequesting,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (isRequesting) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.material3.Text("Requesting...")
                                } else {
                                    androidx.compose.material3.Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    androidx.compose.material3.Text("Request on Jellyseerr")
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Back Button overlay
        Box(
            modifier = Modifier
                .padding(if (isTv) 32.dp else 16.dp)
                .align(Alignment.TopStart)
        ) {
            if (isTv) {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            } else {
                androidx.compose.material3.IconButton(onClick = onBackPressed) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
