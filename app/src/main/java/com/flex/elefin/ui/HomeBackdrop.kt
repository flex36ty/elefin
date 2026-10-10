package com.flex.elefin.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val homePalettes = LruCache<String, Color>(96)
private val fallbackTone = Color(0xFF101218)
private data class ReadyBackdrop(val bitmap: Bitmap, val tone: Color)

/** The displayed image and its scrim are committed together, only after loading succeeds. */
@Composable
fun HomeBackdrop(
    item: JellyfinItem?,
    api: JellyfinApiService?,
    darkMode: Boolean,
    lowPower: Boolean,
    use4K: Boolean,
    disableAnimations: Boolean,
    isScrolling: () -> Boolean = { false },
    artworkUrl: String? = null
) {
    val context = LocalContext.current
    val loader = context.imageLoader
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer().background(Color(0xFF090A0D))) {
        val aspect = constraints.maxWidth.toFloat() / constraints.maxHeight.coerceAtLeast(1)
        val width = if (lowPower) 1280 else if (use4K) 3840 else 1920
        val height = (width / aspect).roundToInt().coerceAtLeast(1)
        val itemId = if (item?.Type == "Episode") item.SeriesId ?: item.Id else item?.Id
        var ready by remember(api) { mutableStateOf<ReadyBackdrop?>(null) }

        LaunchedEffect(itemId, api, width, height, darkMode, artworkUrl) {
            if (darkMode) {
                ready = null
                return@LaunchedEffect
            }
            val urls = if (artworkUrl != null) listOf(artworkUrl).filter { it.isNotBlank() }
                else if (itemId != null && api != null) listOf("Backdrop", "Thumb", "Primary").map { type ->
                    api.getImageUrl(itemId, type, null, maxWidth = width,
                        maxHeight = height, quality = if (lowPower) 75 else 90)
                } else emptyList()
            val headers = if (artworkUrl == null) api?.getImageRequestHeaders()
                ?: okhttp3.Headers.Builder().build() else okhttp3.Headers.Builder().build()
            snapshotFlow { isScrolling() }.first { !it }
            // Try the primary image if the server has no backdrop. Keep the previous image on failure.
            for (url in urls) {
                try {
                    val result = loader.execute(ImageRequest.Builder(context)
                        .data(url)
                        .headers(headers)
                        .size(width, height)
                        .allowHardware(false)
                        .crossfade(false)
                        .build())
                    val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                        ?: continue
                    val paletteKey = "$url:$aspect"
                    val tone = homePalettes.get(paletteKey) ?: withContext(Dispatchers.Default) {
                        extractHomeTone(bitmap, aspect)
                    }.also { homePalettes.put(paletteKey, it) }
                    ensureActive()
                    // Keep texture uploads and full-screen crossfades out of a vertical scroll.
                    snapshotFlow { isScrolling() }.first { !it }
                    ready = ReadyBackdrop(bitmap, tone)
                    break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A failed or missing image should never blank a previously loaded backdrop.
                }
            }
        }

        if (!darkMode) {
            Box(Modifier.fillMaxSize().flowingArtworkScrim(
                ready?.tone ?: fallbackTone, disableAnimations || lowPower).drawWithCache {
                val leftScrim = Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.78f),
                    0.42f to Color.Black.copy(alpha = 0.58f),
                    0.78f to Color.Black.copy(alpha = 0.08f),
                    1f to Color.Transparent)
                val navigationScrim = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.48f),
                    0.18f to Color.Transparent)
                onDrawWithContent {
                    drawContent()
                    drawRect(leftScrim)
                    drawRect(navigationScrim)
                }
            }) {
                Crossfade(targetState = ready,
                    animationSpec = tween(if (disableAnimations || lowPower) 0 else 1400,
                        easing = FastOutSlowInEasing),
                    label = "home_atmosphere") { frame ->
                    frame?.let {
                        Image(bitmap = it.bitmap.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    }
}

/** Sample the visible artwork and retain its dominant hue at a readable background brightness. */
internal fun extractHomeTone(bitmap: Bitmap, viewportAspect: Float): Color {
    val sourceAspect = bitmap.width.toFloat() / bitmap.height
    // Shrink before cropping so even a 4K backdrop only needs tiny temporary bitmaps.
    val thumbnail = Bitmap.createScaledBitmap(bitmap, 128, (128 / sourceAspect).roundToInt().coerceAtLeast(1), true)
    val cropWidth = if (sourceAspect > viewportAspect) (thumbnail.height * viewportAspect).roundToInt() else thumbnail.width
    val cropHeight = if (sourceAspect < viewportAspect) (thumbnail.width / viewportAspect).roundToInt() else thumbnail.height
    val crop = Bitmap.createBitmap(thumbnail, (thumbnail.width - cropWidth) / 2,
        (thumbnail.height - cropHeight) / 2, cropWidth.coerceAtLeast(1), cropHeight.coerceAtLeast(1))
    val sample = Bitmap.createScaledBitmap(crop, 96, 64, true)
    try {
        val swatches = Palette.from(sample).maximumColorCount(16).clearFilters().generate().swatches
        val candidate = swatches.filter { swatch ->
            swatch.hsl[2] in 0.08f..0.90f && swatch.hsl[1] > 0.10f
        }.maxByOrNull { it.population }
            ?: swatches.maxByOrNull { it.population } ?: return fallbackTone
        val hsl = candidate.hsl.copyOf()
        hsl[1] = (hsl[1] * 0.85f).coerceAtMost(0.65f)
        hsl[2] = (hsl[2] * 0.45f).coerceIn(0.14f, 0.22f)
        return Color(ColorUtils.HSLToColor(hsl))
    } finally {
        if (sample !== crop && sample !== bitmap) sample.recycle()
        if (crop !== thumbnail && crop !== bitmap) crop.recycle()
        if (thumbnail !== bitmap) thumbnail.recycle()
    }
}
