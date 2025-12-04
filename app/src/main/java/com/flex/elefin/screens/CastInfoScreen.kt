package com.flex.elefin.screens

import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.PersonDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CastInfoScreen(
    personId: String,
    personName: String,
    personType: String? = null, // Actor, Director, Writer, etc.
    apiService: JellyfinApiService?,
    onNavigateToItem: (JellyfinItem) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var personDetails by remember { mutableStateOf<PersonDetails?>(null) }
    var filmography by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val topSectionFocusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Fetch person details and filmography
    LaunchedEffect(personId, apiService) {
        if (apiService != null) {
            withContext(Dispatchers.IO) {
                try {
                    // Fetch person details
                    val details = apiService.getPersonDetails(personId)
                    personDetails = details
                    Log.d("CastInfoScreen", "Loaded person details: ${details?.Name}, Overview: ${details?.Overview?.take(100)}")

                    // Fetch filmography
                    val items = apiService.getPersonFilmography(personId)
                    filmography = items
                    Log.d("CastInfoScreen", "Loaded ${items.size} filmography items")
                } catch (e: Exception) {
                    Log.e("CastInfoScreen", "Error loading person data", e)
                } finally {
                    isLoading = false
                }
            }
        } else {
            isLoading = false
        }
    }

    // Request focus on top section (actor card) when content loads
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            try {
                topSectionFocusRequester.requestFocus()
            } catch (e: Exception) {
                Log.w("CastInfoScreen", "Could not request focus on top section: ${e.message}")
            }
        }
    }

    // Get person image URL
    val imageUrl = remember(personId, personDetails) {
        personDetails?.ImageTags?.get("Primary")?.let { tag ->
            apiService?.getPersonImageUrl(personId, "Primary", tag, maxWidth = 400, maxHeight = 600)
        } ?: apiService?.getPersonImageUrl(personId, "Primary", maxWidth = 400, maxHeight = 600) ?: ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface) // Solid material dark background, no gradient
    ) {

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        } else {
            // Use regular LazyColumn for smoother scrolling
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                // Top section: Photo and Info - wrapped in focusable Box for D-pad navigation (no zoom effect)
                item {
                    var isTopFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(topSectionFocusRequester)
                            .onFocusChanged { 
                                isTopFocused = it.isFocused
                                // Scroll to top when this section gets focus
                                if (it.isFocused) {
                                    coroutineScope.launch {
                                        lazyListState.animateScrollToItem(0)
                                    }
                                }
                            }
                            .focusable()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isTopFocused) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                else Color.Transparent
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Person photo
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                if (imageUrl.isNotEmpty() && apiService != null) {
                                    val headerMap = apiService.getImageRequestHeaders()
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(imageUrl)
                                            .headers(headerMap)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = personName,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = personName.take(2).uppercase(),
                                            style = MaterialTheme.typography.headlineLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }

                            // Person info column
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Name
                                Text(
                                    text = personDetails?.Name ?: personName,
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )

                                // Type (Actor, Director, Writer, etc.) - use passed type, not personDetails.Type which returns "Person"
                                if (!personType.isNullOrEmpty()) {
                                    Text(
                                        text = personType,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Birth date (uses helper that checks both PremiereDate and BirthDate)
                                personDetails?.birthDateValue?.let { birthDate ->
                                    val formattedDate = formatBirthDate(birthDate)
                                    Text(
                                        text = "Born $formattedDate",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }

                                // Death date (uses helper that checks both EndDate and DeathDate)
                                personDetails?.deathDateValue?.let { deathDate ->
                                    val formattedDate = formatBirthDate(deathDate)
                                    Text(
                                        text = "Died $formattedDate",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }

                                // Place of birth
                                personDetails?.ProductionLocations?.firstOrNull()?.let { location ->
                                    Text(
                                        text = location,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }

                                // Biography/Overview
                                personDetails?.Overview?.let { biography ->
                                    if (biography.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = biography,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3f,
                                            maxLines = 5,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Movies section
                val movies = filmography.filter { it.Type == "Movie" }
                if (movies.isNotEmpty()) {
                    item {
                        Text(
                            text = "Movies",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 16.dp, top = 16.dp)
                        )
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(start = 40.dp, end = 40.dp),
                            modifier = Modifier.padding(bottom = 24.dp)
                        ) {
                            items(movies) { item ->
                                FilmographyCard(
                                    item = item,
                                    apiService = apiService,
                                    onClick = { onNavigateToItem(item) },
                                    onFocused = {
                                        // Scroll to show the row when a card is focused
                                        coroutineScope.launch {
                                            lazyListState.animateScrollToItem(if (movies.isNotEmpty()) 2 else 1)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // TV Shows section
                val tvShows = filmography.filter { it.Type == "Series" }
                if (tvShows.isNotEmpty()) {
                    item {
                        Text(
                            text = "Shows",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
                        )
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(start = 40.dp, end = 40.dp),
                            modifier = Modifier.padding(bottom = 24.dp)
                        ) {
                            items(tvShows) { item ->
                                FilmographyCard(
                                    item = item,
                                    apiService = apiService,
                                    onClick = { onNavigateToItem(item) },
                                    onFocused = {
                                        // Scroll to show the row when a card is focused
                                        val targetIndex = if (movies.isNotEmpty()) 4 else 2
                                        coroutineScope.launch {
                                            lazyListState.animateScrollToItem(targetIndex)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Bottom spacer
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun FilmographyCard(
    item: JellyfinItem,
    apiService: JellyfinApiService?,
    onClick: () -> Unit,
    onFocused: () -> Unit = {}
) {
    val context = LocalContext.current

    val imageUrl = remember(item) {
        item.ImageTags?.get("Primary")?.let { tag ->
            apiService?.getImageUrl(item.Id, "Primary", tag, maxWidth = 300, maxHeight = 450)
        } ?: ""
    }

    val cardWidth = 120.dp
    val cardHeight = 180.dp

    Column(
        modifier = Modifier.width(cardWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .onFocusChanged { 
                    if (it.isFocused) {
                        onFocused()
                    }
                },
            colors = CardDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp))
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (imageUrl.isNotEmpty() && apiService != null) {
                    val headerMap = apiService.getImageRequestHeaders()
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .headers(headerMap)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.Name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.Name.take(2).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                // Year badge
                item.ProductionYear?.let { year ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(
                                Color.Black.copy(alpha = 0.7f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Title
        Text(
            text = item.Name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatBirthDate(dateString: String): String {
    return try {
        val datePart = dateString.substringBefore("T")
        val parts = datePart.split("-")
        if (parts.size >= 3) {
            val year = parts[0]
            val month = parts[1].toIntOrNull() ?: return datePart
            val day = parts[2].toIntOrNull() ?: return datePart
            
            val monthNames = listOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            )
            val monthName = monthNames.getOrNull(month - 1) ?: return datePart
            "$monthName $day, $year"
        } else {
            datePart
        }
    } catch (e: Exception) {
        dateString
    }
}
