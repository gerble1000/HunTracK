package uk.hunttrack.core.data.phase2

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import uk.hunttrack.core.data.db.BoundaryAreaEntity
import uk.hunttrack.core.data.db.BoundaryDao
import uk.hunttrack.core.data.db.BoundaryPointEntity
import uk.hunttrack.core.data.db.BoundaryZoneEntity
import uk.hunttrack.core.data.db.BoundaryZonePointEntity
import uk.hunttrack.core.data.db.HuntingPermissionDao
import uk.hunttrack.core.data.db.HuntingPermissionEntity
import uk.hunttrack.domain.geo.BoundaryMath
import uk.hunttrack.domain.geo.BoundaryFenceStyle
import uk.hunttrack.domain.model.BoundaryArea
import uk.hunttrack.domain.model.BoundaryPoint
import uk.hunttrack.domain.model.BoundaryZone
import uk.hunttrack.domain.model.BoundaryZoneKind
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
        combine(
            boundaryDao.observeAreas(),
            boundaryDao.observeAllPoints(),
            boundaryDao.observeZones(),
            boundaryDao.observeZonePoints(),
        ) { areas, points, zones, zonePoints ->
            areas.map { area ->
                val pts = points.filter { it.areaId == area.id }
                    .map { BoundaryPoint(it.latitude, it.longitude, it.orderIndex) }
                val areaZones = zones.filter { it.boundaryId == area.id }.map { zone ->
                    zone.toDomain(zonePoints.filter { it.zoneId == zone.id })
                }
                area.toDomain(pts, areaZones)
            }
        }

    suspend fun savePermission(permission: HuntingPermission): Long =
        permissionDao.upsert(permission.toEntity())

    suspend fun linkPermissionToBoundary(permissionId: Long, boundaryAreaId: Long) {
        val existing = permissionDao.getById(permissionId) ?: return
        permissionDao.upsert(existing.copy(boundaryAreaId = boundaryAreaId))
        val area = boundaryDao.getArea(boundaryAreaId) ?: return
        val label = existing.label.ifBlank { existing.farmName }
        val renamed = BoundaryFenceStyle.nameAfterLink(area.name, label)
        if (renamed != area.name) {
            boundaryDao.upsertArea(area.copy(name = renamed))
        }
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

    /**
     * Saves a zone on its parent boundary. The boundary outline and area are not touched,
     * and the zone polygon is stored as drawn (it may extend past the boundary edge).
     */
    suspend fun saveZone(zone: BoundaryZone): Long {
        val zoneId = boundaryDao.upsertZone(
            BoundaryZoneEntity(
                id = zone.id,
                boundaryId = zone.boundaryId,
                name = zone.name,
                kind = zone.kind.name,
                notes = zone.notes,
            ),
        )
        val resolvedId = if (zone.id == 0L) zoneId else zone.id
        boundaryDao.deleteZonePoints(resolvedId)
        boundaryDao.insertZonePoints(
            zone.points.map {
                BoundaryZonePointEntity(
                    zoneId = resolvedId,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    orderIndex = it.orderIndex,
                )
            },
        )
        return resolvedId
    }

    suspend fun deleteZone(id: Long) {
        boundaryDao.deleteZonePoints(id)
        boundaryDao.deleteZone(id)
    }

    suspend fun deleteBoundary(id: Long) {
        permissionDao.clearBoundaryLink(id)
        val zones = boundaryDao.zonesFor(id)
        zones.forEach { zone -> boundaryDao.deleteZonePoints(zone.id) }
        boundaryDao.deleteZonesForBoundary(id)
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

private fun BoundaryAreaEntity.toDomain(
    points: List<BoundaryPoint>,
    zones: List<BoundaryZone> = emptyList(),
) = BoundaryArea(
    id = id,
    name = name,
    zoneType = runCatching { BoundaryZoneType.valueOf(zoneType) }.getOrDefault(BoundaryZoneType.HUNTING),
    notes = notes,
    points = points,
    areaSqMeters = areaSqMeters,
    zones = zones,
)

private fun BoundaryZoneEntity.toDomain(points: List<BoundaryZonePointEntity>) = BoundaryZone(
    id = id,
    boundaryId = boundaryId,
    name = name,
    kind = runCatching { BoundaryZoneKind.valueOf(kind) }.getOrDefault(BoundaryZoneKind.DANGER),
    notes = notes,
    points = points.map { BoundaryPoint(it.latitude, it.longitude, it.orderIndex) },
)
