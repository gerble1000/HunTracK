package uk.hunttrack.domain.model

enum class MarkerCategoryGroup {
    WILDLIFE,
    HUNTING,
    NAVIGATION,
    TRACKING,
}

enum class MarkerCategory(
    val group: MarkerCategoryGroup,
    val label: String,
) {
    DEER(MarkerCategoryGroup.WILDLIFE, "Deer"),
    FOX(MarkerCategoryGroup.WILDLIFE, "Fox"),
    RABBIT(MarkerCategoryGroup.WILDLIFE, "Rabbit"),
    BIRD(MarkerCategoryGroup.WILDLIFE, "Bird"),
    BADGER(MarkerCategoryGroup.WILDLIFE, "Badger"),
    OTHER_WILDLIFE(MarkerCategoryGroup.WILDLIFE, "Other"),
    HIGH_SEAT(MarkerCategoryGroup.HUNTING, "High seat"),
    HIDE(MarkerCategoryGroup.HUNTING, "Hide"),
    /** Harvest / kill location for carcass revisit and cull log pins. */
    CULL(MarkerCategoryGroup.HUNTING, "Cull"),
    FEEDING_AREA(MarkerCategoryGroup.HUNTING, "Feeding area"),
    TRAIL_CAMERA(MarkerCategoryGroup.HUNTING, "Trail camera"),
    /** Electronic animal caller / Bluetooth speaker placement. */
    CALLER(MarkerCategoryGroup.HUNTING, "Caller"),
    WATER_SOURCE(MarkerCategoryGroup.HUNTING, "Water source"),
    SALT_LICK(MarkerCategoryGroup.HUNTING, "Salt lick"),
    TREE_STAND(MarkerCategoryGroup.HUNTING, "Tree stand"),
    PARKING(MarkerCategoryGroup.NAVIGATION, "Parking"),
    GATE(MarkerCategoryGroup.NAVIGATION, "Gate"),
    FENCE(MarkerCategoryGroup.NAVIGATION, "Fence"),
    BOUNDARY(MarkerCategoryGroup.NAVIGATION, "Boundary"),
    DANGER_AREA(MarkerCategoryGroup.NAVIGATION, "Danger area"),
    SHELTER(MarkerCategoryGroup.NAVIGATION, "Shelter"),
    /** Downed prey / retrieve point — guide walker back with arrival alert. */
    HOMING(MarkerCategoryGroup.NAVIGATION, "Homing"),
    FOOTPRINT(MarkerCategoryGroup.TRACKING, "Footprint"),
    BLOOD(MarkerCategoryGroup.TRACKING, "Blood"),
    HAIR(MarkerCategoryGroup.TRACKING, "Hair"),
    DROPPINGS(MarkerCategoryGroup.TRACKING, "Droppings"),
    BEDDING_AREA(MarkerCategoryGroup.TRACKING, "Bedding area"),
    LAST_SIGHTING(MarkerCategoryGroup.TRACKING, "Last sighting"),
}

data class FieldMarker(
    val id: Long = 0,
    val category: MarkerCategory,
    val name: String,
    val notes: String = "",
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val createdAtEpochMs: Long,
    val weatherSnapshotJson: String? = null,
    val photoUri: String? = null,
    val isPrivate: Boolean = true,
    /** When set, wildlife marker uses species-specific icon from [UkFieldCatalog]. */
    val speciesId: String? = null,
)

data class Route(
    val id: Long = 0,
    val name: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val distanceMeters: Double = 0.0,
)

data class TrackPoint(
    val id: Long = 0,
    val routeId: Long,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val speedMps: Float? = null,
    val recordedAtEpochMs: Long,
)

enum class OfflineMapStatus {
    PENDING,
    DOWNLOADING,
    READY,
    FAILED,
}

data class OfflineMapRegion(
    val id: Long = 0,
    val name: String,
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
    val sizeBytes: Long = 0,
    val status: OfflineMapStatus = OfflineMapStatus.PENDING,
    val path: String? = null,
)

data class WeatherSnapshot(
    val latitude: Double,
    val longitude: Double,
    val fetchedAtEpochMs: Long,
    val temperatureC: Double,
    val feelsLikeC: Double,
    val humidityPercent: Int,
    val pressureHpa: Double,
    val windSpeedKmh: Double,
    val windDirectionDeg: Double,
    val windGustKmh: Double?,
    val rainProbabilityPercent: Int?,
    val visibilityKm: Double?,
    val weatherCode: Int,
    val cloudCoverPercent: Int?,
    val hourly: List<HourlyForecast> = emptyList(),
    val daily: List<DailyForecast> = emptyList(),
)

data class HourlyForecast(
    val epochMs: Long,
    val temperatureC: Double,
    val windSpeedKmh: Double,
    val windDirectionDeg: Double,
    val rainProbabilityPercent: Int,
    val weatherCode: Int,
)

data class DailyForecast(
    val epochMs: Long,
    val tempMinC: Double,
    val tempMaxC: Double,
    val rainProbabilityPercent: Int,
    val weatherCode: Int,
    val windSpeedMaxKmh: Double,
    val frostRisk: Boolean,
    val fogRisk: Boolean,
    val stormRisk: Boolean,
)

data class AstronomyInfo(
    val sunriseEpochMs: Long,
    val sunsetEpochMs: Long,
    val moonriseEpochMs: Long?,
    val moonsetEpochMs: Long?,
    val moonPhase: Double,
    val moonIllumination: Double,
    val goldenHourStartEpochMs: Long,
    val goldenHourEndEpochMs: Long,
    val civilTwilightBeginEpochMs: Long,
    val civilTwilightEndEpochMs: Long,
)

data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double?,
    val speedMps: Float?,
    val bearingDegrees: Float?,
    val timestampEpochMs: Long,
)

enum class ActivityLevel { LOW, MODERATE, HIGH }

data class ActivityRating(
    val level: ActivityLevel,
    val title: String,
    val reasons: List<String>,
)

enum class MapLayerStyle {
    STREET,
    OUTDOOR,
    SATELLITE,
}

enum class WindUnit { KMH, MPH }

/** Global metric vs imperial for distances and linked defaults. */
enum class UnitSystem { METRIC, IMPERIAL }

enum class AppThemeMode {
    WOODLAND,
    NIGHT,
    HUNT,
}

data class UserPreferences(
    val useCelsius: Boolean = true,
    val windUnit: WindUnit = WindUnit.MPH,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val soundsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val mapLayerStyle: MapLayerStyle = MapLayerStyle.OUTDOOR,
    val themeMode: AppThemeMode = AppThemeMode.WOODLAND,
    /** Show Ordnance Survey Grid References on GPS, map, and markers (UK). */
    val showOsGridRef: Boolean = true,
    /** When on, cull markers appear on the map (table remains primary cull view). */
    val showCullPinsOnMap: Boolean = false,
    /** When on, long-pressing anywhere on the map lets the user add any marker type at that spot. */
    val markAnywhereEnabled: Boolean = true,
    /** When on, Map opens with training sheet (turn off in Settings). */
    val mapTrainingEnabled: Boolean = false,
    /** When on, Wind & scent opens with training sheet (turn off in Settings). */
    val windTrainingEnabled: Boolean = false,
    /** UK tide times — live from TideTimes.org.uk with daily cache / offline fallback. */
    val tidesEnabled: Boolean = false,
    val tideStationId: String = "",
    val tideStationName: String = "",
    val tideStationLatitude: Double? = null,
    val tideStationLongitude: Double? = null,
)
