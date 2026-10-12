package com.flex.elefin.player.cinema

/** Match the episode credits marker, falling back to the configured final seconds. */
internal fun cinemaAutoplayDue(completed: Boolean, duration: Double, position: Double,
    creditsStartMs: Long?, countdownSeconds: Int): Boolean {
    if (completed) return true
    if (duration <= 0) return false
    return if (creditsStartMs != null && creditsStartMs > 0 && creditsStartMs < duration * 1000) {
        position * 1000 >= creditsStartMs
    } else {
        duration - position in 0.0..countdownSeconds.toDouble()
    }
}

internal data class CinemaCreditsChapter(val startMs: Long, val endMs: Long)

internal fun cinemaCreditsChapter(chapters: List<com.flex.elefin.jellyfin.ChapterInfo>, durationMs: Long): CinemaCreditsChapter? {
    if (durationMs <= 0) return null
    val ordered = chapters.sortedBy { it.startMs }
    val creditsName = Regex("^(?:end[ -]*credits|closing[ -]*credits|rolling[ -]*credits|credits)(?:\\s*[-:–].*)?$", RegexOption.IGNORE_CASE)
    val chapter = ordered.firstOrNull {
        it.startMs >= durationMs / 2 && it.startMs < durationMs &&
            creditsName.matches(it.Name.orEmpty().trim())
    } ?: return null
    return CinemaCreditsChapter(chapter.startMs,
        ordered.firstOrNull { it.startMs > chapter.startMs }?.startMs?.coerceAtMost(durationMs) ?: durationMs)
}

internal data class CinemaIntroRange(val startMs: Long, val endMs: Long)

internal fun cinemaIntroRange(startMs: Long?, endMs: Long?,
    chapters: List<com.flex.elefin.jellyfin.ChapterInfo>, durationMs: Long): CinemaIntroRange? {
    if (startMs != null && endMs != null && startMs >= 0 && endMs > startMs &&
        (durationMs <= 0 || endMs < durationMs)) return CinemaIntroRange(startMs, endMs)
    if (durationMs <= 0) return null
    val ordered = chapters.sortedBy { it.startMs }
    val introName = Regex("^(?:intro|opening|opening[ -]+credits|opening[ -]+titles|title[ -]+sequence|theme[ -]+song)$", RegexOption.IGNORE_CASE)
    val chapter = ordered.firstOrNull { it.startMs >= 0 && it.startMs < durationMs / 2 &&
        introName.matches(it.Name.orEmpty().trim()) } ?: return null
    val next = ordered.firstOrNull { it.startMs > chapter.startMs } ?: return null
    if (next.startMs >= durationMs || next.startMs > durationMs / 2) return null
    return CinemaIntroRange(chapter.startMs, next.startMs)
}
