package com.flex.elefin.player.cinema

import androidx.compose.runtime.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CancellationException
import com.flex.elefin.jellyfin.JellyfinApiService

@Serializable
data class TrickplayInfo(val Width: Int = 0, val Height: Int = 0,
    val TileWidth: Int = 0, val TileHeight: Int = 0, val ThumbnailCount: Int = 0, val Interval: Int = 0) {
    fun frame(positionMs: Long): Int? = if (Width > 0 && Height > 0 && TileWidth > 0 && TileHeight > 0 &&
        ThumbnailCount > 0 && Interval > 0 && Width.toLong() * Height * TileWidth * TileHeight <= 16_000_000L)
        (positionMs.coerceAtLeast(0) / Interval).coerceAtMost(ThumbnailCount.toLong() - 1).toInt() else null
}

@Composable
internal fun TrickplayPreview(api: JellyfinApiService, itemId: String, sourceId: String,
    info: TrickplayInfo, positionMs: Long, modifier: Modifier = Modifier) {
    val frame = info.frame(positionMs) ?: return
    val sheet = frame / (info.TileWidth * info.TileHeight)
    val cell = frame % (info.TileWidth * info.TileHeight)
    val context = LocalContext.current
    val url = remember(api, itemId, sourceId, info.Width, sheet) {
        android.net.Uri.parse("${api.serverBaseUrl.trimEnd('/')}/Videos/$itemId/Trickplay/${info.Width}/$sheet.jpg")
            .buildUpon().appendQueryParameter("mediaSourceId", sourceId).build().toString()
    }
    val bitmap by produceState<ImageBitmap?>(null, url) {
        value = null
        try {
            val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url)
                .headers(api.getImageRequestHeaders()).allowHardware(false).size(coil.size.Size.ORIGINAL).build())
            if (result is SuccessResult) value = result.drawable.toBitmap().asImageBitmap()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Missing previews never interrupt playback. */ }
    }
    val image = bitmap ?: return
    val x = cell % info.TileWidth * info.Width
    val y = cell / info.TileWidth * info.Height
    if (x + info.Width > image.width || y + info.Height > image.height) return
    Canvas(modifier.width(180.dp).aspectRatio(info.Width.toFloat() / info.Height)
        .clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
        drawImage(image, srcOffset = IntOffset(x, y), srcSize = IntSize(info.Width, info.Height),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()))
    }
}
