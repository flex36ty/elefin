package com.flex.elefin.music.ui.apple

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Artwork atmosphere shared by the TV music screens; no live full-screen blur. */
@Composable
fun AppleBlurBackground(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    darken: Float = 0.55f,
    @Suppress("UNUSED_PARAMETER") blurRadius: Float = 80f
) {
    val context = LocalContext.current
    // A tiny decoded image gives a soft wash of artwork color without a GPU blur pass.
    val request = remember(context, imageUrl) {
        ImageRequest.Builder(context).data(imageUrl).size(32, 32).crossfade(700).build()
    }
    Box(modifier.fillMaxSize().background(Color(0xFF101116))) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(model = request, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0xFF101116).copy(alpha = darken.coerceIn(0.55f, 1f)),
            Color(0xFF101116).copy(alpha = 0.92f), Color(0xFF101116)
        ))))
    }
}
