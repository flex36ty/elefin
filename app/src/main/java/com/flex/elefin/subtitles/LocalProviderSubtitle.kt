package com.flex.elefin.subtitles

import android.content.Context
import com.flex.elefin.jellyfin.*
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

internal fun providerSubtitleFileUrl(base: String, id: String): String =
    URLBuilder().takeFrom(base.trimEnd('/') + "/").apply {
        appendPathSegments("Providers", "Subtitles", "Subtitles", id, encodeSlash = true)
    }.buildString()

private val providerClient = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()

/** Jellyfin authenticates to its provider; only the subtitle bytes are stored on this device. */
suspend fun downloadLocalProviderSubtitle(context: Context, api: JellyfinApiService, itemId: String,
    subtitle: RemoteSubtitleInfo): ServerSubtitleSelection = withContext(Dispatchers.IO) {
    val id = requireNotNull(subtitle.Id)
    val item = api.getItemDetails(itemId) ?: error("Could not load this item's details.")
    val sourceId = item.MediaSources?.firstOrNull()?.Id ?: itemId
    val extension = when (subtitle.Format?.lowercase()) {
        "srt", "subrip" -> "srt"
        "vtt", "webvtt" -> "vtt"
        "ass", "ssa" -> "ass"
        "ttml" -> "ttml"
        else -> error("This subtitle format is not supported for local download.")
    }
    val key = MessageDigest.getInstance("SHA-256").digest("${api.serverBaseUrl}|$itemId|$id".toByteArray())
        .joinToString("") { "%02x".format(it) }.take(24)
    val language = subtitle.ThreeLetterISOLanguageName.orEmpty().replace(Regex("[^a-zA-Z-]"), "").ifBlank { "und" }
    val label = subtitle.Name ?: "Downloaded subtitle"
    val safeName = label.replace(Regex("[^a-zA-Z0-9 .-]"), "_").take(90)
    val directory = File(context.filesDir, "downloaded_subtitles/${item.Id}").apply { mkdirs() }
    val file = File(directory, "${language}_${safeName}_$key.$extension")
    if (!file.exists() || file.length() == 0L) {
        val request = Request.Builder().url(providerSubtitleFileUrl(api.serverBaseUrl, id)).apply {
            api.getVideoRequestHeaders().forEach { (name, value) -> header(name, value) }
        }.build()
        val temporary = File.createTempFile("provider-", ".part", context.cacheDir)
        try {
            providerClient.newCall(request).execute().use { response ->
                checkSubtitleResponse(response.code)
                val body = response.body ?: error("Jellyfin returned an empty subtitle.")
                val type = body.contentType()?.toString().orEmpty()
                check(!type.contains("html") && !type.contains("json")) { "Jellyfin returned an error instead of a subtitle file." }
                check(body.contentLength() <= 5_000_000L) { "Subtitle file is too large." }
                var total = 0L
                body.byteStream().use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= 5_000_000L) { "Subtitle file is too large." }
                        output.write(buffer, 0, count)
                    }
                } }
                check(total > 0 && (body.contentLength() < 0 || total == body.contentLength())) { "Subtitle download was incomplete." }
            }
            check(temporary.renameTo(file)) { "Could not save the subtitle on this device." }
        } finally { temporary.delete() }
    }
    selectLocalProviderSubtitle(context, api, itemId, file.absolutePath)
    android.util.Log.i("ServerSubtitles", "Provider subtitle cached locally: ${file.length()} bytes, format=$extension")
    ServerSubtitleSelection(item, sourceId, MediaStream(Type = "Subtitle", Codec = extension,
        Language = language, DisplayTitle = label, IsExternal = true, Path = file.absolutePath,
        IsHearingImpaired = subtitle.HearingImpaired, IsForced = subtitle.Forced), localPath = file.absolutePath)
}

fun selectedLocalProviderSubtitle(context: Context, api: JellyfinApiService, itemId: String): String? =
    context.getSharedPreferences("local_provider_subtitles", Context.MODE_PRIVATE)
        .getString("${api.serverBaseUrl}|$itemId", null)?.takeIf { File(it).isFile }

fun clearLocalProviderSubtitle(context: Context, api: JellyfinApiService, itemId: String) {
    context.getSharedPreferences("local_provider_subtitles", Context.MODE_PRIVATE).edit()
        .remove("${api.serverBaseUrl}|$itemId").apply()
}

fun selectLocalProviderSubtitle(context: Context, api: JellyfinApiService, itemId: String, path: String) {
    val file = File(path)
    if (!file.isFile) return
    context.getSharedPreferences("local_provider_subtitles", Context.MODE_PRIVATE).edit()
        .putString("${api.serverBaseUrl}|$itemId", file.absolutePath).apply()
}
