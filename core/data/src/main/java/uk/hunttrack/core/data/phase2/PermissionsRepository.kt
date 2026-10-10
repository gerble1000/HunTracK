package uk.hunttrack.core.data.phase2

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import uk.hunttrack.core.data.db.BoundaryAreaEntity
import uk.hunttrack.core.data.db.BoundaryDao
import uk.hunttrack.core.data.db.BoundaryPointEntity
import uk.hunttrack.core.data.db.HuntingPermissionDao
import uk.hunttrack.core.data.db.HuntingPermissionEntity
import uk.hunttrack.domain.geo.BoundaryMath
import uk.hunttrack.domain.model.BoundaryArea
import uk.hunttrack.domain.model.BoundaryPoint
import uk.hunttrack.domain.model.BoundaryZoneType
import uk.hunttrack.domain.model.HuntingPermission
import uk.hunttrack.domain.model.PermissionDocType

@Singleton
class PermissionsRepository @Inject constructor(
    private val permissionDao: HuntingPermissionDao,
    private val boundaryDao: BoundaryDao,
) {
    fun observePermissions(): Flow<List<HuntingPermission>> =
        permissionDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeBoundaries(): Flow<List<BoundaryArea>> =
        combine(boundaryDao.observeAreas(), boundaryDao.observeAllPoints()) { areas, points ->
            areas.map { area ->
                val pts = points.filter { it.areaId == area.id }
                    .map { BoundaryPoint(it.latitude, it.longitude, it.orderIndex) }
                area.toDomain(pts)
            }
        }

    suspend fun savePermission(permission: HuntingPermission): Long =
        permissionDao.upsert(permission.toEntity())

    suspend fun linkPermissionToBoundary(permissionId: Long, boundaryAreaId: Long) {
        val existing = permissionDao.getById(permissionId) ?: return
        permissionDao.upsert(existing.copy(boundaryAreaId = boundaryAreaId))
    }

    suspend fun deletePermission(id: Long) = permissionDao.delete(id)

    suspend fun saveBoundary(area: BoundaryArea): Long {
        val areaId = boundaryDao.upsertArea(
            BoundaryAreaEntity(
                id = area.id,
                name = area.name,
                zoneType = area.zoneType.name,
                notes = area.notes,
                areaSqMeters = BoundaryMath.areaSqMeters(area.points),
            ),
        )
        val resolvedId = if (area.id == 0L) areaId else area.id
        boundaryDao.deletePoints(resolvedId)
        boundaryDao.insertPoints(
            area.points.map {
                BoundaryPointEntity(
                    areaId = resolvedId,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    orderIndex = it.orderIndex,
                )
            },
        )
        return resolvedId
    }

    suspend fun deleteBoundary(id: Long) {
        permissionDao.clearBoundaryLink(id)
        boundaryDao.deletePoints(id)
        boundaryDao.deleteArea(id)
    }
}

private fun HuntingPermissionEntity.toDomain() = HuntingPermission(
    id, label, farmName, permissionType, species, validFromEpochMs, validToEpochMs,
    ownerName, ownerContact, notes,
    runCatching { PermissionDocType.valueOf(docType) }.getOrDefault(PermissionDocType.NOTE),
    attachmentUri,
    boundaryAreaId,
)

private fun HuntingPermission.toEntity() = HuntingPermissionEntity(
    id, label, farmName, permissionType, species, validFromEpochMs, validToEpochMs,
    ownerName, ownerContact, notes, docType.name, attachmentUri, boundaryAreaId,
)

private fun BoundaryAreaEntity.toDomain(points: List<BoundaryPoint>) = BoundaryArea(
    id = id,
    name = name,
    zoneType = runCatching { BoundaryZoneType.valueOf(zoneType) }.getOrDefault(BoundaryZoneType.HUNTING),
    notes = notes,
    points = points,
    areaSqMeters = areaSqMeters,
)
