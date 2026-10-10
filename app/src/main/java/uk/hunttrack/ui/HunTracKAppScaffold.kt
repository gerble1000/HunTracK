package uk.hunttrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import uk.hunttrack.core.data.offline.OfflineMapRepository
import uk.hunttrack.core.data.prefs.PreferencesRepository
import uk.hunttrack.core.designsystem.components.BackToTopButton
import uk.hunttrack.core.designsystem.components.LocalScrollMoreHintState
import uk.hunttrack.core.designsystem.components.LocalScrollToTopController
import uk.hunttrack.core.designsystem.components.MoreBelowHint
import uk.hunttrack.core.designsystem.components.rememberScrollMoreHintState
import uk.hunttrack.core.designsystem.components.rememberScrollToTopController
import uk.hunttrack.core.designsystem.sound.HuntSound
import uk.hunttrack.core.designsystem.sound.SoundManager
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.core.location.LocationRepository
import uk.hunttrack.domain.model.AppThemeMode
import uk.hunttrack.domain.model.UserPreferences
import uk.hunttrack.feature.calendar.CalendarRoute
import uk.hunttrack.feature.calls.CallsRoute
import uk.hunttrack.feature.compass.CompassRoute
import uk.hunttrack.feature.culls.CullRoute
import uk.hunttrack.feature.emergency.EmergencyRoute
import uk.hunttrack.feature.home.HomeRoute
import uk.hunttrack.feature.map.MapRoute
import uk.hunttrack.feature.markers.MarkersViewModel
import uk.hunttrack.feature.markers.QuickAddMarkerSheet
import uk.hunttrack.feature.permissions.PermissionsRoute
import uk.hunttrack.feature.settings.SettingsRoute
import uk.hunttrack.feature.species.SpeciesRoute
import uk.hunttrack.feature.weather.WeatherRoute
import uk.hunttrack.feature.wind.WindRoute
import uk.hunttrack.ui.about.AboutScreen
import uk.hunttrack.ui.more.MoreScreen

sealed class Dest(val route: String, val label: String, val icon: ImageVector? = null) {
    data object Home : Dest("home", "Home", Icons.Default.Home)
    data object Map : Dest("map", "Map", Icons.Default.Map)
    data object Weather : Dest("weather", "Weather", Icons.Default.Cloud)
    data object More : Dest("more", "More", Icons.Default.MoreHoriz)
    data object Compass : Dest("compass", "Compass", Icons.Default.Explore)
    data object Wind : Dest("wind", "Wind")
    data object Settings : Dest("settings", "Settings")
    data object About : Dest("about", "About")
    data object Species : Dest("species", "Species")
    data object Calls : Dest("calls", "Calls")
    data object Permissions : Dest("permissions", "Permissions")
    data object Emergency : Dest("emergency", "Emergency")
    data object Calendar : Dest("calendar", "Calendar")
    data object Culls : Dest("culls", "Cull log")
    data object ComingSoon : Dest("coming/{feature}", "Soon")
}

