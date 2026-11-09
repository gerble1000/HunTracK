package uk.hunttrack.core.data.export

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import uk.hunttrack.core.data.marker.MarkerRepository
import uk.hunttrack.core.data.phase2.EmergencyRepository
import uk.hunttrack.core.data.phase2.PermissionsRepository
import uk.hunttrack.core.data.route.RouteRepository
import uk.hunttrack.domain.export.FieldBackup
import uk.hunttrack.domain.export.FieldBackupBundle
import uk.hunttrack.domain.export.FieldBackupCodec
import uk.hunttrack.domain.export.FieldBackupException

data class FieldCounts(
    val permissions: Int = 0,
    val boundaries: Int = 0,
    val markers: Int = 0,
    val routes: Int = 0,
)

@Singleton
class FieldBackupRepository @Inject constructor(
    private val permissionsRepository: PermissionsRepository,
    private val markerRepository: MarkerRepository,
    private val routeRepository: RouteRepository,
    private val emergencyRepository: EmergencyRepository,
) {
    fun observeCounts(): Flow<FieldCounts> = combine(
        permissionsRepository.observePermissions(),
        permissionsRepository.observeBoundaries(),
        markerRepository.observeMarkers(),
        routeRepository.observeRoutes(),
    ) { permissions, boundaries, markers, routes ->
        FieldCounts(permissions.size, boundaries.size, markers.size, routes.size)
    }

    suspend fun export(includeMedical: Boolean): FieldBackup {
        val permissions = permissionsRepository.observePermissions().first()
        val boundaries = permissionsRepository.observeBoundaries().first()
        val markers = markerRepository.observeMarkers().first()
        val routes = routeRepository.observeRoutes().first()
        val points = routes.flatMap { routeRepository.getTrackPoints(it.id) }
        val medical = if (includeMedical) emergencyRepository.getProfile() else null
        return FieldBackupCodec.build(
            exportedAtEpochMs = System.currentTimeMillis(),
            permissions = permissions,
            boundaries = boundaries,
            markers = markers,
            routes = routes,
            trackPoints = points,
            medical = medical,
            includeMedical = includeMedical,
        )
    }

    fun zip(backup: FieldBackup): ByteArray = FieldBackupBundle.zipBytes(backup)

    /**
     * Adds records from a JSON backup or a HunTracK zip.
     * Medical fields are applied only when [applyMedical] is true.
     * The existing PIN and biometric flag are kept.
     */
    suspend fun importBytes(bytes: ByteArray, applyMedical: Boolean): String {
        val text = bytes.toString(Charsets.UTF_8).trimStart()
        val backup = if (text.startsWith("{")) {
            FieldBackupCodec.decode(text)
        } else {
            runCatching { FieldBackupBundle.backupFromZip(bytes) }.getOrElse {
                throw FieldBackupException("Choose a HunTracK JSON backup or the zip from Share backup.")
            }
        }
        val decoded = FieldBackupCodec.materialize(backup, applyMedical)
        val idMap = linkedMapOf<Long, Long>()
        decoded.boundaries.forEach { area ->
            val newId = permissionsRepository.saveBoundary(area.copy(id = 0))
            if (area.id != 0L) idMap[area.id] = newId
        }
        decoded.permissions.forEach { permission ->
            val mapped = permission.boundaryAreaId?.let { old -> idMap[old] }
            permissionsRepository.savePermission(
                permission.copy(id = 0, boundaryAreaId = mapped, attachmentUri = null),
            )
        }
        decoded.markers.forEach { marker ->
            markerRepository.save(marker.copy(id = 0, photoUri = null, weatherSnapshotJson = null))
        }
        decoded.routes.forEach { (route, points) ->
            routeRepository.importRoute(route.copy(id = 0), points)
        }
        val importedMedical = decoded.medical
        val medicalNote = if (importedMedical != null) {
            val existing = emergencyRepository.getProfile()
            emergencyRepository.saveProfile(
                importedMedical.copy(
                    pinHash = existing.pinHash,
                    biometricEnabled = existing.biometricEnabled,
                ),
            )
            " Medical profile text was replaced. PIN and biometric unlock were kept."
        } else {
            " Medical profile was not changed."
        }
        return "Added ${decoded.permissions.size} permissions, ${decoded.boundaries.size} boundaries, " +
            "${decoded.markers.size} markers, ${decoded.routes.size} tracks.$medicalNote"
    }
}
