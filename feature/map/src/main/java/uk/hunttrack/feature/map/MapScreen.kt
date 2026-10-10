package uk.hunttrack.feature.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PointF
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.concurrent.Executors
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import uk.hunttrack.core.common.MapLibreStyleSupport
import uk.hunttrack.core.common.bearingToCardinal
import uk.hunttrack.core.common.formatBoundaryArea
import uk.hunttrack.core.common.toFeet
import uk.hunttrack.core.common.formatBoundaryPerimeter
import uk.hunttrack.core.common.formatFieldDistancePair
import uk.hunttrack.core.designsystem.components.ClickableGlassCard
import uk.hunttrack.core.designsystem.components.FieldGuideSheet
import uk.hunttrack.core.designsystem.components.FieldGuides
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.HintingOptionRow
import uk.hunttrack.core.designsystem.components.slideRightOnPress
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.geo.BoundaryMath
import uk.hunttrack.domain.geo.OsGridRef
import uk.hunttrack.domain.model.AppThemeMode
import uk.hunttrack.domain.model.BoundaryPoint
import uk.hunttrack.domain.model.FieldMarker
import uk.hunttrack.domain.model.GpsFix
import uk.hunttrack.domain.model.MapLayerStyle
import uk.hunttrack.domain.model.MarkerCategory
import uk.hunttrack.domain.model.displayName
import uk.hunttrack.domain.model.mapColorHex
import uk.hunttrack.domain.model.mapFillOpacity
import uk.hunttrack.domain.scent.ScentPlumeGeometry
import uk.hunttrack.core.designsystem.markers.MarkerIconResolver
import uk.hunttrack.domain.model.OfflineMapRegion
import uk.hunttrack.domain.model.OfflineMapStatus
import uk.hunttrack.feature.map.R

/** Open map panels / sheets — dismissed one at a time via Back. */
private enum class MapOverlaySection {
    HOMING,
    LAYERS,
    OFFLINE,
    MARKER,
}

