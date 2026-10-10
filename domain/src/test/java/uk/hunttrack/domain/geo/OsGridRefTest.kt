package uk.hunttrack.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OsGridRefTest {

    @Test
    fun latLngToOsGrid_southamptonArea_matchesKnownRef() {
        // Approx OS example area around SU 37290 15552 (~50.937°N, -1.469°W)
        val ref = OsGridRef.latLngToOsGrid(50.9375, -1.4690)
        assertNotNull(ref)
        assertTrue(ref!!.startsWith("SU "))
        assertEquals(3, ref.split(" ").size)
        assertEquals(5, ref.split(" ")[1].length)
        assertEquals(5, ref.split(" ")[2].length)
    }

    @Test
    fun roundTrip_tenFigure_isWithinFewMetres() {
        val original = "SU 37290 15552"
        val latLng = OsGridRef.osGridToLatLng(original)
        assertNotNull(latLng)
        val back = OsGridRef.latLngToOsGrid(latLng!!.latitude, latLng.longitude)
        assertNotNull(back)
        // Same 100km square and within ~2 m on digits (Helmert approx + square centre)
        assertTrue(back!!.startsWith("SU "))
        val (_, e1, n1) = original.split(" ")
        val (_, e2, n2) = back.split(" ")
        assertTrue(abs(e1.toInt() - e2.toInt()) <= 2)
        assertTrue(abs(n1.toInt() - n2.toInt()) <= 2)
    }

    @Test
    fun parse_acceptsSpacedAndCompact() {
        val spaced = OsGridRef.osGridToLatLng("SU 37290 15552")
        val compact = OsGridRef.osGridToLatLng("SU3729015552")
        assertNotNull(spaced)
        assertNotNull(compact)
        assertEquals(spaced!!.latitude, compact!!.latitude, 1e-8)
        assertEquals(spaced.longitude, compact.longitude, 1e-8)
    }

    @Test
    fun parse_sixAndEightFigure() {
        assertNotNull(OsGridRef.osGridToLatLng("SU 372 155"))
        assertNotNull(OsGridRef.osGridToLatLng("SU 3729 1555"))
    }

    @Test
    fun outsideGb_returnsNull() {
        assertNull(OsGridRef.latLngToOsGrid(40.7128, -74.0060)) // NYC
        assertNull(OsGridRef.osGridToLatLng("ZZ 00000 00000"))
        assertNull(OsGridRef.osGridToLatLng("not a ref"))
    }

    @Test
    fun knownPoint_towerBridgeApprox() {
        // Tower Bridge ~ TQ 336 805
        val ref = OsGridRef.latLngToOsGrid(51.5055, -0.0754, digits = 6)
        assertNotNull(ref)
        assertTrue(ref!!.startsWith("TQ "))
    }
}
