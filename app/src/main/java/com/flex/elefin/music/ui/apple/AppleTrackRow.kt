package com.flex.elefin.music.ui.apple

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.flex.elefin.music.model.Track

/**
 * Apple Music-style track row with clean minimalist design and TV focus states.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppleTrackRow(
    track: Track,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrentTrack: Boolean = false,
    isPlaying: Boolean = false,
    showAlbumArt: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "row_scale"
    )

    val backgroundColor = when {
        isCurrentTrack -> Color(0xFFFA2D55).copy(alpha = 0.15f)
        isFocused -> Color.White.copy(alpha = 0.1f)
        else -> Color.Transparent
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = backgroundColor,
            focusedContainerColor = if (isCurrentTrack) 
                Color(0xFFFA2D55).copy(alpha = 0.25f) 
            else 
                Color.White.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track number or playing indicator
            Box(
                modifier = Modifier.width(36.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isCurrentTrack && isPlaying -> {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Playing",
                            tint = Color(0xFFFA2D55),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    isFocused -> {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    else -> {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrentTrack) Color(0xFFFA2D55) else Color(0xFF8E8E93)
                        )
                    }
                }
            }

            // Album art (optional)
            if (showAlbumArt) {
                Spacer(modifier = Modifier.width(12.dp))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(track.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = track.album,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrentTrack) Color(0xFFFA2D55) else Color.White,
                    fontWeight = if (isCurrentTrack) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (showAlbumArt) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8E8E93),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Duration
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8E8E93)
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

