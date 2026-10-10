package uk.hunttrack.feature.tides



import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import dagger.hilt.android.lifecycle.HiltViewModel

import javax.inject.Inject

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.SharingStarted

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.combine

import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.launch

import uk.hunttrack.core.data.prefs.PreferencesRepository

import uk.hunttrack.core.data.tides.TideRepository

import uk.hunttrack.core.location.LocationRepository

import uk.hunttrack.domain.model.TideDataSource

import uk.hunttrack.domain.model.TideForecast

import uk.hunttrack.domain.model.TideStation

import uk.hunttrack.domain.model.UserPreferences

import java.text.SimpleDateFormat

import java.util.Date

import java.util.Locale



data class TidesUiState(

    val prefs: UserPreferences = UserPreferences(),

    val stations: List<TideStation> = emptyList(),

    val forecast: TideForecast? = null,

    val loadingStations: Boolean = false,

    val loadingForecast: Boolean = false,

    val error: String? = null,

    val lastUpdatedLabel: String? = null,

)



@HiltViewModel

class TidesViewModel @Inject constructor(

    private val tideRepository: TideRepository,

    private val preferencesRepository: PreferencesRepository,

    private val locationRepository: LocationRepository,

) : ViewModel() {



    private val stations = MutableStateFlow<List<TideStation>>(emptyList())

    private val forecast = MutableStateFlow<TideForecast?>(null)

    private val loadingStations = MutableStateFlow(false)

    private val loadingForecast = MutableStateFlow(false)

    private val error = MutableStateFlow<String?>(null)



    val uiState: StateFlow<TidesUiState> = combine(

        combine(

            preferencesRepository.preferences,

            stations,

            forecast,

        ) { prefs, stationList, tideForecast ->

            Triple(prefs, stationList, tideForecast)

        },

        combine(loadingStations, loadingForecast, error) { loadingS, loadingF, err ->

            LoadingState(loadingS, loadingF, err)

        },

    ) { (prefs, stationList, tideForecast), loading ->

        TidesUiState(

            prefs = prefs,

            stations = stationList,

            forecast = tideForecast,

            loadingStations = loading.loadingStations,

            loadingForecast = loading.loadingForecast,

            error = loading.error,

            lastUpdatedLabel = tideForecast?.let { formatUpdated(it.fetchedAtEpochMs, it.source) },

        )

    }.stateIn(viewModelScope, SharingStarted.Eagerly, TidesUiState())



    private data class LoadingState(

        val loadingStations: Boolean,

        val loadingForecast: Boolean,

        val error: String?,

    )



    init {

        viewModelScope.launch {

            preferencesRepository.preferences.collect { prefs ->

                if (prefs.tidesEnabled) {

                    ensureStationsLoaded(forceRefresh = false)

                    if (prefs.tideStationId.isNotBlank()) {

                        refreshForecast(forceRefresh = false)

                    }

                }

            }

        }

    }



    fun setTidesEnabled(enabled: Boolean) {

        viewModelScope.launch {

            preferencesRepository.setTidesEnabled(enabled)

            if (enabled) {

                ensureStationsLoaded(forceRefresh = false)

                refreshForecast(forceRefresh = false)

            }

        }

    }



    fun selectStation(station: TideStation) {

        viewModelScope.launch {

            preferencesRepository.setTideStation(

                id = station.id,

                name = station.name,

                latitude = station.latitude,

                longitude = station.longitude,

            )

            refreshForecast(forceRefresh = true)

        }

    }



    fun useNearestStation() {

        viewModelScope.launch {

            ensureStationsLoaded(forceRefresh = false)

            val fix = locationRepository.bestFixOrNull() ?: locationRepository.lastKnown()

            if (fix == null) {

                error.value = "Turn on location to find the nearest tide station."

                return@launch

            }

            val nearest = tideRepository.nearestStation(stations.value, fix.latitude, fix.longitude)

            if (nearest == null) {

                error.value = "No tide stations loaded yet."

                return@launch

            }

            selectStation(nearest)

        }

    }



    fun refreshAll(forceRefresh: Boolean = true) {

        viewModelScope.launch {

            ensureStationsLoaded(forceRefresh = forceRefresh)

            refreshForecast(forceRefresh = forceRefresh)

        }

    }



    fun loadStationsForMap() {

        viewModelScope.launch {

            ensureStationsLoaded(forceRefresh = false)

        }

    }



    fun clearError() {

        error.value = null

    }



    fun clearTideCache() {

        viewModelScope.launch {

            tideRepository.clearCache()

            stations.value = emptyList()

            forecast.value = null

        }

    }



    private suspend fun ensureStationsLoaded(forceRefresh: Boolean) {

        loadingStations.value = true

        error.value = null

        tideRepository.getStations(forceRefresh)

            .onSuccess { stations.value = it }

            .onFailure { error.value = it.message ?: "Could not load UK tide stations." }

        loadingStations.value = false

    }



    private suspend fun refreshForecast(forceRefresh: Boolean) {

        val prefs = uiState.value.prefs

        if (prefs.tideStationId.isBlank()) {

            forecast.value = null

            return

        }

        val station = stations.value.firstOrNull { it.id == prefs.tideStationId }

            ?: TideStation(

                id = prefs.tideStationId,

                name = prefs.tideStationName.ifBlank { "Selected station" },

                country = "UK",

                latitude = prefs.tideStationLatitude ?: 54.0,

                longitude = prefs.tideStationLongitude ?: -2.0,

            )

        loadingForecast.value = true

        error.value = null

        forecast.value = tideRepository.getForecast(station, forceRefresh)

        loadingForecast.value = false

    }



    private fun formatUpdated(epochMs: Long, source: TideDataSource): String {

        val time = SimpleDateFormat("EEE d MMM HH:mm", Locale.UK).format(Date(epochMs))

        val suffix = when (source) {

            TideDataSource.CALCULATED -> "offline estimate"

            TideDataSource.LIVE -> "live"

            TideDataSource.CACHE -> "cached"

            TideDataSource.UNAVAILABLE -> "offline"

        }

        return "Updated $time ($suffix)"

    }

}


