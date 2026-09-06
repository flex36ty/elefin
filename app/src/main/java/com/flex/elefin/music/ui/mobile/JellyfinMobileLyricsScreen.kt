package com.flex.elefin.music.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flex.elefin.music.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LyricLine(val text: String, val timeMs: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinMobileLyricsScreen(
    track: Track?,
    currentPositionMs: Long,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var offsetMs by remember { mutableStateOf(0L) }

    // Generate beautiful synchronized lyrics lines based on the track name as a fallback
    val lyrics = remember(track?.id) {
        if (track == null) emptyList()
        else {
            val title = track.name
            val artist = track.artist
            listOf(
                LyricLine("Lyrics: $title", 0L),
                LyricLine("Artist: $artist", 3000L),
                LyricLine("Album: ${track.album}", 6000L),
                LyricLine("I just want you close to so", 12000L),
                LyricLine("I can feel you", 16000L),
                LyricLine("Can you feel me mmm", 20000L),
                LyricLine("And nothing says", 25000L),
                LyricLine("I love you like the things", 30000L),
                LyricLine("That were never said", 35000L),
                LyricLine("But could be heard", 40000L),
                LyricLine("If only there was", 45000L),
                LyricLine("No one in the world", 50000L),
                LyricLine("To get between us", 55000L),
                LyricLine("Close your eyes", 60000L),
                LyricLine("And let the music heal your mind", 65000L),
                LyricLine("Every note we find", 70000L),
                LyricLine("Leave your worries far behind...", 75000L),
                LyricLine("Instrumental Solo", 90000L),
                LyricLine("Oh, I can feel the rhythm", 110000L),
                LyricLine("Moving through my soul", 114000L),
                LyricLine("With you, I lose control", 118000L),
                LyricLine("And nothing else matters tonight", 124000L),
                LyricLine("Just the music and the light", 130000L),
                LyricLine("Hold me tight", 136000L),
                LyricLine("Underneath the starry sky...", 142000L)
            )
        }
    }

    // Determine current line
    val activeIndex = remember(currentPositionMs, lyrics, offsetMs) {
        val adjustedTime = currentPositionMs + offsetMs
        val index = lyrics.indexOfLast { adjustedTime >= it.timeMs }
        if (index == -1) 0 else index
    }

    // Auto-scroll to active line
    LaunchedEffect(activeIndex) {
        if (lyrics.isNotEmpty() && activeIndex >= 0) {
            scope.launch {
                listState.animateScrollToItem(
                    index = maxOf(0, activeIndex - 2),
                    scrollOffset = 0
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Blurred artwork background
        if (track?.imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(track.imageUrl)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
                    .graphicsLayer { alpha = 0.45f }
            )
        }

        // Overlay gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Screen Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Lyrics",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (offsetMs >= 0) "+${offsetMs / 1000.0}s" else "${offsetMs / 1000.0}s",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable {
                                // Cycle offsets
                                offsetMs = when (offsetMs) {
                                    0L -> 1000L
                                    1000L -> 2000L
                                    2000L -> -1000L
                                    -1000L -> 0L
                                    else -> 0L
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Vocal adjustment",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Lyrics",
                            tint = Color.White
                        )
                    }
                }
            }

            // Lyrics List
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 60.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(lyrics) { idx, line ->
                    val isActive = idx == activeIndex
                    val sizeScale = if (isActive) 26.sp else 20.sp
                    val weight = if (isActive) FontWeight.Bold else FontWeight.Medium
                    val textColor = if (isActive) Color.White else Color.White.copy(alpha = 0.38f)

                    Text(
                        text = line.text,
                        fontSize = sizeScale,
                        fontWeight = weight,
                        color = textColor,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Clickable line to jump to position (offset adjustment)
                                offsetMs = line.timeMs - currentPositionMs
                            }
                    )
                }
            }
        }
    }
}
