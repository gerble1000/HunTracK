package uk.hunttrack.feature.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uk.hunttrack.core.data.marker.MarkerRepository
import uk.hunttrack.core.data.offline.OfflineMapRepository
import uk.hunttrack.core.data.phase2.BoundaryCreationSession
import uk.hunttrack.core.data.phase2.PermissionMapViewSession
import uk.hunttrack.core.data.phase2.PermissionsRepository
import uk.hunttrack.core.data.prefs.PreferencesRepository
import uk.hunttrack.core.data.route.RouteRepository
import uk.hunttrack.core.data.weather.WeatherRepository
import uk.hunttrack.core.designsystem.sound.HuntSound
import uk.hunttrack.core.designsystem.sound.SoundManager
import uk.hunttrack.core.location.CompassRepository
import uk.hunttrack.core.location.LocationRepository
import uk.hunttrack.domain.model.BoundaryArea
import uk.hunttrack.domain.model.BoundaryCaptureMode
import uk.hunttrack.domain.model.BoundaryPoint
import uk.hunttrack.domain.model.BoundaryZoneType
import uk.hunttrack.domain.model.PendingBoundaryCreation
import uk.hunttrack.domain.model.PermissionMapFocusItem
import uk.hunttrack.domain.model.FieldMarker
import uk.hunttrack.domain.model.GpsFix
import uk.hunttrack.domain.model.MapLayerStyle
import uk.hunttrack.domain.model.MarkerCategory
import uk.hunttrack.domain.model.OfflineMapRegion
import uk.hunttrack.domain.model.TrackPoint
import uk.hunttrack.domain.model.UserPreferences
import uk.hunttrack.domain.scent.ScentPlumeGeometry
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ScentApproachPlan(
    val targetName: String,
    val targetLat: Double,
    val targetLng: Double,
    /** Point ~220 m downwind of the target — start walk here. */
    val startLat: Double,
    val startLng: Double,
    /** Facing this bearing = wind on your front (into the wind). */
    val walkHeadingDeg: Double,
    val windFromDeg: Double,
)

enum class BoundaryRecordMode { OFF, WALKING, DRAWING }

/** Draw-fence prep (pan/zoom) vs active finger drawing on the map. */
enum class BoundaryDrawPhase { PREP, ACTIVE }

data class MapUiState(
    val markers: List<FieldMarker> = emptyList(),
    val gps: GpsFix? = null,
    val prefs: UserPreferences = UserPreferences(),
    /** False until the first real preferences emission (avoids guide flash-reset). */
    val prefsLoaded: Boolean = false,
    val offlineRegions: List<OfflineMapRegion> = emptyList(),
    val tracking: Boolean = false,
    val activeRouteId: Long? = null,
    val trackPoints: List<TrackPoint> = emptyList(),
    val trackDistanceMeters: Double = 0.0,
    val selectedMarker: FieldMarker? = null,
    val showOfflineManager: Boolean = false,
    /** Meteorological wind FROM direction (degrees). Smoke travels +180°. */
    val windFromDeg: Double? = null,
    val windSpeedKmh: Double? = null,
    val windGustKmh: Double? = null,
    val humidityPercent: Int? = null,
    val temperatureC: Double? = null,
    /** Active downed-prey / retrieve pin (at most one). */
    val homingTarget: FieldMarker? = null,
    val homingDistanceMeters: Double? = null,
    val homingBearingDeg: Double? = null,
    /** Relative turn: 0 = straight ahead, +right, −left (needs compass). */
    val homingRelativeDeg: Double? = null,
    val homingAtTarget: Boolean = false,
    /** Magnetic heading for map track-up orientation. */
    val compassHeadingDeg: Float? = null,
    /** Into-wind approach route after scent-check marker chosen. */
    val scentApproach: ScentApproachPlan? = null,
    val boundaries: List<BoundaryArea> = emptyList(),
    val boundaryMode: BoundaryRecordMode = BoundaryRecordMode.OFF,
    val boundaryDrawPhase: BoundaryDrawPhase = BoundaryDrawPhase.PREP,
    val pendingBoundaryCreation: PendingBoundaryCreation? = null,
    val draftBoundaryPoints: List<BoundaryPoint> = emptyList(),
    /** Wallet permissions highlighted on the map (from Permissions tab). */
    val permissionMapFocus: List<PermissionMapFocusItem> = emptyList(),
    /** Temporary pin from “Go to OS Grid Ref”. */
    val goToTarget: GoToOsGridTarget? = null,
    val goToError: String? = null,
)

