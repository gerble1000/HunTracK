package uk.hunttrack.domain.track

import uk.hunttrack.domain.geo.BoundaryMath
import uk.hunttrack.domain.model.TrackPoint

/**
 * Points the hunter has actually walked. A fix closer than [MIN_STEP_METERS]
 * is ignored so a stationary phone does not pile up dots. The returned list
 * is the previous trail plus the new point: nothing already accepted is dropped
 * except the oldest once [MAX_POINTS] is reached.
 */
object WalkTrail {
    const val MIN_STEP_METERS = 8.0
    const val MAX_POINTS = 400

    fun append(
        points: List<TrackPoint>,
        latitude: Double,
        longitude: Double,
        elevationMeters: Double?,
        speedMps: Float?,
        nowEpochMs: Long,
    ): List<TrackPoint> {
        val last = points.lastOrNull()
        if (last != null &&
            BoundaryMath.haversineMeters(last.latitude, last.longitude, latitude, longitude) < MIN_STEP_METERS
        ) {
            return points
        }
        val next = TrackPoint(
            routeId = 0,
            latitude = latitude,
            longitude = longitude,
            elevationMeters = elevationMeters,
            speedMps = speedMps,
            recordedAtEpochMs = nowEpochMs,
        )
        return (points + next).takeLast(MAX_POINTS)
    }
}
