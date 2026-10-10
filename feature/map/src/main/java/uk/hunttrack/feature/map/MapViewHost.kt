package uk.hunttrack.feature.map

import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.maps.MapView
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.theme.HuntColors

/**
 * Creates a MapLibre [MapView] that follows the host [Lifecycle]
 * (not composition-only start/resume) and can be recreated via [retryKey].
 *
 * Default SurfaceView renderer — TextureView hit the same MapRenderer::render
 * null-deref on Honor/Android 16 (missing style renderer metadata).
 */
@Composable
internal fun rememberMapViewWithLifecycle(retryKey: Int = 0): MapView? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember(retryKey) {
        runCatching {
            MapView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // Avoid pure-black MapView clear colour (some OEMs show black if theme bg is black).
                setBackgroundColor(android.graphics.Color.parseColor("#E8E4DC"))
                onCreate(null)
            }
        }.getOrNull()
    } ?: return null

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }
    return mapView
}

@Composable
internal fun MapUnavailablePanel(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Map,
                    contentDescription = null,
                    tint = HuntColors.BronzeBright,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Map unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    color = HuntColors.Cream,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "This device couldn’t open the map. Tap retry to try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HuntColors.Mist,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Retry map")
                }
            }
        }
    }
}

/** Bumps a key so [rememberMapViewWithLifecycle] recreates the native map. */
@Composable
internal fun rememberMapRetryState(): Pair<Int, () -> Unit> {
    var retryKey by remember { mutableIntStateOf(0) }
    return retryKey to { retryKey++ }
}
