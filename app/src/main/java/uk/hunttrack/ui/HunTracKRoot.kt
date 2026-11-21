package uk.hunttrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import uk.hunttrack.feature.emergency.DistressMode
import uk.hunttrack.feature.emergency.DistressNotifications
import uk.hunttrack.feature.emergency.EmergencyBeaconOverlay
import uk.hunttrack.ui.splash.SplashScreen
import uk.hunttrack.ui.splash.StudioIntroScreen

private enum class ColdStartPhase {
    Studio,
    Splash,
    App,
}

/** Process-wide: studio intro + splash only on cold start, never when theme remounts. */
private object SplashGate {
    @Volatile
    var hasShownThisProcess: Boolean = false
}

@Composable
fun HunTracKRoot() {
    var phase by remember {
        mutableStateOf(
            if (SplashGate.hasShownThisProcess) ColdStartPhase.App else ColdStartPhase.Studio,
        )
    }

    LaunchedEffect(phase) {
        if (phase != ColdStartPhase.Splash) return@LaunchedEffect
        SplashGate.hasShownThisProcess = true
        delay(6_000)
        phase = ColdStartPhase.App
    }

    AnimatedVisibility(
        visible = phase == ColdStartPhase.Studio,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        StudioIntroScreen(
            onFinished = {
                SplashGate.hasShownThisProcess = true
                phase = ColdStartPhase.Splash
            },
        )
    }
    AnimatedVisibility(
        visible = phase == ColdStartPhase.Splash,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        SplashScreen(
            onFinished = {
                SplashGate.hasShownThisProcess = true
                phase = ColdStartPhase.App
            },
        )
    }
    AnimatedVisibility(
        visible = phase == ColdStartPhase.App,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val beaconVisible by DistressMode.visible.collectAsStateWithLifecycle()
        val context = LocalContext.current
        Box(Modifier.fillMaxSize()) {
            HunTracKAppScaffold(modifier = Modifier.fillMaxSize())
            if (beaconVisible) {
                EmergencyBeaconOverlay(onStop = { DistressNotifications.stop(context) })
            }
        }
    }
}
