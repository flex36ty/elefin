package com.flex.elefin.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Artwork-derived colors used by library screens
 */
data class ArtworkPalette(
    val background: Color,
    val backgroundDark: Color,
    val accent: Color,
    val onBackground: Color
)

/**
 * Palette extraction on a background dispatcher
 */
object PlexPaletteExtractor {

    suspend fun extract(
        context: Context,
        bitmap: Bitmap
    ): ArtworkPalette = withContext(Dispatchers.Default) {

        // Bound palette work to a small sample.
        val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)

        val palette = Palette.from(scaled)
            .maximumColorCount(16)
            .clearFilters()
            .generate()

        val accentColor = when {
            palette.vibrantSwatch != null -> palette.vibrantSwatch!!
            palette.lightVibrantSwatch != null -> palette.lightVibrantSwatch!!
            palette.mutedSwatch != null -> palette.mutedSwatch!!
            else -> palette.dominantSwatch
        }?.rgb ?: Color.White.toArgb()

        val background = extractHomeTone(bitmap, 16f / 9f)
        val accent = clampForAccent(Color(accentColor))

        val onBackground = if (ColorUtils.calculateLuminance(background.toArgb()) < 0.35)
            Color.White else Color.Black

        if (scaled !== bitmap) scaled.recycle()
        ArtworkPalette(
            background = background,
            backgroundDark = background,
            accent = accent,
            onBackground = onBackground
        )
    }

    private fun clampForAccent(color: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)

        hsl[1] = hsl[1].coerceIn(0.45f, 0.85f)
        hsl[2] = hsl[2].coerceIn(0.45f, 0.65f)

        return Color(ColorUtils.HSLToColor(hsl))
    }
}

/**
 * Small helpers (clean + reusable)
 */
fun Color.darken(amount: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[2] = (hsl[2] - amount).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Compose: Plex-style background gradient
 * Keep the artwork hue through the bottom edge on every screen size.
 */
@Composable
fun PlexBackdropGradient(palette: ArtworkPalette, disableAnimations: Boolean = false): Brush {
    val tone by animateColorAsState(palette.background,
        animationSpec = tween(if (disableAnimations) 0 else 1200,
            easing = FastOutSlowInEasing), label = "artwork_scrim")
    return Brush.verticalGradient(
        0f to tone.copy(alpha = 0.04f),
        0.24f to tone.copy(alpha = 0.15f),
        0.46f to tone.copy(alpha = 0.90f),
        0.66f to tone,
        1f to tone
    )
}

/**
 * Focus & CTA coloring (very Plex)
 */
@Composable
fun PlexPrimaryButton(
    palette: ArtworkPalette,
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = palette.accent,
            contentColor = Color.Black
        )
    ) {
        Text(text)
    }
}

/**
 * Cinematic multi-stop gradient for text readability and smooth backdrop fades
 */
@Composable
fun PlexMultiStopGradient(
    baseColor: Color = Color(0xFF09090B),
    startY: Float = 0f,
    endY: Float = Float.POSITIVE_INFINITY
): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            baseColor.copy(alpha = 0.15f),
            baseColor.copy(alpha = 0.55f),
            baseColor.copy(alpha = 0.88f),
            baseColor
        ),
        startY = startY,
        endY = endY
    )
}

/**
 * Atmospheric ambient radial glow brush from palette accent color
 */
@Composable
fun PlexAmbientRadialGlow(
    palette: ArtworkPalette,
    radius: Float = 900f
): Brush {
    return Brush.radialGradient(
        colors = listOf(
            palette.accent.copy(alpha = 0.28f),
            palette.background.copy(alpha = 0.15f),
            Color.Transparent
        ),
        radius = radius
    )
}

