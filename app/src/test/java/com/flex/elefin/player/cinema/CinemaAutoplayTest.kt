package com.flex.elefin.player.cinema

import org.junit.Assert.*
import org.junit.Test

class CinemaAutoplayTest {
    @Test fun countdownStartsOnlyInFinalWindowWithoutCredits() {
        assertFalse(cinemaAutoplayDue(false, 100.0, 89.0, null, 10))
        assertTrue(cinemaAutoplayDue(false, 100.0, 90.0, null, 10))
        assertFalse(cinemaAutoplayDue(false, 100.0, 20.0, null, 10))
    }
    @Test fun creditsMarkerStartsCountdownAndSeekingBackHidesIt() {
        assertTrue(cinemaAutoplayDue(false, 1000.0, 900.0, 900000, 10))
        assertFalse(cinemaAutoplayDue(false, 1000.0, 899.0, 900000, 10))
    }
    @Test fun invalidMarkerFallsBackAndUnknownDurationWaitsForCompletion() {
        assertTrue(cinemaAutoplayDue(false, 100.0, 95.0, 200000, 10))
        assertFalse(cinemaAutoplayDue(false, 0.0, 0.0, null, 10))
        assertTrue(cinemaAutoplayDue(true, 0.0, 0.0, null, 10))
    }
    private fun chapter(name: String, ms: Long) = com.flex.elefin.jellyfin.ChapterInfo(ms * 10000, name)
    @Test fun creditsSkipPreservesFollowingScene() {
        val range = cinemaCreditsChapter(listOf(chapter("End Credits", 900000), chapter("Post-credits scene", 950000)), 1000000)
        assertEquals(CinemaCreditsChapter(900000, 950000), range)
    }
    @Test fun finalCreditsEndAtDuration() {
        assertEquals(CinemaCreditsChapter(900000, 1000000), cinemaCreditsChapter(listOf(chapter("CREDITS", 900000)), 1000000))
    }
    @Test fun openingCreditsAndGenericChaptersAreNotEndCredits() {
        assertNull(cinemaCreditsChapter(listOf(chapter("Opening Credits", 0), chapter("Chapter 12", 900000)), 1000000))
        assertNull(cinemaCreditsChapter(listOf(chapter("Credits", 10000)), 1000000))
        assertNull(cinemaCreditsChapter(listOf(chapter("Post-credits scene", 950000)), 1000000))
    }
    @Test fun introMarkerOverridesChapters() {
        assertEquals(CinemaIntroRange(1000, 61000), cinemaIntroRange(1000, 61000,
            listOf(chapter("Intro", 0), chapter("Episode", 90000)), 1000000))
    }
    @Test fun introChapterEndsAtNextChapter() {
        assertEquals(CinemaIntroRange(30000, 120000), cinemaIntroRange(null, null,
            listOf(chapter("Cold open", 0), chapter("Opening Credits", 30000), chapter("Episode", 120000)), 1000000))
    }
    @Test fun introNeedsAnUnambiguousEnd() {
        assertNull(cinemaIntroRange(null, null, listOf(chapter("Intro", 0)), 1000000))
        assertNull(cinemaIntroRange(60000, 1000, emptyList(), 1000000))
        assertNull(cinemaIntroRange(null, null, listOf(chapter("Chapter 1", 0), chapter("Episode", 60000)), 1000000))
    }
}
