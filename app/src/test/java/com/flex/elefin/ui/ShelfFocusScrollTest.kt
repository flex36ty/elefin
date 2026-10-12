package com.flex.elefin.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfFocusScrollTest {
    @Test fun enteringVerticallyPreservesVisibleCardPosition() {
        val spec = TvBringIntoViewSpec(vertical = false, horizontalPivotEnabled = { false })
        assertEquals(0f, spec.calculateScrollDistance(100f, 200f, 1600f), .01f)
        assertEquals(-20f, spec.calculateScrollDistance(-20f, 200f, 1600f), .01f)
        assertEquals(50f, spec.calculateScrollDistance(1450f, 200f, 1600f), .01f)
    }
    @Test fun horizontalKeyRestoresNormalPivot() {
        var horizontal = false
        val spec = TvBringIntoViewSpec(vertical = false, horizontalPivotEnabled = { horizontal })
        assertEquals(0f, spec.calculateScrollDistance(100f, 200f, 1600f), .01f)
        horizontal = true
        assertEquals(-300f, spec.calculateScrollDistance(100f, 200f, 1600f), .01f)
    }
}
