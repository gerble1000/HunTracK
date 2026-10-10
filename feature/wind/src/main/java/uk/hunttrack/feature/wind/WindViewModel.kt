package uk.hunttrack.feature.wind

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uk.hunttrack.core.data.marker.MarkerRepository
import uk.hunttrack.core.data.prefs.PreferencesRepository
import uk.hunttrack.core.data.weather.WeatherRepository
import uk.hunttrack.core.location.LocationRepository
import uk.hunttrack.domain.model.FieldMarker
import uk.hunttrack.domain.model.GpsFix
import uk.hunttrack.domain.model.UserPreferences
import uk.hunttrack.domain.model.WeatherSnapshot
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class ScentRisk { UNKNOWN, CLEAR, RISK }

enum class WindCharacter { SLOW_NARROW, STEADY, FAST, VARIABLE_WIDE }

data class WindUiState(
    val weather: WeatherSnapshot? = null,
    val prefs: UserPreferences = UserPreferences(),
    val prefsLoaded: Boolean = false,
    val markers: List<FieldMarker> = emptyList(),
    val selectedMarkerId: Long? = null,
    val selectedMarker: FieldMarker? = null,
    val hunterGps: GpsFix? = null,
    val scentRisk: ScentRisk = ScentRisk.UNKNOWN,
    val scentBearingDeg: Double? = null,
    val bearingToTargetDeg: Double? = null,
    val distanceToTargetM: Double? = null,
    val windCharacter: WindCharacter = WindCharacter.STEADY,
    val loading: Boolean = true,
)

@HiltViewModel
class WindViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository,
    markerRepository: MarkerRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val weatherState = MutableStateFlow<WeatherSnapshot?>(null)
    private val selectedId = MutableStateFlow<Long?>(null)
    private val loading = MutableStateFlow(true)

    private val gpsFlow = locationRepository.observeLocation()
        .map<GpsFix, GpsFix?> { it }
        .onStart { emit(locationRepository.lastKnown()) }
        .catch { emit(locationRepository.lastKnown()) }

    val uiState: StateFlow<WindUiState> = combine(
        weatherState,
        preferencesRepository.preferences,
        markerRepository.observeMarkers(),
        selectedId,
        combine(loading, gpsFlow) { isLoading, gps -> isLoading to gps },
    ) { weather, prefs, markers, selected, loadGps ->
        val (isLoading, gps) = loadGps
        val marker = markers.firstOrNull { it.id == selected }
        val hunter = gps
        val character = windCharacter(weather?.windSpeedKmh, weather?.windGustKmh)

        var scentRisk = ScentRisk.UNKNOWN
        var scentBearing: Double? = null
        var bearingToTarget: Double? = null
        var distanceM: Double? = null

        if (weather != null && marker != null && hunter != null) {
            scentBearing = (weather.windDirectionDeg + 180.0) % 360.0
            bearingToTarget = bearing(hunter.latitude, hunter.longitude, marker.latitude, marker.longitude)
            distanceM = haversine(hunter.latitude, hunter.longitude, marker.latitude, marker.longitude)
            val delta = angularDiff(scentBearing!!, bearingToTarget!!)
            scentRisk = if (delta < 40.0) ScentRisk.RISK else ScentRisk.CLEAR
        }

        WindUiState(
            weather = weather,
            prefs = prefs,
            prefsLoaded = true,
            markers = markers,
            selectedMarkerId = selected,
            selectedMarker = marker,
            hunterGps = hunter,
            scentRisk = scentRisk,
            scentBearingDeg = scentBearing,
            bearingToTargetDeg = bearingToTarget,
            distanceToTargetM = distanceM,
            windCharacter = character,
            loading = isLoading,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WindUiState())

    init {
        viewModelScope.launch {
            refresh(silent = false)
            while (isActive) {
                delay(WEATHER_POLL_MS)
                refresh(silent = true)
            }
        }
    }

    fun refresh() = refresh(silent = false)

    fun refresh(silent: Boolean) {
        viewModelScope.launch {
            if (!silent) loading.value = true
            val fix = locationRepository.bestFixOrNull()
                ?: locationRepository.lastKnown()
                ?: locationRepository.fallbackFix()
            weatherState.value = runCatching {
                weatherRepository.getWeather(fix.latitude, fix.longitude, forceRefresh = true)
            }.getOrElse { weatherRepository.getCachedWeather() }
            if (!silent) loading.value = false
        }
    }

    fun selectMarker(id: Long?) {
        selectedId.value = id
        viewModelScope.launch {
            preferencesRepository.setScentPlanMarkerId(id)
        }
    }

    private fun windCharacter(speed: Double?, gust: Double?): WindCharacter {
        val s = speed ?: return WindCharacter.STEADY
        val g = gust ?: s
        return when {
            g - s >= 8.0 -> WindCharacter.VARIABLE_WIDE
            s < 3.2 -> WindCharacter.SLOW_NARROW // <2 mph light / pooling
            s >= 24.1 -> WindCharacter.FAST // >15 mph stiff / wind burn
            s in 8.0..16.1 -> WindCharacter.STEADY // 5–10 mph
            else -> WindCharacter.STEADY
        }
    }

    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
            sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun angularDiff(a: Double, b: Double): Double {
        val d = kotlin.math.abs(a - b) % 360.0
        return if (d > 180) 360 - d else d
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    companion object {
        private const val WEATHER_POLL_MS = 5 * 60_000L
    }
}
