package uk.hunttrack.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun HunTracKTheme(
    themeMode: HuntThemeMode = HuntThemeMode.WOODLAND,
    content: @Composable () -> Unit,
) {
    val palette = HuntPalette.forMode(themeMode)
    HuntColors.apply(palette)
    val scheme = darkColorScheme(
        primary = palette.bronze,
        onPrimary = palette.ink,
        primaryContainer = palette.forestGreen,
        onPrimaryContainer = palette.cream,
        secondary = palette.moss,
        onSecondary = palette.ink,
        tertiary = palette.gold,
        onTertiary = palette.ink,
        background = palette.forestDeep,
        onBackground = palette.cream,
        surface = palette.forestMid,
        onSurface = palette.cream,
        surfaceVariant = palette.slate,
        onSurfaceVariant = palette.mist,
        surfaceBright = palette.forestGreen,
        surfaceDim = palette.forestDeep,
        surfaceContainerLowest = palette.forestDeep,
        surfaceContainerLow = palette.forestDeep,
        surfaceContainer = palette.forestMid,
        surfaceContainerHigh = palette.forestGreen,
        surfaceContainerHighest = palette.slate,
        outline = palette.slateLight,
        outlineVariant = palette.slate,
        error = palette.danger,
        onError = Color.White,
        inverseSurface = palette.cream,
        inverseOnSurface = palette.ink,
        inversePrimary = palette.bronze,
    )
    // Avoid key(themeMode) — remounting the UI tree restarts splash & stag roar.
    CompositionLocalProvider(LocalHuntPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HuntTypography,
            content = content,
        )
    }
}
