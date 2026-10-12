package com.flex.elefin.player.cinema

import org.junit.Assert.*
import org.junit.Test

class TrickplayInfoTest {
    private val info = TrickplayInfo(320, 180, 10, 10, 896, 10000)
    @Test fun framesClampAtBothEnds() {
        assertEquals(0, info.frame(-1))
        assertEquals(0, info.frame(9999))
        assertEquals(1, info.frame(10000))
        assertEquals(895, info.frame(Long.MAX_VALUE))
    }
    @Test fun sheetBoundaryAndLastPartialSheet() {
        assertEquals(99, info.frame(999999))
        assertEquals(100, info.frame(1000000))
        assertEquals(895, info.frame(8950000))
    }
    @Test fun invalidMetadataDisablesPreview() {
        assertNull(info.copy(Interval = 0).frame(0))
        assertNull(info.copy(TileWidth = 0).frame(0))
        assertNull(info.copy(ThumbnailCount = 0).frame(0))
        assertNull(info.copy(Width = 10000).frame(0))
    }
}
