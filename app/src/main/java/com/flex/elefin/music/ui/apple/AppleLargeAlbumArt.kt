package com.flex.elefin.music.ui.apple

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Apple Music-style large album artwork with parallax/scale animation on focus.
 */
@Composable
fun AppleLargeAlbumArt(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 380.dp,
    focused: Boolean = false,
    cornerRadius: Dp = 12.dp
) {
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 300),
        label = "album_scale"
    )
    
    val elevation by animateFloatAsState(
        targetValue = if (focused) 48f else 24f,
        animationSpec = tween(durationMillis = 300),
        label = "album_elevation"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation
                shape = RoundedCornerShape(cornerRadius)
                clip = true
            }
            .shadow(
                elevation = elevation.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = Color.Black.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF2C2C2E))
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(size / 3)
                )
            }
        }
    }
}