data class GoToOsGridTarget(
    val osGridRef: String,
    val latitude: Double,
    val longitude: Double,
)

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val markerRepository: MarkerRepository,
    private val permissionsRepository: PermissionsRepository,
    private val permissionMapViewSession: PermissionMapViewSession,
    private val boundaryCreationSession: BoundaryCreationSession,
    private val locationRepository: LocationRepository,
    private val offlineMapRepository: OfflineMapRepository,
    private val routeRepository: RouteRepository,
    private val preferencesRepository: PreferencesRepository,
    private val weatherRepository: WeatherRepository,
    private val compassRepository: CompassRepository,
    private val soundManager: SoundManager,
) : ViewModel() {

    private val gpsState = MutableStateFlow<GpsFix?>(null)
    private val headingState = MutableStateFlow<Float?>(null)
    private val windFromState = MutableStateFlow<Double?>(null)
    private val windSpeedState = MutableStateFlow<Double?>(null)
    private val windGustState = MutableStateFlow<Double?>(null)
    private val humidityState = MutableStateFlow<Int?>(null)
    private val temperatureState = MutableStateFlow<Double?>(null)
    private val trackingState = MutableStateFlow(false)
    private val routeIdState = MutableStateFlow<Long?>(null)
    private val trackPointsState = MutableStateFlow<List<TrackPoint>>(emptyList())
    private val selectedMarkerState = MutableStateFlow<FieldMarker?>(null)
    private val showOfflineState = MutableStateFlow(false)
    private val atTargetState = MutableStateFlow(false)
    private val activeHomingState = MutableStateFlow<FieldMarker?>(null)
    private val scentPlanIdState = MutableStateFlow<Long?>(null)
    private val boundariesState = MutableStateFlow<List<BoundaryArea>>(emptyList())
    private val boundaryModeState = MutableStateFlow(BoundaryRecordMode.OFF)
    private val boundaryDrawPhaseState = MutableStateFlow(BoundaryDrawPhase.PREP)
    private val draftBoundaryState = MutableStateFlow<List<BoundaryPoint>>(emptyList())
    private val pendingCreationState = MutableStateFlow<PendingBoundaryCreation?>(null)
    private val permissionFocusState = MutableStateFlow<List<PermissionMapFocusItem>>(emptyList())
    private val goToTargetState = MutableStateFlow<GoToOsGridTarget?>(null)
    private val goToErrorState = MutableStateFlow<String?>(null)
    private val _returnToPermissionsAfterBoundary = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val returnToPermissionsAfterBoundary: SharedFlow<Unit> = _returnToPermissionsAfterBoundary.asSharedFlow()
    private var boundaryWalkJob: Job? = null
    private var alertSoundForId: Long? = null
    private var trackJob: Job? = null

    private data class TrackingBundle(
        val tracking: Boolean,
        val routeId: Long?,
        val points: List<TrackPoint>,
        val selected: FieldMarker?,
        val showOffline: Boolean,
    )

    val uiState: StateFlow<MapUiState> = combine(
        markerRepository.observeMarkers(),
        gpsState,
        preferencesRepository.preferences,
        offlineMapRepository.observeRegions(),
        combine(trackingState, routeIdState, trackPointsState, selectedMarkerState, showOfflineState) { tracking, routeId, points, selected, showOffline ->
            TrackingBundle(tracking, routeId, points, selected, showOffline)
        },
    ) { markers, gps, prefs, regions, tracking ->
        Triple(markers, gps, prefs) to (regions to tracking)
    }.combine(
        combine(
            combine(windFromState, windSpeedState, windGustState, humidityState, temperatureState) { from, speed, gust, humidity, temp ->
                listOf(from, speed, gust, humidity, temp)
            },
            combine(headingState, atTargetState, activeHomingState) { heading, atTarget, homing ->
                listOf(heading, atTarget, homing)
            },
        ) { wind, rest ->
            wind + rest
        },
    ) { left, extras ->
        left to extras
    }.combine(
        combine(
            combine(
                scentPlanIdState,
                boundariesState,
                boundaryModeState,
                boundaryDrawPhaseState,
                draftBoundaryState,
            ) { planId, boundaries, mode, drawPhase, draft ->
                listOf(planId, boundaries, mode, drawPhase, draft)
            },
            pendingCreationState,
        ) { boundaryCore, pending ->
            boundaryCore + pending
        },
    ) { leftExtras, boundaryExtras ->
        leftExtras to boundaryExtras
    }.combine(
        combine(permissionFocusState, goToTargetState, goToErrorState) { focus, goTo, goToError ->
            Triple(focus, goTo, goToError)
        },
    ) { pair, goToBundle ->
        val (permissionFocus, goToTarget, goToError) = goToBundle
        val (leftExtras, boundaryExtras) = pair
        val (left, extras) = leftExtras
        val (markersGpsPrefs, regionsTracking) = left
        val (markers, gps, prefs) = markersGpsPrefs
        val (regions, tracking) = regionsTracking
        val windFrom = extras[0] as Double?
        val windSpeed = extras[1] as Double?
        val windGust = extras[2] as Double?
        val humidity = extras[3] as Int?
        val temperature = extras[4] as Double?
        val heading = extras[5] as Float?
        val atTarget = extras[6] as Boolean
        val activeHoming = extras[7] as FieldMarker?
        val planId = boundaryExtras[0] as Long?
        val boundaries = boundaryExtras[1] as List<BoundaryArea>
        val boundaryMode = boundaryExtras[2] as BoundaryRecordMode
        val boundaryDrawPhase = boundaryExtras[3] as BoundaryDrawPhase
        val draftBoundary = boundaryExtras[4] as List<BoundaryPoint>
        val pendingBoundary = boundaryExtras[5] as PendingBoundaryCreation?
        val homing = activeHoming
            ?: markers.filter { it.category == MarkerCategory.HOMING }.maxByOrNull { it.createdAtEpochMs }
        val distance = if (gps != null && homing != null) {
            haversine(gps.latitude, gps.longitude, homing.latitude, homing.longitude)
        } else {
            null
        }
        val bearing = if (gps != null && homing != null) {
            bearingDeg(gps.latitude, gps.longitude, homing.latitude, homing.longitude)
        } else {
            null
        }
        val relative = if (bearing != null && heading != null) {
            var delta = bearing - heading
            while (delta > 180.0) delta -= 360.0
            while (delta < -180.0) delta += 360.0
            delta
        } else {
            null
        }
        val planTarget = planId?.let { id ->
            markers.firstOrNull { it.id == id && it.category != MarkerCategory.HOMING }
        }
        val scentApproach = if (planTarget != null && windFrom != null) {
            val downwind = (windFrom + 180.0) % 360.0
            val standoffM = ScentPlumeGeometry.approachStandoffM(
                windSpeed,
                windGust,
                humidity,
                temperature,
            )
            val (startLat, startLng) = offsetMeters(
                planTarget.latitude,
                planTarget.longitude,
                downwind,
                standoffM,
            )
            ScentApproachPlan(
                targetName = planTarget.name,
                targetLat = planTarget.latitude,
                targetLng = planTarget.longitude,
                startLat = startLat,
                startLng = startLng,
                walkHeadingDeg = windFrom,
                windFromDeg = windFrom,
            )
        } else {
            null
        }
        MapUiState(
            markers = markers
                .filterNot { it.category == MarkerCategory.HOMING }
                .filter { prefs.showCullPinsOnMap || it.category != MarkerCategory.CULL },
            gps = gps,
            prefs = prefs,
            prefsLoaded = true,
            offlineRegions = regions,
            tracking = tracking.tracking,
            activeRouteId = tracking.routeId,
            trackPoints = tracking.points,
            trackDistanceMeters = distanceOf(tracking.points),
            selectedMarker = tracking.selected,
            showOfflineManager = tracking.showOffline,
            windFromDeg = windFrom,
            windSpeedKmh = windSpeed,
            windGustKmh = windGust,
            humidityPercent = humidity,
            temperatureC = temperature,
            homingTarget = homing,
            homingDistanceMeters = distance,
            homingBearingDeg = bearing,
            homingRelativeDeg = relative,
            homingAtTarget = atTarget,
            compassHeadingDeg = heading,
            scentApproach = scentApproach,
            boundaries = boundaries,
            boundaryMode = boundaryMode,
            boundaryDrawPhase = boundaryDrawPhase,
            pendingBoundaryCreation = pendingBoundary,
            draftBoundaryPoints = draftBoundary,
            permissionMapFocus = permissionFocus,
            goToTarget = goToTarget,
            goToError = goToError,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MapUiState())

    init {
        viewModelScope.launch {
            preferencesRepository.scentPlanMarkerIdFlow.collect { scentPlanIdState.value = it }
        }
        viewModelScope.launch {
            markerRepository.observeMarkers().collect { list ->
                activeHomingState.value = list
                    .filter { it.category == MarkerCategory.HOMING }
                    .maxByOrNull { it.createdAtEpochMs }
            }
        }
        viewModelScope.launch {
            permissionsRepository.observeBoundaries().collect { boundariesState.value = it }
        }
        viewModelScope.launch {
            var locked = false
            locationRepository.observeLocation().collect { fix ->
                gpsState.value = fix
                if (!locked && fix.accuracyMeters < 25f) {
                    locked = true
                    soundManager.play(HuntSound.GPS_LOCK)
                }
                checkHomingArrival(fix, activeHomingState.value)
                appendBoundaryWalkPoint(fix)
            }
        }
        viewModelScope.launch {
            compassRepository.observeCompass().collect { reading ->
                if (reading.available) {
                    headingState.value = reading.magneticHeadingDeg
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                refreshWind()
                delay(5 * 60_000L)
            }
        }
    }

    private fun checkHomingArrival(fix: GpsFix, target: FieldMarker?) {
        if (target == null) {
            atTargetState.value = false
            alertSoundForId = null
            return
        }
        val dist = haversine(fix.latitude, fix.longitude, target.latitude, target.longitude)
        val accurateEnough = fix.accuracyMeters < 40f
        if (dist <= AlertRadiusMeters && accurateEnough) {
            if (alertSoundForId != target.id) {
                alertSoundForId = target.id
                soundManager.play(HuntSound.ARRIVAL)
            }
        } else if (dist > AlertResetMeters) {
            if (alertSoundForId == target.id) {
                alertSoundForId = null
            }
        }
        if (dist <= AtTargetRadiusMeters && accurateEnough) {
            atTargetState.value = true
        } else if (dist > AtTargetResetMeters) {
            atTargetState.value = false
        } else if (dist > AtTargetRadiusMeters) {
            atTargetState.value = false
        }
    }

    private suspend fun refreshWind() {
        val fix = locationRepository.bestFixOrNull()
            ?: locationRepository.lastKnown()
            ?: gpsState.value
            ?: locationRepository.fallbackFix()
        val weather = runCatching {
            weatherRepository.getWeather(fix.latitude, fix.longitude, forceRefresh = false)
        }.getOrNull() ?: weatherRepository.getCachedWeather()
        if (weather != null) {
            windFromState.value = weather.windDirectionDeg
            windSpeedState.value = weather.windSpeedKmh
            windGustState.value = weather.windGustKmh
            humidityState.value = weather.humidityPercent
            temperatureState.value = weather.temperatureC
        }
    }

    fun setMapLayer(style: MapLayerStyle) {
        viewModelScope.launch { preferencesRepository.setMapLayerStyle(style) }
    }

    fun setShowOsGridRef(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowOsGridRef(enabled) }
    }

    fun setShowCullPinsOnMap(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowCullPinsOnMap(enabled) }
    }

    fun goToOsGridRef(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            goToErrorState.value = "Enter an OS Grid Reference"
            return
        }
        val latLng = uk.hunttrack.domain.geo.OsGridRef.osGridToLatLng(trimmed)
        if (latLng == null) {
            goToErrorState.value = "Invalid OS Grid Reference"
            goToTargetState.value = null
            return
        }
        val display = uk.hunttrack.domain.geo.OsGridRef.latLngToOsGrid(latLng.latitude, latLng.longitude)
            ?: trimmed.uppercase()
        goToErrorState.value = null
        goToTargetState.value = GoToOsGridTarget(display, latLng.latitude, latLng.longitude)
        soundManager.play(HuntSound.CLICK)
    }

    fun clearGoToTarget() {
        goToTargetState.value = null
        goToErrorState.value = null
    }

    fun selectMarker(marker: FieldMarker?) {
        selectedMarkerState.value = marker
    }

    fun deleteSelectedMarker() {
        viewModelScope.launch {
            selectedMarkerState.value?.id?.let { markerRepository.delete(it) }
            selectedMarkerState.value = null
            soundManager.play(HuntSound.SUCCESS)
        }
    }

    /** Delete the most recently placed field marker (not the homing pin). */
    fun deleteLastMarker() {
        viewModelScope.launch {
            val last = uiState.value.markers.maxByOrNull { it.createdAtEpochMs }
                ?: markerRepository.observeMarkers().first()
                    .filter { it.category != MarkerCategory.HOMING }
                    .maxByOrNull { it.createdAtEpochMs }
            if (last != null) {
                markerRepository.delete(last.id)
                if (selectedMarkerState.value?.id == last.id) {
                    selectedMarkerState.value = null
                }
                soundManager.play(HuntSound.SUCCESS)
            }
        }
    }

    /** Delete all field markers. Homing pin is left alone — use End home for that. */
    fun clearMarkerTrail() {
        viewModelScope.launch {
            markerRepository.observeMarkers().first()
                .filter { it.category != MarkerCategory.HOMING }
                .forEach { markerRepository.delete(it.id) }
            selectedMarkerState.value = null
            soundManager.play(HuntSound.SUCCESS)
        }
    }

    /** Place / move the retrieve pin at current GPS. */
    fun placeHomingHere() {
        viewModelScope.launch {
            val fix = locationRepository.bestFixOrNull() ?: gpsState.value
            if (fix == null) return@launch
            placeHomingAt(fix.latitude, fix.longitude, fix.altitudeMeters)
        }
    }

    /** Place / move the retrieve pin at a tapped map coordinate. */
    fun placeHomingAt(latitude: Double, longitude: Double, elevationMeters: Double? = null) {
        viewModelScope.launch {
            alertSoundForId = null
            atTargetState.value = false
            markerRepository.setHoming(
                latitude = latitude,
                longitude = longitude,
                elevationMeters = elevationMeters,
                name = "Downed prey",
                notes = "Homing retrieve point",
            )
            soundManager.play(HuntSound.MARKER_PLACE)
        }
    }

    fun clearHoming() {
        viewModelScope.launch {
            markerRepository.clearHoming()
            atTargetState.value = false
            alertSoundForId = null
        }
    }

    fun toggleOfflineManager(show: Boolean) {
        showOfflineState.value = show
    }

    fun downloadArea() {
        viewModelScope.launch {
            val fix = gpsState.value ?: locationRepository.fallbackFix()
            val ok = runCatching {
                offlineMapRepository.createRegionAround(fix.latitude, fix.longitude)
            }.isSuccess
            if (ok) soundManager.play(HuntSound.SUCCESS)
        }
    }

    fun deleteOfflineRegion(id: Long) {
        viewModelScope.launch { offlineMapRepository.deleteRegion(id) }
    }

    fun toggleTracking() {
        if (trackingState.value) stopTracking() else startTracking()
    }

    private fun startTracking() {
        viewModelScope.launch {
            val id = routeRepository.startRoute("Track ${System.currentTimeMillis()}")
            routeIdState.value = id
            trackingState.value = true
            trackPointsState.value = emptyList()
            trackJob = launch {
                locationRepository.observeLocation().collect { fix ->
                    val point = TrackPoint(
                        routeId = id,
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        elevationMeters = fix.altitudeMeters,
                        speedMps = fix.speedMps,
                        recordedAtEpochMs = fix.timestampEpochMs,
                    )
                    routeRepository.addPoint(id, point)
                    trackPointsState.value = trackPointsState.value + point
                }
            }
        }
    }

    private fun stopTracking() {
        trackJob?.cancel()
        trackJob = null
        viewModelScope.launch {
            routeIdState.value?.let { routeRepository.finishRoute(it, distanceOf(trackPointsState.value)) }
            trackingState.value = false
        }
    }

    private fun distanceOf(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversine(
                points[i - 1].latitude,
                points[i - 1].longitude,
                points[i].latitude,
                points[i].longitude,
            )
        }
        return total
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun clearScentApproach() {
        viewModelScope.launch { preferencesRepository.setScentPlanMarkerId(null) }
    }

    fun activatePendingBoundaryCreation() {
        val pending = boundaryCreationSession.getPending() ?: return
        if (boundaryModeState.value != BoundaryRecordMode.OFF) return
        pendingCreationState.value = pending
        when (pending.mode) {
            BoundaryCaptureMode.WALK -> startWalkBoundary(fromPermissions = true)
            BoundaryCaptureMode.DRAW -> startDrawBoundary(fromPermissions = true)
        }
    }

    fun activatePermissionMapView() {
        permissionFocusState.value = permissionMapViewSession.getFocus().orEmpty()
    }

    fun clearPermissionMapFocus() {
        permissionFocusState.value = emptyList()
        permissionMapViewSession.clear()
    }

    fun startWalkBoundary(fromPermissions: Boolean = false) {
        if (!fromPermissions) {
            pendingCreationState.value = null
            boundaryCreationSession.clear()
        }
        boundaryModeState.value = BoundaryRecordMode.WALKING
        boundaryDrawPhaseState.value = BoundaryDrawPhase.PREP
        draftBoundaryState.value = emptyList()
        soundManager.play(HuntSound.CLICK)
    }

    fun startDrawBoundary(fromPermissions: Boolean = false) {
        if (!fromPermissions) {
            pendingCreationState.value = null
            boundaryCreationSession.clear()
        }
        boundaryModeState.value = BoundaryRecordMode.DRAWING
        boundaryDrawPhaseState.value = BoundaryDrawPhase.PREP
        draftBoundaryState.value = emptyList()
        soundManager.play(HuntSound.CLICK)
    }

    fun beginBoundaryDraw() {
        if (boundaryModeState.value != BoundaryRecordMode.DRAWING) return
        boundaryDrawPhaseState.value = BoundaryDrawPhase.ACTIVE
        soundManager.play(HuntSound.CLICK)
    }

    fun pauseBoundaryDraw() {
        if (boundaryModeState.value != BoundaryRecordMode.DRAWING) return
        boundaryDrawPhaseState.value = BoundaryDrawPhase.PREP
    }

    fun cancelBoundaryDraft() {
        boundaryModeState.value = BoundaryRecordMode.OFF
        boundaryDrawPhaseState.value = BoundaryDrawPhase.PREP
        draftBoundaryState.value = emptyList()
        pendingCreationState.value = null
        boundaryCreationSession.clear()
    }

    fun appendBoundaryDrawPoint(latitude: Double, longitude: Double) {
        if (boundaryModeState.value != BoundaryRecordMode.DRAWING) return
        if (boundaryDrawPhaseState.value != BoundaryDrawPhase.ACTIVE) return
        val current = draftBoundaryState.value
        val last = current.lastOrNull()
        if (last != null) {
            val dist = haversine(last.latitude, last.longitude, latitude, longitude)
            if (dist < 1.0) return
        }
        draftBoundaryState.value = current + BoundaryPoint(latitude, longitude, current.size)
    }

    fun finishBoundary(name: String = "Field boundary") {
        viewModelScope.launch {
            val points = draftBoundaryState.value
            if (points.size < 2) {
                cancelBoundaryDraft()
                return@launch
            }
            val pending = pendingCreationState.value
            val resolvedName = pending?.name?.takeIf { it.isNotBlank() }
                ?: name.ifBlank { "Field boundary" }
            val zoneType = pending?.zoneType ?: BoundaryZoneType.HUNTING
            val notes = when {
                pending?.permissionId != null && boundaryModeState.value == BoundaryRecordMode.WALKING ->
                    "Walked boundary linked to permission"
                pending?.permissionId != null ->
                    "Drawn boundary linked to permission"
                pending != null && boundaryModeState.value == BoundaryRecordMode.WALKING ->
                    "Walked boundary from permissions"
                pending != null ->
                    "Drawn boundary from permissions"
                boundaryModeState.value == BoundaryRecordMode.WALKING ->
                    "Walked boundary on map"
                else -> "Drawn boundary on map"
            }
            val boundaryId = permissionsRepository.saveBoundary(
                BoundaryArea(
                    name = resolvedName,
                    zoneType = zoneType,
                    notes = notes,
                    points = points,
                ),
            )
            pending?.permissionId?.let { permissionId ->
                permissionsRepository.linkPermissionToBoundary(permissionId, boundaryId)
            }
            val returnToPermissions = pending?.returnToPermissions == true
            boundaryModeState.value = BoundaryRecordMode.OFF
            boundaryDrawPhaseState.value = BoundaryDrawPhase.PREP
            draftBoundaryState.value = emptyList()
            pendingCreationState.value = null
            boundaryCreationSession.clear()
            soundManager.play(HuntSound.SUCCESS)
            if (returnToPermissions) {
                _returnToPermissionsAfterBoundary.tryEmit(Unit)
            }
        }
    }

    private fun appendBoundaryWalkPoint(fix: GpsFix) {
        if (boundaryModeState.value != BoundaryRecordMode.WALKING) return
        val current = draftBoundaryState.value
        val last = current.lastOrNull()
        if (last != null) {
            val dist = haversine(last.latitude, last.longitude, fix.latitude, fix.longitude)
            if (dist < 4.0) return
        }
        draftBoundaryState.value = current + BoundaryPoint(
            fix.latitude,
            fix.longitude,
            current.size,
        )
    }

    /** Destination point given start, bearing (deg), and distance (metres). */
    private fun offsetMeters(
        lat: Double,
        lon: Double,
        bearingDeg: Double,
        distanceM: Double,
    ): Pair<Double, Double> {
        val r = 6371000.0
        val brng = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val angDist = distanceM / r
        val lat2 = asin(sin(lat1) * cos(angDist) + cos(lat1) * sin(angDist) * cos(brng))
        val lon2 = lon1 + atan2(
            sin(brng) * sin(angDist) * cos(lat1),
            cos(angDist) - sin(lat1) * sin(lat2),
        )
        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }

    companion object {
        const val AlertRadiusMeters = 5.0
        const val AlertResetMeters = 8.0
        const val AtTargetRadiusMeters = 1.0
        const val AtTargetResetMeters = 2.0
    }
}
