package uk.hunttrack.core.data.offline

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import uk.hunttrack.core.common.MapLibreStyleSupport
import uk.hunttrack.core.data.db.OfflineMapDao
import uk.hunttrack.core.data.db.OfflineMapRegionEntity
import uk.hunttrack.domain.model.OfflineMapRegion
import uk.hunttrack.domain.model.OfflineMapStatus

/**
 * Real offline map packs backed by MapLibre [OfflineManager].
 *
 * Each pack downloads the OpenFreeMap vector style (free, no API key, OSM data refreshed
 * weekly) within a bounding box, so Streets and Outdoor/Topo keep working with no network.
 * The local registry (Room) stores the pack name/bounds; the `path` column carries the
 * OfflineManager region id so deletes and status updates can find the matching region.
 */
@Singleton
class OfflineMapRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: OfflineMapDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        // Re-attach progress observers and resume any download interrupted by a restart.
        scope.launch { refreshFromOfflineManager() }
    }

    fun observeRegions(): Flow<List<OfflineMapRegion>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    /**
     * Creates and starts downloading a square offline pack around [centerLat]/[centerLng]
     * covering [sizeSqMiles] square miles (default 15 sq mi ≈ 6.2 km per side).
     */
    suspend fun createRegionAround(
        centerLat: Double,
        centerLng: Double,
        sizeSqMiles: Double = 15.0,
    ): Long = withContext(Dispatchers.Main.immediate) {
        val sideKm = sqrt(sizeSqMiles * SqMilesToSqKm)
        val halfDegLat = (sideKm / 2.0) / 111.0
        val halfDegLng = (sideKm / 2.0) / (111.0 * cos(centerLat * PI / 180.0))
        val north = centerLat + halfDegLat
        val south = centerLat - halfDegLat
        val east = centerLng + halfDegLng
        val west = centerLng - halfDegLng

        val name = "Offline area ${dao.count() + 1}"
        val definition = OfflineTilePyramidRegionDefinition(
            styleURL = MapLibreStyleSupport.OPEN_FREEMAP_LIBERTY_URL,
            bounds = LatLngBounds.from(north, east, south, west),
            minZoom = MinZoom.toDouble(),
            maxZoom = MaxZoom.toDouble(),
            pixelRatio = context.resources.displayMetrics.density,
        )
        val metadata = JSONObject().put("name", name).toString().toByteArray(Charsets.UTF_8)

        val region = suspendCancellableCoroutine<OfflineRegion> { cont ->
            OfflineManager.getInstance(context).createOfflineRegion(
                definition,
                metadata,
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        cont.resumeWith(Result.success(offlineRegion))
                    }

                    override fun onError(error: String) {
                        cont.resumeWith(Result.failure(IllegalStateException(error)))
                    }
                },
            )
        }

        val localId = dao.upsert(
            OfflineMapRegionEntity(
                name = name,
                north = north,
                south = south,
                east = east,
                west = west,
                sizeBytes = 0L,
                status = OfflineMapStatus.DOWNLOADING.name,
                path = region.id.toString(),
            ),
        )
        attachObserver(region, localId)
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        localId
    }

    suspend fun deleteRegion(id: Long) = withContext(Dispatchers.Main.immediate) {
        val entity = dao.getById(id) ?: return@withContext
        dao.delete(id)
        val offlineId = entity.path?.toLongOrNull() ?: return@withContext
        suspendCancellableCoroutine<Unit> { cont ->
            OfflineManager.getInstance(context).getOfflineRegion(
                offlineId,
                object : OfflineManager.GetOfflineRegionCallback {
                    override fun onRegion(offlineRegion: OfflineRegion) {
                        offlineRegion.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() {
                                cont.resumeWith(Result.success(Unit))
                            }

                            override fun onError(error: String) {
                                cont.resumeWith(Result.success(Unit))
                            }
                        })
                    }

                    override fun onRegionNotFound() {
                        cont.resumeWith(Result.success(Unit))
                    }

                    override fun onError(error: String) {
                        cont.resumeWith(Result.success(Unit))
                    }
                },
            )
        }
    }

    /** Re-attach observers to existing regions and resume any incomplete download. */
    private suspend fun refreshFromOfflineManager() {
        val regions = listOfflineRegions()
        if (regions.isEmpty()) return
        val entities = dao.getAll()
        regions.forEach { region ->
            val entity = entities.firstOrNull { it.path == region.id.toString() }
            val localId = entity?.id ?: -1L
            attachObserver(region, localId)
            val status = awaitStatus(region) ?: return@forEach
            if (entity != null) {
                dao.update(
                    entity.copy(
                        sizeBytes = status.completedResourceSize,
                        status = if (status.isComplete) {
                            OfflineMapStatus.READY.name
                        } else {
                            OfflineMapStatus.DOWNLOADING.name
                        },
                    ),
                )
            }
            if (!status.isComplete) {
                region.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }
        }
    }

    private fun attachObserver(region: OfflineRegion, localId: Long) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                if (localId <= 0) return
                scope.launch {
                    dao.getById(localId)?.let { entity ->
                        dao.update(
                            entity.copy(
                                sizeBytes = status.completedResourceSize,
                                status = if (status.isComplete) {
                                    OfflineMapStatus.READY.name
                                } else {
                                    OfflineMapStatus.DOWNLOADING.name
                                },
                            ),
                        )
                    }
                }
            }

            override fun onError(error: OfflineRegionError) {
                if (localId <= 0) return
                scope.launch {
                    dao.getById(localId)?.let { entity ->
                        dao.update(entity.copy(status = OfflineMapStatus.FAILED.name))
                    }
                }
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                if (localId <= 0) return
                scope.launch {
                    dao.getById(localId)?.let { entity ->
                        dao.update(entity.copy(status = OfflineMapStatus.FAILED.name))
                    }
                }
            }
        })
    }

    private suspend fun listOfflineRegions(): List<OfflineRegion> =
        suspendCancellableCoroutine { cont ->
            OfflineManager.getInstance(context).listOfflineRegions(
                object : OfflineManager.ListOfflineRegionsCallback {
                    override fun onList(offlineRegions: Array<OfflineRegion>?) {
                        cont.resumeWith(Result.success(offlineRegions?.toList().orEmpty()))
                    }

                    override fun onError(error: String) {
                        cont.resumeWith(Result.success(emptyList()))
                    }
                },
            )
        }

    private suspend fun awaitStatus(region: OfflineRegion): OfflineRegionStatus? =
        suspendCancellableCoroutine { cont ->
            region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                override fun onStatus(status: OfflineRegionStatus?) {
                    cont.resumeWith(Result.success(status))
                }

                override fun onError(error: String?) {
                    cont.resumeWith(Result.success(null))
                }
            })
        }

    private companion object {
        const val SqMilesToSqKm = 2.58998811
        const val MinZoom = 0
        const val MaxZoom = 14
    }
}

private fun OfflineMapRegionEntity.toDomain() = OfflineMapRegion(
    id = id,
    name = name,
    north = north,
    south = south,
    east = east,
    west = west,
    sizeBytes = sizeBytes,
    status = runCatching { OfflineMapStatus.valueOf(status) }.getOrDefault(OfflineMapStatus.PENDING),
    path = path,
)
