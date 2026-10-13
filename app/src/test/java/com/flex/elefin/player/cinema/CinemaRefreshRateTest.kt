package com.flex.elefin.player.cinema
import org.junit.Assert.*
import org.junit.Test
class CinemaRefreshRateTest {
    @Test fun matchesIntegerCadence() {
        assertTrue(cinemaCadenceMatches(120.0, 24.0))
        assertTrue(cinemaCadenceMatches(50.0, 25.0))
        assertTrue(cinemaCadenceMatches(59.94, 29.97))
        assertFalse(cinemaCadenceMatches(60.0, 24.0))
        assertFalse(cinemaCadenceMatches(24.0, 24000.0 / 1001))
        assertFalse(cinemaCadenceMatches(60.0, Double.NaN))
        assertFalse(cinemaCadenceMatches(60.0, 0.0))
    }
}
