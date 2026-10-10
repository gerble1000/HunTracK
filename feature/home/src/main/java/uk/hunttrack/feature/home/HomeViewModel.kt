package uk.hunttrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uk.hunttrack.core.data.prefs.PreferencesRepository
import uk.hunttrack.core.data.tides.TideRepository
import uk.hunttrack.core.data.weather.WeatherRepository
import uk.hunttrack.core.location.LocationRepository
import uk.hunttrack.domain.astronomy.AstronomyCalculator
import uk.hunttrack.domain.model.ActivityRating
import uk.hunttrack.domain.model.AstronomyInfo
import uk.hunttrack.domain.model.GpsFix
import uk.hunttrack.domain.model.TideForecast
import uk.hunttrack.domain.model.TideStation
import uk.hunttrack.domain.model.UserPreferences
import uk.hunttrack.domain.model.WeatherSnapshot
import uk.hunttrack.domain.rating.HuntingConditionsRating

data class HomeUiState(
    val weather: WeatherSnapshot? = null,
    val astronomy: AstronomyInfo? = null,
    val gps: GpsFix? = null,
    val activity: ActivityRating? = null,
    val tideForecast: TideForecast? = null,
    val prefs: UserPreferences = UserPreferences(),
    val loading: Boolean = true,
    val error: String? = null,
    val fromCache: Boolean = false,
    val nowEpochMs: Long = System.currentTimeMillis(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository,
    private val tideRepository: TideRepository,
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val weatherState = MutableStateFlow<WeatherSnapshot?>(null)
    private val gpsState = MutableStateFlow<GpsFix?>(null)
    private val tideForecastState = MutableStateFlow<TideForecast?>(null)
    private val loadingState = MutableStateFlow(true)
    private val errorState = MutableStateFlow<String?>(null)
    private val clockTick = MutableStateFlow(System.currentTimeMillis())

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            weatherState,
            gpsState,
            tideForecastState,
            preferencesRepository.preferences,
        ) { weather, gps, tideForecast, prefs ->
            TideCore(weather, gps, tideForecast, prefs)
        },
        combine(loadingState, errorState, clockTick) { loading, error, now ->
            Triple(loading, error, now)
        },
    ) { tideCore, (loading, error, now) ->
        val fix = tideCore.gps ?: locationRepository.fallbackFix()
        val astronomy = AstronomyCalculator.calculate(fix.latitude, fix.longitude, now)
        HomeUiState(
            weather = tideCore.weather,
            astronomy = astronomy,
            gps = tideCore.gps,
            activity = HuntingConditionsRating.rate(tideCore.weather, astronomy, now),
            tideForecast = tideCore.tideForecast,
            prefs = tideCore.prefs,
            loading = loading,
            error = error,
            nowEpochMs = now,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    private data class TideCore(
        val weather: WeatherSnapshot?,
        val gps: GpsFix?,
        val tideForecast: TideForecast?,
        val prefs: UserPreferences,
    )

    init {
        viewModelScope.launch {
            locationRepository.observeLocation().collect { gpsState.value = it }
        }
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                refreshTides(prefs, forceRefresh = false)
            }
        }
        // Recalculate hunting conditions / astronomy every minute
        viewModelScope.launch {
            while (isActive) {
                delay(CLOCK_TICK_MS)
                clockTick.value = System.currentTimeMillis()
            }
        }
        // Pull fresh weather periodically (temps, wind, activity inputs)
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
            if (!silent) loadingState.value = true
            errorState.value = null
            val fix = locationRepository.bestFixOrNull()
                ?: locationRepository.lastKnown()
                ?: gpsState.value
                ?: locationRepository.fallbackFix()
            gpsState.value = locationRepository.latestFix.value ?: gpsState.value ?: fix
            try {
                weatherState.value = weatherRepository.getWeather(fix.latitude, fix.longitude, forceRefresh = true)
                clockTick.value = System.currentTimeMillis()
            } catch (e: Exception) {
                weatherState.value = weatherRepository.getCachedWeather()
                errorState.value = e.message ?: "Weather unavailable"
            }
            refreshTides(uiState.value.prefs, forceRefresh = true)
            if (!silent) loadingState.value = false
        }
    }

    private suspend fun refreshTides(prefs: UserPreferences, forceRefresh: Boolean) {
        if (!prefs.tidesEnabled || prefs.tideStationId.isBlank()) {
            tideForecastState.value = null
            return
        }
        val station = TideStation(
            id = prefs.tideStationId,
            name = prefs.tideStationName.ifBlank { "Selected port" },
            country = "UK",
            latitude = prefs.tideStationLatitude ?: 54.0,
            longitude = prefs.tideStationLongitude ?: -2.0,
        )
        tideForecastState.value = tideRepository.getForecast(station, forceRefresh)
    }

    companion object {
        private const val CLOCK_TICK_MS = 60_000L
        private const val WEATHER_POLL_MS = 5 * 60_000L
    }
}