@Composable
fun MapRoute(
    viewModel: MapViewModel = hiltViewModel(),
    onPlaceMarkerHere: () -> Unit = {},
    onPlaceMarkerAt: (Double, Double) -> Unit = { _, _ -> },
    onMarkAnywhereAt: (Double, Double) -> Unit = { _, _ -> },
    onQuickPlaceNext: (FieldMarker) -> Unit = {},
    onQuickPlaceFromLatest: () -> Unit = {},
    onBoundaryCompletedFromPermissions: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.activatePendingBoundaryCreation()
                viewModel.activatePermissionMapView()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.returnToPermissionsAfterBoundary.collect {
            onBoundaryCompletedFromPermissions()
        }
    }

    MapScreen(
        state = state,
        onSetLayer = viewModel::setMapLayer,
        onSetShowOsGridRef = viewModel::setShowOsGridRef,
        onSetShowCullPins = viewModel::setShowCullPinsOnMap,
        onGoToOsGrid = viewModel::goToOsGridRef,
        onClearGoTo = viewModel::clearGoToTarget,
        onSelectMarker = viewModel::selectMarker,
        onDeleteMarker = viewModel::deleteSelectedMarker,
        onClearTrail = viewModel::clearMarkerTrail,
        onDeleteLastMarker = viewModel::deleteLastMarker,
        onToggleOffline = viewModel::toggleOfflineManager,
        onDownload = viewModel::downloadArea,
        onDeleteRegion = viewModel::deleteOfflineRegion,
        onPlaceMarkerHere = onPlaceMarkerHere,
        onPlaceMarkerAt = onPlaceMarkerAt,
        onMarkAnywhereAt = onMarkAnywhereAt,
        onQuickPlaceNext = onQuickPlaceNext,
        onQuickPlaceFromLatest = onQuickPlaceFromLatest,
        onPlaceHomingHere = viewModel::placeHomingHere,
        onPlaceHomingAt = viewModel::placeHomingAt,
        onClearHoming = viewModel::clearHoming,
        onClearScentApproach = viewModel::clearScentApproach,
        onStartWalkBoundary = viewModel::startWalkBoundary,
        onStartDrawBoundary = viewModel::startDrawBoundary,
        onBeginBoundaryDraw = viewModel::beginBoundaryDraw,
        onPauseBoundaryDraw = viewModel::pauseBoundaryDraw,
        onCancelBoundaryDraft = viewModel::cancelBoundaryDraft,
        onAppendBoundaryDrawPoint = viewModel::appendBoundaryDrawPoint,
        onFinishBoundary = viewModel::finishBoundary,
        onClearPermissionMapFocus = viewModel::clearPermissionMapFocus,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    state: MapUiState,
    onSetLayer: (MapLayerStyle) -> Unit,
    onSetShowOsGridRef: (Boolean) -> Unit = {},
    onSetShowCullPins: (Boolean) -> Unit = {},
    onGoToOsGrid: (String) -> Unit = {},
    onClearGoTo: () -> Unit = {},
    onSelectMarker: (FieldMarker?) -> Unit,
    onDeleteMarker: () -> Unit,
    onClearTrail: () -> Unit,
    onDeleteLastMarker: () -> Unit = {},
    onToggleOffline: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onDeleteRegion: (Long) -> Unit,
    onPlaceMarkerHere: () -> Unit = {},
    onPlaceMarkerAt: (Double, Double) -> Unit = { _, _ -> },
    onMarkAnywhereAt: (Double, Double) -> Unit = { _, _ -> },
    onQuickPlaceNext: (FieldMarker) -> Unit = {},
    onQuickPlaceFromLatest: () -> Unit = {},
    onPlaceHomingHere: () -> Unit = {},
    onPlaceHomingAt: (Double, Double) -> Unit = { _, _ -> },
    onClearHoming: () -> Unit = {},
    onClearScentApproach: () -> Unit = {},
    onStartWalkBoundary: () -> Unit = {},
    onStartDrawBoundary: () -> Unit = {},
    onBeginBoundaryDraw: () -> Unit = {},
    onPauseBoundaryDraw: () -> Unit = {},
    onCancelBoundaryDraft: () -> Unit = {},
    onAppendBoundaryDrawPoint: (Double, Double) -> Unit = { _, _ -> },
    onFinishBoundary: () -> Unit = {},
    onClearPermissionMapFocus: () -> Unit = {},
) {
    val context = LocalContext.current
    val (mapRetryKey, bumpMapRetry) = rememberMapRetryState()
    var mapInitFailed by remember { mutableStateOf(false) }
    val mapView = rememberMapViewWithLifecycle(mapRetryKey)
    if (mapView == null) {
        MapUnavailablePanel(
            onRetry = {
                mapInitFailed = false
                bumpMapRetry()
            },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    var mapReady by remember(mapRetryKey) { mutableStateOf(false) }
    var mapLibre by remember(mapRetryKey) { mutableStateOf<MapLibreMap?>(null) }
    var lastAppliedBasemap by remember(mapRetryKey) { mutableStateOf<MapLayerStyle?>(null) }
    var mapCameraTick by remember { mutableIntStateOf(0) }
    var mapZoomLevel by remember { mutableDoubleStateOf(15.0) }
    var showLayers by remember { mutableStateOf(false) }
    var centeredOnce by remember { mutableStateOf(false) }
    var homingPlaceMode by remember { mutableStateOf(false) }
    var sectionStack by remember { mutableStateOf(listOf<MapOverlaySection>()) }
    var showGuide by remember { mutableStateOf(false) }
    var showSmoke by remember { mutableStateOf(true) }
    var showTrack by remember { mutableStateOf(true) }
    var showMarkerPath by remember { mutableStateOf(false) }
    var showScale by remember { mutableStateOf(true) }
    var showOverlaysSheet by remember { mutableStateOf(false) }
    /** When true, map rotates with compass (track-up). */
    var trackUp by remember { mutableStateOf(false) }
    var goToInput by remember { mutableStateOf("") }

    LaunchedEffect(state.prefsLoaded, state.prefs.mapTrainingEnabled) {
        if (!state.prefsLoaded) return@LaunchedEffect
        // Settings toggle only — no “don't show again”; dismiss closes for this visit
        if (state.prefs.mapTrainingEnabled) {
            showGuide = true
        }
    }

    fun dismissGuide() {
        showGuide = false
    }

    fun pushSection(section: MapOverlaySection) {
        sectionStack = sectionStack.filter { it != section } + section
    }
    fun removeSection(section: MapOverlaySection) {
        sectionStack = sectionStack.filter { it != section }
    }
    fun endHoming() {
        homingPlaceMode = false
        onClearHoming()
        removeSection(MapOverlaySection.HOMING)
    }
    fun popLastSection() {
        val last = sectionStack.lastOrNull() ?: return
        sectionStack = sectionStack.dropLast(1)
        when (last) {
            MapOverlaySection.HOMING -> {
                // Collapse HUD only — pin stays until End home
                homingPlaceMode = false
            }
            MapOverlaySection.LAYERS -> showLayers = false
            MapOverlaySection.OFFLINE -> onToggleOffline(false)
            MapOverlaySection.MARKER -> onSelectMarker(null)
        }
    }

    val showHomingPanel = MapOverlaySection.HOMING in sectionStack
    val canGoBack = sectionStack.isNotEmpty()

    BackHandler(enabled = canGoBack) { popLastSection() }

    LaunchedEffect(state.selectedMarker?.id) {
        if (state.selectedMarker != null) {
            pushSection(MapOverlaySection.MARKER)
        } else {
            removeSection(MapOverlaySection.MARKER)
        }
    }
    LaunchedEffect(state.showOfflineManager) {
        if (state.showOfflineManager) {
            pushSection(MapOverlaySection.OFFLINE)
        } else {
            removeSection(MapOverlaySection.OFFLINE)
        }
    }
    LaunchedEffect(state.homingTarget?.id) {
        if (state.homingTarget == null) {
            removeSection(MapOverlaySection.HOMING)
            homingPlaceMode = false
        }
    }

    // Track-up: keep map bearing aligned with phone compass heading
    LaunchedEffect(mapReady, trackUp, state.compassHeadingDeg) {
        if (!mapReady || !trackUp) return@LaunchedEffect
        val heading = state.compassHeadingDeg ?: return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.easeCamera(CameraUpdateFactory.bearingTo(heading.toDouble()), 180)
        }
    }

    val markersRef = rememberUpdatedState(state.markers)
    val onSelectRef = rememberUpdatedState(onSelectMarker)
    val onPlaceAtRef = rememberUpdatedState(onPlaceMarkerAt)
    val onMarkAnywhereAtRef = rememberUpdatedState(onMarkAnywhereAt)
    val markAnywhereEnabledRef = rememberUpdatedState(state.prefs.markAnywhereEnabled)
    val homingPlaceModeRef = rememberUpdatedState(homingPlaceMode)
    val boundaryModeRef = rememberUpdatedState(state.boundaryMode)
    val boundaryDrawPhaseRef = rememberUpdatedState(state.boundaryDrawPhase)
    val onAppendBoundaryPointRef = rememberUpdatedState(onAppendBoundaryDrawPoint)
    val boundaryDraftPointsRef = rememberUpdatedState(state.draftBoundaryPoints)
    val boundariesRef = rememberUpdatedState(state.boundaries)
    val liveBoundaryDrawCoords = remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    val gpsRef = rememberUpdatedState(state.gps)
    val windFromRef = rememberUpdatedState(state.windFromDeg)
    val windSpeedRef = rememberUpdatedState(state.windSpeedKmh)
    val windGustRef = rememberUpdatedState(state.windGustKmh)
    val humidityRef = rememberUpdatedState(state.humidityPercent)
    val temperatureRef = rememberUpdatedState(state.temperatureC)
    val mapLayerRef = rememberUpdatedState(state.prefs.mapLayerStyle)
    val showSmokeRef = rememberUpdatedState(showSmoke)
    val onHomingPlaceRef = rememberUpdatedState<(LatLng) -> Unit> { latLng ->
        onPlaceHomingAt(latLng.latitude, latLng.longitude)
        homingPlaceMode = false
        pushSection(MapOverlaySection.HOMING)
    }

    // MapView lifecycle is owned by rememberMapViewWithLifecycle().

    LaunchedEffect(state.prefs.mapLayerStyle, mapReady) {
        if (!mapReady) return@LaunchedEffect
        if (lastAppliedBasemap == state.prefs.mapLayerStyle) return@LaunchedEffect
        lastAppliedBasemap = state.prefs.mapLayerStyle
        mapView.getMapAsync { map ->
            applyBasemap(
                map,
                context,
                state.prefs.mapLayerStyle,
                state.gps?.latitude ?: DefaultMapLatitude,
            ) { style ->
                ensureSources(style, context)
                applyZoomLimits(map, state.gps?.latitude ?: DefaultMapLatitude)
                ensureBasemapOverzoom(style, oneMeterPinchZoom(state.gps?.latitude ?: DefaultMapLatitude))
                syncMapOverlays(
                    style = style,
                    context = context,
                    state = state,
                    showTrack = showTrack,
                    showMarkerPath = showMarkerPath,
                    showSmoke = showSmoke,
                )
            }
        }
    }

    LaunchedEffect(
        state.markers.size,
        state.markers.lastOrNull()?.id,
        state.trackPoints.size,
        mapReady,
        showTrack,
        showMarkerPath,
        state.scentApproach?.targetName,
        state.boundaries.size,
        state.draftBoundaryPoints.size,
        state.boundaryMode,
        state.boundaryDrawPhase,
        state.offlineRegions.size,
        state.offlineRegions.lastOrNull()?.id,
    ) {
        if (!mapReady) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                ensureSources(style, context)
                syncMapOverlays(
                    style = style,
                    context = context,
                    state = state,
                    showTrack = showTrack,
                    showMarkerPath = showMarkerPath,
                    showSmoke = false,
                    markersOnly = true,
                )
            }
            mapCameraTick++
        }
    }

    LaunchedEffect(state.gps, mapReady) {
        val gps = state.gps ?: return@LaunchedEffect
        if (!mapReady) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                ensureSources(style, context)
                updateUserLocation(style, gps)
            }
            if (!centeredOnce) {
                centeredOnce = true
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(gps.latitude, gps.longitude),
                        15.0.coerceIn(MinZoomPreference, AbsoluteMaxZoomCap),
                    ),
                )
            }
        }
    }

    LaunchedEffect(state.goToTarget, mapReady) {
        val target = state.goToTarget ?: return@LaunchedEffect
        if (!mapReady) return@LaunchedEffect
        showLayers = false
        removeSection(MapOverlaySection.LAYERS)
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                ensureSources(style, context)
                updateGoToPin(style, target)
            }
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(target.latitude, target.longitude),
                    16.0.coerceIn(MinZoomPreference, AbsoluteMaxZoomCap),
                ),
            )
        }
    }

    LaunchedEffect(state.goToTarget, mapReady) {
        if (state.goToTarget != null || !mapReady) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.let { updateGoToPin(it, null) }
        }
    }

    LaunchedEffect(state.prefs.showOsGridRef, mapReady, mapCameraTick) {
        if (!mapReady) return@LaunchedEffect
        mapView.getMapAsync { map ->
            val style = map.style ?: return@getMapAsync
            if (!state.prefs.showOsGridRef) {
                updateOsGridOverlay(style, emptyList())
                return@getMapAsync
            }
            val bounds = runCatching { map.projection.visibleRegion.latLngBounds }.getOrNull()
                ?: return@getMapAsync
            val spacing = osGridSpacingForZoom(map.cameraPosition.zoom)
            val lines = OsGridRef.gridLinesForViewport(
                south = bounds.latitudeSouth,
                west = bounds.longitudeWest,
                north = bounds.latitudeNorth,
                east = bounds.longitudeEast,
                spacingMeters = spacing,
            )
            updateOsGridOverlay(style, lines)
        }
    }

    LaunchedEffect(mapReady, state.gps?.latitude) {
        if (!mapReady) return@LaunchedEffect
        val lat = state.gps?.latitude ?: DefaultMapLatitude
        mapView.getMapAsync { map ->
            applyZoomLimits(map, lat)
        }
    }

    LaunchedEffect(
        mapReady,
        state.homingTarget?.id,
        state.homingTarget?.latitude,
        state.homingTarget?.longitude,
        state.gps?.latitude,
        state.gps?.longitude,
    ) {
        if (!mapReady) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                ensureSources(style, context)
                updateHomingOverlay(style, state.gps, state.homingTarget)
            }
        }
    }

    // Animated scent / wind smoke — one geographic cone at all zoom levels
    LaunchedEffect(mapReady, showSmoke) {
        if (!mapReady) return@LaunchedEffect
        if (!showSmoke) {
            mapView.getMapAsync { map ->
                map.style?.let { style ->
                    updateWindSmoke(style, null, null, null, 0f, showSmoke = false)
                }
            }
            return@LaunchedEffect
        }

        var mapRef: MapLibreMap? = null
        mapView.getMapAsync { map -> mapRef = map }

        var phase = 0f
        while (isActive) {
            if (!showSmokeRef.value) break
            val style = mapRef?.style
            if (style != null) {
                val gps = gpsRef.value
                updateUserLocation(style, gps)
                updateWindSmoke(
                    style,
                    gps,
                    windFromRef.value,
                    windSpeedRef.value,
                    phase,
                    showSmoke = true,
                    mapLayerRef.value,
                    windGustKmh = windGustRef.value,
                    humidityPercent = humidityRef.value,
                    temperatureC = temperatureRef.value,
                )
            }
            val windSpeed = windSpeedRef.value
            delay(smokeFrameDelayMs(windSpeed))
            phase = (phase + smokePhaseStep(windSpeed)) % 1f
        }
    }

    // Freehand boundary drawing — single-finger drag while in ACTIVE draw phase
    DisposableEffect(mapReady, state.boundaryMode, state.boundaryDrawPhase) {
        if (!mapReady ||
            state.boundaryMode != BoundaryRecordMode.DRAWING ||
            state.boundaryDrawPhase != BoundaryDrawPhase.ACTIVE
        ) {
            liveBoundaryDrawCoords.value = emptyList()
            mapView.setOnTouchListener(null)
            mapView.getMapAsync { map ->
                map.style?.let(::clearLiveBoundaryLine)
                map.uiSettings.isScrollGesturesEnabled = true
                map.uiSettings.isZoomGesturesEnabled = true
                map.uiSettings.isRotateGesturesEnabled = true
            }
            return@DisposableEffect onDispose {
                liveBoundaryDrawCoords.value = emptyList()
                mapView.setOnTouchListener(null)
            }
        }

        var strokeActive = false
        var lastX = 0f
        var lastY = 0f
        val minSamplePx = 10f
        val minVisualSamplePx = 4f
        var strokeBasePoints: List<BoundaryPoint> = emptyList()
        val localStrokePoints = mutableListOf<BoundaryPoint>()

        fun mergedDraftPoints(): List<BoundaryPoint> = buildList {
            addAll(strokeBasePoints)
            addAll(localStrokePoints)
        }

        fun latLngFrom(map: MapLibreMap, x: Float, y: Float) =
            map.projection.fromScreenLocation(PointF(x, y))

        fun samplePoint(map: MapLibreMap, x: Float, y: Float) {
            val latLng = latLngFrom(map, x, y)
            onAppendBoundaryPointRef.value(latLng.latitude, latLng.longitude)
        }

        fun publishDrawCoords(map: MapLibreMap, x: Float, y: Float) {
            val preview = latLngFrom(map, x, y)
            val coords = buildList {
                strokeBasePoints.forEach { add(it.latitude to it.longitude) }
                localStrokePoints.forEach { add(it.latitude to it.longitude) }
                add(preview.latitude to preview.longitude)
            }
            liveBoundaryDrawCoords.value = coords
            val style = map.style ?: return
            ensureSources(style, context)
            updateLiveBoundaryLine(style, coords)
                updateBoundariesOverlay(style, boundariesRef.value, mergedDraftPoints(), emptyList())
            bringBoundaryDrawLayersToFront(style)
        }

        mapView.getMapAsync { map ->
            val listener = View.OnTouchListener { _, event ->
                if (boundaryModeRef.value != BoundaryRecordMode.DRAWING ||
                    boundaryDrawPhaseRef.value != BoundaryDrawPhase.ACTIVE
                ) {
                    return@OnTouchListener false
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (strokeActive) {
                            strokeActive = false
                            localStrokePoints.clear()
                            liveBoundaryDrawCoords.value = emptyList()
                            map.style?.let(::clearLiveBoundaryLine)
                            map.uiSettings.isScrollGesturesEnabled = true
                            map.uiSettings.isZoomGesturesEnabled = true
                            map.uiSettings.isRotateGesturesEnabled = true
                        }
                        false
                    }
                    MotionEvent.ACTION_DOWN -> {
                        if (event.pointerCount == 1) {
                            strokeActive = true
                            strokeBasePoints = boundaryDraftPointsRef.value
                            localStrokePoints.clear()
                            map.uiSettings.isScrollGesturesEnabled = false
                            map.uiSettings.isZoomGesturesEnabled = false
                            map.uiSettings.isRotateGesturesEnabled = false
                            val latLng = latLngFrom(map, event.x, event.y)
                            localStrokePoints.add(
                                BoundaryPoint(
                                    latLng.latitude,
                                    latLng.longitude,
                                    strokeBasePoints.size,
                                ),
                            )
                            samplePoint(map, event.x, event.y)
                            lastX = event.x
                            lastY = event.y
                            publishDrawCoords(map, event.x, event.y)
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!strokeActive || event.pointerCount != 1) return@OnTouchListener false
                        val dx = event.x - lastX
                        val dy = event.y - lastY
                        val distSq = dx * dx + dy * dy
                        if (distSq >= minVisualSamplePx * minVisualSamplePx) {
                            val latLng = latLngFrom(map, event.x, event.y)
                            localStrokePoints.add(
                                BoundaryPoint(
                                    latLng.latitude,
                                    latLng.longitude,
                                    strokeBasePoints.size + localStrokePoints.size,
                                ),
                            )
                            lastX = event.x
                            lastY = event.y
                        }
                        if (distSq >= minSamplePx * minSamplePx) {
                            samplePoint(map, event.x, event.y)
                        }
                        publishDrawCoords(map, event.x, event.y)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (strokeActive) {
                            strokeActive = false
                            val style = map.style
                            if (style != null) {
                                ensureSources(style, context)
                                val committed = boundaryDraftPointsRef.value.ifEmpty { mergedDraftPoints() }
                                updateBoundariesOverlay(style, boundariesRef.value, committed, emptyList())
                                bringBoundaryDrawLayersToFront(style)
                            }
                            localStrokePoints.clear()
                            liveBoundaryDrawCoords.value = emptyList()
                            map.style?.let(::clearLiveBoundaryLine)
                            map.uiSettings.isScrollGesturesEnabled = true
                            map.uiSettings.isZoomGesturesEnabled = true
                            map.uiSettings.isRotateGesturesEnabled = true
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            mapView.setOnTouchListener(listener)
        }

        onDispose {
            liveBoundaryDrawCoords.value = emptyList()
            mapView.setOnTouchListener(null)
            mapView.getMapAsync { map ->
                map.style?.let(::clearLiveBoundaryLine)
                map.uiSettings.apply {
                    isScrollGesturesEnabled = true
                    isZoomGesturesEnabled = true
                    isRotateGesturesEnabled = true
                }
            }
        }
    }

    LaunchedEffect(state.draftBoundaryPoints, state.boundaries, mapReady, state.boundaryMode) {
        if (!mapReady || state.boundaryMode == BoundaryRecordMode.OFF) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                ensureSources(style, context)
                updateBoundariesOverlay(style, state.boundaries, state.draftBoundaryPoints, state.permissionMapFocus)
                updatePermissionLabelOverlay(style, state.boundaries, state.permissionMapFocus)
                bringBoundaryDrawLayersToFront(style)
            }
        }
    }

    LaunchedEffect(mapReady, state.permissionMapFocus, state.boundaries) {
        if (!mapReady) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                ensureSources(style, context)
                updateBoundariesOverlay(style, state.boundaries, state.draftBoundaryPoints, state.permissionMapFocus)
                updatePermissionLabelOverlay(style, state.boundaries, state.permissionMapFocus)
            }
        }
    }

    LaunchedEffect(mapReady, state.permissionMapFocus) {
        if (!mapReady || state.permissionMapFocus.isEmpty()) return@LaunchedEffect
        val focusIds = state.permissionMapFocus.map { it.boundaryAreaId }.toSet()
        val points = state.boundaries.filter { it.id in focusIds }.flatMap { it.points }
        if (points.isEmpty()) return@LaunchedEffect
        mapView.getMapAsync { map ->
            val builder = LatLngBounds.Builder()
            points.forEach { builder.include(LatLng(it.latitude, it.longitude)) }
            runCatching {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 140))
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                (mapView.parent as? ViewGroup)?.removeView(mapView)
                mapView
            },
            // Overlay sync is driven only by LaunchedEffects — never from recomposition.
            update = {},
            modifier = Modifier.fillMaxSize(),
        )

        MapThemeDimOverlay(
            themeMode = state.prefs.themeMode,
            modifier = Modifier.fillMaxSize(),
        )

        if (state.permissionMapFocus.isNotEmpty()) {
            PermissionFocusBanner(
                labels = state.permissionMapFocus.map { it.label },
                onClear = onClearPermissionMapFocus,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp),
            )
        }

        MapMarkerOverlay(
            mapView = mapView,
            mapReady = mapReady,
            cameraTick = mapCameraTick,
            markers = state.markers,
        )

        BoundaryDrawStrokeOverlay(
            mapView = mapView,
            mapReady = mapReady,
            cameraTick = mapCameraTick,
            drawCoords = liveBoundaryDrawCoords.value,
        )

        // Map setup runs once per MapView instance
        LaunchedEffect(mapView) {
            mapInitFailed = false
            val opened = runCatching {
                mapView.getMapAsync { map ->
                    val setupOk = runCatching {
                        mapLibre = map
                        val initLat = state.gps?.latitude ?: DefaultMapLatitude
                        applyZoomLimits(map, initLat)
                        map.uiSettings.isAttributionEnabled = true
                        map.uiSettings.isRotateGesturesEnabled = true
                        map.uiSettings.isTiltGesturesEnabled = true
                        applyBasemap(map, context, state.prefs.mapLayerStyle, initLat) { style ->
                            val styleOk = runCatching {
                                lastAppliedBasemap = state.prefs.mapLayerStyle
                                applyZoomLimits(map, initLat)
                                ensureBasemapOverzoom(style, oneMeterPinchZoom(initLat))
                                ensureSources(style, context)
                                syncMapOverlays(
                                    style = style,
                                    context = context,
                                    state = state,
                                    showTrack = showTrack,
                                    showMarkerPath = showMarkerPath,
                                    showSmoke = showSmoke,
                                )
                                mapReady = true
                                mapZoomLevel = map.cameraPosition.zoom
                                mapCameraTick++
                                val fix = state.gps
                                if (fix != null) {
                                    centeredOnce = true
                                    map.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(fix.latitude, fix.longitude),
                                            15.0.coerceIn(MinZoomPreference, AbsoluteMaxZoomCap),
                                        ),
                                    )
                                } else {
                                    // UK overview when no fix yet — min zoom still allows pinch out to the globe.
                                    map.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(52.4862, -1.8904),
                                            5.5,
                                        ),
                                    )
                                }
                            }.isSuccess
                            if (!styleOk) {
                                mapReady = false
                                mapInitFailed = true
                            }
                        }
                    map.addOnMapClickListener { latLng ->
                        if (homingPlaceModeRef.value) {
                            onHomingPlaceRef.value(latLng)
                            return@addOnMapClickListener true
                        }
                        // Fat hit box around tap so markers are easy to select
                        val screenPoint = map.projection.toScreenLocation(latLng)
                        val pad = 56f
                        val hitBox = RectF(
                            screenPoint.x - pad,
                            screenPoint.y - pad,
                            screenPoint.x + pad,
                            screenPoint.y + pad,
                        )
                        val hit = map.queryRenderedFeatures(
                            hitBox,
                            "markers-dots",
                        )
                        val hitId = hit.firstOrNull { it.hasProperty("id") }
                            ?.getNumberProperty("id")?.toLong()
                        val hitTitle = hit.firstOrNull { it.hasProperty("title") }?.getStringProperty("title")
                        val chosen = when {
                            hitId != null -> markersRef.value.firstOrNull { it.id == hitId }
                            hitTitle != null -> markersRef.value.firstOrNull { it.name == hitTitle }
                                ?: markersRef.value.minByOrNull {
                                    distanceMeters(it.latitude, it.longitude, latLng.latitude, latLng.longitude)
                                }
                            else -> null
                        }
                        if (chosen != null) {
                            onSelectRef.value(chosen)
                            return@addOnMapClickListener true
                        }
                        val nearest = markersRef.value.minByOrNull {
                            distanceMeters(it.latitude, it.longitude, latLng.latitude, latLng.longitude)
                        }
                        if (nearest != null) {
                            val dist = distanceMeters(
                                nearest.latitude,
                                nearest.longitude,
                                latLng.latitude,
                                latLng.longitude,
                            )
                            if (dist < MarkerHitRadiusMeters) {
                                onSelectRef.value(nearest)
                                return@addOnMapClickListener true
                            }
                        }
                        if (boundaryModeRef.value != BoundaryRecordMode.OFF) {
                            // Mid-fence-draw: keep taps from dropping stray waypoints
                            onSelectRef.value(null)
                            return@addOnMapClickListener false
                        }
                        if (markAnywhereEnabledRef.value) {
                            // One-touch mark anywhere: open the Mark location form pinned to this spot
                            onMarkAnywhereAtRef.value(latLng.latitude, latLng.longitude)
                        } else {
                            // Mark-anywhere off: quick waypoint, saved like the others
                            onPlaceAtRef.value(latLng.latitude, latLng.longitude)
                        }
                        true
                    }
                    map.addOnMapLongClickListener { latLng ->
                        if (homingPlaceModeRef.value) {
                            return@addOnMapLongClickListener false
                        }
                        if (markAnywhereEnabledRef.value) {
                            // Mark anywhere: open the full marker sheet pinned to this spot
                            onMarkAnywhereAtRef.value(latLng.latitude, latLng.longitude)
                        } else {
                            onPlaceAtRef.value(latLng.latitude, latLng.longitude)
                        }
                        true
                    }
                    map.addOnCameraMoveListener {
                        mapZoomLevel = map.cameraPosition.zoom
                        mapCameraTick++
                    }
                        map.addOnCameraIdleListener {
                            mapZoomLevel = map.cameraPosition.zoom
                            mapCameraTick++
                            val lat = map.cameraPosition.target?.latitude
                                ?: gpsRef.value?.latitude
                                ?: DefaultMapLatitude
                            applyZoomLimits(map, lat)
                        }
                    }.isSuccess
                    if (!setupOk) {
                        mapReady = false
                        mapInitFailed = true
                    }
                }
            }.isSuccess
            if (!opened) {
                mapReady = false
                mapInitFailed = true
            }
        }

        if (mapInitFailed) {
            MapUnavailablePanel(
                onRetry = {
                    mapReady = false
                    mapLibre = null
                    lastAppliedBasemap = null
                    centeredOnce = false
                    mapInitFailed = false
                    bumpMapRetry()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(
                    horizontal = 10.dp,
                    vertical = if (showHomingPanel) 4.dp else 8.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (showHomingPanel) 4.dp else 6.dp),
        ) {
            if (state.prefs.showOsGridRef) {
                val gps = state.gps
                val grid = gps?.let { OsGridRef.latLngToOsGrid(it.latitude, it.longitude) }
                if (grid != null) {
                    GlassPanel(Modifier.fillMaxWidth(), compact = true) {
                        Text("OS Grid", style = MaterialTheme.typography.labelMedium, color = HuntColors.Mist)
                        Text(grid, style = MaterialTheme.typography.titleMedium, color = HuntColors.BronzeBright)
                    }
                }
            }
            state.goToTarget?.let { target ->
                GlassPanel(Modifier.fillMaxWidth(), compact = true) {
                    Text("Go to", style = MaterialTheme.typography.labelMedium, color = HuntColors.Mist)
                    Text(target.osGridRef, style = MaterialTheme.typography.titleMedium, color = HuntColors.Cream)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            onPlaceMarkerAt(target.latitude, target.longitude)
                        }) {
                            Text("Save marker", color = HuntColors.BronzeBright)
                        }
                        TextButton(onClick = onClearGoTo) {
                            Text("Clear pin", color = HuntColors.Mist)
                        }
                    }
                }
            }
            if (state.boundaryMode != BoundaryRecordMode.OFF) {
                GlassPanel(Modifier.fillMaxWidth()) {
                    state.pendingBoundaryCreation?.let { pending ->
                        Text(
                            pending.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = HuntColors.BronzeBright,
                        )
                        Text(
                            pending.zoneType.displayName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = HuntColors.Mist,
                        )
                    } ?: Text(
                        when (state.boundaryMode) {
                            BoundaryRecordMode.WALKING -> "Walking boundary"
                            BoundaryRecordMode.DRAWING -> "Draw boundary"
                            else -> "Boundary"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = HuntColors.BronzeBright,
                    )
                    Text(
                        when {
                            state.boundaryMode == BoundaryRecordMode.WALKING ->
                                "Walk the fence line — GPS adds points automatically. Tap Save fence when done."
                            state.boundaryMode == BoundaryRecordMode.DRAWING &&
                                state.boundaryDrawPhase == BoundaryDrawPhase.PREP ->
                                "Pinch and zoom until your whole shoot or area fits on screen, then tap Start drawing."
                            state.boundaryMode == BoundaryRecordMode.DRAWING ->
                                "Drag your finger around the boundary. Tap Pan map to move the view, then draw again."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = HuntColors.Cream,
                    )
                    Text(
                        "${state.draftBoundaryPoints.size} points recorded",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Mist,
                    )
                    if (state.draftBoundaryPoints.size >= 2) {
                        val perimeter = BoundaryMath.perimeterMeters(
                            state.draftBoundaryPoints,
                            closeLoop = state.draftBoundaryPoints.size >= 3,
                        )
                        Text(
                            "Perimeter: ${formatBoundaryPerimeter(perimeter)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = HuntColors.Cream,
                        )
                    }
                    if (state.draftBoundaryPoints.size >= 3) {
                        val area = BoundaryMath.areaSqMeters(state.draftBoundaryPoints)
                        Text(
                            "Area: ${formatBoundaryArea(area)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = HuntColors.Cream,
                        )
                    }
                }
            }
            state.scentApproach?.let { plan ->
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text(
                        "Into-wind approach",
                        style = MaterialTheme.typography.titleMedium,
                        color = HuntColors.BronzeBright,
                    )
                    Text(
                        "Route to ${plan.targetName}: walk ${bearingToCardinal(plan.walkHeadingDeg)} " +
                            "so the wind hits the front of you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HuntColors.Cream,
                    )
                    Text(
                        "Orange line · start downwind → finish at marker",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Mist,
                    )
                    MapToolAssistChip(
                        onClick = onClearScentApproach,
                        label = { Text("Clear approach") },
                    )
                }
            }
            if (showHomingPanel) {
                GlassPanel(Modifier.fillMaxWidth(), compact = true) {
                    Text("Homing retrieve", style = MaterialTheme.typography.labelMedium, color = HuntColors.Cream)
                    when {
                        state.homingAtTarget -> {
                            Text(
                                "At target",
                                style = MaterialTheme.typography.titleSmall,
                                color = HuntColors.Success,
                            )
                            Text(
                                "Within 1 m of pin",
                                style = MaterialTheme.typography.labelSmall,
                                color = HuntColors.Mist,
                            )
                            TextButton(
                                onClick = { endHoming() },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            ) {
                                Text("End homing", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        homingPlaceMode && state.homingTarget == null -> {
                            Text(
                                "Tap map for prey pin · or Home here",
                                style = MaterialTheme.typography.labelSmall,
                                color = HuntColors.Gold,
                            )
                        }
                        state.homingDistanceMeters != null && state.homingBearingDeg != null -> {
                            val m = state.homingDistanceMeters
                            Text(
                                formatFieldDistancePair(m, state.prefs.unitSystem),
                                style = MaterialTheme.typography.titleSmall,
                                color = HuntColors.BronzeBright,
                            )
                            val turnHint = state.homingRelativeDeg?.let { rel ->
                                when {
                                    kotlin.math.abs(rel) < 15.0 -> "Ahead"
                                    rel > 0 -> "Right ${rel.toInt()}°"
                                    else -> "Left ${(-rel).toInt()}°"
                                }
                            }
                            Text(
                                "${state.homingBearingDeg.toInt()}° ${bearingToCardinal(state.homingBearingDeg)}" +
                                    (turnHint?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = HuntColors.Mist,
                            )
                            if (homingPlaceMode) {
                                Text(
                                    "Tap map to move pin",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HuntColors.Gold,
                                )
                            }
                            TextButton(
                                onClick = { endHoming() },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            ) {
                                Text("End homing", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        else -> {
                            Text(
                                "Place pin or end retrieve",
                                style = MaterialTheme.typography.labelSmall,
                                color = HuntColors.Gold,
                            )
                            TextButton(
                                onClick = { endHoming() },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            ) {
                                Text("End homing", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            HintingOptionRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                prominentHint = true,
            ) {
                if (canGoBack) {
                    MapToolAssistChip(
                        onClick = { popLastSection() },
                        label = { Text("Back") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        },
                    )
                }
                MapToolAssistChip(
                    onClick = { showGuide = true },
                    label = { Text("?") },
                    leadingIcon = {
                        Icon(Icons.Outlined.HelpOutline, contentDescription = "Map help")
                    },
                )
                MapToolFilterChip(
                    selected = homingPlaceMode || showHomingPanel,
                    onClick = {
                        when {
                            homingPlaceMode -> {
                                homingPlaceMode = false
                                if (state.homingTarget == null) removeSection(MapOverlaySection.HOMING)
                            }
                            showHomingPanel -> {
                                // Already open — enter place mode to move the pin
                                homingPlaceMode = true
                            }
                            else -> {
                                // Re-open navigate panel (pin stays if already set)
                                pushSection(MapOverlaySection.HOMING)
                                if (state.homingTarget == null) homingPlaceMode = true
                            }
                        }
                    },
                    label = {
                        Text(
                            when {
                                homingPlaceMode -> "Home ON"
                                showHomingPanel -> "Home"
                                else -> "Home"
                            },
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Flag, null) },
                )
                MapToolAssistChip(
                    onClick = {
                        onPlaceHomingHere()
                        pushSection(MapOverlaySection.HOMING)
                    },
                    enabled = state.gps != null,
                    label = { Text("Home here") },
                    leadingIcon = { Icon(Icons.Default.NearMe, null) },
                )
                if (state.homingTarget != null) {
                    MapToolAssistChip(
                        onClick = { endHoming() },
                        label = { Text("End home") },
                        leadingIcon = { Icon(Icons.Default.Flag, null) },
                    )
                }
                MapToolFilterChip(
                    selected = trackUp,
                    onClick = {
                        trackUp = !trackUp
                        if (!trackUp) {
                            mapView.getMapAsync { map ->
                                map.animateCamera(CameraUpdateFactory.bearingTo(0.0))
                            }
                        }
                    },
                    label = { Text(if (trackUp) "Track up" else "North up") },
                    leadingIcon = {
                        Icon(
                            if (trackUp) Icons.Default.Navigation else Icons.Default.Explore,
                            contentDescription = null,
                        )
                    },
                )
                if (state.markers.isNotEmpty()) {
                    MapToolAssistChip(
                        onClick = onDeleteLastMarker,
                        label = { Text("Delete last") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                    )
                    MapToolAssistChip(
                        onClick = onClearTrail,
                        label = { Text("Delete all") },
                        leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                    )
                }
                if (state.boundaryMode == BoundaryRecordMode.OFF) {
                    MapToolAssistChip(
                        onClick = onStartWalkBoundary,
                        label = { Text("Walk fence") },
                        leadingIcon = { Icon(Icons.Default.DirectionsWalk, null) },
                    )
                    MapToolAssistChip(
                        onClick = onStartDrawBoundary,
                        label = { Text("Draw fence") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                    )
                } else {
                    if (state.boundaryMode == BoundaryRecordMode.DRAWING &&
                        state.boundaryDrawPhase == BoundaryDrawPhase.PREP
                    ) {
                        MapToolAssistChip(
                            onClick = onBeginBoundaryDraw,
                            label = { Text("Start drawing") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                        )
                    }
                    if (state.boundaryMode == BoundaryRecordMode.DRAWING &&
                        state.boundaryDrawPhase == BoundaryDrawPhase.ACTIVE
                    ) {
                        MapToolAssistChip(
                            onClick = onPauseBoundaryDraw,
                            label = { Text("Pan map") },
                            leadingIcon = { Icon(Icons.Default.Explore, null) },
                        )
                    }
                    MapToolAssistChip(
                        onClick = onFinishBoundary,
                        enabled = when (state.boundaryMode) {
                            BoundaryRecordMode.WALKING -> state.draftBoundaryPoints.size >= 2
                            BoundaryRecordMode.DRAWING -> state.draftBoundaryPoints.size >= 3
                            else -> false
                        },
                        label = { Text("Save fence") },
                        leadingIcon = { Icon(Icons.Default.Save, null) },
                    )
                    MapToolAssistChip(
                        onClick = onCancelBoundaryDraft,
                        label = { Text("Cancel") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                    )
                }
                MapToolAssistChip(
                    onClick = {
                        showLayers = true
                        pushSection(MapOverlaySection.LAYERS)
                    },
                    label = { Text("Layers") },
                    leadingIcon = { Icon(Icons.Default.Layers, null) },
                )
                MapToolAssistChip(
                    onClick = {
                        onToggleOffline(true)
                        pushSection(MapOverlaySection.OFFLINE)
                    },
                    label = { Text("Offline") },
                    leadingIcon = { Icon(Icons.Default.CloudDownload, null) },
                )
                MapToolAssistChip(
                    onClick = { showOverlaysSheet = true },
                    label = { Text("Overlays") },
                    leadingIcon = { Icon(Icons.Default.Visibility, null) },
                )
            }
        }

        // Scale bar sits directly above Sync — bottom-left
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showScale) {
                MapScaleBarOverlay(
                    mapView = mapView,
                    mapReady = mapReady,
                    cameraTick = mapCameraTick,
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(HuntColors.Bronze.copy(alpha = if (state.gps != null) 1f else 0.45f))
                    .clickable(enabled = state.gps != null) {
                        centreOnUser(mapView, mapLibre, state.gps)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Sync to my location",
                    tint = HuntColors.Ink,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Sync", style = MaterialTheme.typography.labelMedium, color = HuntColors.Ink)
            }
        }

        // Quick + above scaffold Mark GPS — drop next waypoint at current GPS
        if (state.markers.isNotEmpty()) {
            FloatingActionButton(
                onClick = {
                    val gps = state.gps
                    if (gps != null) {
                        onPlaceMarkerAt(gps.latitude, gps.longitude)
                    } else {
                        val template = state.selectedMarker
                            ?: state.markers.maxByOrNull { it.createdAtEpochMs }
                        if (template != null) onQuickPlaceNext(template)
                        else onQuickPlaceFromLatest()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 88.dp)
                    .size(48.dp),
                containerColor = HuntColors.BronzeBright,
                contentColor = HuntColors.Ink,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add waypoint here")
            }
        }
    }

    if (showOverlaysSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOverlaysSheet = false },
            containerColor = HuntColors.ForestMid,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Overlays", style = MaterialTheme.typography.headlineMedium, color = HuntColors.Cream)
                Text(
                    "Show or hide scent smoke, GPS track, marker path, and map scale.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HuntColors.Mist,
                )
                OverlayToggleRow(
                    label = "Scent smoke",
                    checked = showSmoke,
                    onCheckedChange = { showSmoke = it },
                )
                OverlayToggleRow(
                    label = "GPS track",
                    checked = showTrack,
                    onCheckedChange = { showTrack = it },
                )
                OverlayToggleRow(
                    label = "Marker path",
                    checked = showMarkerPath,
                    onCheckedChange = { showMarkerPath = it },
                )
                OverlayToggleRow(
                    label = "Map scale (m & ft)",
                    checked = showScale,
                    onCheckedChange = { showScale = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapToolFilterChip(
                        selected = showSmoke,
                        onClick = { showSmoke = !showSmoke },
                        label = { Text("Smoke") },
                    )
                    MapToolFilterChip(
                        selected = showTrack,
                        onClick = { showTrack = !showTrack },
                        label = { Text("Track") },
                    )
                    MapToolFilterChip(
                        selected = showMarkerPath,
                        onClick = { showMarkerPath = !showMarkerPath },
                        label = { Text("Path") },
                    )
                    MapToolFilterChip(
                        selected = showScale,
                        onClick = { showScale = !showScale },
                        label = { Text("Scale") },
                    )
                }
            }
        }
    }

    if (showLayers) {
        ModalBottomSheet(
            onDismissRequest = {
                showLayers = false
                removeSection(MapOverlaySection.LAYERS)
            },
            containerColor = HuntColors.ForestMid,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Map layers", style = MaterialTheme.typography.headlineMedium, color = HuntColors.Cream)
                Text("Pick a basemap — each looks different", style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
                MapLayerStyle.entries.forEach { style ->
                    val (title, desc) = when (style) {
                        MapLayerStyle.STREET -> "Streets" to "OpenFreeMap · OpenStreetMap, updated weekly"
                        MapLayerStyle.OUTDOOR -> "Outdoor / Topo" to "OpenFreeMap + terrain shading"
                        MapLayerStyle.SATELLITE -> "Satellite" to "Esri aerial · online only (refreshed in patches)"
                    }
                    ClickableGlassCard(
                        onClick = {
                            onSetLayer(style)
                            showLayers = false
                            removeSection(MapOverlaySection.LAYERS)
                        },
                        selected = state.prefs.mapLayerStyle == style,
                        hint = if (state.prefs.mapLayerStyle == style) "Active layer" else "Tap to switch ›",
                    ) {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = HuntColors.Cream)
                        Text(desc, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
                    }
                }
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text("OS Grid Reference", style = MaterialTheme.typography.titleMedium, color = HuntColors.Cream)
                    Text(
                        "Blue National Grid lines on the map, plus grid refs on GPS and markers",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Mist,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Show OS Grid lines", color = HuntColors.Cream)
                        Switch(
                            checked = state.prefs.showOsGridRef,
                            onCheckedChange = onSetShowOsGridRef,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Cull pins on map", color = HuntColors.Cream)
                        Switch(
                            checked = state.prefs.showCullPinsOnMap,
                            onCheckedChange = onSetShowCullPins,
                        )
                    }
                }
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text("Go to OS Grid", style = MaterialTheme.typography.titleMedium, color = HuntColors.Cream)
                    Text(
                        "Example: SU 37290 15552",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Mist,
                    )
                    OutlinedTextField(
                        value = goToInput,
                        onValueChange = { goToInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Grid reference") },
                    )
                    state.goToError?.let {
                        Text(it, color = HuntColors.Danger, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { onGoToOsGrid(goToInput) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Go to location")
                    }
                }
            }
        }
    }

    if (state.showOfflineManager) {
        ModalBottomSheet(
            onDismissRequest = {
                onToggleOffline(false)
                removeSection(MapOverlaySection.OFFLINE)
            },
            containerColor = HuntColors.ForestMid,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Offline maps", style = MaterialTheme.typography.headlineMedium, color = HuntColors.Cream)
                Text(
                    "Download ~15 sq mile field packs for rural use — Streets and Outdoor/Topo " +
                        "work fully offline from the downloaded pack. Satellite needs a connection " +
                        "(imagery can't be stored offline). Save as many packs as you need.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HuntColors.Mist,
                )
                TextButton(onClick = { onDownload() }) {
                    Text("Download 15 sq mile pack around me", color = HuntColors.BronzeBright)
                }
                state.offlineRegions.forEach { region ->
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Text(region.name, style = MaterialTheme.typography.titleMedium, color = HuntColors.Cream)
                        Text(
                            "${region.status.name} · ${"%.0f".format(region.sizeBytes / (1024.0 * 1024.0))} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = HuntColors.Mist,
                        )
                        if (region.status != OfflineMapStatus.DOWNLOADING) {
                            TextButton(onClick = { onDeleteRegion(region.id) }) {
                                Text("Delete", color = HuntColors.Danger)
                            }
                        }
                    }
                }
            }
        }
    }

    state.selectedMarker?.let { marker ->
        ModalBottomSheet(
            onDismissRequest = {
                onSelectMarker(null)
                removeSection(MapOverlaySection.MARKER)
            },
            containerColor = HuntColors.ForestMid,
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(marker.name, style = MaterialTheme.typography.headlineMedium, color = HuntColors.Cream)
                Text(marker.category.label, style = MaterialTheme.typography.titleMedium, color = HuntColors.Bronze)
                Text(marker.notes.ifBlank { "No notes" }, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${"%.5f".format(marker.latitude)}, ${"%.5f".format(marker.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = HuntColors.Mist,
                )
                if (state.prefs.showOsGridRef) {
                    OsGridRef.latLngToOsGrid(marker.latitude, marker.longitude)?.let { grid ->
                        Text(grid, style = MaterialTheme.typography.titleMedium, color = HuntColors.BronzeBright)
                    }
                }
                Button(
                    onClick = {
                        onQuickPlaceNext(marker)
                        onSelectMarker(null)
                    },
                    enabled = state.gps != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Place next ${marker.category.label} here")
                }
                Text(
                    "Drops another marker at your current GPS — same type, no form.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HuntColors.Mist,
                )
                Button(
                    onClick = {
                        onDeleteMarker()
                        removeSection(MapOverlaySection.MARKER)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = HuntColors.Danger,
                        contentColor = HuntColors.Cream,
                    ),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete this marker")
                }
            }
        }
    }

    FieldGuideSheet(
        visible = showGuide,
        title = "Map training",
        subtitle = "How Home, markers, and wind smoke work. Tap ? anytime to reopen. Turn auto-open on/off in Settings.",
        sections = FieldGuides.map,
        onDismiss = { dismissGuide() },
        confirmLabel = "Close",
    )
}

private data class MapScaleBarState(
    val meters: Double,
    val barWidthPx: Float,
    val label: String,
)

private fun niceScaleDistance(meters: Double): Double {
    if (meters <= 0.0) return 1.0
    if (meters < 1.0) return 1.0
    val exponent = floor(log10(meters)).toInt()
    val base = 10.0.pow(exponent)
    val normalized = meters / base
    val nice = when {
        normalized < 1.5 -> 1.0
        normalized < 3.5 -> 2.0
        normalized < 7.5 -> 5.0
        else -> 10.0
    }
    return nice * base
}

private fun formatMapScaleLabel(meters: Double): String {
    val feet = meters.toFeet()
    val metersLabel = when {
        meters >= 1000.0 -> "${String.format(java.util.Locale.UK, "%.1f", meters / 1000.0)} km"
        meters >= 1.0 -> "${meters.toInt()} m"
        else -> "${(meters * 100.0).toInt()} cm"
    }
    val feetLabel = when {
        feet >= 5280.0 -> "${String.format(java.util.Locale.UK, "%.1f", feet / 5280.0)} mi"
        feet >= 1.0 -> "${feet.toInt()} ft"
        else -> "${(feet * 12.0).toInt()} in"
    }
    return "$metersLabel · $feetLabel"
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MapScaleBarOverlay(
    mapView: MapView,
    mapReady: Boolean,
    cameraTick: Int,
    modifier: Modifier = Modifier,
) {
    val scaleState = remember { mutableStateOf<MapScaleBarState?>(null) }
    val scaleBright = Color(0xFFFFEB3B)

    LaunchedEffect(mapReady, cameraTick) {
        if (!mapReady) {
            scaleState.value = null
            return@LaunchedEffect
        }
        mapView.getMapAsync { map ->
            val center = map.cameraPosition.target ?: return@getMapAsync
            val projection = map.projection
            val centerScreen = projection.toScreenLocation(center)
            val samplePx = ScaleBarSamplePixels
            val offsetScreen = PointF(centerScreen.x + samplePx, centerScreen.y)
            val start = projection.fromScreenLocation(centerScreen)
            val end = projection.fromScreenLocation(offsetScreen)
            val sampleMeters = distanceMeters(start.latitude, start.longitude, end.latitude, end.longitude)
            if (sampleMeters <= 0.0) {
                scaleState.value = null
                return@getMapAsync
            }
            val targetBarPx = ScaleBarTargetPixels
            val rawMeters = sampleMeters * (targetBarPx / samplePx)
            val niceMeters = niceScaleDistance(rawMeters)
            val barWidthPx = (niceMeters / sampleMeters * samplePx).toFloat().coerceIn(36f, 140f)
            scaleState.value = MapScaleBarState(
                meters = niceMeters,
                barWidthPx = barWidthPx,
                label = formatMapScaleLabel(niceMeters),
            )
        }
    }

    val state = scaleState.value ?: return
    val density = LocalDensity.current
    val barWidth = with(density) { state.barWidthPx.toDp() }

    Column(
        modifier = modifier
            .background(HuntColors.Ink.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .pointerInteropFilter { false },
    ) {
        Text(
            state.label,
            style = MaterialTheme.typography.labelSmall,
            color = scaleBright,
        )
        Spacer(Modifier.height(3.dp))
        Canvas(
            modifier = Modifier
                .width(barWidth)
                .height(6.dp),
        ) {
            val cap = 2.5f
            drawLine(
                color = scaleBright,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = scaleBright,
                start = Offset(0f, 0f),
                end = Offset(0f, size.height),
                strokeWidth = cap,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = scaleBright,
                start = Offset(size.width, 0f),
                end = Offset(size.width, size.height),
                strokeWidth = cap,
                cap = StrokeCap.Round,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BoundaryDrawStrokeOverlay(
    mapView: MapView,
    mapReady: Boolean,
    cameraTick: Int,
    drawCoords: List<Pair<Double, Double>>,
) {
    val screenPoints = remember { mutableStateOf<List<Offset>>(emptyList()) }

    LaunchedEffect(mapReady, cameraTick, drawCoords) {
        if (!mapReady || drawCoords.size < 2) {
            screenPoints.value = emptyList()
            return@LaunchedEffect
        }
        mapView.getMapAsync { map ->
            val projection = map.projection
            screenPoints.value = drawCoords.mapNotNull { (lat, lng) ->
                val screen = projection.toScreenLocation(LatLng(lat, lng))
                if (screen.x.isFinite() && screen.y.isFinite()) {
                    Offset(screen.x, screen.y)
                } else {
                    null
                }
            }
        }
    }

    if (screenPoints.value.size < 2) return

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInteropFilter { false },
    ) {
        val points = screenPoints.value
        for (index in 0 until points.lastIndex) {
            drawLine(
                color = FenceLineComposeColor,
                start = points[index],
                end = points[index + 1],
                strokeWidth = FenceLineWidth * 2f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun MapMarkerOverlay(
    mapView: MapView,
    mapReady: Boolean,
    cameraTick: Int,
    markers: List<FieldMarker>,
) {
    val markerPositions = remember { mutableStateMapOf<Long, Pair<Float, Float>>() }

    LaunchedEffect(mapReady, cameraTick, markers.size, markers.lastOrNull()?.id) {
        if (!mapReady || markers.isEmpty()) {
            markerPositions.clear()
            return@LaunchedEffect
        }
        mapView.getMapAsync { map ->
            val projection = map.projection
            val liveIds = markers.map { it.id }.toSet()
            markerPositions.keys.retainAll(liveIds)
            markers.forEach { marker ->
                val screen = projection.toScreenLocation(LatLng(marker.latitude, marker.longitude))
                if (screen.x.isFinite() && screen.y.isFinite()) {
                    markerPositions[marker.id] = screen.x to screen.y
                }
            }
        }
    }

    for (marker in markers) {
        val pos = markerPositions[marker.id] ?: continue
        key(marker.id) {
            val (x, y) = pos
            val iconRes = MarkerIconResolver.iconFor(marker.category, marker.speciesId)
            val isCaller = marker.category == MarkerCategory.CALLER
            val pulseProgress = if (isCaller) {
                val transition = rememberInfiniteTransition(label = "caller-pulse-${marker.id}")
                val p by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1600, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "caller-pulse-progress",
                )
                p
            } else {
                0f
            }
            Box(
                modifier = Modifier
                    .absoluteOffset { IntOffset(x.toInt() - 24, y.toInt() - 52) }
                    .size(48.dp),
            ) {
                if (isCaller) {
                    // Expanding sound rings behind the pin
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(56.dp),
                    ) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        listOf(0f, 0.33f, 0.66f).forEach { phaseOffset ->
                            val t = ((pulseProgress + phaseOffset) % 1f)
                            val radius = size.minDimension * (0.22f + 0.38f * t)
                            val alpha = (1f - t) * 0.45f
                            drawCircle(
                                color = HuntColors.BronzeBright.copy(alpha = alpha),
                                radius = radius,
                                center = Offset(cx, cy),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()),
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(HuntColors.Bronze)
                        .border(2.dp, HuntColors.Ink, CircleShape),
                )
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = marker.name,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(42.dp),
                )
            }
            Text(
                marker.name,
                modifier = Modifier.absoluteOffset { IntOffset(x.toInt() - 72, y.toInt() + 2) },
                style = MaterialTheme.typography.labelSmall,
                color = HuntColors.Cream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OverlayToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = HuntColors.Cream)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


private const val AbsoluteMaxZoomCap = 25.5
/** Allow world / globe view; MapLibre treats 0 as full Earth. */
private const val MinZoomPreference = 0.0
private const val DefaultMapLatitude = 54.0
/** Scale bar geometry — keep in sync with [MapScaleBarOverlay] and [scaleBarRawMeters]. */
private const val ScaleBarSamplePixels = 80f
private const val ScaleBarTargetPixels = 88f
/** Raw scale-bar metres above this show as 2 m after [niceScaleDistance] rounding. */
private const val ScaleBarOneMeterThreshold = 1.45
private const val MarkerHitRadiusMeters = 55.0
private const val FenceLineColor = "#1A3A6E"
/** Saved offline pack border: thin blue line with yellow dashes. */
private const val OfflineRegionBlue = "#2E86DE"
private const val OfflineRegionYellow = "#FFD54F"
private const val FocusBoundaryLineColor = "#E2B05C"
private const val FenceLineWidth = 2.5f
private val FenceLineComposeColor = Color(0xFF1A3A6E)

private fun oneMeterPinchZoom(latitude: Double): Double {
    val targetMetersPerPixel = (ScaleBarOneMeterThreshold * 0.88) / ScaleBarTargetPixels
    return zoomLevelForMetersPerPixel(latitude, targetMetersPerPixel)
        .coerceIn(MinZoomPreference, AbsoluteMaxZoomCap)
}

private fun applyZoomLimits(map: MapLibreMap, latitude: Double = DefaultMapLatitude) {
    val maxZoom = oneMeterPinchZoom(latitude)
    map.setMaxZoomPreference(maxZoom)
    map.setMinZoomPreference(MinZoomPreference)
    map.style?.let { ensureBasemapOverzoom(it, maxZoom) }
}

/**
 * Allow deep pinch on our raster basemap only.
 * Do NOT rewrite min/max zoom on every OpenFreeMap vector layer — that can blank the map
 * on some OEMs (Honor) while overlays like OS grid still draw.
 */
private fun ensureBasemapOverzoom(style: Style, maxZoom: Double = AbsoluteMaxZoomCap) {
    val maxF = maxZoom.toFloat()
    val minF = MinZoomPreference.toFloat()
    style.getLayer("basemap-layer")?.let { layer ->
        runCatching { layer.setMinZoom(minF) }
        runCatching { layer.setMaxZoom(maxF) }
    }
    (style.getSource("basemap") as? RasterSource)?.setMaxOverscaleFactorForParentTiles(16)
}

private fun scaleBarRawMeters(map: MapLibreMap, center: LatLng): Double {
    val projection = map.projection
    val centerScreen = projection.toScreenLocation(center)
    val offsetScreen = PointF(centerScreen.x + ScaleBarSamplePixels, centerScreen.y)
    val start = projection.fromScreenLocation(centerScreen)
    val end = projection.fromScreenLocation(offsetScreen)
    val sampleMeters = distanceMeters(start.latitude, start.longitude, end.latitude, end.longitude)
    if (sampleMeters <= 0.0) return Double.MAX_VALUE
    return sampleMeters * (ScaleBarTargetPixels / ScaleBarSamplePixels)
}

private fun resolvedMaxZoom(map: MapLibreMap, latitude: Double): Double {
    applyZoomLimits(map, latitude)
    return oneMeterPinchZoom(latitude)
}

private fun zoomLevelForMetersPerPixel(latitude: Double, metersPerPixel: Double): Double {
    if (metersPerPixel <= 0.0) return AbsoluteMaxZoomCap
    val circumference = 40075016.686 * cos(Math.toRadians(latitude))
    val scale = circumference / (512.0 * metersPerPixel)
    return (ln(scale) / ln(2.0)).coerceIn(MinZoomPreference, AbsoluteMaxZoomCap)
}

private fun effectiveSyncZoom(map: MapLibreMap, latitude: Double): Double {
    val maxZoom = resolvedMaxZoom(map, latitude)
    val targetMetersPerPixel = ScaleBarOneMeterThreshold / ScaleBarTargetPixels
    val formulaZoom = zoomLevelForMetersPerPixel(latitude, targetMetersPerPixel)
    return minOf(maxZoom, formulaZoom).coerceIn(MinZoomPreference, maxZoom)
}

private fun syncMapToUser(map: MapLibreMap, fix: GpsFix) {
    val style = map.style ?: return
    ensureBasemapOverzoom(style, oneMeterPinchZoom(fix.latitude))
    applyZoomLimits(map, fix.latitude)

    val target = LatLng(fix.latitude, fix.longitude)
    var targetZoom = effectiveSyncZoom(map, fix.latitude)

    fun cameraUpdate(zoom: Double) = CameraUpdateFactory.newCameraPosition(
        CameraPosition.Builder()
            .target(target)
            .zoom(zoom)
            .bearing(map.cameraPosition.bearing)
            .tilt(0.0)
            .build(),
    )

    // animateCamera is more reliable than moveCamera on some OEM GL stacks
    map.animateCamera(cameraUpdate(targetZoom), 450)

    var attempts = 0
    lateinit var verifyListener: MapLibreMap.OnCameraIdleListener
    verifyListener = MapLibreMap.OnCameraIdleListener {
        attempts++
        val pos = map.cameraPosition
        val center = pos.target ?: target
        val maxZoom = resolvedMaxZoom(map, fix.latitude)
        val rawMeters = scaleBarRawMeters(map, center)
        val zoomGap = targetZoom - pos.zoom
        val offMeters = distanceMeters(
            fix.latitude,
            fix.longitude,
            center.latitude,
            center.longitude,
        )

        val needsMoreZoom = rawMeters > ScaleBarOneMeterThreshold &&
            pos.zoom < maxZoom - 0.05 &&
            attempts < 5

        if (needsMoreZoom) {
            val bump = (ln(rawMeters / (ScaleBarOneMeterThreshold * 0.92)) / ln(2.0))
                .coerceIn(0.35, 1.75)
            targetZoom = (pos.zoom + bump).coerceAtMost(maxZoom)
            applyZoomLimits(map, fix.latitude)
            map.moveCamera(cameraUpdate(targetZoom))
            return@OnCameraIdleListener
        }

        if ((zoomGap > 0.2 || offMeters > 2.5) && attempts < 5) {
            applyZoomLimits(map, fix.latitude)
            targetZoom = effectiveSyncZoom(map, fix.latitude)
            map.moveCamera(cameraUpdate(targetZoom))
            return@OnCameraIdleListener
        }

        map.removeOnCameraIdleListener(verifyListener)
    }
    map.addOnCameraIdleListener(verifyListener)
}

private fun centreOnUser(mapView: MapView, map: MapLibreMap?, gps: GpsFix?) {
    val fix = gps ?: return
    // Always go through getMapAsync so we use a live style reference (stale mapLibre can miss sync).
    mapView.getMapAsync { asyncMap ->
        if (asyncMap.style != null) {
            syncMapToUser(asyncMap, fix)
        } else {
            // Style still loading — centre once it appears
            asyncMap.getStyle { _ ->
                syncMapToUser(asyncMap, fix)
            }
        }
    }
}

private fun applyBasemap(
    map: MapLibreMap,
    context: Context,
    layer: MapLayerStyle,
    latitude: Double = DefaultMapLatitude,
    onReady: (Style) -> Unit,
) {
    applyZoomLimits(map, latitude)
    fun finish(style: Style) {
        applyZoomLimits(map, latitude)
        ensureBasemapOverzoom(style, oneMeterPinchZoom(latitude))
        onReady(style)
    }
    when (layer) {
        MapLayerStyle.STREET -> applyOpenFreeMapBasemap(map, context, ::finish)
        MapLayerStyle.OUTDOOR -> applyOpenFreeMapBasemap(map, context) { style ->
            addHillshadeOverlay(style)
            finish(style)
        }
        MapLayerStyle.SATELLITE -> {
            // Esri World Imagery uses z/y/x tile order; refreshed in regional patches, so some
            // areas may be older than others. Online-only — not part of offline packs.
            val json = MapLibreStyleSupport.rasterBasemapStyleJson(
                tileUrls = listOf(
                    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
                ),
                minZoom = 0,
                maxZoom = 19,
                attribution = "Esri, Maxar, Earthstar Geographics, and the GIS User Community",
            )
            map.setStyle(Style.Builder().fromJson(json), ::finish)
        }
    }
}

/**
 * Loads the OpenFreeMap vector basemap — free, no API key, OSM data regenerated weekly, and
 * self-hostable, so it keeps working even if the public instance ever changed.
 *
 * Online the style JSON is fetched and patched with renderer metadata (fixes the
 * black-basemap MapRenderer bug on some OEMs) before loading from JSON. Offline it falls back
 * to the style URL so the style can be served from a region downloaded by OfflineManager.
 */
private fun applyOpenFreeMapBasemap(map: MapLibreMap, context: Context, onReady: (Style) -> Unit) {
    val styleUrl = MapLibreStyleSupport.OPEN_FREEMAP_LIBERTY_URL
    if (!MapLibreStyleSupport.isNetworkAvailable(context)) {
        map.setStyle(styleUrl, onReady)
        return
    }
    Executors.newSingleThreadExecutor().execute {
        val patched = MapLibreStyleSupport.loadPatchedStyleJson(styleUrl)
        Handler(Looper.getMainLooper()).post {
            if (patched != null) {
                map.setStyle(Style.Builder().fromJson(patched), onReady)
            } else {
                map.setStyle(styleUrl, onReady)
            }
        }
    }
}

/** Adds keyless Esri hillshade as a subtle terrain layer beneath the vector map. */
private fun addHillshadeOverlay(style: Style) {
    val sourceId = "hillshade-source"
    val layerId = "hillshade-layer"
    if (style.getSource(sourceId) != null || style.getLayer(layerId) != null) return
    val tileSet = TileSet("2.2.0", MapLibreStyleSupport.ESRI_HILLSHADE_TILE_URL)
    tileSet.attribution = "Sources: Esri, HERE, Garmin, USGS, NGA"
    style.addSource(RasterSource(sourceId, tileSet, 256))
    val layer = RasterLayer(layerId, sourceId).withProperties(
        PropertyFactory.rasterOpacity(0.45f),
    )
    val below = style.getLayer("natural_earth") ?: style.getLayer("background")
    if (below != null) {
        style.addLayerBelow(layer, below.id)
    } else {
        style.addLayerAt(layer, 1)
    }
}

private fun ensureSources(style: Style, context: Context) {
    addStyleIcon(style, context, "user-person", R.drawable.ic_map_hunter, 96, 112)
    addStyleIcon(style, context, "deer-waypoint", R.drawable.ic_deer_head, 96, 96)
    addStyleIcon(style, context, "nav-waypoint", R.drawable.ic_nav_pin, 96, 96)
    addStyleIcon(style, context, "homing-pin", R.drawable.ic_homing_pin, 96, 96)

    // Smoke first so tracks, markers, and GPS sit above it
    if (style.getSource("wind-smoke-source") == null) {
        style.addSource(GeoJsonSource("wind-smoke-source", FeatureCollection.fromFeatures(emptyArray())))
    }
    style.getLayer("wind-smoke-fill")?.let { legacy ->
        runCatching { style.removeLayer(legacy) }
    }
    style.getLayer("wind-smoke-spine")?.let { legacy ->
        runCatching { style.removeLayer(legacy) }
    }
    style.getLayer("wind-smoke-spine-layer")?.let { legacy ->
        runCatching { style.removeLayer(legacy) }
    }
    style.getSource("wind-smoke-spine-source")?.let { legacy ->
        runCatching { style.removeSource(legacy) }
    }
    if (style.getLayer("wind-smoke-layer") == null && style.getSource("wind-smoke-source") != null) {
        style.addLayer(
            CircleLayer("wind-smoke-layer", "wind-smoke-source").withProperties(
                PropertyFactory.circleRadius(Expression.get("r")),
                PropertyFactory.circleColor("#E8B4D4"),
                PropertyFactory.circleOpacity(Expression.get("o")),
                PropertyFactory.circleBlur(0.88f),
                PropertyFactory.circlePitchAlignment("viewport"),
            ),
        )
    }
    if (style.getSource("boundary-source") == null) {
        style.addSource(GeoJsonSource("boundary-source", FeatureCollection.fromFeatures(emptyArray())))
    }
    if (style.getLayer("boundary-fill") == null && style.getSource("boundary-source") != null) {
        style.addLayer(
            FillLayer("boundary-fill", "boundary-source").withProperties(
                PropertyFactory.fillColor(Expression.get("fill")),
                PropertyFactory.fillOpacity(
                    Expression.coalesce(
                        Expression.get("fillOpacity"),
                        Expression.literal(0.08),
                    ),
                ),
            ),
        )
    }
    if (style.getLayer("boundary-line") == null && style.getSource("boundary-source") != null) {
        style.addLayer(
            LineLayer("boundary-line", "boundary-source").withProperties(
                PropertyFactory.lineColor(Expression.coalesce(Expression.get("line"), Expression.literal(FenceLineColor))),
                PropertyFactory.lineWidth(Expression.coalesce(Expression.get("lineWidth"), Expression.literal(FenceLineWidth))),
                PropertyFactory.lineOpacity(Expression.coalesce(Expression.get("lineOpacity"), Expression.literal(0.95))),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            ),
        )
    }
    style.getLayer("boundary-line")?.setProperties(
        PropertyFactory.lineColor(Expression.coalesce(Expression.get("line"), Expression.literal(FenceLineColor))),
        PropertyFactory.lineWidth(Expression.coalesce(Expression.get("lineWidth"), Expression.literal(FenceLineWidth))),
        PropertyFactory.lineOpacity(Expression.coalesce(Expression.get("lineOpacity"), Expression.literal(0.95))),
    )
    if (style.getSource("permission-label-source") == null) {
        style.addSource(GeoJsonSource("permission-label-source", FeatureCollection.fromFeatures(emptyArray())))
    }
    if (style.getLayer("permission-label-layer") == null && style.getSource("permission-label-source") != null) {
        style.addLayer(
            SymbolLayer("permission-label-layer", "permission-label-source").withProperties(
                PropertyFactory.textField(Expression.get("label")),
                PropertyFactory.textSize(14f),
                PropertyFactory.textColor("#F5EED8"),
                PropertyFactory.textHaloColor("#0B1A12"),
                PropertyFactory.textHaloWidth(1.5f),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
                PropertyFactory.textAnchor("center"),
            ),
        )
    }
    style.getLayer("boundary-fill")?.setProperties(
        PropertyFactory.fillColor(Expression.get("fill")),
        PropertyFactory.fillOpacity(
            Expression.coalesce(
                Expression.get("fillOpacity"),
                Expression.literal(0.08),
            ),
        ),
    )
    if (style.getSource("boundary-draft-source") == null) {
        style.addSource(GeoJsonSource("boundary-draft-source", FeatureCollection.fromFeatures(emptyArray())))
    }
    if (style.getLayer("boundary-draft-fill") == null && style.getSource("boundary-draft-source") != null) {
        style.addLayer(
            FillLayer("boundary-draft-fill", "boundary-draft-source").withProperties(
                PropertyFactory.fillColor(FenceLineColor),
                PropertyFactory.fillOpacity(0.08f),
            ),
        )
    }
    if (style.getLayer("boundary-draft-line") == null && style.getSource("boundary-draft-source") != null) {
        style.addLayer(
            LineLayer("boundary-draft-line", "boundary-draft-source").withProperties(
                PropertyFactory.lineColor(FenceLineColor),
                PropertyFactory.lineWidth(FenceLineWidth),
                PropertyFactory.lineOpacity(0.95f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            ),
        )
    }
    if (style.getSource("boundary-live-source") == null) {
        style.addSource(GeoJsonSource("boundary-live-source", FeatureCollection.fromFeatures(emptyArray())))
    }
    if (style.getLayer("boundary-live-glow") == null && style.getSource("boundary-live-source") != null) {
        style.addLayer(
            LineLayer("boundary-live-glow", "boundary-live-source").withProperties(
                PropertyFactory.visibility(Property.NONE),
                PropertyFactory.lineColor("#FFFFFF"),
                PropertyFactory.lineWidth(14f),
                PropertyFactory.lineOpacity(0.55f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            ),
        )
    }
    if (style.getLayer("boundary-live-line") == null && style.getSource("boundary-live-source") != null) {
        style.addLayer(
            LineLayer("boundary-live-line", "boundary-live-source").withProperties(
                PropertyFactory.visibility(Property.NONE),
                PropertyFactory.lineColor(FenceLineColor),
                PropertyFactory.lineWidth(FenceLineWidth),
                PropertyFactory.lineOpacity(0.95f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            ),
        )
    }
    if (style.getSource("marker-path-source") == null) {
        style.addSource(GeoJsonSource("marker-path-source", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            LineLayer("marker-path-layer", "marker-path-source").withProperties(
                PropertyFactory.lineColor("#C4A574"),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineOpacity(0.95f),
            ),
        )
    }
    if (style.getSource("offline-regions-source") == null) {
        style.addSource(GeoJsonSource("offline-regions-source", FeatureCollection.fromFeatures(emptyArray())))
    }
    if (style.getLayer("offline-regions-blue") == null && style.getSource("offline-regions-source") != null) {
        style.addLayer(
            LineLayer("offline-regions-blue", "offline-regions-source").withProperties(
                PropertyFactory.visibility(Property.NONE),
                PropertyFactory.lineColor(OfflineRegionBlue),
                PropertyFactory.lineWidth(3.5f),
                PropertyFactory.lineOpacity(0.9f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
            ),
        )
    }
    if (style.getLayer("offline-regions-dash") == null && style.getSource("offline-regions-source") != null) {
        style.addLayer(
            LineLayer("offline-regions-dash", "offline-regions-source").withProperties(
                PropertyFactory.visibility(Property.NONE),
                PropertyFactory.lineColor(OfflineRegionYellow),
                PropertyFactory.lineWidth(2.2f),
                PropertyFactory.lineOpacity(0.95f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
                PropertyFactory.lineDasharray(arrayOf(3.0f, 3.0f)),
            ),
        )
    }
    if (style.getSource("track-source") == null) {
        style.addSource(GeoJsonSource("track-source", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            LineLayer("track-layer", "track-source").withProperties(
                PropertyFactory.lineColor("#5FD0A8"),
                PropertyFactory.lineWidth(4.5f),
                PropertyFactory.lineOpacity(0.95f),
            ),
        )
    }
    if (style.getSource("scent-approach-source") == null) {
        style.addSource(GeoJsonSource("scent-approach-source", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            LineLayer("scent-approach-line", "scent-approach-source").withProperties(
                PropertyFactory.lineColor("#E2B05C"),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineOpacity(0.95f),
                PropertyFactory.lineDasharray(arrayOf(1.5f, 1.2f)),
            ),
        )
        style.addLayer(
            CircleLayer("scent-approach-ends", "scent-approach-source").withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor("#F3C97A"),
                PropertyFactory.circleStrokeColor("#0B1A12"),
                PropertyFactory.circleStrokeWidth(2f),
            ),
        )
        style.addLayer(
            SymbolLayer("scent-approach-label", "scent-approach-source").withProperties(
                PropertyFactory.textField("{label}"),
                PropertyFactory.textSize(12f),
                PropertyFactory.textColor("#F5E6C8"),
                PropertyFactory.textHaloColor("#0B1A12"),
                PropertyFactory.textHaloWidth(1.5f),
                PropertyFactory.textOffset(arrayOf(0f, 1.6f)),
                PropertyFactory.textAllowOverlap(true),
            ),
        )
    }
    // Hunter (user) layers created FIRST so field markers always paint on top of the
    // silhouette — otherwise a marker dropped at your current GPS spot is hidden under it.
    if (style.getSource("user-source") == null) {
        style.addSource(GeoJsonSource("user-source", FeatureCollection.fromFeatures(emptyArray())))
    }
    if (style.getLayer("user-accuracy") == null && style.getSource("user-source") != null) {
        style.addLayer(
            CircleLayer("user-accuracy", "user-source").withProperties(
                PropertyFactory.visibility(Property.NONE),
                PropertyFactory.circleRadius(22f),
                PropertyFactory.circleColor("#E2B05C"),
                PropertyFactory.circleOpacity(0f),
            ),
        )
    }
    if (style.getLayer("user-dot") == null && style.getSource("user-source") != null) {
        style.addLayer(
            CircleLayer("user-dot", "user-source").withProperties(
                PropertyFactory.visibility(Property.NONE),
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor("#F3C97A"),
                PropertyFactory.circleOpacity(0f),
            ),
        )
    }
    if (style.getLayer("user-person-layer") == null && style.getSource("user-source") != null) {
        style.addLayer(
            SymbolLayer("user-person-layer", "user-source").withProperties(
                PropertyFactory.iconImage("user-person"),
                PropertyFactory.iconSize(1.15f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor("bottom"),
                PropertyFactory.iconOffset(arrayOf(0f, 0f)),
            ),
        )
    }

    if (style.getSource("markers-source") == null) {
        style.addSource(GeoJsonSource("markers-source", FeatureCollection.fromFeatures(emptyArray())))
    }
    // Marker icons render in Compose (MapMarkerOverlay) — GeoJSON source kept for paths + taps.
    listOf("markers-icons", "markers-icons-deer", "markers-icons-nav", "markers-layer").forEach { legacyId ->
        style.getLayer(legacyId)?.let { legacy ->
            runCatching { style.removeLayer(legacy) }
        }
    }

    if (style.getSource("homing-line-source") == null) {
        style.addSource(GeoJsonSource("homing-line-source", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            LineLayer("homing-line-layer", "homing-line-source").withProperties(
                PropertyFactory.lineColor("#E84848"),
                PropertyFactory.lineWidth(3.5f),
                PropertyFactory.lineOpacity(0.95f),
            ),
        )
    }
    if (style.getSource("homing-target-source") == null) {
        style.addSource(GeoJsonSource("homing-target-source", FeatureCollection.fromFeatures(emptyArray())))
        style.addLayer(
            SymbolLayer("homing-target-layer", "homing-target-source").withProperties(
                PropertyFactory.iconImage("homing-pin"),
                PropertyFactory.iconSize(0.95f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor("bottom"),
            ),
        )
    }
    bringBoundaryDrawLayersToFront(style)
}

/** Keep fence draft + live stroke above tracks, smoke, and other map overlays. */
private fun bringBoundaryDrawLayersToFront(style: Style) {
    listOf(
        "boundary-draft-fill",
        "boundary-draft-line",
        "boundary-live-glow",
        "boundary-live-line",
    ).forEach { layerId ->
        val layer = style.getLayer(layerId) ?: return@forEach
        runCatching {
            style.removeLayer(layer)
            style.addLayer(layer)
        }
    }
}

/** Rasterise vector drawables reliably for MapLibre style images. */
private fun addStyleIcon(
    style: Style,
    context: Context,
    name: String,
    @DrawableRes resId: Int,
    width: Int,
    height: Int,
) {
    if (style.getImage(name) != null) return
    runCatching {
        val drawable = ContextCompat.getDrawable(context, resId) ?: return
        drawable.setBounds(0, 0, width, height)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.draw(canvas)
        style.addImage(name, bmp)
    }.onFailure {
        runCatching {
            val drawable = ContextCompat.getDrawable(context, resId) ?: return
            style.addImage(name, drawable.toBitmap(width, height))
        }
    }
}

/** Line + pin from hunter GPS to downed-prey retrieve point. */
private fun updateHomingOverlay(style: Style, gps: GpsFix?, target: FieldMarker?) {
    val lineSource = style.getSource("homing-line-source") as? GeoJsonSource
    val targetSource = style.getSource("homing-target-source") as? GeoJsonSource
    if (target == null) {
        lineSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        targetSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        return
    }
    targetSource?.setGeoJson(
        FeatureCollection.fromFeature(
            Feature.fromGeometry(Point.fromLngLat(target.longitude, target.latitude)),
        ),
    )
    if (gps != null) {
        val points = listOf(
            Point.fromLngLat(gps.longitude, gps.latitude),
            Point.fromLngLat(target.longitude, target.latitude),
        )
        lineSource?.setGeoJson(
            FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points))),
        )
    } else {
        lineSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    }
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dLon = Math.toRadians(lon2 - lon1)
    val y = sin(dLon) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

private fun syncMapOverlays(
    style: Style,
    context: Context,
    state: MapUiState,
    showTrack: Boolean,
    showMarkerPath: Boolean,
    showSmoke: Boolean,
    markersOnly: Boolean = false,
) {
    updateMarkers(style, context, state.markers)
    updateMarkerPath(style, state.markers, showMarkerPath)
    updateGoToPin(style, state.goToTarget)
    updateOfflineRegionsOverlay(style, state.offlineRegions)
    if (markersOnly) {
        updateBoundariesOverlay(style, state.boundaries, state.draftBoundaryPoints, state.permissionMapFocus)
        updatePermissionLabelOverlay(style, state.boundaries, state.permissionMapFocus)
        return
    }
    updateGpsTrack(style, state, showTrack)
    updateUserLocation(style, state.gps)
    // Wind smoke is driven by the LaunchedEffect animation loop — avoid resetting phase here.
    updateHomingOverlay(style, state.gps, state.homingTarget)
    updateScentApproach(style, state.scentApproach)
    updateBoundariesOverlay(style, state.boundaries, state.draftBoundaryPoints, state.permissionMapFocus)
    updatePermissionLabelOverlay(style, state.boundaries, state.permissionMapFocus)
}

/** Zoom-dependent OS National Grid spacing (Explorer-style 1 km when close in). */
private fun osGridSpacingForZoom(zoom: Double): Int = when {
    zoom < 7.5 -> 100_000
    zoom < 10.5 -> 10_000
    zoom < 13.5 -> 1_000
    else -> 100
}

private fun updateOsGridOverlay(style: Style, lines: List<OsGridRef.GridLine>) {
    fun ensureSource(id: String) {
        if (style.getSource(id) == null) {
            style.addSource(GeoJsonSource(id, FeatureCollection.fromFeatures(emptyArray())))
        }
    }
    fun ensureLineLayer(layerId: String, sourceId: String, color: String, width: Float, opacity: Float) {
        if (style.getLayer(layerId) == null && style.getSource(sourceId) != null) {
            style.addLayer(
                LineLayer(layerId, sourceId).withProperties(
                    PropertyFactory.lineColor(color),
                    PropertyFactory.lineWidth(width),
                    PropertyFactory.lineOpacity(opacity),
                    PropertyFactory.lineCap("butt"),
                    PropertyFactory.lineJoin("miter"),
                ),
            )
        }
    }
    ensureSource("os-grid-minor-source")
    ensureSource("os-grid-major-source")
    ensureLineLayer("os-grid-minor", "os-grid-minor-source", "#4A90D9", 1.1f, 0.55f)
    ensureLineLayer("os-grid-major", "os-grid-major-source", "#2E6BB0", 2.2f, 0.78f)

    fun toFeatures(subset: List<OsGridRef.GridLine>): Array<Feature> =
        subset.map { line ->
            val points = line.points.map { Point.fromLngLat(it.longitude, it.latitude) }
            Feature.fromGeometry(LineString.fromLngLats(points))
        }.toTypedArray()

    val major = lines.filter { it.major }
    val minor = lines.filterNot { it.major }
    (style.getSource("os-grid-major-source") as? GeoJsonSource)
        ?.setGeoJson(FeatureCollection.fromFeatures(toFeatures(major)))
    (style.getSource("os-grid-minor-source") as? GeoJsonSource)
        ?.setGeoJson(FeatureCollection.fromFeatures(toFeatures(minor)))

    val visibility = if (lines.isEmpty()) Property.NONE else Property.VISIBLE
    style.getLayer("os-grid-minor")?.setProperties(PropertyFactory.visibility(visibility))
    style.getLayer("os-grid-major")?.setProperties(PropertyFactory.visibility(visibility))
}

private fun updateGoToPin(style: Style, target: GoToOsGridTarget?) {
    if (style.getSource("goto-source") == null) {
        style.addSource(GeoJsonSource("goto-source", FeatureCollection.fromFeatures(emptyArray())))
    }
    if (style.getLayer("goto-pin") == null && style.getSource("goto-source") != null) {
        style.addLayer(
            CircleLayer("goto-pin", "goto-source").withProperties(
                PropertyFactory.circleRadius(10f),
                PropertyFactory.circleColor("#E2B05C"),
                PropertyFactory.circleStrokeColor("#1A1A1A"),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleOpacity(0.95f),
            ),
        )
    }
    val features = if (target == null) {
        emptyArray()
    } else {
        arrayOf(Feature.fromGeometry(Point.fromLngLat(target.longitude, target.latitude)))
    }
    (style.getSource("goto-source") as? GeoJsonSource)
        ?.setGeoJson(FeatureCollection.fromFeatures(features))
}

private fun updateMarkers(style: Style, context: Context, markers: List<FieldMarker>) {
    addStyleIcon(style, context, "deer-waypoint", R.drawable.ic_deer_head, 96, 96)
    addStyleIcon(style, context, "nav-waypoint", R.drawable.ic_nav_pin, 96, 96)

    if (style.getSource("markers-source") == null) {
        style.addSource(GeoJsonSource("markers-source", FeatureCollection.fromFeatures(emptyArray())))
    }
    // Marker icons are rendered as Compose overlays (MapMarkerOverlay).
    // Keep the source for tap detection / path lines but hide the circle layer
    // so only the icon is visible — avoids cluttering the map with dots.
    style.getLayer("markers-dots")?.setProperties(
        PropertyFactory.visibility(Property.NONE),
    )

    val features = markers.map { marker ->
        Feature.fromGeometry(Point.fromLngLat(marker.longitude, marker.latitude)).also { f ->
            f.addStringProperty("title", marker.name)
            f.addNumberProperty("id", marker.id.toDouble())
            f.addStringProperty("group", marker.category.group.name)
        }
    }.toTypedArray()
    (style.getSource("markers-source") as? GeoJsonSource)
        ?.setGeoJson(FeatureCollection.fromFeatures(features))
}

/** Blue border with yellow dashes marking every saved offline pack area. */
private fun updateOfflineRegionsOverlay(style: Style, regions: List<OfflineMapRegion>) {
    val source = style.getSource("offline-regions-source") as? GeoJsonSource
    val features = regions.map { region ->
        val nw = Point.fromLngLat(region.west, region.north)
        val ne = Point.fromLngLat(region.east, region.north)
        val se = Point.fromLngLat(region.east, region.south)
        val sw = Point.fromLngLat(region.west, region.south)
        Feature.fromGeometry(LineString.fromLngLats(listOf(nw, ne, se, sw, nw))).also { feature ->
            feature.addStringProperty("name", region.name)
        }
    }.toTypedArray()
    source?.setGeoJson(FeatureCollection.fromFeatures(features))
    val visibility = if (regions.isEmpty()) Property.NONE else Property.VISIBLE
    style.getLayer("offline-regions-blue")?.setProperties(PropertyFactory.visibility(visibility))
    style.getLayer("offline-regions-dash")?.setProperties(PropertyFactory.visibility(visibility))
}

/** Line connecting markers in the order they were placed. */
private fun updateMarkerPath(style: Style, markers: List<FieldMarker>, show: Boolean = true) {
    if (!show) {
        (style.getSource("marker-path-source") as? GeoJsonSource)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        style.getLayer("marker-path-layer")?.setProperties(PropertyFactory.visibility(Property.NONE))
        return
    }
    style.getLayer("marker-path-layer")?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
    val ordered = markers.sortedBy { it.createdAtEpochMs }
    val points = ordered.map { Point.fromLngLat(it.longitude, it.latitude) }
    val collection = if (points.size >= 2) {
        FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points)))
    } else {
        FeatureCollection.fromFeatures(emptyArray())
    }
    (style.getSource("marker-path-source") as? GeoJsonSource)?.setGeoJson(collection)
}

private fun updateGpsTrack(style: Style, state: MapUiState, show: Boolean = true) {
    if (!show) {
        (style.getSource("track-source") as? GeoJsonSource)
            ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        style.getLayer("track-layer")?.setProperties(PropertyFactory.visibility(Property.NONE))
        return
    }
    style.getLayer("track-layer")?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
    val points = state.trackPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
    val collection = if (points.size >= 2) {
        FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points)))
    } else {
        FeatureCollection.fromFeatures(emptyArray())
    }
    (style.getSource("track-source") as? GeoJsonSource)?.setGeoJson(collection)
}

private fun updateUserLocation(style: Style, gps: GpsFix?) {
    // No yellow disc under the hunter — silhouette only
    style.getLayer("user-accuracy")?.setProperties(PropertyFactory.visibility(Property.NONE))
    style.getLayer("user-dot")?.setProperties(PropertyFactory.visibility(Property.NONE))
    style.getLayer("user-person-layer")?.setProperties(
        PropertyFactory.visibility(Property.VISIBLE),
        PropertyFactory.iconImage("user-person"),
        PropertyFactory.iconSize(1.15f),
        PropertyFactory.iconAllowOverlap(true),
        PropertyFactory.iconIgnorePlacement(true),
        PropertyFactory.iconAnchor("bottom"),
        PropertyFactory.iconOffset(arrayOf(0f, 0f)),
    )
    val collection = if (gps != null) {
        FeatureCollection.fromFeature(
            Feature.fromGeometry(Point.fromLngLat(gps.longitude, gps.latitude)),
        )
    } else {
        FeatureCollection.fromFeatures(emptyArray())
    }
    (style.getSource("user-source") as? GeoJsonSource)?.setGeoJson(collection)
}

/** Orange dashed line: start downwind of target → walk into the wind to the marker. */
private fun updateScentApproach(style: Style, plan: ScentApproachPlan?) {
    val source = style.getSource("scent-approach-source") as? GeoJsonSource ?: return
    val lineLayer = style.getLayer("scent-approach-line")
    val endsLayer = style.getLayer("scent-approach-ends")
    val labelLayer = style.getLayer("scent-approach-label")
    if (plan == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        lineLayer?.setProperties(PropertyFactory.visibility(Property.NONE))
        endsLayer?.setProperties(PropertyFactory.visibility(Property.NONE))
        labelLayer?.setProperties(PropertyFactory.visibility(Property.NONE))
        return
    }
    lineLayer?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
    endsLayer?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
    labelLayer?.setProperties(PropertyFactory.visibility(Property.VISIBLE))

    val start = Point.fromLngLat(plan.startLng, plan.startLat)
    val end = Point.fromLngLat(plan.targetLng, plan.targetLat)
    val midLat = (plan.startLat + plan.targetLat) / 2.0
    val midLng = (plan.startLng + plan.targetLng) / 2.0
    val line = Feature.fromGeometry(LineString.fromLngLats(listOf(start, end)))
    val startDot = Feature.fromGeometry(start).also {
        it.addStringProperty("label", "Start · into wind")
    }
    val endDot = Feature.fromGeometry(end).also {
        it.addStringProperty("label", plan.targetName)
    }
    val midLabel = Feature.fromGeometry(Point.fromLngLat(midLng, midLat)).also {
        it.addStringProperty("label", "Wind on your front →")
    }
    source.setGeoJson(FeatureCollection.fromFeatures(listOf(line, startDot, endDot, midLabel)))
}

/**
 * Scent plume tint — soft magenta steam, readable on satellite and outdoor tiles.
 */
private fun smokeColorForLayer(layer: MapLayerStyle): String = when (layer) {
    MapLayerStyle.SATELLITE -> "#E8B4D4"
    MapLayerStyle.OUTDOOR -> "#E0A8CC"
    MapLayerStyle.STREET -> "#D99AC0"
}

/** Frame pacing — weather-only (zoom must not change smoke character). */
private fun smokeFrameDelayMs(speedKmh: Double?): Long {
    val s = (speedKmh ?: 12.0).coerceIn(0.0, 60.0)
    return when {
        s <= 3.2 -> 52L // light / pooling
        s <= 16.0 -> 44L // steady moderate
        s <= 24.0 -> 40L
        else -> 34L // stiff / wind burn
    }
}

/** How fast each puff travels downwind — weather-only. */
private fun smokePhaseStep(speedKmh: Double?): Float {
    val s = (speedKmh ?: 12.0).coerceIn(0.0, 60.0)
    return when {
        s <= 3.2 -> 0.022f
        s <= 16.0 -> 0.038f
        s <= 24.0 -> 0.048f
        else -> 0.060f
    }
}

private fun clearWindSmokeSpine(style: Style) {
    (style.getSource("wind-smoke-spine-source") as? GeoJsonSource)
        ?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    style.getLayer("wind-smoke-spine-layer")
        ?.setProperties(PropertyFactory.visibility(Property.NONE))
}

/** Bright rubber-band line while the finger is dragging (updated directly on the map). */
private fun updateLiveBoundaryLine(style: Style, coords: List<Pair<Double, Double>>) {
    if (style.getSource("boundary-live-source") == null) return
    if (coords.size < 2) {
        clearLiveBoundaryLine(style)
        return
    }
    val source = style.getSource("boundary-live-source") as GeoJsonSource
    val points = coords.map { (lat, lng) -> Point.fromLngLat(lng, lat) }
    source.setGeoJson(
        FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points))),
    )
    style.getLayer("boundary-live-line")?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
    bringBoundaryDrawLayersToFront(style)
}

private fun clearLiveBoundaryLine(style: Style) {
    val source = style.getSource("boundary-live-source") as? GeoJsonSource ?: return
    source.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
    style.getLayer("boundary-live-glow")?.setProperties(PropertyFactory.visibility(Property.NONE))
    style.getLayer("boundary-live-line")?.setProperties(PropertyFactory.visibility(Property.NONE))
}

/** Saved boundaries + active draft fence line on the map. */
private fun updateBoundariesOverlay(
    style: Style,
    boundaries: List<uk.hunttrack.domain.model.BoundaryArea>,
    draftPoints: List<uk.hunttrack.domain.model.BoundaryPoint>,
    focusItems: List<uk.hunttrack.domain.model.PermissionMapFocusItem> = emptyList(),
) {
    val savedSource = style.getSource("boundary-source") as? GeoJsonSource
    val draftSource = style.getSource("boundary-draft-source") as? GeoJsonSource
    val focusIds = focusItems.map { it.boundaryAreaId }.toSet()
    val savedFeatures = boundaries.flatMap { area ->
        val highlighted = focusIds.isEmpty() || area.id in focusIds
        boundaryMapFeatures(
            points = area.points,
            lineColor = if (highlighted && focusIds.isNotEmpty()) FocusBoundaryLineColor else FenceLineColor,
            fillColor = area.zoneType.mapColorHex(),
            fillOpacity = area.zoneType.mapFillOpacity().toDouble(),
            includeFill = false,
            lineWidth = if (highlighted && focusIds.isNotEmpty()) 3.5f else FenceLineWidth,
            lineOpacity = if (highlighted) 0.98 else 0.28,
        ).toList()
    }.toTypedArray()
    savedSource?.setGeoJson(FeatureCollection.fromFeatures(savedFeatures))

    val draft = draftPoints.sortedBy { it.orderIndex }
    if (draft.size >= 2) {
        val draftFeatures = boundaryMapFeatures(
            points = draft,
            lineColor = FenceLineColor,
            fillColor = FenceLineColor,
            fillOpacity = 0.10,
            includeFill = true,
        )
        draftSource?.setGeoJson(FeatureCollection.fromFeatures(draftFeatures.toList()))
        style.getLayer("boundary-draft-line")?.setProperties(PropertyFactory.visibility(Property.VISIBLE))
        style.getLayer("boundary-draft-fill")?.setProperties(
            if (draft.size >= 3) PropertyFactory.visibility(Property.VISIBLE)
            else PropertyFactory.visibility(Property.NONE),
        )
    } else {
        draftSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        style.getLayer("boundary-draft-line")?.setProperties(PropertyFactory.visibility(Property.NONE))
        style.getLayer("boundary-draft-fill")?.setProperties(PropertyFactory.visibility(Property.NONE))
    }
}

/** LineString for the visible outline; optional Polygon for fill (LineLayer ignores polygons). */
private fun boundaryMapFeatures(
    points: List<uk.hunttrack.domain.model.BoundaryPoint>,
    lineColor: String,
    fillColor: String,
    fillOpacity: Double,
    includeFill: Boolean = true,
    lineWidth: Float = FenceLineWidth,
    lineOpacity: Double = 0.95,
): Array<Feature> {
    val sorted = points.sortedBy { it.orderIndex }
    if (sorted.size < 2) return emptyArray()
    val coords = sorted.map { Point.fromLngLat(it.longitude, it.latitude) }
    val features = mutableListOf<Feature>()
    val outline = if (sorted.size >= 3) {
        coords + coords.first()
    } else {
        coords
    }
    features.add(
        Feature.fromGeometry(LineString.fromLngLats(outline)).also { feature ->
            feature.addStringProperty("line", lineColor)
            feature.addNumberProperty("lineWidth", lineWidth.toDouble())
            feature.addNumberProperty("lineOpacity", lineOpacity)
        },
    )
    if (sorted.size >= 3 && includeFill) {
        features.add(
            Feature.fromGeometry(Polygon.fromLngLats(listOf(outline))).also { feature ->
                feature.addStringProperty("fill", fillColor)
                feature.addNumberProperty("fillOpacity", fillOpacity)
            },
        )
    }
    return features.toTypedArray()
}

private fun updateWindSmoke(
    style: Style,
    gps: GpsFix?,
    windFromDeg: Double?,
    windSpeedKmh: Double?,
    phase: Float,
    showSmoke: Boolean = true,
    mapLayer: MapLayerStyle = MapLayerStyle.OUTDOOR,
    windGustKmh: Double? = null,
    humidityPercent: Int? = null,
    temperatureC: Double? = null,
) {
    val source = style.getSource("wind-smoke-source") as? GeoJsonSource
    val layer = style.getLayer("wind-smoke-layer")
    if (!showSmoke || gps == null) {
        source?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        layer?.setProperties(PropertyFactory.visibility(Property.NONE))
        clearWindSmokeSpine(style)
        return
    }
    val originLat = gps.latitude
    val originLng = gps.longitude
    val windFrom = windFromDeg ?: 270.0
    val downwind = (windFrom + 180.0) % 360.0
    val crosswind = downwind + 90.0
    val plume = ScentPlumeGeometry.forConditions(
        windSpeedKmh,
        windGustKmh,
        humidityPercent,
        temperatureC,
    )
    val plumeLen = plume.lengthM
    val scatter = plume.scatter
    val dilution = plume.dilution
    val color = smokeColorForLayer(mapLayer)

    clearWindSmokeSpine(style)

    val phaseRad = phase * 6.2831855f

    layer?.setProperties(
        PropertyFactory.visibility(Property.VISIBLE),
        PropertyFactory.circleColor(color),
        PropertyFactory.circleBlur(0.88f),
        PropertyFactory.circlePitchAlignment("viewport"),
        PropertyFactory.circleRadius(Expression.get("r")),
        PropertyFactory.circleOpacity(Expression.get("o")),
    )

    // Geographic puffs along the full modelled cone (same at every zoom).
    val particleCount = 36
    val lifeSpan = 0.92f
    val stagger = 1f / particleCount
    val features = buildList {
        repeat(particleCount) { i ->
            val age = (phase + i * stagger) % 1f
            if (age > lifeSpan) return@repeat

            val t = age / lifeSpan
            val dist = 4.0 + t * plumeLen
            val fadeIn = (t / 0.08f).coerceIn(0f, 1f)
            // Dilution: stiff / thermal lift fades sooner along the cone.
            val fadeTail = (0.35 - dilution * 0.25).coerceIn(0.12, 0.35)
            val fadeOut = ((1f - t) / fadeTail.toFloat()).coerceIn(0f, 1f)
            val envelope = fadeIn * fadeOut
            val billow = sin(t * Math.PI).toFloat()

            val halfWidth = ScentPlumeGeometry.halfWidthAt(dist, plume.halfAngleDeg) * scatter
            // Gusty / calm: multi-lane pocket scatter; steady: tight cone.
            val lane = ((i % 5) - 2) / 2.4
            val lateralBase = halfWidth * lane * (0.2 + 0.8 * t) * (0.55 + 0.45 * (scatter - 0.5).coerceIn(0.0, 1.0))
            val wobble = sin(phaseRad * 2.4f + i * 1.65f) * halfWidth * (0.35 + 0.25 * scatter)
            val (lat, lng) = offsetMeters(originLat, originLng, downwind, dist)
            val (lat2, lng2) = offsetMeters(lat, lng, crosswind, lateralBase + wobble)

            val radius = (8f + billow * 22f + (halfWidth * 0.08).toFloat()).coerceIn(4f, 40f)
            val opacity = (
                envelope * billow * (0.78f - dilution.toFloat() * 0.35f) * (1f - t * dilution.toFloat() * 0.5f)
                ).coerceIn(0.08f, 0.62f)
            add(
                Feature.fromGeometry(Point.fromLngLat(lng2, lat2)).also { f ->
                    f.addNumberProperty("r", radius)
                    f.addNumberProperty("o", opacity)
                },
            )
        }
        // Source billow near the hunter — fixed geographic metres (not zoom-scaled).
        repeat(8) { j ->
            val sourceAge = (phase + j * 0.12f) % 1f
            val sourceT = (sourceAge / 0.55f).coerceIn(0f, 1f)
            val sourceDist = 2.0 + sourceT * 18.0
            val (lat, lng) = offsetMeters(originLat, originLng, downwind, sourceDist)
            val sourceHalf = ScentPlumeGeometry.halfWidthAt(sourceDist, plume.halfAngleDeg) * scatter
            val swirl = sin(phaseRad * 3.4f + j * 1.9f) * sourceHalf * 0.45
            val (lat2, lng2) = offsetMeters(lat, lng, crosswind, swirl)
            val puff = 0.45f + 0.55f * sin(phaseRad * 5f + j * 1.2f)
            add(
                Feature.fromGeometry(Point.fromLngLat(lng2, lat2)).also { f ->
                    f.addNumberProperty("r", (9f + puff * 11f).coerceIn(4f, 26f))
                    f.addNumberProperty("o", (0.26f + puff * 0.32f).coerceIn(0.18f, 0.58f))
                },
            )
        }
    }
    source?.setGeoJson(FeatureCollection.fromFeatures(features.toTypedArray()))
}

private fun offsetMeters(lat: Double, lng: Double, bearingDeg: Double, distanceM: Double): Pair<Double, Double> {
    val r = 6371000.0
    val brng = Math.toRadians(bearingDeg)
    val lat1 = Math.toRadians(lat)
    val lon1 = Math.toRadians(lng)
    val angDist = distanceM / r
    val lat2 = asin(sin(lat1) * cos(angDist) + cos(lat1) * sin(angDist) * cos(brng))
    val lon2 = lon1 + atan2(
        sin(brng) * sin(angDist) * cos(lat1),
        cos(angDist) - sin(lat1) * sin(lat2),
    )
    return Math.toDegrees(lat2) to Math.toDegrees(lon2)
}

/** Punch cream/white studio backdrop to alpha for map icon compositing. */
private fun keyOutCreamBackground(source: Bitmap): Bitmap {
    val w = source.width
    val h = source.height
    val out = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
    fun isBg(c: Int): Boolean {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return r >= 235 && g >= 230 && b >= 215 && (r + g + b) >= 700
    }
    val visited = BooleanArray(w * h)
    val queue = ArrayDeque<Int>()
    fun seed(x: Int, y: Int) {
        if (x !in 0 until w || y !in 0 until h) return
        val i = y * w + x
        if (visited[i]) return
        if (!isBg(out.getPixel(x, y))) return
        visited[i] = true
        queue.add(i)
    }
    seed(0, 0)
    seed(w - 1, 0)
    seed(0, h - 1)
    seed(w - 1, h - 1)
    seed(w / 2, 0)
    seed(0, h / 2)
    seed(w - 1, h / 2)
    seed(w / 2, h - 1)
    val dx = intArrayOf(1, -1, 0, 0)
    val dy = intArrayOf(0, 0, 1, -1)
    while (queue.isNotEmpty()) {
        val i = queue.removeFirst()
        val x = i % w
        val y = i / w
        out.setPixel(x, y, 0x00000000)
        for (k in 0..3) {
            val nx = x + dx[k]
            val ny = y + dy[k]
            if (nx !in 0 until w || ny !in 0 until h) continue
            val ni = ny * w + nx
            if (visited[ni]) continue
            if (!isBg(out.getPixel(nx, ny))) continue
            visited[ni] = true
            queue.add(ni)
        }
    }
    return out
}

/** Solid dark chips so cream labels stay readable over pale map tiles. */
@Composable
private fun MapToolAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier.slideRightOnPress(interactionSource),
        enabled = enabled,
        leadingIcon = leadingIcon,
        interactionSource = interactionSource,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = HuntColors.ForestDeep,
            labelColor = HuntColors.Cream,
            leadingIconContentColor = HuntColors.BronzeBright,
            disabledContainerColor = HuntColors.ForestDeep.copy(alpha = 0.55f),
            disabledLabelColor = HuntColors.Mist.copy(alpha = 0.75f),
            disabledLeadingIconContentColor = HuntColors.Mist.copy(alpha = 0.75f),
        ),
        border = BorderStroke(1.dp, HuntColors.Bronze.copy(alpha = 0.75f)),
    )
}

@Composable
private fun MapToolFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.slideRightOnPress(interactionSource),
        enabled = enabled,
        leadingIcon = leadingIcon,
        interactionSource = interactionSource,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = HuntColors.ForestDeep,
            labelColor = HuntColors.Cream,
            iconColor = HuntColors.BronzeBright,
            disabledContainerColor = HuntColors.ForestDeep.copy(alpha = 0.55f),
            disabledLabelColor = HuntColors.Mist.copy(alpha = 0.75f),
            disabledLeadingIconColor = HuntColors.Mist.copy(alpha = 0.75f),
            selectedContainerColor = HuntColors.ForestGreen,
            selectedLabelColor = HuntColors.Cream,
            selectedLeadingIconColor = HuntColors.Gold,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = HuntColors.Bronze.copy(alpha = 0.75f),
            selectedBorderColor = HuntColors.BronzeBright,
            disabledBorderColor = HuntColors.SlateLight.copy(alpha = 0.5f),
            disabledSelectedBorderColor = HuntColors.SlateLight.copy(alpha = 0.5f),
        ),
    )
}

private fun updatePermissionLabelOverlay(
    style: Style,
    boundaries: List<uk.hunttrack.domain.model.BoundaryArea>,
    focusItems: List<uk.hunttrack.domain.model.PermissionMapFocusItem>,
) {
    val source = style.getSource("permission-label-source") as? GeoJsonSource
    val layer = style.getLayer("permission-label-layer")
    if (focusItems.isEmpty()) {
        source?.setGeoJson(FeatureCollection.fromFeatures(emptyArray()))
        layer?.setProperties(PropertyFactory.visibility(Property.NONE))
        return
    }
    val labelByBoundary = focusItems.associate { it.boundaryAreaId to it.label }
    val features = boundaries.mapNotNull { area ->
        val label = labelByBoundary[area.id] ?: return@mapNotNull null
        val centroid = BoundaryMath.centroid(area.points) ?: return@mapNotNull null
        Feature.fromGeometry(Point.fromLngLat(centroid.second, centroid.first)).also {
            it.addStringProperty("label", label)
        }
    }.toTypedArray()
    source?.setGeoJson(FeatureCollection.fromFeatures(features))
    layer?.setProperties(PropertyFactory.visibility(if (features.isEmpty()) Property.NONE else Property.VISIBLE))
}

@Composable
private fun PermissionFocusBanner(
    labels: List<String>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = when (labels.size) {
        1 -> labels.first()
        else -> "${labels.size} permissions"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HuntColors.ForestDeep.copy(alpha = 0.92f))
            .border(1.dp, HuntColors.Bronze.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Permission view", style = MaterialTheme.typography.labelMedium, color = HuntColors.Mist)
            Text(summary, style = MaterialTheme.typography.titleSmall, color = HuntColors.Cream)
            if (labels.size > 1) {
                Text(
                    labels.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = HuntColors.Mist,
                    maxLines = 2,
                )
            }
        }
        TextButton(onClick = onClear) { Text("Clear") }
    }
}

/** Softens basemap tiles to match Night / Hunt UI themes without blocking map gestures. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MapThemeDimOverlay(
    themeMode: AppThemeMode,
    modifier: Modifier = Modifier,
) {
    val overlay = when (themeMode) {
        AppThemeMode.WOODLAND -> null
        AppThemeMode.NIGHT -> Color.Black.copy(alpha = 0.38f)
        AppThemeMode.HUNT -> Color(0xFF120808).copy(alpha = 0.42f)
    } ?: return

    Box(
        modifier = modifier
            .pointerInteropFilter { false }
            .background(overlay),
    )
}
