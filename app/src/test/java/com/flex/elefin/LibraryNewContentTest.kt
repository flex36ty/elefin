package com.flex.elefin.jellyfin

import org.junit.Assert.*
import org.junit.Test

class LibraryNewContentTest {
    @Test fun handlesJellyfinFractionalDatesAndOffsets() {
        assertEquals(libraryAddedMillis("2026-10-14T12:00:00.123Z"), libraryAddedMillis("2026-10-14T12:00:00.1234567Z"))
        assertEquals(libraryAddedMillis("2026-10-14T12:00:00Z"), libraryAddedMillis("2026-10-14T08:00:00-04:00"))
        assertEquals(libraryAddedMillis("2026-10-14T12:00:00.100Z"), libraryAddedMillis("2026-10-14T12:00:00.1Z"))
        assertNull(libraryAddedMillis(null))
        assertNull(libraryAddedMillis("not a date"))
    }
    @Test fun onlyAdditionsAfterBothSessionAndLibraryVisitCount() {
        assertTrue(libraryHasNewContent(150, 100, 0))
        assertFalse(libraryHasNewContent(100, 100, 0))
        assertFalse(libraryHasNewContent(150, 100, 200))
        assertTrue(libraryHasNewContent(201, 100, 200))
        assertFalse(libraryHasNewContent(null, 100, 0))
    }
}
