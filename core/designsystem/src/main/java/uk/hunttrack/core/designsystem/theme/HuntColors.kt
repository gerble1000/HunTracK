package uk.hunttrack.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class HuntThemeMode {
    WOODLAND,
    NIGHT,
    HUNT,
}

data class HuntPalette(
    val forestDeep: Color,
    val forestMid: Color,
    val forestGreen: Color,
    val moss: Color,
    val charcoal: Color,
    val ink: Color,
    val slate: Color,
    val slateLight: Color,
    val mist: Color,
    val cream: Color,
    val bronze: Color,
    val bronzeBright: Color,
    val gold: Color,
    val danger: Color,
    val success: Color,
    val glass: Color,
    val glassBorder: Color,
) {
    companion object {
        val Woodland = HuntPalette(
            forestDeep = Color(0xFF071A10),
            forestMid = Color(0xFF143726),
            forestGreen = Color(0xFF2A6B44),
            moss = Color(0xFF3F9A5C),
            charcoal = Color(0xFF0E1114),
            ink = Color(0xFF14110C),
            slate = Color(0xFF334048),
            slateLight = Color(0xFF4E5C66),
            mist = Color(0xFFC8D2DA),
            cream = Color(0xFFF6F1E6),
            bronze = Color(0xFFE2B05C),
            bronzeBright = Color(0xFFF3C97A),
            gold = Color(0xFFF0D48A),
            danger = Color(0xFFE05A45),
            success = Color(0xFF45B872),
            glass = Color(0xE01B3A28),
            glassBorder = Color(0x66E2B05C),
        )

        /** Stealth night — near black, cool steel. */
        val Night = HuntPalette(
            forestDeep = Color(0xFF030508),
            forestMid = Color(0xFF0B1018),
            forestGreen = Color(0xFF1A3048),
            moss = Color(0xFF3A6A8A),
            charcoal = Color(0xFF05070A),
            ink = Color(0xFF020304),
            slate = Color(0xFF1E2A38),
            slateLight = Color(0xFF3A4A5C),
            mist = Color(0xFF9AAFC2),
            cream = Color(0xFFE4ECF4),
            bronze = Color(0xFF7EB0D4),
            bronzeBright = Color(0xFFA8D0F0),
            gold = Color(0xFFB8C8D8),
            danger = Color(0xFFE07060),
            success = Color(0xFF4AA888),
            glass = Color(0xE00B1018),
            glassBorder = Color(0x667EB0D4),
        )

        /** Active hunting — deep crimson / night-vision red. */
        val Hunt = HuntPalette(
            forestDeep = Color(0xFF120405),
            forestMid = Color(0xFF2A0A0C),
            forestGreen = Color(0xFF6B1A1E),
            moss = Color(0xFFA02830),
            charcoal = Color(0xFF0C0304),
            ink = Color(0xFF080202),
            slate = Color(0xFF3A1418),
            slateLight = Color(0xFF5C2428),
            mist = Color(0xFFD8B0B4),
            cream = Color(0xFFFFE8E6),
            bronze = Color(0xFFE84848),
            bronzeBright = Color(0xFFFF6A5C),
            gold = Color(0xFFFF8A70),
            danger = Color(0xFFFF3A2A),
            success = Color(0xFFE07050),
            glass = Color(0xE02A0A0C),
            glassBorder = Color(0x66E84848),
        )

        fun forMode(mode: HuntThemeMode): HuntPalette = when (mode) {
            HuntThemeMode.WOODLAND -> Woodland
            HuntThemeMode.NIGHT -> Night
            HuntThemeMode.HUNT -> Hunt
        }
    }
}

val LocalHuntPalette = staticCompositionLocalOf { HuntPalette.Woodland }

/**
 * Theme-aware color tokens. [apply] is called by [HunTracKTheme] before content recomposes.
 */
object HuntColors {
    @Volatile
    private var palette: HuntPalette = HuntPalette.Woodland

    fun apply(next: HuntPalette) {
        palette = next
    }

    val ForestDeep get() = palette.forestDeep
    val ForestMid get() = palette.forestMid
    val ForestGreen get() = palette.forestGreen
    val Moss get() = palette.moss
    val Charcoal get() = palette.charcoal
    val Ink get() = palette.ink
    val Slate get() = palette.slate
    val SlateLight get() = palette.slateLight
    val Mist get() = palette.mist
    val Cream get() = palette.cream
    val Bronze get() = palette.bronze
    val BronzeBright get() = palette.bronzeBright
    val Gold get() = palette.gold
    val Danger get() = palette.danger
    val Success get() = palette.success
    val Glass get() = palette.glass
    val GlassBorder get() = palette.glassBorder
}
