package uk.hunttrack.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.hunttrack.domain.model.BoundaryPoint

/**
 * A boundary the size Greg recorded (about 33,000 ha, ~67 km around) makes a
 * hand-drawn zone's edges kilometres long. MapLibre then keeps only part of the
 * fill. These cases lock the render-time densify that stops that.
 */
class ZoneOverlayGeometryTest {
    @Test
    fun largeZoneInsideAThirtyThousandHectareBoundaryKeepsEveryEdgeTileSized() {
        val boundary = estateRing(lat0 = 51.20, lon0 = -2.10, radiusMeters = 10_300.0, wobble = 0.08)
        val areaHa = BoundaryMath.areaSqMeters(boundary) / 10_000.0
        val perimeterKm = BoundaryMath.perimeterMeters(boundary) / 1_000.0
        assertTrue("area was $areaHa ha", areaHa in 30_000.0..36_000.0)
        assertTrue("perimeter was $perimeterKm km", perimeterKm in 64.0..75.0)

        // Four corners only, the way a zoomed-out finger stroke is stored:
        // edges of several kilometres, which MapLibre clips to a fragment.
        val danger = listOf(
            point(51.20, -2.10, 0),
            point(51.20, -2.04, 1),
            point(51.16, -2.04, 2),
            point(51.16, -2.10, 3),
        )
        assertTrue(longestEdgeMeters(danger) > 4_000.0)

        val rendered = ZoneOverlayGeometry.densify(danger)
        assertTrue(longestEdgeMeters(rendered) <= ZoneOverlayGeometry.MaxSegmentMeters)
        assertContainsVertices(rendered, danger)
        assertAreaUnchanged(danger, rendered)
        // The zone is not clipped to the estate; densify only inserts colinear points.
        assertTrue(BoundaryMath.contains(boundary, 51.18, -2.07))
    }

    @Test
    fun soldZoneThatCrossesTheBoundaryEdgeIsDensifiedNotClipped() {
        val boundary = estateRing(lat0 = 51.20, lon0 = -2.10, radiusMeters = 10_300.0, wobble = 0.0)
        // One vertex sits outside the estate. Zones are allowed to do that.
        val sold = listOf(
            point(51.28, -2.10, 0),
            point(51.32, -2.05, 1),
            point(51.28, -2.00, 2),
        )
        val outside = sold[1]
        assertTrue(!BoundaryMath.contains(boundary, outside.latitude, outside.longitude))
        assertTrue(longestEdgeMeters(sold) > 5_000.0)

        val rendered = ZoneOverlayGeometry.densify(sold)
        assertTrue(longestEdgeMeters(rendered) <= ZoneOverlayGeometry.MaxSegmentMeters)
        assertContainsVertices(rendered, sold)
        assertEquals(outside.latitude, rendered.first { it.latitude == outside.latitude && it.longitude == outside.longitude }.latitude, 0.0)
        assertAreaUnchanged(sold, rendered)
    }

    @Test
    fun shortEdgesOnASmallBoundaryAreNotSubdivided() {
        // 25 ha square. Edges are ~500 m, still longer than one high-zoom tile,
        // so they are split — but a 30 m edge, typical of a close-up stroke, is kept.
        val side = 500.0
        val small = square(51.5, -1.8, side)
        assertTrue(BoundaryMath.areaSqMeters(small) in 240_000.0..260_000.0)
        val rendered = ZoneOverlayGeometry.densify(small)
        assertTrue(rendered.size > small.size)
        assertTrue(longestEdgeMeters(rendered) <= ZoneOverlayGeometry.MaxSegmentMeters)

        val alreadyFine = listOf(
            point(51.5, -1.8, 0),
            point(51.5, -1.7996, 1),
            point(51.5002, -1.7996, 2),
        )
        assertTrue(longestEdgeMeters(alreadyFine) < ZoneOverlayGeometry.MaxSegmentMeters)
        assertEquals(alreadyFine, ZoneOverlayGeometry.densify(alreadyFine))
    }

    private fun assertContainsVertices(rendered: List<BoundaryPoint>, original: List<BoundaryPoint>) {
        for (vertex in original) {
            assertTrue(
                "missing $vertex",
                rendered.any { it.latitude == vertex.latitude && it.longitude == vertex.longitude },
            )
        }
    }

    private fun assertAreaUnchanged(original: List<BoundaryPoint>, rendered: List<BoundaryPoint>) {
        val before = BoundaryMath.areaSqMeters(original)
        val after = BoundaryMath.areaSqMeters(rendered)
        assertEquals(before, after, before * 0.001)
    }

    private fun longestEdgeMeters(points: List<BoundaryPoint>): Double {
        val ordered = points.sortedBy { it.orderIndex }
        var longest = 0.0
        for (index in ordered.indices) {
            val a = ordered[index]
            val b = ordered[(index + 1) % ordered.size]
            val edge = BoundaryMath.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            if (edge > longest) longest = edge
        }
        return longest
    }

    private fun point(lat: Double, lon: Double, order: Int) = BoundaryPoint(lat, lon, order)

    private fun square(lat: Double, lon: Double, sideMeters: Double): List<BoundaryPoint> {
        val dLat = sideMeters / 111_320.0
        val dLon = sideMeters / (111_320.0 * kotlin.math.cos(Math.toRadians(lat)))
        return listOf(
            point(lat, lon, 0),
            point(lat, lon + dLon, 1),
            point(lat + dLat, lon + dLon, 2),
            point(lat + dLat, lon, 3),
        )
    }

    /** Closed-enough ring: a wobbled circle, not stored as a closed duplicate vertex. */
    private fun estateRing(
        lat0: Double,
        lon0: Double,
        radiusMeters: Double,
        wobble: Double,
    ): List<BoundaryPoint> {
        val count = 48
        return List(count) { index ->
            val angle = 2.0 * Math.PI * index / count
            val radius = radiusMeters * (1.0 + wobble * kotlin.math.sin(3.0 * angle))
            val dLat = radius * kotlin.math.cos(angle) / 111_320.0
            val dLon = radius * kotlin.math.sin(angle) / (111_320.0 * kotlin.math.cos(Math.toRadians(lat0)))
            point(lat0 + dLat, lon0 + dLon, index)
        }
    }
}
