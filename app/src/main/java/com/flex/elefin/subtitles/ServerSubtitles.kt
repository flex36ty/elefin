package com.flex.elefin.subtitles

import com.flex.elefin.jellyfin.*
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import kotlinx.coroutines.delay
import java.util.Locale

internal fun remoteSubtitleUrl(base: String, itemId: String, value: String): String =
    URLBuilder().takeFrom(base.trimEnd('/') + "/").apply {
        appendPathSegments("Items", itemId, "RemoteSearch", "Subtitles", value, encodeSlash = true)
    }.buildString()

internal fun checkSubtitleResponse(status: Int) {
    if (status in 200..299) return
    throw IllegalStateException(when (status) {
        401 -> "Your Jellyfin session has expired. Please sign in again."
        403 -> "Your Jellyfin user needs permission to manage subtitles."
        404 -> "This item or subtitle is no longer available. Search again."
        429 -> "The subtitle provider's limit was reached. Try again later."
        else -> "Jellyfin could not complete the request (HTTP $status). Check the server's subtitle plugin and account configuration."
    })
}

internal fun subtitleServerLanguage(code: String): String = when (code) {
    "pt-br" -> "pob"
    "zh-cn" -> "zho"
    "zh-tw" -> "zht"
    else -> Locale.forLanguageTag(code).isO3Language
}

private fun MediaStream.subtitleIdentity() = listOf(Index, Path, Codec, Language)

internal fun newlyAddedSubtitle(before: MediaSource, after: MediaSource): MediaStream? {
    val existing = before.MediaStreams.orEmpty().filter { it.Type == "Subtitle" }.map { it.subtitleIdentity() }.toSet()
    return after.MediaStreams.orEmpty().filter {
        it.Type == "Subtitle" && it.IsExternal == true && it.Index != null && it.subtitleIdentity() !in existing
    }.singleOrNull()
}

data class ServerSubtitleSelection(val item: JellyfinItem, val sourceId: String, val stream: MediaStream, val localPath: String? = null)

/** A 204 can hide a provider failure; only select a track confirmed in refreshed metadata. */
suspend fun downloadServerSubtitle(api: JellyfinApiService, itemId: String, subtitleId: String): ServerSubtitleSelection {
    val before = api.getItemDetails(itemId) ?: error("Could not load this item's subtitle tracks. Try again.")
    val source = before.MediaSources?.firstOrNull() ?: error("No playable media source was found.")
    api.downloadRemoteSubtitle(itemId, subtitleId)
    repeat(12) {
        delay(1000)
        val refreshed = api.getItemDetails(itemId)
        val updatedSource = refreshed?.MediaSources?.firstOrNull { it.Id == source.Id }
        if (updatedSource != null) {
            val stream = newlyAddedSubtitle(source, updatedSource)
            if (stream != null) return ServerSubtitleSelection(refreshed, updatedSource.Id ?: itemId, stream)
        }
    }
    error("Jellyfin has not exposed a new subtitle track. It may already exist, still be processing, or the provider download failed. Check the subtitle list and the server's plugin configuration before retrying.")
}
