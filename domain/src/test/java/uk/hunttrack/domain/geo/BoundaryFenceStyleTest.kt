package uk.hunttrack.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundaryFenceStyleTest {
    @Test
    fun unlinkedFenceStaysVisibleAndBlue() {
        assertTrue(BoundaryFenceStyle.isVisible(emptyList(), setOf(1L)))
        assertEquals(BoundaryFenceStyle.DRAW_COLOR, BoundaryFenceStyle.lineColor(linked = false))
        assertEquals(BoundaryFenceStyle.DRAW_WIDTH, BoundaryFenceStyle.lineWidth(linked = false))
    }

    @Test
    fun linkedFenceIsGoldAndHiddenOnlyWhenEveryPermissionIsHidden() {
        assertEquals(BoundaryFenceStyle.LINKED_COLOR, BoundaryFenceStyle.lineColor(linked = true))
        assertTrue(BoundaryFenceStyle.isVisible(listOf(4L, 9L), setOf(4L)))
        assertFalse(BoundaryFenceStyle.isVisible(listOf(4L, 9L), setOf(4L, 9L)))
    }

    @Test
    fun linkingReplacesOnlyAStockBoundaryName() {
        assertEquals("Bob's farm", BoundaryFenceStyle.nameAfterLink("Hunting area", "Bob's farm"))
        assertEquals("Bob's farm", BoundaryFenceStyle.nameAfterLink("", "Bob's farm"))
        assertEquals("North paddock", BoundaryFenceStyle.nameAfterLink("North paddock", "Bob's farm"))
        assertEquals("Hunting area", BoundaryFenceStyle.nameAfterLink("Hunting area", "  "))
    }
}
