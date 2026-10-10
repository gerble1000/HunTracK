package uk.hunttrack.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.hunttrack.core.designsystem.components.ClickableGlassCard
import uk.hunttrack.core.designsystem.components.FieldGuideSheet
import uk.hunttrack.core.designsystem.components.FieldGuides
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.HuntButton
import uk.hunttrack.core.designsystem.components.RegisterLazyListScrollToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.model.AppThemeMode
import uk.hunttrack.domain.model.MapLayerStyle
import uk.hunttrack.domain.model.UnitSystem
import uk.hunttrack.domain.model.WindUnit
import uk.hunttrack.feature.tides.TideSettingsSection
import uk.hunttrack.feature.tides.TideStationMapDialog
import uk.hunttrack.feature.tides.TidesUiState
import uk.hunttrack.feature.tides.TidesViewModel

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    tidesViewModel: TidesViewModel = hiltViewModel(),
    onOpenAbout: () -> Unit = {},
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val tidesState by tidesViewModel.uiState.collectAsStateWithLifecycle()
    var showMapGuide by remember { mutableStateOf(false) }
    var showWindGuide by remember { mutableStateOf(false) }
    var showTideMap by remember { mutableStateOf(false) }
    var pendingStation by remember { mutableStateOf<uk.hunttrack.domain.model.TideStation?>(null) }

    SettingsScreen(
        prefs = prefs,
        tidesState = tidesState,
        onCelsius = viewModel::setCelsius,
        onWindUnit = viewModel::setWindUnit,
        onUnitSystem = viewModel::setUnitSystem,
        onSounds = viewModel::setSounds,
        onVibration = viewModel::setVibration,
        onMapLayer = viewModel::setMapLayer,
        onShowOsGridRef = viewModel::setShowOsGridRef,
        onShowCullPinsOnMap = viewModel::setShowCullPinsOnMap,
        onMarkAnywhere = viewModel::setMarkAnywhere,
        onTheme = viewModel::setThemeMode,
        onClearCache = viewModel::clearWeatherCache,
        onMapTraining = viewModel::setMapTrainingEnabled,
        onWindTraining = viewModel::setWindTrainingEnabled,
        onShowMapGuide = { showMapGuide = true },
        onShowWindGuide = { showWindGuide = true },
        onOpenAbout = onOpenAbout,
        onTidesEnabled = tidesViewModel::setTidesEnabled,
        onOpenTideMap = {
            tidesViewModel.loadStationsForMap()
            pendingStation = tidesState.stations.firstOrNull { it.id == tidesState.prefs.tideStationId }
            showTideMap = true
        },
        onTideNearest = tidesViewModel::useNearestStation,
        onTideRefresh = { tidesViewModel.refreshAll(forceRefresh = true) },
        onClearTideCache = tidesViewModel::clearTideCache,
    )
    FieldGuideSheet(
        visible = showMapGuide,
        title = "Map training",
        subtitle = "How Range, Home, markers, and wind smoke work.",
        sections = FieldGuides.map,
        onDismiss = { showMapGuide = false },
        confirmLabel = "Close",
    )
    FieldGuideSheet(
        visible = showWindGuide,
        title = "Wind & scent — training",
        subtitle = "Will animals smell you from downwind?",
        sections = FieldGuides.wind,
        onDismiss = { showWindGuide = false },
        confirmLabel = "Close",
    )
    TideStationMapDialog(
        visible = showTideMap,
        stations = tidesState.stations,
        selectedStationId = pendingStation?.id ?: tidesState.prefs.tideStationId,
        loading = tidesState.loadingStations,
        onPickStation = { pendingStation = it },
        onConfirm = {
            pendingStation?.let { tidesViewModel.selectStation(it) }
            showTideMap = false
        },
        onDismiss = { showTideMap = false },
    )
}

