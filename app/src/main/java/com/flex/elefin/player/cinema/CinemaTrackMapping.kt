package com.flex.elefin.player.cinema

import com.flex.elefin.jellyfin.MediaStream

// FFmpeg indexes only container streams; Jellyfin may put external subtitles before those.
internal fun cinemaServerStreamIndex(streams: List<MediaStream>, containerIndex: Int?): Int? =
    containerIndex?.takeIf { it >= 0 }?.let { index ->
        streams.filter { it.IsExternal != true }.sortedBy { it.Index }.getOrNull(index)?.Index
    }

internal fun cinemaTrackLabel(stream: MediaStream?, title: String?, language: String?, fallback: String): String {
    stream?.DisplayTitle?.takeIf { it.isNotBlank() }?.let { return it }
    val code = (stream?.Language ?: language)?.takeIf { it.isNotBlank() && it != "und" }
    val fullLanguage = stream?.DisplayLanguage?.takeIf { it.isNotBlank() }
        ?: code?.let { java.util.Locale.forLanguageTag(it).getDisplayLanguage(java.util.Locale.ENGLISH).takeIf { name -> name.isNotBlank() } }
    val description = stream?.Title?.takeIf { it.isNotBlank() } ?: title?.takeIf { it.isNotBlank() }
    return listOfNotNull(fullLanguage, description?.takeUnless {
        it.equals(code, ignoreCase = true) || it.equals(fullLanguage, ignoreCase = true)
    }, "SDH".takeIf { stream?.IsHearingImpaired == true && description?.contains("SDH", true) != true },
        "Forced".takeIf { stream?.IsForced == true && description?.contains("Forced", true) != true })
        .joinToString(" - ").ifBlank { fallback }
}
