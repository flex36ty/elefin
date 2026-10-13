package com.flex.elefin.player

import com.flex.elefin.jellyfin.MediaStream
import java.util.Locale

/** Device language wins over a file's default flag; unknown languages retain file order. */
internal fun preferredAudioIndex(streams: List<MediaStream>, locale: Locale = Locale.getDefault()): Int? {
    fun language(value: String?): String {
        val tag = value.orEmpty().lowercase(Locale.ROOT).replace('_', '-').substringBefore('-')
        val aliases = mapOf("fre" to "fra", "ger" to "deu", "chi" to "zho", "dut" to "nld", "cze" to "ces", "gre" to "ell", "rum" to "ron", "per" to "fas", "wel" to "cym", "slo" to "slk", "alb" to "sqi", "arm" to "hye", "baq" to "eus", "bur" to "mya", "geo" to "kat", "ice" to "isl", "mac" to "mkd", "mao" to "mri", "may" to "msa", "tib" to "bod")
        return aliases[tag] ?: if (tag.length == 2) runCatching { Locale.forLanguageTag(tag).isO3Language }.getOrDefault(tag) else tag
    }
    val allAudio = streams.filter { it.Type == "Audio" && it.Index != null }
    val mainAudio = allAudio.filterNot {
        listOfNotNull(it.Title, it.DisplayTitle).any { title -> title.contains("commentary", ignoreCase = true) }
    }
    val audio = mainAudio.ifEmpty { allAudio }
    val target = language(locale.language)
    val matching = audio.filter { target.isNotEmpty() && language(it.Language) == target }
    return (matching.firstOrNull { it.IsDefault == true } ?: matching.firstOrNull()
        ?: audio.firstOrNull { it.IsDefault == true } ?: audio.firstOrNull())?.Index
}
