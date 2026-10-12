package com.flex.elefin.player.cinema

import com.flex.elefin.jellyfin.MediaStream
import org.junit.Assert.*
import org.junit.Test

class CinemaTrackMappingTest {
    @Test fun externalSubtitlesDoNotShiftEmbeddedTrackSelection() {
        val streams = listOf(
            MediaStream(Index = 0, Type = "Subtitle", IsExternal = true),
            MediaStream(Index = 1, Type = "Subtitle", IsExternal = true),
            MediaStream(Index = 2, Type = "Video"),
            MediaStream(Index = 3, Type = "Audio"),
            MediaStream(Index = 4, Type = "Subtitle"))
        assertEquals(3, cinemaServerStreamIndex(streams, 1))
        assertEquals(4, cinemaServerStreamIndex(streams, 2))
        assertNull(cinemaServerStreamIndex(streams, -1))
        assertNull(cinemaServerStreamIndex(streams, 99))
        assertNull(cinemaServerStreamIndex(streams, null))
    }
}
