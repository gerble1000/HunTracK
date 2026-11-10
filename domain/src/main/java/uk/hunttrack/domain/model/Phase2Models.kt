package uk.hunttrack.domain.model

enum class UkNation { ENGLAND, WALES, SCOTLAND, NORTHERN_IRELAND }

enum class SpeciesGroup {
    DEER,
    OTHER_QUARRY,
    BIRD,
    WATERFOWL,
    CORVID,
    PEST,
}

data class Species(
    val id: String,
    val commonName: String,
    val scientificName: String,
    val group: SpeciesGroup,
    val summary: String,
    val habitat: String,
    val behaviour: String,
    val distribution: String,
    val maleTraits: String,
    val femaleTraits: String,
    val youngTraits: String,
    val tracks: String,
    val droppings: String,
    val seasonsNote: String,
)

data class LegalSeason(
    val speciesId: String,
    val nation: UkNation,
    val sexLabel: String,
    val openFromMonthDay: String,
    val openToMonthDay: String,
    val notes: String = "",
)

data class AnimalCall(
    val id: String,
    val name: String,
    val speciesId: String,
    val category: String,
    val description: String,
    val packId: String,
    val remoteUrl: String,
    val isBuiltIn: Boolean = true,
    val filePath: String? = null,
    val credit: String = "",
)

