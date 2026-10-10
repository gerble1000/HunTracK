package uk.hunttrack.core.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.hunttrack.domain.model.AppThemeMode
import uk.hunttrack.domain.model.MapLayerStyle
import uk.hunttrack.domain.model.UnitSystem
import uk.hunttrack.domain.model.UserPreferences
import uk.hunttrack.domain.model.WindUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("hunttrack_prefs")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val useCelsius = booleanPreferencesKey("use_celsius")
    private val windUnit = stringPreferencesKey("wind_unit")
    private val unitSystem = stringPreferencesKey("unit_system")
    private val soundsEnabled = booleanPreferencesKey("sounds_enabled")
    private val vibrationEnabled = booleanPreferencesKey("vibration_enabled")
    private val mapLayer = stringPreferencesKey("map_layer")
    private val themeMode = stringPreferencesKey("theme_mode")
    private val showOsGridRef = booleanPreferencesKey("show_os_grid_ref")
    private val showCullPinsOnMap = booleanPreferencesKey("show_cull_pins_on_map")
    private val markAnywhereEnabled = booleanPreferencesKey("mark_anywhere_enabled")
    private val mapTrainingEnabled = booleanPreferencesKey("map_training_enabled")
    private val windTrainingEnabled = booleanPreferencesKey("wind_training_enabled")
    private val scentPlanMarkerId = longPreferencesKey("scent_plan_marker_id")
    private val tidesEnabled = booleanPreferencesKey("tides_enabled")
    private val tideStationId = stringPreferencesKey("tide_station_id")
    private val tideStationName = stringPreferencesKey("tide_station_name")
    private val tideStationLatitude = stringPreferencesKey("tide_station_latitude")
    private val tideStationLongitude = stringPreferencesKey("tide_station_longitude")

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val system = prefs[unitSystem]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
            ?: UnitSystem.METRIC
        UserPreferences(
            useCelsius = prefs[useCelsius] ?: (system == UnitSystem.METRIC),
            windUnit = prefs[windUnit]?.let { runCatching { WindUnit.valueOf(it) }.getOrNull() }
                ?: if (system == UnitSystem.IMPERIAL) WindUnit.MPH else WindUnit.KMH,
            unitSystem = system,
            soundsEnabled = prefs[soundsEnabled] ?: true,
            vibrationEnabled = prefs[vibrationEnabled] ?: true,
            mapLayerStyle = prefs[mapLayer]?.let { runCatching { MapLayerStyle.valueOf(it) }.getOrNull() }
                ?: MapLayerStyle.OUTDOOR,
            themeMode = prefs[themeMode]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.WOODLAND,
            showOsGridRef = prefs[showOsGridRef] ?: true,
            showCullPinsOnMap = prefs[showCullPinsOnMap] ?: false,
            markAnywhereEnabled = prefs[markAnywhereEnabled] ?: true,
            mapTrainingEnabled = prefs[mapTrainingEnabled] ?: false,
            windTrainingEnabled = prefs[windTrainingEnabled] ?: false,
            tidesEnabled = prefs[tidesEnabled] ?: false,
            tideStationId = prefs[tideStationId].orEmpty(),
            tideStationName = prefs[tideStationName].orEmpty(),
            tideStationLatitude = prefs[tideStationLatitude]?.toDoubleOrNull(),
            tideStationLongitude = prefs[tideStationLongitude]?.toDoubleOrNull(),
        )
    }

    /** Marker chosen for scent approach planning (shown as into-wind route on Map). */
    val scentPlanMarkerIdFlow: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[scentPlanMarkerId]
    }

    suspend fun setScentPlanMarkerId(id: Long?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(scentPlanMarkerId) else prefs[scentPlanMarkerId] = id
        }
    }

    suspend fun setUseCelsius(value: Boolean) {
        context.dataStore.edit { it[useCelsius] = value }
    }

    suspend fun setWindUnit(value: WindUnit) {
        context.dataStore.edit { it[windUnit] = value.name }
    }

    /** Sets metric / imperial and aligns temperature + wind defaults. */
    suspend fun setUnitSystem(value: UnitSystem) {
        context.dataStore.edit {
            it[unitSystem] = value.name
            when (value) {
                UnitSystem.METRIC -> {
                    it[useCelsius] = true
                    it[windUnit] = WindUnit.KMH.name
                }
                UnitSystem.IMPERIAL -> {
                    it[useCelsius] = false
                    it[windUnit] = WindUnit.MPH.name
                }
            }
        }
    }

    suspend fun setSoundsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[soundsEnabled] = enabled }
    }

    suspend fun setVibrationEnabled(value: Boolean) {
        context.dataStore.edit { it[vibrationEnabled] = value }
    }

    suspend fun setMapLayerStyle(value: MapLayerStyle) {
        context.dataStore.edit { it[mapLayer] = value.name }
    }

    suspend fun setShowOsGridRef(value: Boolean) {
        context.dataStore.edit { it[showOsGridRef] = value }
    }

    suspend fun setShowCullPinsOnMap(value: Boolean) {
        context.dataStore.edit { it[showCullPinsOnMap] = value }
    }

    suspend fun setMarkAnywhereEnabled(value: Boolean) {
        context.dataStore.edit { it[markAnywhereEnabled] = value }
    }

    suspend fun setThemeMode(value: AppThemeMode) {
        context.dataStore.edit { it[themeMode] = value.name }
    }

    suspend fun setMapTrainingEnabled(value: Boolean) {
        context.dataStore.edit { it[mapTrainingEnabled] = value }
    }

    suspend fun setWindTrainingEnabled(value: Boolean) {
        context.dataStore.edit { it[windTrainingEnabled] = value }
    }

    suspend fun setTidesEnabled(value: Boolean) {
        context.dataStore.edit { it[tidesEnabled] = value }
    }

    suspend fun setTideStation(id: String, name: String, latitude: Double, longitude: Double) {
        context.dataStore.edit {
            it[tideStationId] = id
            it[tideStationName] = name
            it[tideStationLatitude] = latitude.toString()
            it[tideStationLongitude] = longitude.toString()
        }
    }

    suspend fun clearTideStation() {
        context.dataStore.edit {
            it.remove(tideStationId)
            it.remove(tideStationName)
            it.remove(tideStationLatitude)
            it.remove(tideStationLongitude)
        }
    }
}
