package com.flex.elefin.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.flex.elefin.jellyfin.AppSettings
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun EpisodeLongPressMenu(
    episode: JellyfinItem,
    apiService: JellyfinApiService?,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSubtitleSelected: (Int?) -> Unit
) {
    var itemDetails by remember { mutableStateOf<JellyfinItem?>(null) }
    var isLoadingSubtitles by remember { mutableStateOf(true) }
    val storedSubtitleIndex = settings.getSubtitlePreference(episode.Id)
    
    // Fetch full item details to get MediaSources with subtitle streams
    LaunchedEffect(episode.Id, apiService) {
        if (apiService != null) {
            withContext(Dispatchers.IO) {
                try {
                    val details = apiService.getItemDetails(episode.Id)
                    itemDetails = details
                    isLoadingSubtitles = false
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // Ignore cancellation exceptions - they're expected when composition changes
                    throw e // Re-throw to properly handle cancellation
                } catch (e: Exception) {
                    Log.e("EpisodeLongPressMenu", "Error fetching item details", e)
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .fillMaxHeight(0.6f),
                shape = RoundedCornerShape(16.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                // Show subtitle selector as a vertical one-line list (one item per line)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Episode title
                    Text(
                        text = episode.Name ?: "Episode",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Subtitle selector as vertical one-line list (one item per line)
                    SubtitleSelectorContentVertical(
                        subtitleStreams = subtitleStreams,
                        isLoadingSubtitles = isLoadingSubtitles,
                        storedSubtitleIndex = storedSubtitleIndex,
                        onSubtitleSelected = { subtitleIndex ->
                            onSubtitleSelected(subtitleIndex)
                            onDismiss() // Close the entire menu after selection
                        },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
fun SubtitleSelectorContentVertical(
    subtitleStreams: List<com.flex.elefin.jellyfin.MediaStream>,
    isLoadingSubtitles: Boolean,
    storedSubtitleIndex: Int?,
    onSubtitleSelected: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
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
                            style = MaterialTheme.typography.bodyMedium
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
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (subtitleInfo.isNotEmpty()) {
                                Text(
                                    text = subtitleInfo,
                                    style = MaterialTheme.typography.bodySmall,
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

private fun getSubtitleName(subtitleStreams: List<com.flex.elefin.jellyfin.MediaStream>, index: Int): String {
    return subtitleStreams.find { it.Index == index }?.DisplayTitle
        ?: subtitleStreams.find { it.Index == index }?.Language
        ?: "Unknown"
}
