package uk.hunttrack.feature.emergency

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import uk.hunttrack.domain.emergency.EmergencyBeacon

private val BeaconRed = Color(0xFFFF0000)
private val BeaconWhite = Color(0xFFFFFFFF)

/** Bright full-screen flash. Stays up until the hunter holds the stop control. */
@Composable
fun EmergencyBeaconOverlay(onStop: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler { }
        BeaconWindowBrightness()
        var red by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(EmergencyBeacon.PHASE_MS)
                red = !red
            }
        }
        val background = if (red) BeaconRed else BeaconWhite
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background),
        ) {
            HoldToStop(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                onStop = onStop,
            )
        }
    }
}

@Composable
private fun BeaconWindowBrightness() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val previous = window?.attributes?.screenBrightness
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val params = window.attributes
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            window.attributes = params
        }
        onDispose {
            if (window != null) {
                val params = window.attributes
                params.screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

@Composable
private fun HoldToStop(modifier: Modifier = Modifier, onStop: () -> Unit) {
    val label = stringResource(R.string.emergency_beacon_hold)
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(holding) {
        if (!holding) {
            progress = 0f
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (holding) {
            withFrameNanos { now ->
                progress = ((now - start) / 1_000_000f / EmergencyBeacon.HOLD_TO_STOP_MS)
                    .coerceIn(0f, 1f)
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    holding = true
                    val up = withTimeoutOrNull(EmergencyBeacon.HOLD_TO_STOP_MS) {
                        waitForUpOrCancellation()
                    }
                    holding = false
                    if (up == null) onStop()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2f
            drawRoundRect(
                color = Color.White,
                cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx()),
            )
            drawArc(
                color = Color.Black,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.titleLarge,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
