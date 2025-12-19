package com.flex.elefin.ui

import android.content.Context
import android.graphics.Bitmap
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
 * Core data model (what Plex effectively caches)
 */
data class ArtworkPalette(
    val background: Color,
    val backgroundDark: Color,
    val accent: Color,
    val onBackground: Color
)

/**
 * Palette extractor (Plex-style rules)
 */
object PlexPaletteExtractor {

    suspend fun extract(
        context: Context,
        bitmap: Bitmap
    ): ArtworkPalette = withContext(Dispatchers.Default) {

        // ⚠️ Downscale aggressively (Plex does this)
        val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)

        val palette = Palette.from(scaled)
            .maximumColorCount(16)
            .clearFilters() // Plex does NOT use the default white/black filters
            .generate()

        // Preferred order (very Plex-like)
        val bgColor = when {
            palette.darkMutedSwatch != null -> palette.darkMutedSwatch!!
            palette.darkVibrantSwatch != null -> palette.darkVibrantSwatch!!
            palette.mutedSwatch != null -> palette.mutedSwatch!!
            else -> palette.dominantSwatch
        }?.rgb ?: Color.DarkGray.toArgb()

        val accentColor = when {
            palette.vibrantSwatch != null -> palette.vibrantSwatch!!
            palette.lightVibrantSwatch != null -> palette.lightVibrantSwatch!!
            palette.mutedSwatch != null -> palette.mutedSwatch!!
            else -> palette.dominantSwatch
        }?.rgb ?: Color.White.toArgb()

        val background = clampForBackground(Color(bgColor))
        val accent = clampForAccent(Color(accentColor))

        val onBackground = if (ColorUtils.calculateLuminance(background.toArgb()) < 0.35)
            Color.White else Color.Black

        ArtworkPalette(
            background = background,
            backgroundDark = background.darken(0.15f),
            accent = accent,
            onBackground = onBackground
        )
    }

    /**
     * Plex-style color clamps (THIS is why it looks good)
     * Background clamp (dark, cinematic, muted)
     */
    private fun clampForBackground(color: Color): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)

        // Plex-like rules
        hsl[1] = hsl[1].coerceAtMost(0.45f)   // kill neon saturation
        hsl[2] = hsl[2].coerceIn(0.10f, 0.25f) // force dark luminance

        return Color(ColorUtils.HSLToColor(hsl))
    }

    /**
     * Accent clamp (still visible, never neon)
     */
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
 * This is exactly how Plex layers it visually.
 */
@Composable
fun PlexBackdropGradient(palette: ArtworkPalette): Brush {
    return Brush.verticalGradient(
        colors = listOf(
            palette.background.copy(alpha = 0f), // Transparent top to show artwork
            palette.background.copy(alpha = 0.6f),
            palette.backgroundDark,
            Color.Black
        ),
        startY = 0f,
        endY = 1400f
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
