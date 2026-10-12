package com.flex.elefin.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.core.Animatable
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val homePalettes = LruCache<String, HomeArtworkPalette>(96)
private val fallbackTone = Color(0xFF101218)
internal data class HomeArtworkPalette(
    val topLeft: Color,
    val topRight: Color,
    val bottomLeft: Color,
    val bottomRight: Color
) {
    val average: Color get() = androidx.compose.ui.graphics.lerp(
        androidx.compose.ui.graphics.lerp(topLeft, topRight, 0.5f),
        androidx.compose.ui.graphics.lerp(bottomLeft, bottomRight, 0.5f), 0.5f)
}
private val fallbackPalette = HomeArtworkPalette(fallbackTone, fallbackTone, fallbackTone, fallbackTone)
private data class ReadyBackdrop(val bitmap: Bitmap)

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
        val width = minOf(constraints.maxWidth.coerceAtLeast(1), if (lowPower) 1280 else if (use4K) 3840 else 1920)
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
            // Do not decode artwork for cards merely passed while holding a D-pad direction.
            delay(180)
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
                    val palette = homePalettes.get(paletteKey) ?: withContext(Dispatchers.Default) {
                        extractHomePalette(bitmap, aspect)
                    }.also { homePalettes.put(paletteKey, it) }
                    val rendered = withContext(Dispatchers.Default) {
                        renderHomeBackdrop(bitmap, palette, width, height)
                    }
                    ensureActive()
                    // Keep texture uploads and full-screen crossfades out of a vertical scroll.
                    snapshotFlow { isScrolling() }.first { !it }
                    ready = ReadyBackdrop(rendered)
                    break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A failed or missing image should never blank a previously loaded backdrop.
                }
            }
        }

        var displayed by remember(api) { mutableStateOf<ReadyBackdrop?>(null) }
        var previous by remember(api) { mutableStateOf<ReadyBackdrop?>(null) }
        val fade = remember { Animatable(1f) }
        val immediate by rememberUpdatedState(disableAnimations || lowPower)
        val scrolling by rememberUpdatedState(isScrolling)
        LaunchedEffect(api) {
            // Pause an in-flight dissolve as soon as scrolling begins. Resume from the
            // same alpha after idle, rather than scheduling another full-screen animation
            // alongside card composition, focus transfer and row movement.
            snapshotFlow { ready to scrolling() }.collectLatest { (frame, moving) ->
                if (frame == null) {
                    displayed = null
                    previous = null
                } else if (!moving) {
                    delay(180)
                    if (displayed !== frame) {
                        // Finish the current dissolve before taking another frame, so a
                        // focus change never replaces the outgoing image mid-transition.
                        if (previous != null && fade.value < 1f && !immediate) {
                            fade.animateTo(1f, tween((2000 * (1f - fade.value)).roundToInt(), easing = FastOutSlowInEasing))
                        }
                        previous = displayed
                        fade.snapTo(if (immediate || previous == null) 1f else 0f)
                        displayed = frame
                    }
                    if (fade.value < 1f) {
                        if (immediate) fade.snapTo(1f)
                        else fade.animateTo(1f, tween((2000 * (1f - fade.value)).roundToInt(), easing = FastOutSlowInEasing))
                    }
                    previous = null
                }
            }
        }

        if (!darkMode) {
            previous?.let { frame ->
                Image(bitmap = frame.bitmap.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            displayed?.let { frame ->
                Image(bitmap = frame.bitmap.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
                    contentScale = ContentScale.Crop)
            }
        }
    }
}

/** Sample the visible artwork and retain its dominant hue at a readable background brightness. */
internal fun extractHomeTone(bitmap: Bitmap, viewportAspect: Float): Color {
    return extractHomePalette(bitmap, viewportAspect).average
}

/** Independently sample four regions; this is Elefin's palette algorithm, not Plex's native extractor. */
internal fun extractHomePalette(bitmap: Bitmap, viewportAspect: Float): HomeArtworkPalette {
    val sourceAspect = bitmap.width.toFloat() / bitmap.height
    // Shrink before cropping so even a 4K backdrop only needs tiny temporary bitmaps.
    val scale = 128f / maxOf(bitmap.width, bitmap.height)
    val thumbnail = Bitmap.createScaledBitmap(bitmap,
        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
        (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
    val cropWidth = if (sourceAspect > viewportAspect) (thumbnail.height * viewportAspect).roundToInt() else thumbnail.width
    val cropHeight = if (sourceAspect < viewportAspect) (thumbnail.width / viewportAspect).roundToInt() else thumbnail.height
    val crop = Bitmap.createBitmap(thumbnail, (thumbnail.width - cropWidth) / 2,
        (thumbnail.height - cropHeight) / 2, cropWidth.coerceAtLeast(1), cropHeight.coerceAtLeast(1))
    val sample = Bitmap.createScaledBitmap(crop, 96, 64, true)
    try {
        fun regionTone(left: Int, top: Int, right: Int, bottom: Int): Color {
            val swatches = Palette.from(sample).setRegion(left, top, right, bottom)
                .maximumColorCount(12).clearFilters().generate().swatches
            val candidate = swatches.filter { swatch ->
                swatch.hsl[2] in 0.08f..0.90f && swatch.hsl[1] > 0.10f
            }.maxByOrNull { it.population }
                ?: swatches.maxByOrNull { it.population } ?: return fallbackTone
            val hsl = candidate.hsl.copyOf()
            hsl[1] = (hsl[1] * 0.85f).coerceAtMost(0.65f)
            hsl[2] = (hsl[2] * 0.45f).coerceIn(0.14f, 0.22f)
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(ColorUtils.HSLToColor(hsl), hsv)
            hsv[2] = hsv[2].coerceAtMost(0.30f)
            return Color(android.graphics.Color.HSVToColor(hsv))
        }
        return HomeArtworkPalette(
            regionTone(0, 0, 48, 32), regionTone(48, 0, 96, 32),
            regionTone(0, 32, 48, 64), regionTone(48, 32, 96, 64))
    } finally {
        if (sample !== crop && sample !== bitmap) sample.recycle()
        if (crop !== thumbnail && crop !== bitmap) crop.recycle()
        if (thumbnail !== bitmap) thumbnail.recycle()
    }
}
