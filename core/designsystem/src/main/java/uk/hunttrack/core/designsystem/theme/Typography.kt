package uk.hunttrack.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Expressive outdoor display using serif for brand moments; clean geometric for UI body.
// Do NOT hardcode colors here — Buttons/Chips rely on LocalContentColor for contrast.
val HuntDisplayFont = FontFamily.Serif
val HuntBodyFont = FontFamily.SansSerif

val HuntTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = HuntDisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        letterSpacing = 1.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = HuntDisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        letterSpacing = 0.5.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = HuntDisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = HuntDisplayFont,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = HuntBodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = HuntBodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = HuntBodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = HuntBodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = HuntBodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = HuntBodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = HuntBodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
    ),
)
