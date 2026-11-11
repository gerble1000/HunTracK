package uk.hunttrack.domain.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import uk.hunttrack.domain.model.BoundaryPoint

object BoundaryMath {
    fun areaSqMeters(points: List<BoundaryPoint>): Double {
        if (points.size < 3) return 0.0
        val ordered = points.sortedBy { it.orderIndex }
        // Equirectangular projection around polygon centroid for UK-scale areas
        val lat0 = ordered.map { it.latitude }.average()
        val lon0 = ordered.map { it.longitude }.average()
        val coords = ordered.map { p ->
            val x = Math.toRadians(p.longitude - lon0) * cos(Math.toRadians(lat0)) * 6371000.0
            val y = Math.toRadians(p.latitude - lat0) * 6371000.0
            x to y
        }
        var sum = 0.0
        for (i in coords.indices) {
            val (x1, y1) = coords[i]
            val (x2, y2) = coords[(i + 1) % coords.size]
            sum += x1 * y2 - x2 * y1
        }
        return kotlin.math.abs(sum) / 2.0
    }

    /** Path length along recorded points; closes the loop when there are 3+ points. */
    fun perimeterMeters(points: List<BoundaryPoint>, closeLoop: Boolean = points.size >= 3): Double {
        if (points.size < 2) return 0.0
        val ordered = points.sortedBy { it.orderIndex }
        var total = 0.0
        for (i in 0 until ordered.lastIndex) {
            val a = ordered[i]
            val b = ordered[i + 1]
            total += haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        if (closeLoop) {
            val first = ordered.first()
            val last = ordered.last()
            total += haversineMeters(last.latitude, last.longitude, first.latitude, first.longitude)
        }
        return total
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Ray-cast hit test. Zones are not clipped to their parent boundary, so a point
     * outside the boundary can still fall inside a zone.
     */
    fun contains(points: List<BoundaryPoint>, latitude: Double, longitude: Double): Boolean {
        val ordered = points.sortedBy { it.orderIndex }
        if (ordered.size < 3) return false
        var inside = false
        var previous = ordered.lastIndex
        for (index in ordered.indices) {
            val currentLat = ordered[index].latitude
            val currentLng = ordered[index].longitude
            val previousLat = ordered[previous].latitude
            val previousLng = ordered[previous].longitude
            val crosses = (currentLat > latitude) != (previousLat > latitude)
            if (crosses) {
                val span = previousLat - currentLat
                val edgeLng = if (span == 0.0) {
                    currentLng
                } else {
                    (previousLng - currentLng) * (latitude - currentLat) / span + currentLng
                }
                if (longitude < edgeLng) inside = !inside
            }
            previous = index
        }
        return inside
    }

    /** Simple centroid for map labels and camera framing. */
    fun centroid(points: List<BoundaryPoint>): Pair<Double, Double>? {
        if (points.isEmpty()) return null
        val ordered = points.sortedBy { it.orderIndex }
        return ordered.map { it.latitude }.average() to ordered.map { it.longitude }.average()
    }
}
