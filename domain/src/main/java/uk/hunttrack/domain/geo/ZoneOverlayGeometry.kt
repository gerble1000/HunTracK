package uk.hunttrack.domain.geo

import kotlin.math.ceil
import uk.hunttrack.domain.model.BoundaryPoint

/**
 * Geometry prepared for the map overlay only. Stored zone points are not rewritten:
 * a zone keeps the vertices the hunter drew, including any that sit outside the boundary.
 */
object ZoneOverlayGeometry {
    /**
     * Longest straight edge sent to the map.
     *
     * MapLibre tiles GeoJSON and stores each tile's vertices as 16-bit integers
     * (extent 8192, default source max zoom 18). An edge of several kilometres
     * overflows that space once the camera is close enough for the tile to be
     * smaller than the edge, so the fill is drawn for only part of the polygon
     * or not at all. A 40 m segment stays inside one zoom-18 tile at UK latitudes
     * (about 90 m) and still overzooms cleanly when the camera goes closer.
     * Short edges, such as a zone inside a 25 ha boundary, are left as drawn.
     */
    const val MaxSegmentMeters = 40.0

    fun densify(
        points: List<BoundaryPoint>,
        maxSegmentMeters: Double = MaxSegmentMeters,
    ): List<BoundaryPoint> {
        if (points.size < 2 || maxSegmentMeters <= 0.0) return points
        val ordered = points.sortedBy { it.orderIndex }
        val out = ArrayList<BoundaryPoint>(ordered.size)
        for (index in ordered.indices) {
            val start = ordered[index]
            val end = ordered[(index + 1) % ordered.size]
            if (out.isEmpty() || !samePlace(out.last(), start)) {
                out.add(start)
            }
            val distance = BoundaryMath.haversineMeters(
                start.latitude,
                start.longitude,
                end.latitude,
                end.longitude,
            )
            if (distance > maxSegmentMeters) {
                val steps = ceil(distance / maxSegmentMeters).toInt().coerceAtLeast(2)
                for (step in 1 until steps) {
                    val t = step.toDouble() / steps
                    out.add(
                        BoundaryPoint(
                            latitude = start.latitude + (end.latitude - start.latitude) * t,
                            longitude = start.longitude + (end.longitude - start.longitude) * t,
                            orderIndex = 0,
                        ),
                    )
                }
            }
        }
        if (out.size == ordered.size) return ordered
        return out.mapIndexed { index, point -> point.copy(orderIndex = index) }
    }

    private fun samePlace(a: BoundaryPoint, b: BoundaryPoint): Boolean =
        a.latitude == b.latitude && a.longitude == b.longitude
}
