package uk.hunttrack.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "markers")
data class MarkerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val name: String,
    val notes: String,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
    val createdAtEpochMs: Long,
    val weatherSnapshotJson: String?,
    val photoUri: String?,
    val isPrivate: Boolean = true,
    val speciesId: String? = null,
)

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val distanceMeters: Double,
)

@Entity(tableName = "track_points")
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double?,
    val speedMps: Float?,
    val recordedAtEpochMs: Long,
)

@Entity(tableName = "offline_map_regions")
data class OfflineMapRegionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
    val sizeBytes: Long,
    val status: String,
    val path: String?,
)

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val gridKey: String,
    val latitude: Double,
    val longitude: Double,
    val fetchedAtEpochMs: Long,
    val payloadJson: String,
)

@Entity(tableName = "call_journal")
data class CallJournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callId: String?,
    val speciesId: String?,
    val notes: String,
    val response: String,
    val distanceEstimateMeters: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val weatherJson: String?,
    val recordedAtEpochMs: Long,
)

@Entity(tableName = "user_calls")
data class UserCallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val speciesId: String,
    val category: String,
    val description: String,
    val filePath: String,
    val createdAtEpochMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val weatherJson: String?,
    val notes: String,
)

@Entity(tableName = "audio_packs")
data class AudioPackEntity(
    @PrimaryKey val id: String,
    val downloaded: Boolean,
    val downloadedAtEpochMs: Long?,
)

@Entity(tableName = "hunting_permissions")
data class HuntingPermissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String = "",
    val farmName: String,
    val permissionType: String,
    val species: String,
    val validFromEpochMs: Long,
    val validToEpochMs: Long,
    val ownerName: String,
    val ownerContact: String,
    val notes: String,
    val docType: String,
    val attachmentUri: String?,
    val boundaryAreaId: Long? = null,
)

@Entity(tableName = "boundary_areas")
data class BoundaryAreaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val zoneType: String,
    val notes: String,
    val areaSqMeters: Double,
)

@Entity(tableName = "boundary_points")
data class BoundaryPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val areaId: Long,
    val latitude: Double,
    val longitude: Double,
    val orderIndex: Int,
)

@Entity(tableName = "calendar_day_entries",
    indices = [Index(value = ["dayStartEpochMs"], unique = true)],
)
data class CalendarDayEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayStartEpochMs: Long,
    val title: String,
    val notes: String,
    val speciesId: String?,
    val location: String,
    val updatedAtEpochMs: Long,
)

@Entity(tableName = "tide_stations_cache")
data class TideStationCacheEntity(
    @PrimaryKey val id: Int = 1,
    val payloadJson: String,
    val fetchedAtEpochMs: Long,
)

@Entity(tableName = "tide_events_cache")
data class TideEventsCacheEntity(
    @PrimaryKey val stationId: String,
    val payloadJson: String,
    val fetchedAtEpochMs: Long,
)

@Entity(tableName = "emergency_profile")
data class EmergencyProfileEntity(
    @PrimaryKey val id: Int = 1,
    val fullName: String,
    val bloodType: String,
    val allergies: String,
    val medicalInfo: String,
    val medication: String,
    val notes: String,
    val contactsJson: String,
    val pinHash: String?,
    val biometricEnabled: Boolean,
)

@Entity(tableName = "cull_records")
data class CullRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val culledAtEpochMs: Long,
    val speciesId: String?,
    val speciesLabel: String,
    val sex: String,
    val ageClass: String,
    val count: Int,
    val latitude: Double?,
    val longitude: Double?,
    val osGridRef: String,
    val locationName: String,
    val carcassTestStatus: String,
    val notes: String,
    val linkedMarkerId: Long?,
)
