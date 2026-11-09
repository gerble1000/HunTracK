package uk.hunttrack.domain.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.hunttrack.domain.model.BoundaryArea
import uk.hunttrack.domain.model.BoundaryPoint
import uk.hunttrack.domain.model.BoundaryZoneType
import uk.hunttrack.domain.model.EmergencyContact
import uk.hunttrack.domain.model.EmergencyProfile
import uk.hunttrack.domain.model.FieldMarker
import uk.hunttrack.domain.model.HuntingPermission
import uk.hunttrack.domain.model.MarkerCategory
import uk.hunttrack.domain.model.PermissionDocType
import uk.hunttrack.domain.model.Route
import uk.hunttrack.domain.model.TrackPoint

class FieldBackupException(message: String) : IllegalArgumentException(message)

data class DecodedFieldBackup(
    val permissions: List<HuntingPermission>,
    val showOnMapByPermissionId: Map<Long, Boolean>,
    val boundaries: List<BoundaryArea>,
    val markers: List<FieldMarker>,
    val routes: List<Pair<Route, List<TrackPoint>>>,
    /** Set only when the file contains a medical profile and the user asked to apply it. */
    val medical: EmergencyProfile?,
)

object FieldBackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        explicitNulls = false
        isLenient = true
    }

    fun build(
        exportedAtEpochMs: Long,
        permissions: List<HuntingPermission>,
        boundaries: List<BoundaryArea>,
        markers: List<FieldMarker>,
        routes: List<Route>,
        trackPoints: List<TrackPoint>,
        medical: EmergencyProfile?,
        includeMedical: Boolean,
        showOnMapByPermissionId: Map<Long, Boolean> = emptyMap(),
    ): FieldBackup {
        val pointsByRoute = trackPoints.groupBy { it.routeId }
        return FieldBackup(
            exportedAtEpochMs = exportedAtEpochMs,
            includesMedicalProfile = includeMedical && medical != null,
            localFilesOmitted = true,
            permissions = permissions.map { permission ->
                BackupPermission(
                    id = permission.id,
                    label = permission.label,
                    farmName = permission.farmName,
                    permissionType = permission.permissionType,
                    species = permission.species,
                    validFromEpochMs = permission.validFromEpochMs,
                    validToEpochMs = permission.validToEpochMs,
                    ownerName = permission.ownerName,
                    ownerContact = permission.ownerContact,
                    notes = permission.notes,
                    docType = permission.docType.name,
                    boundaryAreaId = permission.boundaryAreaId,
                    showOnMap = showOnMapByPermissionId[permission.id] ?: true,
                    attachmentOmitted = !permission.attachmentUri.isNullOrBlank(),
                )
            },
            boundaries = boundaries.map { area ->
                BackupBoundary(
                    id = area.id,
                    name = area.name,
                    zoneType = area.zoneType.name,
                    notes = area.notes,
                    areaSqMeters = area.areaSqMeters,
                    points = area.points.sortedBy { it.orderIndex }.map {
                        BackupPoint(it.latitude, it.longitude, it.orderIndex)
                    },
                )
            },
            markers = markers.map { marker ->
                BackupMarker(
                    id = marker.id,
                    category = marker.category.name,
                    name = marker.name,
                    notes = marker.notes,
                    latitude = marker.latitude,
                    longitude = marker.longitude,
                    elevationMeters = marker.elevationMeters,
                    createdAtEpochMs = marker.createdAtEpochMs,
                    speciesId = marker.speciesId,
                    isPrivate = marker.isPrivate,
                    photoOmitted = !marker.photoUri.isNullOrBlank(),
                )
            },
            routes = routes.map { route ->
                BackupRoute(
                    id = route.id,
                    name = route.name,
                    startedAtEpochMs = route.startedAtEpochMs,
                    endedAtEpochMs = route.endedAtEpochMs,
                    distanceMeters = route.distanceMeters,
                    points = pointsByRoute[route.id].orEmpty()
                        .sortedBy { it.recordedAtEpochMs }
                        .map { point ->
                            BackupTrackPoint(
                                latitude = point.latitude,
                                longitude = point.longitude,
                                elevationMeters = point.elevationMeters,
                                speedMps = point.speedMps,
                                recordedAtEpochMs = point.recordedAtEpochMs,
                            )
                        },
                )
            },
            medicalProfile = if (includeMedical && medical != null) medical.toBackup() else null,
        )
    }

    fun encode(backup: FieldBackup): String = json.encodeToString(backup)

    fun decode(text: String): FieldBackup {
        val backup = runCatching { json.decodeFromString<FieldBackup>(text) }
            .getOrElse { throw FieldBackupException("This file is not a HunTracK backup.") }
        if (backup.format != FieldBackupFormat.FORMAT) {
            throw FieldBackupException("Unrecognised backup format.")
        }
        if (backup.version != FieldBackupFormat.VERSION) {
            throw FieldBackupException("This backup version is not supported.")
        }
        return backup
    }

    /**
     * Turns a backup into domain records.
     * Medical data is returned only when [applyMedical] is true and the file included it.
     * PIN and biometric fields are never present to copy.
     */
    fun materialize(backup: FieldBackup, applyMedical: Boolean): DecodedFieldBackup {
        return DecodedFieldBackup(
            permissions = backup.permissions.map { it.toDomain() },
            showOnMapByPermissionId = backup.permissions.associate { it.id to it.showOnMap },
            boundaries = backup.boundaries.map { it.toDomain() },
            markers = backup.markers.map { it.toDomain() },
            routes = backup.routes.map { route ->
                Route(
                    id = route.id,
                    name = route.name,
                    startedAtEpochMs = route.startedAtEpochMs,
                    endedAtEpochMs = route.endedAtEpochMs,
                    distanceMeters = route.distanceMeters,
                ) to route.points.map { point ->
                    TrackPoint(
                        routeId = route.id,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        elevationMeters = point.elevationMeters,
                        speedMps = point.speedMps,
                        recordedAtEpochMs = point.recordedAtEpochMs,
                    )
                }
            },
            medical = if (applyMedical && backup.includesMedicalProfile) {
                backup.medicalProfile?.toDomain()
            } else {
                null
            },
        )
    }

    private fun EmergencyProfile.toBackup() = BackupMedicalProfile(
        fullName = fullName,
        bloodType = bloodType,
        allergies = allergies,
        medicalInfo = medicalInfo,
        medication = medication,
        notes = notes,
        contacts = contacts.map {
            BackupEmergencyContact(it.name, it.phone, it.relationship)
        },
    )

    private fun BackupPermission.toDomain() = HuntingPermission(
        id = id,
        label = label,
        farmName = farmName,
        permissionType = permissionType,
        species = species,
        validFromEpochMs = validFromEpochMs,
        validToEpochMs = validToEpochMs,
        ownerName = ownerName,
        ownerContact = ownerContact,
        notes = notes,
        docType = runCatching { PermissionDocType.valueOf(docType) }.getOrDefault(PermissionDocType.NOTE),
        attachmentUri = null,
        boundaryAreaId = boundaryAreaId,
    )

    private fun BackupBoundary.toDomain() = BoundaryArea(
        id = id,
        name = name,
        zoneType = runCatching { BoundaryZoneType.valueOf(zoneType) }.getOrDefault(BoundaryZoneType.HUNTING),
        notes = notes,
        points = points.sortedBy { it.orderIndex }.map {
            BoundaryPoint(it.latitude, it.longitude, it.orderIndex)
        },
        areaSqMeters = areaSqMeters,
    )

    private fun BackupMarker.toDomain() = FieldMarker(
        id = id,
        category = runCatching { MarkerCategory.valueOf(category) }.getOrDefault(MarkerCategory.OTHER_WILDLIFE),
        name = name,
        notes = notes,
        latitude = latitude,
        longitude = longitude,
        elevationMeters = elevationMeters,
        createdAtEpochMs = createdAtEpochMs,
        weatherSnapshotJson = null,
        photoUri = null,
        isPrivate = isPrivate,
        speciesId = speciesId,
    )

    private fun BackupMedicalProfile.toDomain() = EmergencyProfile(
        fullName = fullName,
        bloodType = bloodType,
        allergies = allergies,
        medicalInfo = medicalInfo,
        medication = medication,
        notes = notes,
        contacts = contacts.map { EmergencyContact(it.name, it.phone, it.relationship) },
        pinHash = null,
        biometricEnabled = false,
    )
}