private val bottomTabs = listOf(Dest.Home, Dest.Map, Dest.Weather, Dest.More)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SoundEntryPoint {
    fun soundManager(): SoundManager
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocationEntryPoint {
    fun locationRepository(): LocationRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PrefsEntryPoint {
    fun preferencesRepository(): PreferencesRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OfflineEntryPoint {
    fun offlineMapRepository(): OfflineMapRepository
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HunTracKAppScaffold(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var showQuickAdd by remember { mutableStateOf(false) }
    var pendingPlaceLat by remember { mutableStateOf<Double?>(null) }
    var pendingPlaceLng by remember { mutableStateOf<Double?>(null) }
    val markersVm: MarkersViewModel = hiltViewModel()
    val placeError by markersVm.placeError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val soundManager = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, SoundEntryPoint::class.java).soundManager()
    }
    val locationRepository = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, LocationEntryPoint::class.java).locationRepository()
    }
    val preferencesRepository = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, PrefsEntryPoint::class.java).preferencesRepository()
    }
    val offlineMapRepository = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, OfflineEntryPoint::class.java).offlineMapRepository()
    }
    val prefs by preferencesRepository.preferences.collectAsStateWithLifecycle(initialValue = UserPreferences())
    val soundsOn = prefs.soundsEnabled
    val vibrationOn = prefs.vibrationEnabled
    val latestFix by locationRepository.latestFix.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollToTopController = rememberScrollToTopController()
    val scrollMoreHintState = rememberScrollMoreHintState()

    fun navigateHome() {
        soundManager.play(HuntSound.CLICK)
        // Prefer popping back to Home if it's already on the stack (Map, Cull log, etc.).
        if (!navController.popBackStack(Dest.Home.route, inclusive = false)) {
            // Home wasn't on the stack (tab saveState) — navigate and restore it.
            navController.navigate(Dest.Home.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = false
                }
                launchSingleTop = true
                restoreState = true
            }
        }
        // Forget More sub-pages so the next More tap shows the list, not compass/etc.
        navController.clearBackStack(Dest.More.route)
    }

    fun navigateToTab(dest: Dest) {
        soundManager.play(HuntSound.CLICK)
        if (dest == Dest.More) {
            // Clear any saved More sub-page (compass, species, etc.) so the tab
            // always returns to the More list after leaving via the HunTracK title.
            navController.clearBackStack(Dest.More.route)
            navController.navigate(Dest.More.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
            return
        }
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openQuickAddAt(lat: Double?, lng: Double?) {
        pendingPlaceLat = lat
        pendingPlaceLng = lng
        markersVm.clearPlaceError()
        showQuickAdd = true
    }

    LaunchedEffect(soundsOn) {
        soundManager.setSoundsEnabled(soundsOn)
    }

    LaunchedEffect(vibrationOn) {
        soundManager.setVibrationEnabled(vibrationOn)
    }

    LaunchedEffect(placeError) {
        val msg = placeError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        markersVm.clearPlaceError()
    }

    LaunchedEffect(Unit) {
        locationRepository.refreshPermission()
        locationRepository.observeLocation().collect { /* latestFix updated in repo */ }
    }

    Scaffold(
        modifier = modifier,
        containerColor = HuntColors.ForestDeep,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "HunTracK",
                        color = HuntColors.Cream,
                        modifier = Modifier.clickable(onClick = ::navigateHome),
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            val next = when (prefs.themeMode) {
                                AppThemeMode.WOODLAND -> AppThemeMode.NIGHT
                                AppThemeMode.NIGHT -> AppThemeMode.HUNT
                                AppThemeMode.HUNT -> AppThemeMode.WOODLAND
                            }
                            scope.launch { preferencesRepository.setThemeMode(next) }
                        },
                    ) {
                        Icon(
                            imageVector = when (prefs.themeMode) {
                                AppThemeMode.WOODLAND -> Icons.Default.Forest
                                AppThemeMode.NIGHT -> Icons.Default.DarkMode
                                AppThemeMode.HUNT -> Icons.Default.Whatshot
                            },
                            contentDescription = when (prefs.themeMode) {
                                AppThemeMode.WOODLAND -> "Theme: Woodland — tap for Night"
                                AppThemeMode.NIGHT -> "Theme: Night — tap for Hunt"
                                AppThemeMode.HUNT -> "Theme: Hunt — tap for Woodland"
                            },
                            tint = HuntColors.BronzeBright,
                        )
                    }
                    IconButton(
                        onClick = {
                            val next = !soundsOn
                            soundManager.setSoundsEnabled(next)
                            scope.launch { preferencesRepository.setSoundsEnabled(next) }
                        },
                    ) {
                        Icon(
                            imageVector = if (soundsOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = if (soundsOn) "Mute sounds" else "Unmute sounds",
                            tint = if (soundsOn) HuntColors.BronzeBright else HuntColors.Mist,
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = HuntColors.Charcoal,
                    titleContentColor = HuntColors.Cream,
                    actionIconContentColor = HuntColors.BronzeBright,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (currentRoute in bottomTabs.map { it.route } || currentRoute == Dest.Map.route) {
                FloatingActionButton(
                    onClick = {
                        soundManager.play(HuntSound.CLICK)
                        openQuickAddAt(null, null)
                    },
                    containerColor = HuntColors.Bronze,
                    contentColor = HuntColors.Charcoal,
                ) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = "Mark current location")
                }
            }
        },
        bottomBar = {
            if (currentRoute in bottomTabs.map { it.route }) {
                NavigationBar(containerColor = HuntColors.Charcoal) {
                    bottomTabs.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { navigateToTab(dest) },
                            icon = { Icon(dest.icon!!, contentDescription = dest.label) },
                            label = {
                                Text(
                                    text = dest.label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 11.sp,
                                        letterSpacing = 0.sp,
                                        lineHeight = 12.sp,
                                    ),
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = HuntColors.Bronze,
                                selectedTextColor = HuntColors.Bronze,
                                indicatorColor = HuntColors.ForestGreen,
                                unselectedIconColor = HuntColors.Mist,
                                unselectedTextColor = HuntColors.Mist,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        CompositionLocalProvider(
            LocalScrollToTopController provides scrollToTopController,
            LocalScrollMoreHintState provides scrollMoreHintState,
        ) {
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Dest.Home.route,
                    modifier = Modifier.fillMaxSize(),
                ) {
            composable(Dest.Home.route) {
                HomeRoute(
                    onOpenEmergency = {
                        navController.navigate(Dest.Emergency.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Dest.Map.route) {
                MapRoute(
                    onPlaceMarkerHere = { openQuickAddAt(null, null) },
                    onPlaceMarkerAt = { lat, lng -> markersVm.dropWaypointAt(lat, lng) },
                    onMarkAnywhereAt = { lat, lng -> openQuickAddAt(lat, lng) },
                    onQuickPlaceNext = { marker -> markersVm.placeNextFrom(marker) },
                    onQuickPlaceFromLatest = { openQuickAddAt(null, null) },
                    onBoundaryCompletedFromPermissions = {
                        navController.navigate(Dest.Permissions.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Dest.Weather.route) { WeatherRoute() }
            composable(Dest.More.route) {
                MoreScreen(
                    onOpenCompass = { navController.navigate(Dest.Compass.route) },
                    onOpenSettings = { navController.navigate(Dest.Settings.route) },
                    onOpenAbout = { navController.navigate(Dest.About.route) },
                    onOpenSpecies = { navController.navigate(Dest.Species.route) },
                    onOpenCalls = { navController.navigate(Dest.Calls.route) },
                    onOpenPermissions = { navController.navigate(Dest.Permissions.route) },
                    onOpenEmergency = { navController.navigate(Dest.Emergency.route) },
                    onOpenCalendar = { navController.navigate(Dest.Calendar.route) },
                    onOpenCulls = { navController.navigate(Dest.Culls.route) },
                    onOpenComingSoon = { feature ->
                        navController.navigate("coming/$feature")
                    },
                )
            }
            composable(Dest.Compass.route) { CompassRoute() }
            composable(Dest.Wind.route) {
                WindRoute(
                    onOpenMap = {
                        navController.navigate(Dest.Map.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Dest.Settings.route) {
                SettingsRoute(onOpenAbout = { navController.navigate(Dest.About.route) })
            }
            composable(Dest.About.route) {
                AboutScreen()
            }
            composable(Dest.Species.route) { SpeciesRoute() }
            composable(Dest.Calls.route) { CallsRoute() }
            composable(Dest.Permissions.route) {
                PermissionsRoute(
                    onOpenMapForBoundary = {
                        navController.navigate(Dest.Map.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Dest.Emergency.route) { EmergencyRoute() }
            composable(Dest.Calendar.route) { CalendarRoute() }
            composable(Dest.Culls.route) { CullRoute() }
            composable("coming/{feature}") { entry ->
                ComingSoonScreen(entry.arguments?.getString("feature") ?: "Feature")
            }
                }
                val showScrollHints = currentRoute != Dest.Map.route
                MoreBelowHint(
                    visible = showScrollHints && scrollMoreHintState.canScrollForward,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onClick = {
                        soundManager.play(HuntSound.CLICK)
                        scope.launch { scrollMoreHintState.nudge() }
                    },
                )
                AnimatedVisibility(
                    visible = showScrollHints &&
                        scrollMoreHintState.canScrollBackward &&
                        !scrollMoreHintState.canScrollForward,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp),
                ) {
                    BackToTopButton()
                }
            }
        }
    }

    QuickAddMarkerSheet(
        visible = showQuickAdd,
        onDismiss = {
            showQuickAdd = false
            pendingPlaceLat = null
            pendingPlaceLng = null
        },
        latitude = pendingPlaceLat ?: latestFix?.latitude,
        longitude = pendingPlaceLng ?: latestFix?.longitude,
        gpsReady = latestFix != null && (latestFix?.accuracyMeters ?: 999f) < 900f,
        placeError = placeError,
        onSave = { name, category, notes, speciesId ->
            val lat = pendingPlaceLat ?: latestFix?.latitude
            val lng = pendingPlaceLng ?: latestFix?.longitude
            markersVm.addMarker(
                name = name,
                category = category,
                notes = notes,
                latitude = lat,
                longitude = lng,
                speciesId = speciesId,
            )
            showQuickAdd = false
            pendingPlaceLat = null
            pendingPlaceLng = null
            if (currentRoute != Dest.Map.route) {
                navController.navigate(Dest.Map.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
        onSaveOfflineArea = { lat, lng ->
            scope.launch {
                val ok = runCatching {
                    offlineMapRepository.createRegionAround(lat, lng)
                }.isSuccess
                snackbarHostState.showSnackbar(
                    if (ok) {
                        "Offline area saved — 15 sq mile pack downloading"
                    } else {
                        "Couldn't start download — check your connection"
                    },
                )
            }
            showQuickAdd = false
            pendingPlaceLat = null
            pendingPlaceLng = null
        },
    )
}
