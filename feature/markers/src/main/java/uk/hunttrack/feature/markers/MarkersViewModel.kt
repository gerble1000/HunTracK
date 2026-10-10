package uk.hunttrack.feature.markers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.hunttrack.core.data.marker.MarkerRepository
import uk.hunttrack.core.data.weather.WeatherRepository
import uk.hunttrack.core.designsystem.sound.HuntSound
import uk.hunttrack.core.designsystem.sound.SoundManager
import uk.hunttrack.core.location.LocationRepository
import uk.hunttrack.domain.model.FieldMarker
import uk.hunttrack.domain.model.MarkerCategory

@HiltViewModel
class MarkersViewModel @Inject constructor(
    private val markerRepository: MarkerRepository,
    private val locationRepository: LocationRepository,
    @Suppress("unused") private val weatherRepository: WeatherRepository,
    private val soundManager: SoundManager,
) : ViewModel() {

    val markers: StateFlow<List<FieldMarker>> = markerRepository.observeMarkers()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _placeError = MutableStateFlow<String?>(null)
    val placeError: StateFlow<String?> = _placeError.asStateFlow()

    fun clearPlaceError() {
        _placeError.value = null
    }

    fun addMarker(
        name: String,
        category: MarkerCategory,
        notes: String,
        latitude: Double? = null,
        longitude: Double? = null,
        speciesId: String? = null,
    ) {
        viewModelScope.launch {
            val fix = locationRepository.bestFixOrNull()
                ?: locationRepository.latestFix.value
            val lat = latitude ?: fix?.latitude
            val lng = longitude ?: fix?.longitude
            if (lat == null || lng == null) {
                _placeError.value = "Waiting for GPS — enable location and try again."
                return@launch
            }
            if (latitude == null && longitude == null && fix != null && fix.accuracyMeters >= 900f) {
                _placeError.value = "GPS not ready yet — wait for a lock, then save."
                return@launch
            }
            markerRepository.save(
                FieldMarker(
                    category = category,
                    name = name.ifBlank { category.label },
                    notes = notes,
                    latitude = lat,
                    longitude = lng,
                    elevationMeters = fix?.altitudeMeters,
                    createdAtEpochMs = System.currentTimeMillis(),
                    weatherSnapshotJson = null,
                    isPrivate = true,
                    speciesId = speciesId,
                ),
            )
            _placeError.value = null
            soundManager.play(HuntSound.MARKER_PLACE)
        }
    }

    /** Drop a deer waypoint at the current GPS fix (map FAB). */
    fun dropWaypointHere() {
        viewModelScope.launch {
            val fix = locationRepository.bestFixOrNull()
                ?: locationRepository.latestFix.value
            if (fix == null) {
                _placeError.value = "Waiting for GPS — enable location and try again."
                return@launch
            }
            saveWaypoint(fix.latitude, fix.longitude, fix.altitudeMeters)
        }
    }

    /** Long-press / Mark GPS: drop a deer-head waypoint immediately (no form). */
    fun dropWaypointAt(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            saveWaypoint(latitude, longitude, null)
        }
    }

    private suspend fun saveWaypoint(latitude: Double, longitude: Double, elevationMeters: Double?) {
        val all = markerRepository.observeMarkers().first()
        val nextIndex = all.count {
            it.category != MarkerCategory.HOMING &&
                (it.category == MarkerCategory.DEER || it.name.startsWith("Waypoint"))
        } + 1
        markerRepository.save(
            FieldMarker(
                category = MarkerCategory.DEER,
                name = "Waypoint $nextIndex",
                notes = "Map waypoint",
                latitude = latitude,
                longitude = longitude,
                elevationMeters = elevationMeters,
                createdAtEpochMs = System.currentTimeMillis(),
                isPrivate = true,
            ),
        )
        _placeError.value = null
        soundManager.play(HuntSound.MARKER_PLACE)
    }

    /** Drop another marker at current GPS, inheriting category from an existing one (no form). */
    fun placeNextFrom(template: FieldMarker) {
        viewModelScope.launch {
            val all = markerRepository.observeMarkers().first()
            val nextIndex = all.count { it.category == template.category } + 1
            addMarker(
                name = "${template.category.label} $nextIndex",
                category = template.category,
                notes = "",
            )
        }
    }

    /** Quick-drop using the most recently placed marker as the template. */
    fun placeNextFromLatest() {
        viewModelScope.launch {
            val all = markerRepository.observeMarkers().first()
            val latest = all.maxByOrNull { it.createdAtEpochMs }
            if (latest == null) {
                _placeError.value = "Place a first marker, then use + to drop the next."
                return@launch
            }
            val nextIndex = all.count { it.category == latest.category } + 1
            addMarker(
                name = "${latest.category.label} $nextIndex",
                category = latest.category,
                notes = "",
            )
        }
    }

    fun deleteMarker(id: Long) {
        viewModelScope.launch { markerRepository.delete(id) }
    }
}
