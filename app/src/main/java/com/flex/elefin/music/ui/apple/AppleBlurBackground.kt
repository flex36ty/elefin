package com.flex.elefin.music.ui.apple

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * Apple Music-style blurred background with darkening overlay.
 * Uses Compose blur modifier with fallback for older versions.
 */
@Composable
fun AppleBlurBackground(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    darken: Float = 0.55f,
    blurRadius: Float = 80f
) {
    if (imageUrl == null) {
        // Fallback gradient background
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1C1C1E),
                            Color(0xFF000000)
                        )
                    )
                )
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // Use Compose blur on Android 12+
                        Modifier.blur(radius = blurRadius.dp)
                    } else {
                        // Fallback: just reduce alpha for older versions
                        Modifier.graphicsLayer { alpha = 0.6f }
                    }
                )
                .drawWithContent {
                    drawContent()
                    drawRect(Color.Black.copy(alpha = darken))
                },
            contentScale = ContentScale.Crop,
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1C1C1E))
                )
            }
        )
    }
}

