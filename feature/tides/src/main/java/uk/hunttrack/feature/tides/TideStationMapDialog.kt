package uk.hunttrack.feature.tides

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import uk.hunttrack.core.common.MapLibreStyleSupport
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.HuntButton
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.model.TideStation

private const val STATIONS_SOURCE = "tide-stations-source"
private const val STATIONS_LAYER = "tide-stations-layer"

@Composable
fun TideStationMapDialog(
    visible: Boolean,
    stations: List<TideStation>,
    selectedStationId: String?,
    loading: Boolean,
    onPickStation: (TideStation) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var highlighted by remember(selectedStationId, stations) {
        mutableStateOf(stations.firstOrNull { it.id == selectedStationId })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HuntColors.ForestDeep),
        ) {
            if (loading && stations.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text("Loading UK tide stations…", color = HuntColors.Mist, modifier = Modifier.padding(top = 12.dp))
                }
            } else {
                TideStationMap(
                    stations = stations,
                    selectedStation = highlighted,
                    onSelectStation = {
                        highlighted = it
                        onPickStation(it)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text("UK tide station map", style = MaterialTheme.typography.titleLarge, color = HuntColors.Cream)
                    Text(
                        "Tap a coastal dot to select a tide station. Times refresh daily from TideTimes.org.uk.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Mist,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text(
                        highlighted?.name ?: "Tap a station on the map",
                        style = MaterialTheme.typography.titleMedium,
                        color = HuntColors.Cream,
                    )
                    highlighted?.let {
                        Text(
                            "${it.country} · ${String.format("%.4f", it.latitude)}, ${String.format("%.4f", it.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = HuntColors.Mist,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HuntButton(
                            onClick = onDismiss,
                            text = "Cancel",
                            modifier = Modifier.weight(1f),
                        )
                        HuntButton(
                            onClick = onConfirm,
                            text = "Use station",
                            enabled = highlighted != null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TideStationMap(
    stations: List<TideStation>,
    selectedStation: TideStation?,
    onSelectStation: (TideStation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleRef by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            mapRef = map
            map.uiSettings.apply {
                isAttributionEnabled = false
                isLogoEnabled = false
            }
            map.setMinZoomPreference(4.0)
            map.setMaxZoomPreference(14.0)
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(54.5, -3.5))
                .zoom(5.2)
                .build()
            Executors.newSingleThreadExecutor().execute {
                val patched = MapLibreStyleSupport.loadPatchedStyleJson(
                    MapLibreStyleSupport.OPEN_FREEMAP_LIBERTY_URL,
                )
                Handler(Looper.getMainLooper()).post {
                    val onStyle: (Style) -> Unit = { style ->
                        styleRef = style
                        ensureStationLayers(style)
                        syncStationFeatures(style, stations, selectedStation?.id)
                    }
                    if (patched != null) {
                        map.setStyle(Style.Builder().fromJson(patched), onStyle)
                    } else {
                        map.setStyle(MapLibreStyleSupport.OPEN_FREEMAP_LIBERTY_URL, onStyle)
                    }
                }
            }
            map.addOnMapClickListener { point ->
                val style = styleRef ?: return@addOnMapClickListener false
                val screen = map.projection.toScreenLocation(point)
                val hits = map.queryRenderedFeatures(screen, STATIONS_LAYER)
                val stationId = hits.firstOrNull()?.getStringProperty("stationId")
                val station = stations.firstOrNull { it.id == stationId } ?: return@addOnMapClickListener false
                onSelectStation(station)
                syncStationFeatures(style, stations, station.id)
                map.animateCamera(
                    CameraUpdateFactory.newLatLng(LatLng(station.latitude, station.longitude)),
                    350,
                )
                true
            }
        }
    }

    LaunchedEffect(stations, selectedStation?.id, styleRef) {
        val style = styleRef ?: return@LaunchedEffect
        syncStationFeatures(style, stations, selectedStation?.id)
        selectedStation?.let { station ->
            mapRef?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(station.latitude, station.longitude), 9.0),
                500,
            )
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}

private fun ensureStationLayers(style: Style) {
    if (style.getSource(STATIONS_SOURCE) == null) {
        style.addSource(
            GeoJsonSource(
                STATIONS_SOURCE,
                FeatureCollection.fromFeatures(emptyArray()),
                GeoJsonOptions().withCluster(false),
            ),
        )
    }
    if (style.getLayer(STATIONS_LAYER) == null) {
        style.addLayer(
            CircleLayer(STATIONS_LAYER, STATIONS_SOURCE).withProperties(
                PropertyFactory.circleRadius(
                    org.maplibre.android.style.expressions.Expression.switchCase(
                        org.maplibre.android.style.expressions.Expression.eq(
                            org.maplibre.android.style.expressions.Expression.get("selected"),
                            org.maplibre.android.style.expressions.Expression.literal(true),
                        ),
                        org.maplibre.android.style.expressions.Expression.literal(10f),
                        org.maplibre.android.style.expressions.Expression.literal(6f),
                    ),
                ),
                PropertyFactory.circleColor(
                    org.maplibre.android.style.expressions.Expression.switchCase(
                        org.maplibre.android.style.expressions.Expression.eq(
                            org.maplibre.android.style.expressions.Expression.get("selected"),
                            org.maplibre.android.style.expressions.Expression.literal(true),
                        ),
                        org.maplibre.android.style.expressions.Expression.color(0xFFF3C97A.toInt()),
                        org.maplibre.android.style.expressions.Expression.color(0xFFE2B05C.toInt()),
                    ),
                ),
                PropertyFactory.circleStrokeWidth(1.5f),
                PropertyFactory.circleStrokeColor(0xFFF6F1E6.toInt()),
            ),
        )
    }
}

private fun syncStationFeatures(style: Style, stations: List<TideStation>, selectedId: String?) {
    val source = style.getSource(STATIONS_SOURCE) as? GeoJsonSource ?: return
    val features = stations.map { station ->
        Feature.fromGeometry(Point.fromLngLat(station.longitude, station.latitude)).apply {
            addStringProperty("stationId", station.id)
            addStringProperty("name", station.name)
            addBooleanProperty("selected", station.id == selectedId)
        }
    }.toTypedArray()
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}
