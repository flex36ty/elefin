package com.flex.elefin.player.cinema

import org.junit.Assert.assertEquals
import org.junit.Test

class CinemaTimeTest {
    @Test fun invalidTimesDisplayZero() {
        for (time in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals("0:00", cinemaTime(time))
        }
    }
    @Test fun formatsMinutesAndHoursWithoutRoundingUp() {
        assertEquals("0:00", cinemaTime(0.0))
        assertEquals("1:01", cinemaTime(61.9))
        assertEquals("59:59", cinemaTime(3599.9))
        assertEquals("1:00:00", cinemaTime(3600.0))
        assertEquals("12:34:56", cinemaTime(45296.0))
    }
}