data class CallJournalEntry(
    val id: Long = 0,
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

data class AudioPackTrack(
    val callId: String,
    val fileName: String,
    val remoteUrl: String,
)

data class AudioPack(
    val id: String,
    val name: String,
    val description: String,
    val recordingCount: Int,
    val sizeBytes: Long,
    val tracks: List<AudioPackTrack> = emptyList(),
    val downloaded: Boolean = false,
    val downloadProgress: Float = 0f,
)

enum class PermissionDocType { PDF, PHOTO, NOTE }

data class HuntingPermission(
    val id: Long = 0,
    /** Short label to tell permissions apart — e.g. "Bob's farm", "The estate". */
    val label: String = "",
    val farmName: String,
    val permissionType: String,
    val species: String,
    val validFromEpochMs: Long,
    val validToEpochMs: Long,
    val ownerName: String,
    val ownerContact: String,
    val notes: String = "",
    val docType: PermissionDocType = PermissionDocType.NOTE,
    val attachmentUri: String? = null,
    /** Saved map boundary linked to this permission, if any. */
    val boundaryAreaId: Long? = null,
)

fun HuntingPermission.displayLabel(): String =
    label.ifBlank { farmName.ifBlank { "Permission" } }

/** Permission boundary focus requested from the wallet — shown on the map tab. */
data class PermissionMapFocusItem(
    val permissionId: Long,
    val label: String,
    val boundaryAreaId: Long,
)

enum class BoundaryZoneType { HUNTING, NO_GO, SAFE }

fun BoundaryZoneType.displayName(): String = when (this) {
    BoundaryZoneType.HUNTING -> "Hunting area"
    BoundaryZoneType.NO_GO -> "No-go zone"
    BoundaryZoneType.SAFE -> "Safe zone"
}

fun BoundaryZoneType.summary(): String = when (this) {
    BoundaryZoneType.HUNTING -> "Your permitted shoot, beat, or paddock — where you plan to hunt."
    BoundaryZoneType.NO_GO -> "Do not enter — neighbour land, roads, buildings, or other off-limits ground."
    BoundaryZoneType.SAFE -> "Emergency fallback — car park, farmyard, or rendezvous point if you need to pull out."
}

/** Map overlay line/fill colour for this zone type. */
fun BoundaryZoneType.mapColorHex(): String = when (this) {
    BoundaryZoneType.HUNTING -> "#C4A574"
    BoundaryZoneType.NO_GO -> "#E05A45"
    BoundaryZoneType.SAFE -> "#45B872"
}

fun BoundaryZoneType.mapFillOpacity(): Float = when (this) {
    BoundaryZoneType.NO_GO -> 0.2f
    BoundaryZoneType.SAFE -> 0.16f
    BoundaryZoneType.HUNTING -> 0.14f
}

/**
 * A zone drawn on top of one boundary. The boundary outline, area and permission
 * geometry stay as they are — a zone does not cut a hole and is not clipped to the edge.
 */
enum class BoundaryZoneKind { DANGER, SOLD }

fun BoundaryZoneKind.displayName(): String = when (this) {
    BoundaryZoneKind.DANGER -> "Danger / No-go"
    BoundaryZoneKind.SOLD -> "Sold / removed land"
}

fun BoundaryZoneKind.summary(): String = when (this) {
    BoundaryZoneKind.DANGER -> "Do not enter — a hazard or no-go patch inside this ground."
    BoundaryZoneKind.SOLD -> "This parcel has been sold or taken out of the permission."
}

/** Map fill colour. Danger is red; sold land is a distinct amber. */
fun BoundaryZoneKind.mapColorHex(): String = when (this) {
    BoundaryZoneKind.DANGER -> "#E23B3B"
    BoundaryZoneKind.SOLD -> "#C47B2B"
}

fun BoundaryZoneKind.mapFillOpacity(): Float = when (this) {
    BoundaryZoneKind.DANGER -> 0.34f
    BoundaryZoneKind.SOLD -> 0.30f
}

/** Hatch tile registered on the map style. */
fun BoundaryZoneKind.hatchPattern(): String = when (this) {
    BoundaryZoneKind.DANGER -> "zone-hatch-danger"
    BoundaryZoneKind.SOLD -> "zone-hatch-sold"
}

data class BoundaryZone(
    val id: Long = 0,
    val boundaryId: Long = 0,
    val name: String = "",
    val kind: BoundaryZoneKind,
    val notes: String = "",
    val points: List<BoundaryPoint> = emptyList(),
) {
    fun label(): String = name.ifBlank { kind.displayName() }
}

/** Draw session for a zone that belongs to an existing boundary. */
data class PendingZoneCapture(
    val boundaryId: Long,
    /** Set when redrawing an existing zone; null creates a new one. */
    val zoneId: Long? = null,
    val name: String = "",
    val kind: BoundaryZoneKind,
    val notes: String = "",
    val returnToPermissions: Boolean = true,
)

enum class BoundaryCaptureMode { WALK, DRAW }

/** Pending boundary started from Permissions — completed on the map. */
data class PendingBoundaryCreation(
    val mode: BoundaryCaptureMode,
    val name: String,
    val zoneType: BoundaryZoneType,
    val returnToPermissions: Boolean = true,
    /** When set, the new boundary is linked to this permission on save. */
    val permissionId: Long? = null,
)

data class BoundaryArea(
    val id: Long = 0,
    val name: String,
    val zoneType: BoundaryZoneType,
    val notes: String = "",
    val points: List<BoundaryPoint> = emptyList(),
    val areaSqMeters: Double = 0.0,
    /** Overlays on this boundary. They do not change [points] or [areaSqMeters]. */
    val zones: List<BoundaryZone> = emptyList(),
)

data class BoundaryPoint(
    val latitude: Double,
    val longitude: Double,
    val orderIndex: Int,
)

data class EmergencyContact(
    val name: String,
    val phone: String,
    val relationship: String = "",
)

data class EmergencyProfile(
    val fullName: String = "",
    val bloodType: String = "",
    val allergies: String = "",
    val medicalInfo: String = "",
    val medication: String = "",
    val notes: String = "",
    val contacts: List<EmergencyContact> = emptyList(),
    val pinHash: String? = null,
    val biometricEnabled: Boolean = false,
)

data class CalendarDayEntry(
    val id: Long = 0,
    val dayStartEpochMs: Long,
    val title: String = "",
    val notes: String = "",
    val speciesId: String? = null,
    val location: String = "",
    val updatedAtEpochMs: Long = 0,
) {
    val hasContent: Boolean
        get() = title.isNotBlank() || notes.isNotBlank() || location.isNotBlank() || !speciesId.isNullOrBlank()
}

data class TideStation(
    val id: String,
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
)

enum class TideEventType { HIGH, LOW, UNKNOWN }

data class TideEvent(
    val epochMs: Long,
    val type: TideEventType,
    val heightMeters: Double,
    val isApproximate: Boolean,
)

data class TideForecast(
    val station: TideStation,
    val events: List<TideEvent>,
    val fetchedAtEpochMs: Long,
    val source: TideDataSource,
)

enum class TideDataSource { CALCULATED, LIVE, CACHE, UNAVAILABLE }
