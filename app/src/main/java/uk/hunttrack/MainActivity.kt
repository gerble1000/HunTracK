package uk.hunttrack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import uk.hunttrack.core.designsystem.sound.SoundManager
import uk.hunttrack.core.data.prefs.PreferencesRepository
import uk.hunttrack.core.designsystem.theme.HuntThemeMode
import uk.hunttrack.core.designsystem.theme.HunTracKTheme
import uk.hunttrack.core.location.LocationRepository
import uk.hunttrack.domain.model.AppThemeMode
import uk.hunttrack.ui.HunTracKRoot
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var soundManager: SoundManager
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var preferencesRepository: PreferencesRepository

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        locationRepository.refreshPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemNavigationBar()
        locationRepository.refreshPermission()
        requestLocationIfNeeded()
        lifecycleScope.launch {
            val prefs = preferencesRepository.preferences.first()
            soundManager.setSoundsEnabled(prefs.soundsEnabled)
            soundManager.setVibrationEnabled(prefs.vibrationEnabled)
        }
        setContent {
            val prefs by preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = uk.hunttrack.domain.model.UserPreferences(),
            )
            LaunchedEffect(prefs.soundsEnabled, prefs.vibrationEnabled) {
                soundManager.setSoundsEnabled(prefs.soundsEnabled)
                soundManager.setVibrationEnabled(prefs.vibrationEnabled)
            }
            HunTracKTheme(themeMode = prefs.themeMode.toHuntTheme()) {
                DisposableEffect(Unit) {
                    onDispose { /* keep sound manager for app lifetime */ }
                }
                HunTracKRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemNavigationBar()
        locationRepository.refreshPermission()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemNavigationBar()
    }

    private fun hideSystemNavigationBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun requestLocationIfNeeded() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
}

private fun AppThemeMode.toHuntTheme(): HuntThemeMode = when (this) {
    AppThemeMode.WOODLAND -> HuntThemeMode.WOODLAND
    AppThemeMode.NIGHT -> HuntThemeMode.NIGHT
    AppThemeMode.HUNT -> HuntThemeMode.HUNT
}
