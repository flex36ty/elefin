package com.flex.elefin.screens

import org.junit.Assert.*
import org.junit.Test

class LibraryVerticalTargetTest {
    @Test fun movesOneRowPreservingColumn() {
        assertEquals(13, libraryVerticalTarget(19, -1, 6, 100))
        assertEquals(25, libraryVerticalTarget(19, 1, 6, 100))
    }
    @Test fun clampsPartialLastRowAndStopsAtEdges() {
        assertEquals(19, libraryVerticalTarget(17, 1, 6, 20))
        assertNull(libraryVerticalTarget(19, 1, 6, 20))
        assertNull(libraryVerticalTarget(5, -1, 6, 20))
        assertNull(libraryVerticalTarget(0, 1, 6, 0))
    }
}
