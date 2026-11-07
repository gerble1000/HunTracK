package uk.hunttrack.feature.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapChromeSlotsTest {
    @Test
    fun focusedBoundaryPutsTheNameAboveTheMap() {
        val slots = mapChromeSlots(boundaryFocused = true)
        assertEquals(
            listOf(MapChromeSlot.BOUNDARY_NAME, MapChromeSlot.MAP),
            slots,
        )
        assertTrue(boundaryNameClearsToolRow(slots))
    }

    @Test
    fun mapWithoutAFocusedBoundaryHasNoNameBand() {
        val slots = mapChromeSlots(boundaryFocused = false)
        assertEquals(listOf(MapChromeSlot.MAP), slots)
        assertFalse(slots.contains(MapChromeSlot.BOUNDARY_NAME))
        assertTrue(boundaryNameClearsToolRow(slots))
    }

    @Test
    fun framedFenceKeepsClearOfTheToolRow() {
        assertTrue(BoundaryFocusPaddingPx >= 160)
    }

    @Test
    fun nameBandUnderTheMapDoesNotClearTheToolRow() {
        assertFalse(
            boundaryNameClearsToolRow(
                listOf(MapChromeSlot.MAP, MapChromeSlot.BOUNDARY_NAME),
            ),
        )
    }
}
