package com.flex.elefin.player.cinema

import org.junit.Assert.*
import org.junit.Test

class CinemaAutoQualityTest {
    @Test fun originalQualityRemainsWithoutBuffering() {
        val policy = CinemaAutoQuality()
        repeat(300) { assertNull(policy.tick(true, false, true, 40)) }
        assertNull(policy.capMbps)
    }
    @Test fun sustainedBufferingLowersQualityButPauseDoesNot() {
        val policy = CinemaAutoQuality()
        repeat(25) { policy.tick(true, false, true, 40) }
        repeat(7) { assertNull(policy.tick(true, true, false, 40)) }
        assertEquals(20, policy.tick(true, true, false, 40))
        policy.changed(20)
        repeat(100) { assertNull(policy.tick(false, true, false, 40)) }
        repeat(7) { assertNull(policy.tick(true, true, false, 40)) }
        assertEquals(12, policy.tick(true, true, false, 40))
    }
    @Test fun upgradesRequireStablePlaybackAndRespectSourceLimit() {
        val policy = CinemaAutoQuality()
        policy.changed(8)
        repeat(179) { assertNull(policy.tick(true, false, true, 20)) }
        assertEquals(12, policy.tick(true, false, true, 20))
        policy.changed(20)
        repeat(300) { assertNull(policy.tick(true, false, true, 20)) }
    }
    @Test fun neverDropsBelowMinimumOrUpgradesAStalledClock() {
        val policy = CinemaAutoQuality()
        policy.changed(2)
        repeat(300) { assertNull(policy.tick(true, true, false, 40)) }
        repeat(300) { assertNull(policy.tick(true, false, false, 40)) }
    }
}
