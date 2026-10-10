package uk.hunttrack.core.data.route

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.hunttrack.core.data.db.RouteDao
import uk.hunttrack.core.data.db.RouteEntity
import uk.hunttrack.core.data.db.TrackPointEntity
import uk.hunttrack.domain.model.Route
import uk.hunttrack.domain.model.TrackPoint

@Singleton
class RouteRepository @Inject constructor(
    private val dao: RouteDao,
) {
    fun observeRoutes(): Flow<List<Route>> = dao.observeRoutes().map { it.map { e -> e.toDomain() } }

    suspend fun startRoute(name: String): Long {
        return dao.insertRoute(
            RouteEntity(
                name = name,
                startedAtEpochMs = System.currentTimeMillis(),
                endedAtEpochMs = null,
                distanceMeters = 0.0,
            ),
        )
    }

    suspend fun addPoint(routeId: Long, point: TrackPoint): Long {
        return dao.insertTrackPoint(
            TrackPointEntity(
                routeId = routeId,
                latitude = point.latitude,
                longitude = point.longitude,
                elevationMeters = point.elevationMeters,
                speedMps = point.speedMps,
                recordedAtEpochMs = point.recordedAtEpochMs,
            ),
        )
    }

    suspend fun finishRoute(routeId: Long, distanceMeters: Double) {
        val existing = dao.getRoute(routeId) ?: return
        dao.updateRoute(
            existing.copy(
                endedAtEpochMs = System.currentTimeMillis(),
                distanceMeters = distanceMeters,
            ),
        )
    }

    fun observeTrackPoints(routeId: Long): Flow<List<TrackPoint>> =
        dao.observeTrackPoints(routeId).map { list -> list.map { it.toDomain() } }

    suspend fun getTrackPoints(routeId: Long): List<TrackPoint> =
        dao.getTrackPoints(routeId).map { it.toDomain() }
}

private fun RouteEntity.toDomain() = Route(id, name, startedAtEpochMs, endedAtEpochMs, distanceMeters)
private fun TrackPointEntity.toDomain() = TrackPoint(id, routeId, latitude, longitude, elevationMeters, speedMps, recordedAtEpochMs)
