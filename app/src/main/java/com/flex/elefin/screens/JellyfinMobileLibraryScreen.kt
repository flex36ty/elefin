package com.flex.elefin.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flex.elefin.jellyfin.JellyfinItem
import com.flex.elefin.jellyfin.JellyfinRepository
import com.flex.elefin.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinMobileLibraryScreen(
    libraryId: String,
    libraryName: String,
    repository: JellyfinRepository,
    onItemClick: (JellyfinItem, Long) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    // Fetch and collect library items
    val libraryItemsMap by repository.libraryItems.collectAsState(initial = emptyMap())
    val items = remember(libraryItemsMap, libraryId) { libraryItemsMap[libraryId] ?: emptyList() }

    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var showGenreDialog by remember { mutableStateOf(false) }

    LaunchedEffect(libraryId) {
        repository.fetchLibraryItems(libraryId)
        selectedGenre = null
    }

    val availableGenres = remember(items) {
        items.flatMap { it.Genres ?: emptyList() }.distinct().sorted()
    }

    var sortBy by remember { mutableStateOf("Alphabetical") }

    val filteredItems = remember(items, selectedGenre) {
        if (selectedGenre == null) {
            items
        } else {
            items.filter { it.Genres?.contains(selectedGenre) == true }
        }
    }

    val sortedItems = remember(filteredItems, sortBy) {
        when (sortBy) {
            "Alphabetical" -> filteredItems.sortedBy { it.Name }
            "Recently Added" -> filteredItems.sortedByDescending { it.DateCreated ?: "" }
            "Release Date" -> filteredItems.sortedByDescending { it.PremiereDate ?: "" }
            else -> filteredItems
        }
    }

    var showSortDialog by remember { mutableStateOf(false) }

    JetcasterMobileTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = libraryName,
                            fontWeight = FontWeight.Bold,
                            color = JetcasterOnBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = JetcasterPrimary
                            )
                        }
                    },
                    actions = {
                        if (availableGenres.isNotEmpty()) {
                            TextButton(onClick = { showGenreDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "Filter by Genre",
                                    tint = if (selectedGenre != null) JetcasterPrimary else JetcasterPrimary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = selectedGenre ?: "Genre",
                                    color = if (selectedGenre != null) JetcasterPrimary else JetcasterPrimary.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        TextButton(onClick = { showSortDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort",
                                tint = JetcasterPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = sortBy,
                                color = JetcasterPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = JetcasterSurface,
                        titleContentColor = JetcasterOnBackground
                    )
                )
            },
            containerColor = JetcasterBackground
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                JetcasterBackground,
                                JetcasterBackground.copy(alpha = 0.95f)
                            )
                        )
                    )
            ) {
                if (sortedItems.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = sortedItems,
                            key = { it.Id },
                            contentType = { "media_card" }
                        ) { item ->
                            val clickCallback = remember(item) { { onItemClick(item, 0) } }
                            LibraryItemCard(
                                item = item,
                                onClick = clickCallback,
                                repository = repository
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (selectedGenre != null) "No items match the selected genre" else "No items in library",
                            color = JetcasterOnBackground.copy(alpha = 0.6f)
                        )
                    }
                }

                // Genre Filter Dialog
                if (showGenreDialog) {
                    AlertDialog(
                        onDismissRequest = { showGenreDialog = false },
                        title = { Text("Filter by Genre", color = JetcasterOnSurface, fontWeight = FontWeight.Bold) },
                        containerColor = JetcasterSurface,
                        confirmButton = {
                            TextButton(onClick = { showGenreDialog = false }) {
                                Text("Close", color = JetcasterPrimary)
                            }
                        },
                        text = {
                            androidx.compose.foundation.lazy.LazyColumn {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedGenre = null
                                                showGenreDialog = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedGenre == null,
                                            onClick = {
                                                selectedGenre = null
                                                showGenreDialog = false
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = JetcasterPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("All Genres", color = JetcasterOnBackground)
                                    }
                                }
                                items(availableGenres) { genre ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedGenre = genre
                                                showGenreDialog = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedGenre == genre,
                                            onClick = {
                                                selectedGenre = genre
                                                showGenreDialog = false
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = JetcasterPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(genre, color = JetcasterOnBackground)
                                    }
                                }
                            }
                        }
                    )
                }

                // Sort Selection Dialog
                if (showSortDialog) {
                    AlertDialog(
                        onDismissRequest = { showSortDialog = false },
                        title = { Text("Sort By", color = JetcasterOnSurface, fontWeight = FontWeight.Bold) },
                        containerColor = JetcasterSurface,
                        confirmButton = {
                            TextButton(onClick = { showSortDialog = false }) {
                                Text("Cancel", color = JetcasterPrimary)
                            }
                        },
                        text = {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            sortBy = "Alphabetical"
                                            showSortDialog = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = sortBy == "Alphabetical",
                                        onClick = {
                                            sortBy = "Alphabetical"
                                            showSortDialog = false
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = JetcasterPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Alphabetical", color = JetcasterOnBackground)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            sortBy = "Recently Added"
                                            showSortDialog = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = sortBy == "Recently Added",
                                        onClick = {
                                            sortBy = "Recently Added"
                                            showSortDialog = false
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = JetcasterPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Recently Added", color = JetcasterOnBackground)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            sortBy = "Release Date"
                                            showSortDialog = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = sortBy == "Release Date",
                                        onClick = {
                                            sortBy = "Release Date"
                                            showSortDialog = false
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = JetcasterPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Release Date", color = JetcasterOnBackground)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryItemCard(
    item: JellyfinItem,
    onClick: () -> Unit,
    repository: JellyfinRepository
) {
    val context = LocalContext.current
    val reducePosterResolution = remember(repository) { repository.settings.reducePosterResolution }
    val posterUrl = remember(item.Id, reducePosterResolution) {
        val tag = item.ImageTags?.get("Primary") ?: item.ImageTags?.get("Backdrop")
        if (tag != null) {
            repository.apiService.getImageUrl(
                itemId = item.Id,
                imageType = "Primary",
                imageTag = tag,
                maxWidth = if (reducePosterResolution) 200 else 340,
                quality = if (reducePosterResolution) 70 else 75
            )
        } else ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(JetcasterSurfaceVariant)
        ) {
            if (posterUrl.isNotEmpty()) {
                val imageRequest = remember(posterUrl) {
                    ImageRequest.Builder(context)
                        .data(posterUrl)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.Name,
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
                        tint = JetcasterPrimary.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.Name,
            color = JetcasterOnSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
