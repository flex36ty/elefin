package com.flex.elefin.player.cinema

import org.junit.Assert.*
import org.junit.Test

class CinemaHdrLikeTest {
    @Test fun acceptsKnownSdr() {
        assertTrue(cinemaSupportsHdrLike("bt.1886", "bt.709"))
        assertTrue(cinemaSupportsHdrLike("srgb", "bt.709"))
    }
    @Test fun rejectsHdrAndUnknownMetadata() {
        for (transfer in listOf("pq", "hlg", "st2084", "unknown", null)) {
            assertFalse(cinemaSupportsHdrLike(transfer, "bt.709"))
        }
        assertFalse(cinemaSupportsHdrLike("bt.1886", "bt.2020"))
        assertFalse(cinemaSupportsHdrLike("bt.1886", null))
    }
    @Test fun boundsStrength() {
        assertEquals(cinemaHdrLikeShader(0), cinemaHdrLikeShader(-10))
        assertEquals(cinemaHdrLikeShader(100), cinemaHdrLikeShader(200))
    }
}
