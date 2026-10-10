package uk.hunttrack.feature.weather

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
import uk.hunttrack.core.data.weather.WeatherRepository
import uk.hunttrack.core.location.LocationRepository
import uk.hunttrack.domain.model.UserPreferences
import uk.hunttrack.domain.model.WeatherSnapshot
import uk.hunttrack.domain.model.WindUnit

data class WeatherUiState(
    val weather: WeatherSnapshot? = null,
    val prefs: UserPreferences = UserPreferences(),
    val loading: Boolean = true,
    val error: String? = null,
    val nowEpochMs: Long = System.currentTimeMillis(),
)

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val weatherState = MutableStateFlow<WeatherSnapshot?>(null)
    private val loadingState = MutableStateFlow(true)
    private val errorState = MutableStateFlow<String?>(null)
    private val clockTick = MutableStateFlow(System.currentTimeMillis())

    val uiState: StateFlow<WeatherUiState> = combine(
        weatherState,
        preferencesRepository.preferences,
        loadingState,
        errorState,
        clockTick,
    ) { weather, prefs, loading, error, now ->
        WeatherUiState(weather, prefs, loading, error, now)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeatherUiState())

    init {
        viewModelScope.launch {
            refresh(silent = false)
            while (isActive) {
                delay(WEATHER_POLL_MS)
                refresh(silent = true)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(CLOCK_TICK_MS)
                clockTick.value = System.currentTimeMillis()
            }
        }
    }

    fun refresh() = refresh(silent = false)

    fun refresh(silent: Boolean) {
        viewModelScope.launch {
            if (!silent) loadingState.value = true
            val fix = locationRepository.bestFixOrNull()
                ?: locationRepository.lastKnown()
                ?: locationRepository.fallbackFix()
            try {
                weatherState.value = weatherRepository.getWeather(fix.latitude, fix.longitude, forceRefresh = true)
                errorState.value = null
                clockTick.value = System.currentTimeMillis()
            } catch (e: Exception) {
                weatherState.value = weatherRepository.getCachedWeather()
                errorState.value = e.message
            } finally {
                if (!silent) loadingState.value = false
            }
        }
    }

    fun setWindUnit(unit: WindUnit) {
        viewModelScope.launch { preferencesRepository.setWindUnit(unit) }
    }

    companion object {
        private const val CLOCK_TICK_MS = 60_000L
        private const val WEATHER_POLL_MS = 5 * 60_000L
    }
}
