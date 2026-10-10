package uk.hunttrack.feature.compass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import uk.hunttrack.core.location.CompassRepository
import uk.hunttrack.core.location.LocationRepository
import uk.hunttrack.domain.model.GpsFix

data class CompassUiState(
    val magneticHeading: Float = 0f,
    val trueHeading: Float = 0f,
    val gpsHeading: Float? = null,
    val accuracy: Int = 0,
    val gps: GpsFix? = null,
    val needsCalibration: Boolean = false,
    val available: Boolean = true,
    val active: Boolean = false,
)

@HiltViewModel
class CompassViewModel @Inject constructor(
    compassRepository: CompassRepository,
    locationRepository: LocationRepository,
) : ViewModel() {

    // GPS is optional — must NOT block the compass needle (combine previously waited forever
    // when location had no permission / no fix yet).
    private val gpsFlow = locationRepository.observeLocation()
        .map<GpsFix, GpsFix?> { it }
        .onStart { emit(null) }
        .catch { emit(null) }

    val uiState: StateFlow<CompassUiState> = combine(
        compassRepository.observeCompass(),
        gpsFlow,
    ) { compass, gps ->
        val declination = -2f // approx UK
        val trueHeading = (compass.magneticHeadingDeg + declination + 360f) % 360f
        CompassUiState(
            magneticHeading = compass.magneticHeadingDeg,
            trueHeading = trueHeading,
            gpsHeading = gps?.bearingDegrees?.takeIf { (gps.speedMps ?: 0f) > 0.8f },
            accuracy = compass.accuracy,
            gps = gps,
            needsCalibration = compass.accuracy <= android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW,
            available = compass.available,
            active = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompassUiState())
}
