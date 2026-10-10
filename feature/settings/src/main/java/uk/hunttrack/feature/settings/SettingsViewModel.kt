package uk.hunttrack.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.hunttrack.core.data.prefs.PreferencesRepository
import uk.hunttrack.core.data.weather.WeatherRepository
import uk.hunttrack.core.designsystem.sound.HuntSound
import uk.hunttrack.core.designsystem.sound.SoundManager
import uk.hunttrack.domain.model.AppThemeMode
import uk.hunttrack.domain.model.MapLayerStyle
import uk.hunttrack.domain.model.UnitSystem
import uk.hunttrack.domain.model.UserPreferences
import uk.hunttrack.domain.model.WindUnit

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val weatherRepository: WeatherRepository,
    private val soundManager: SoundManager,
) : ViewModel() {

    val prefs: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect {
                soundManager.setSoundsEnabled(it.soundsEnabled)
                soundManager.setVibrationEnabled(it.vibrationEnabled)
            }
        }
    }

    fun setCelsius(value: Boolean) = viewModelScope.launch { preferencesRepository.setUseCelsius(value) }
    fun setWindUnit(unit: WindUnit) = viewModelScope.launch { preferencesRepository.setWindUnit(unit) }
    fun setUnitSystem(system: UnitSystem) = viewModelScope.launch { preferencesRepository.setUnitSystem(system) }
    fun setSounds(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setSoundsEnabled(enabled)
        soundManager.setSoundsEnabled(enabled)
    }
    fun setVibration(enabled: Boolean) = viewModelScope.launch {
        preferencesRepository.setVibrationEnabled(enabled)
        soundManager.setVibrationEnabled(enabled)
        if (enabled) {
            soundManager.play(HuntSound.CLICK)
        }
    }
    fun setMapLayer(style: MapLayerStyle) = viewModelScope.launch { preferencesRepository.setMapLayerStyle(style) }
    fun setShowOsGridRef(enabled: Boolean) =
        viewModelScope.launch { preferencesRepository.setShowOsGridRef(enabled) }
    fun setShowCullPinsOnMap(enabled: Boolean) =
        viewModelScope.launch { preferencesRepository.setShowCullPinsOnMap(enabled) }
    fun setMarkAnywhere(enabled: Boolean) =
        viewModelScope.launch { preferencesRepository.setMarkAnywhereEnabled(enabled) }
    fun setThemeMode(mode: AppThemeMode) = viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    fun clearWeatherCache() = viewModelScope.launch { weatherRepository.clearCache() }
    fun setMapTrainingEnabled(enabled: Boolean) =
        viewModelScope.launch { preferencesRepository.setMapTrainingEnabled(enabled) }
    fun setWindTrainingEnabled(enabled: Boolean) =
        viewModelScope.launch { preferencesRepository.setWindTrainingEnabled(enabled) }
}
