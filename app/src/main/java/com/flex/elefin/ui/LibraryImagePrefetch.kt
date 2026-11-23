package com.flex.elefin.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import com.flex.elefin.jellyfin.JellyfinApiService
import com.flex.elefin.jellyfin.JellyfinItem
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Warm one nearby row without queuing entire pages or observing scroll offsets in the screen. */
@Composable
internal fun LibraryImagePrefetch(
    state: LazyListState,
    items: List<JellyfinItem>,
    api: JellyfinApiService?,
    enabled: Boolean,
    cacheImages: Boolean,
    reducedResolution: Boolean
) {
    val context = LocalContext.current
    val loader = context.imageLoader
    LaunchedEffect(state, items, api, enabled, cacheImages, reducedResolution) {
        if (!enabled || !cacheImages || api == null || items.isEmpty()) return@LaunchedEffect
        snapshotFlow { state.firstVisibleItemIndex }.collectLatest { firstVisible ->
            delay(120)
            val start = ((firstVisible + 2) * 6).coerceAtMost(items.size)
            val end = (start + 6).coerceAtMost(items.size)
            val pending = mutableListOf<Disposable>()
            try {
                for (index in start until end) {
                    val url = api.getImageUrl(items[index].Id, "Primary", null,
                        maxWidth = if (reducedResolution) 300 else 400,
                        maxHeight = if (reducedResolution) 450 else 600,
                        quality = if (reducedResolution) 80 else 85)
                    if (url.isNotEmpty()) {
                        pending += loader.enqueue(ImageRequest.Builder(context)
                            .data(url).headers(api.getImageRequestHeaders())
                            .size(300).crossfade(false).build())
                    }
                }
                awaitCancellation()
            } finally {
                pending.forEach { it.dispose() }
            }
        }
    }
}
