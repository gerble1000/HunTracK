package uk.hunttrack.domain.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.hunttrack.domain.geo.OsGridRef
import uk.hunttrack.domain.model.EmergencyContact

class EmergencyTextTest {

    @Test
    fun usesTheFirstSavedNumberAndIgnoresBlankOnes() {
        val phone = EmergencyText.phoneNumber(
            listOf(
                EmergencyContact("Blank", "  ", "kin"),
                EmergencyContact("Sam", "+44 7700 900123", "partner"),
            ),
        )
        assertEquals("+447700900123", phone)
    }

    @Test
    fun noSavedNumber() {
        assertNull(EmergencyText.phoneNumber(emptyList()))
        assertNull(EmergencyText.phoneNumber(listOf(EmergencyContact("Sam", "call me", "kin"))))
    }

    @Test
    fun messageIncludesLiveGridAndCoordinates() {
        val lat = 52.4862
        val lon = -1.8904
        val grid = OsGridRef.latLngToOsGrid(lat, lon)
        val message = EmergencyText.message(lat, lon)
        assertTrue(message.startsWith("Help! I need medical aid. My location is\n"))
        assertTrue(message.contains("Grid reference: $grid"))
        assertTrue(message.contains("Coordinates: 52.48620, -1.89040"))
        assertTrue(message.contains("https://maps.google.com/?q=52.48620,-1.89040"))
    }

    @Test
    fun noGpsSaysLocationUnavailable() {
        assertEquals(
            "Help! I need medical aid. My location is\nGrid reference: unavailable\nCoordinates: unavailable",
            EmergencyText.message(null, null),
        )
    }

    @Test
    fun outsideTheOsGridStillSendsCoordinates() {
        val message = EmergencyText.message(40.7128, -74.0060)
        assertTrue(message.contains("Grid reference: unavailable"))
        assertTrue(message.contains("Coordinates: 40.71280, -74.00600"))
        assertTrue(message.contains("https://maps.google.com/?q=40.71280,-74.00600"))
    }
}
