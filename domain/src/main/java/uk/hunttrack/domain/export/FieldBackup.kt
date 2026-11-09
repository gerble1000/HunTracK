package uk.hunttrack.domain.export

import kotlinx.serialization.Serializable

/**
 * Portable HunTracK field backup.
 *
 * Version 1 carries permissions, boundaries, markers and recorded tracks.
 * Medical profile fields are present only when the user explicitly includes them.
 * PIN hashes, biometric flags, and on-device file URIs are never written.
 */
object FieldBackupFormat {
    const val FORMAT = "hunttrack-field-backup"
    const val VERSION = 1
    const val JSON_NAME = "hunttrack-backup.json"
    const val GPX_NAME = "hunttrack-markers-tracks.gpx"
    const val KML_NAME = "hunttrack-boundaries.kml"
    const val GEOJSON_NAME = "hunttrack-field.geojson"
    const val ZIP_NAME = "hunttrack-field-backup.zip"

    const val OMISSION_NOTE =
        "Photos, PDFs and other files attached on the device are not included. " +
            "The medical profile is included only when that choice was turned on. " +
            "PIN and biometric unlock data are never exported."
}

@Serializable
data class FieldBackup(
    val format: String = FieldBackupFormat.FORMAT,
    val version: Int = FieldBackupFormat.VERSION,
    val exportedAtEpochMs: Long,
    val includesMedicalProfile: Boolean = false,
    val localFilesOmitted: Boolean = true,
    val note: String = FieldBackupFormat.OMISSION_NOTE,
    val permissions: List<BackupPermission> = emptyList(),
    val boundaries: List<BackupBoundary> = emptyList(),
    val markers: List<BackupMarker> = emptyList(),
    val routes: List<BackupRoute> = emptyList(),
    val medicalProfile: BackupMedicalProfile? = null,
)

@Serializable
data class BackupPermission(
    val id: Long = 0,
    val label: String = "",
    val farmName: String = "",
    val permissionType: String = "",
    val species: String = "",
    val validFromEpochMs: Long = 0,
    val validToEpochMs: Long = 0,
    val ownerName: String = "",
    val ownerContact: String = "",
    val notes: String = "",
    val docType: String = "NOTE",
    val boundaryAreaId: Long? = null,
    /** iOS draws a permission only when this is true. Android shows linked boundaries. */
    val showOnMap: Boolean = true,
    val attachmentOmitted: Boolean = false,
)

@Serializable
data class BackupBoundary(
    val id: Long = 0,
    val name: String = "",
    val zoneType: String = "HUNTING",
    val notes: String = "",
    val areaSqMeters: Double = 0.0,
    val points: List<BackupPoint> = emptyList(),
)

@Serializable
data class BackupPoint(
    val latitude: Double,
    val longitude: Double,
    val orderIndex: Int = 0,
    val elevationMeters: Double? = null,
)

@Serializable
data class BackupMarker(
    val id: Long = 0,
    val category: String = "OTHER_WILDLIFE",
    val name: String = "",
    val notes: String = "",
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val createdAtEpochMs: Long = 0,
    val speciesId: String? = null,
    val isPrivate: Boolean = true,
    val photoOmitted: Boolean = false,
)

@Serializable
data class BackupRoute(
    val id: Long = 0,
    val name: String = "",
    val startedAtEpochMs: Long = 0,
    val endedAtEpochMs: Long? = null,
    val distanceMeters: Double = 0.0,
    val points: List<BackupTrackPoint> = emptyList(),
)

@Serializable
data class BackupTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val speedMps: Float? = null,
    val recordedAtEpochMs: Long = 0,
)

/**
 * Medical fields the user chose to include. Unlock secrets are not part of this object.
 */
@Serializable
data class BackupMedicalProfile(
    val fullName: String = "",
    val bloodType: String = "",
    val allergies: String = "",
    val medicalInfo: String = "",
    val medication: String = "",
    val notes: String = "",
    val contacts: List<BackupEmergencyContact> = emptyList(),
)

@Serializable
data class BackupEmergencyContact(
    val name: String = "",
    val phone: String = "",
    val relationship: String = "",
)