@Composable
fun SettingsScreen(
    prefs: uk.hunttrack.domain.model.UserPreferences,
    tidesState: TidesUiState = TidesUiState(),
    onCelsius: (Boolean) -> Unit,
    onWindUnit: (WindUnit) -> Unit,
    onUnitSystem: (UnitSystem) -> Unit = {},
    onSounds: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    onMapLayer: (MapLayerStyle) -> Unit,
    onShowOsGridRef: (Boolean) -> Unit = {},
    onShowCullPinsOnMap: (Boolean) -> Unit = {},
    onMarkAnywhere: (Boolean) -> Unit = {},
    onTheme: (AppThemeMode) -> Unit,
    onClearCache: () -> Unit,
    onMapTraining: (Boolean) -> Unit = {},
    onWindTraining: (Boolean) -> Unit = {},
    onShowMapGuide: () -> Unit = {},
    onShowWindGuide: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onTidesEnabled: (Boolean) -> Unit = {},
    onOpenTideMap: () -> Unit = {},
    onTideNearest: () -> Unit = {},
    onTideRefresh: () -> Unit = {},
    onClearTideCache: () -> Unit = {},
) {
    ScreenBackground(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        RegisterLazyListScrollToTop(listState)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.headlineLarge)
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Account")
                    Text("Local guest", style = MaterialTheme.typography.titleLarge)
                    Text("Cloud accounts coming soon", style = MaterialTheme.typography.bodySmall, color = HuntColors.Mist)
                }
            }
            item {
                SectionLabel("Display theme")
                Text(
                    "Tap a theme to apply",
                    style = MaterialTheme.typography.bodySmall,
                    color = HuntColors.Mist,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                AppThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        AppThemeMode.WOODLAND -> "Woodland"
                        AppThemeMode.NIGHT -> "Night"
                        AppThemeMode.HUNT -> "Active hunt (red)"
                    }
                    val desc = when (mode) {
                        AppThemeMode.WOODLAND -> "Forest green & bronze"
                        AppThemeMode.NIGHT -> "Stealth black & steel"
                        AppThemeMode.HUNT -> "Crimson night-vision"
                    }
                    ClickableGlassCard(
                        onClick = { onTheme(mode) },
                        selected = prefs.themeMode == mode,
                        hint = if (prefs.themeMode == mode) "Current theme" else "Tap to apply ›",
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.titleLarge, color = HuntColors.Cream)
                        Text(desc, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
                    }
                }
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Units")
                    Text(
                        "Global system — updates distance, wind, and temperature defaults",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Mist,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    ClickableGlassCard(
                        onClick = { onUnitSystem(UnitSystem.METRIC) },
                        selected = prefs.unitSystem == UnitSystem.METRIC,
                        hint = if (prefs.unitSystem == UnitSystem.METRIC) "Active" else "Tap to use ›",
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Text("Metric", style = MaterialTheme.typography.titleLarge, color = HuntColors.Cream)
                        Text("Metres · km/h · °C", style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
                    }
                    ClickableGlassCard(
                        onClick = { onUnitSystem(UnitSystem.IMPERIAL) },
                        selected = prefs.unitSystem == UnitSystem.IMPERIAL,
                        hint = if (prefs.unitSystem == UnitSystem.IMPERIAL) "Active" else "Tap to use ›",
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Text("Imperial", style = MaterialTheme.typography.titleLarge, color = HuntColors.Cream)
                        Text("Yards · mph · °F", style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
                    }
                    RowSwitch("Use Celsius", prefs.useCelsius, onCelsius)
                    RowSwitch("Wind in mph", prefs.windUnit == WindUnit.MPH) {
                        onWindUnit(if (it) WindUnit.MPH else WindUnit.KMH)
                    }
                }
            }
            item {
                SectionLabel("Map style")
                MapLayerStyle.entries.forEach { style ->
                    val (title, desc) = when (style) {
                        MapLayerStyle.STREET -> "Streets" to "Road map · OpenFreeMap (OSM, updated weekly)"
                        MapLayerStyle.OUTDOOR -> "Outdoor / Topo" to "OpenFreeMap + terrain shading"
                        MapLayerStyle.SATELLITE -> "Satellite" to "Aerial imagery · Esri"
                    }
                    ClickableGlassCard(
                        onClick = { onMapLayer(style) },
                        selected = prefs.mapLayerStyle == style,
                        hint = if (prefs.mapLayerStyle == style) "Current layer" else "Tap to select ›",
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = HuntColors.Cream)
                        Text(desc, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
                    }
                }
                GlassPanel(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    SectionLabel("OS Grid & culls")
                    RowSwitch("Show OS Grid lines & refs", prefs.showOsGridRef, onShowOsGridRef)
                    RowSwitch("Show cull pins on map", prefs.showCullPinsOnMap, onShowCullPinsOnMap)
                }
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Markers")
                    RowSwitch("Mark anywhere on map", prefs.markAnywhereEnabled, onMarkAnywhere)
                    Text(
                        "Tap or long-press the map at any spot. On: opens the Mark location form so any marker type (deer, boundary, parking, permissions pins…) can be added there. Off: drops a quick deer waypoint instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Mist,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Sound")
                    RowSwitch("UI sounds", prefs.soundsEnabled, onSounds)
                    RowSwitch("Vibration", prefs.vibrationEnabled, onVibration)
                }
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Field training")
                    Text(
                        "Turn on to show training when you open Map or Wind & scent. Turn off to stop auto open. ? still opens help anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Mist,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    RowSwitch("Map training", prefs.mapTrainingEnabled, onMapTraining)
                    RowSwitch("Wind & scent training", prefs.windTrainingEnabled, onWindTraining)
                    HuntButton(
                        onClick = onShowMapGuide,
                        text = "Read map training",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp),
                    )
                    HuntButton(
                        onClick = onShowWindGuide,
                        text = "Read wind & scent training",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                TideSettingsSection(
                    state = tidesState,
                    onEnabled = onTidesEnabled,
                    onOpenMap = onOpenTideMap,
                    onNearest = onTideNearest,
                    onRefresh = onTideRefresh,
                    onClearCache = onClearTideCache,
                )
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Storage")
                    TextButton(onClick = onClearCache) { Text("Clear weather cache") }
                    Text("Offline map packs are managed from the Map screen.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                ClickableGlassCard(
                    onClick = onOpenAbout,
                    hint = "Open about ›",
                ) {
                    Text("About HunTracK", style = MaterialTheme.typography.titleLarge, color = HuntColors.Cream)
                    Text(
                        "App story, developer GER8LE, and helping hand Greg Willing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HuntColors.Mist,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
