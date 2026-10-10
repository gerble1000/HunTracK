package uk.hunttrack.core.designsystem.animation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.hunttrack.core.designsystem.theme.HuntColors
import kotlin.math.cos
import kotlin.math.sin

/** Field GPS fix quality from horizontal accuracy (metres). */
enum class GpsSignalQuality {
    SEEKING,
    BAD,
    FAIR,
    GOOD,
}

fun gpsSignalQuality(accuracyMeters: Float?): GpsSignalQuality = when {
    accuracyMeters == null -> GpsSignalQuality.SEEKING
    accuracyMeters <= 12f -> GpsSignalQuality.GOOD
    accuracyMeters <= 35f -> GpsSignalQuality.FAIR
    else -> GpsSignalQuality.BAD
}

@Composable
fun GpsLockPulse(
    locked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val transition = rememberInfiniteTransition(label = "gps")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Canvas(modifier = modifier.size(size)) {
        val color = if (locked) HuntColors.Success else HuntColors.Bronze
        drawCircle(color = color.copy(alpha = 0.25f * pulse), radius = size.toPx() / 2 * pulse)
        drawCircle(color = color, radius = size.toPx() / 6)
    }
}

/**
 * Compact woodland-style GPS quality chip: glowing bars + GOOD / FAIR / BAD / SEEKING.
 */
@Composable
fun GpsSignalBadge(
    accuracyMeters: Float?,
    modifier: Modifier = Modifier,
) {
    val quality = gpsSignalQuality(accuracyMeters)
    val accent = when (quality) {
        GpsSignalQuality.GOOD -> HuntColors.Success
        GpsSignalQuality.FAIR -> HuntColors.Gold
        GpsSignalQuality.BAD -> HuntColors.Danger
        GpsSignalQuality.SEEKING -> HuntColors.Bronze
    }
    val label = when (quality) {
        GpsSignalQuality.GOOD -> "GOOD"
        GpsSignalQuality.FAIR -> "FAIR"
        GpsSignalQuality.BAD -> "BAD"
        GpsSignalQuality.SEEKING -> "SEEKING"
    }
    val litBars = when (quality) {
        GpsSignalQuality.GOOD -> 3
        GpsSignalQuality.FAIR -> 2
        GpsSignalQuality.BAD -> 1
        GpsSignalQuality.SEEKING -> 0
    }

    val transition = rememberInfiniteTransition(label = "gpsSignal")
    val breath by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (quality) {
                    GpsSignalQuality.GOOD -> 1_600
                    GpsSignalQuality.FAIR -> 1_200
                    GpsSignalQuality.BAD -> 900
                    GpsSignalQuality.SEEKING -> 700
                },
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scan",
    )

    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .border(1.dp, accent.copy(alpha = 0.55f + 0.25f * breath), shape)
            .background(accent.copy(alpha = 0.10f + 0.06f * breath))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(modifier = Modifier.size(width = 22.dp, height = 18.dp)) {
            val barCount = 3
            val gap = size.width * 0.14f
            val barWidth = (size.width - gap * (barCount - 1)) / barCount
            repeat(barCount) { i ->
                val hFrac = (i + 1) / barCount.toFloat()
                val barHeight = size.height * (0.35f + 0.65f * hFrac)
                val left = i * (barWidth + gap)
                val top = size.height - barHeight
                val lit = i < litBars
                val seekingGlow = quality == GpsSignalQuality.SEEKING &&
                    ((scan * 3f).toInt() % 3) == i
                val fill = when {
                    lit -> accent.copy(alpha = 0.55f + 0.45f * breath)
                    seekingGlow -> accent.copy(alpha = 0.55f)
                    else -> HuntColors.Mist.copy(alpha = 0.18f)
                }
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth * 0.45f, barWidth * 0.45f),
                )
                if (lit && quality == GpsSignalQuality.GOOD && i == 2) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.22f * breath),
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight * 0.45f),
                        cornerRadius = CornerRadius(barWidth * 0.45f, barWidth * 0.45f),
                    )
                }
            }
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                text = when (quality) {
                    GpsSignalQuality.SEEKING -> "locking on"
                    GpsSignalQuality.BAD -> "weak fix"
                    GpsSignalQuality.FAIR -> "usable fix"
                    GpsSignalQuality.GOOD -> "solid fix"
                },
                style = MaterialTheme.typography.labelSmall,
                color = HuntColors.Mist.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * Optional radar-style lock ring (left of coords). Softer companion to [GpsSignalBadge].
 */
@Composable
fun GpsSignalRadar(
    accuracyMeters: Float?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val quality = gpsSignalQuality(accuracyMeters)
    val accent = when (quality) {
        GpsSignalQuality.GOOD -> HuntColors.Success
        GpsSignalQuality.FAIR -> HuntColors.Gold
        GpsSignalQuality.BAD -> HuntColors.Danger
        GpsSignalQuality.SEEKING -> HuntColors.Bronze
    }
    val rings = when (quality) {
        GpsSignalQuality.GOOD -> 3
        GpsSignalQuality.FAIR -> 2
        GpsSignalQuality.BAD -> 1
        GpsSignalQuality.SEEKING -> 0
    }
    val transition = rememberInfiniteTransition(label = "gpsRadar")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "radarPulse",
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )

    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val maxR = this.size.minDimension / 2f
        // Soft wash
        drawCircle(
            color = accent.copy(alpha = 0.12f * pulse),
            radius = maxR * pulse,
            center = Offset(cx, cy),
        )
        for (i in 1..3) {
            val active = i <= rings
            drawCircle(
                color = if (active) accent.copy(alpha = 0.35f + 0.25f * pulse)
                else HuntColors.Mist.copy(alpha = 0.14f),
                radius = maxR * (i / 3f) * 0.92f,
                center = Offset(cx, cy),
                style = Stroke(width = if (active) 2.2f else 1.4f),
            )
        }
        if (quality == GpsSignalQuality.SEEKING) {
            val rad = Math.toRadians(sweep.toDouble())
            val tip = Offset(
                cx + cos(rad).toFloat() * maxR * 0.78f,
                cy + sin(rad).toFloat() * maxR * 0.78f,
            )
            drawLine(
                color = accent.copy(alpha = 0.7f),
                start = Offset(cx, cy),
                end = tip,
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(color = accent, radius = maxR * 0.14f, center = Offset(cx, cy))
        drawCircle(
            color = Color.White.copy(alpha = 0.35f),
            radius = maxR * 0.06f,
            center = Offset(cx - maxR * 0.03f, cy - maxR * 0.03f),
        )
    }
}

@Composable
fun FloatingParticles(
    modifier: Modifier = Modifier,
    particleCount: Int = 18,
    color: Color = HuntColors.Cream.copy(alpha = 0.35f),
) {
    val transition = rememberInfiniteTransition(label = "particles")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "t",
    )
    Canvas(modifier = modifier) {
        repeat(particleCount) { i ->
            val seed = i * 37.17f
            val x = (size.width * ((seed % 100) / 100f) + t * size.width * 0.15f) % size.width
            val y = (size.height * (((seed * 1.3f) % 100) / 100f) + sin((t + i) * 6.28f) * 20f + size.height) % size.height
            drawCircle(color = color, radius = 1.5f + (i % 3), center = Offset(x, y))
        }
    }
}

/**
 * Wind visualisation as drifting smoke/cloud.
 * - Slow wind → narrow cloud, slow drift
 * - Fast wind → faster drift
 * - Variable (gusty) → wide turbulent cloud
 * Direction is meteorological (FROM); smoke travels downwind.
 */
@Composable
fun WindSmokeCloud(
    directionFromDeg: Float,
    speedKmh: Double,
    gustKmh: Double?,
    modifier: Modifier = Modifier,
    color: Color = HuntColors.Mist,
) {
    val gust = gustKmh ?: speedKmh
    val variable = (gust - speedKmh) >= 8.0
    val slow = speedKmh < 10.0

    val widthFactor = when {
        variable -> 1.15f
        slow -> 0.35f
        else -> 0.65f
    }
    val durationMs = when {
        speedKmh <= 5.0 -> 4200
        speedKmh <= 15.0 -> 3200
        speedKmh <= 25.0 -> 2400
        speedKmh <= 35.0 -> 1600
        speedKmh <= 45.0 -> 1100
        else -> 750
    }.coerceIn(700, 4500)

    val transition = rememberInfiniteTransition(label = "smoke")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
        label = "drift",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            tween((durationMs * 0.7f).toInt().coerceAtLeast(500), easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Canvas(modifier = modifier) {
        val travelDeg = directionFromDeg + 180f // downwind = scent travel
        val rad = Math.toRadians(travelDeg.toDouble() - 90.0)
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        val px = -dy
        val py = dx

        val cx = size.width / 2f
        val cy = size.height / 2f
        val travel = size.minDimension * 0.62f
        val halfWidth = size.minDimension * 0.28f * widthFactor

        // Strong downwind arrow + “SCENT” cue
        val tip = Offset(cx + dx * travel * 0.55f, cy + dy * travel * 0.55f)
        val tail = Offset(cx - dx * travel * 0.28f, cy - dy * travel * 0.28f)
        drawLine(HuntColors.BronzeBright, tail, tip, strokeWidth = 7f, cap = StrokeCap.Round)
        val arrow = 18f
        drawLine(
            HuntColors.BronzeBright,
            tip,
            Offset(tip.x - dx * arrow + px * 11f, tip.y - dy * arrow + py * 11f),
            strokeWidth = 5f,
            cap = StrokeCap.Round,
        )
        drawLine(
            HuntColors.BronzeBright,
            tip,
            Offset(tip.x - dx * arrow - px * 11f, tip.y - dy * arrow - py * 11f),
            strokeWidth = 5f,
            cap = StrokeCap.Round,
        )

        // Teardrop plume: fine light-magenta threads
        val puffCount = if (variable) 36 else if (slow) 24 else 30
        val smokeTint = Color(0xFFE8B4D4)
        val smokeHighlight = Color(0xFFF5D6EA)
        repeat(puffCount) { i ->
            val phase = (t + i * 0.028f) % 1f
            val along = phase // 0 at hunter → 1 at tip
            val taper = (1f - along) * (1f - along) // teardrop width
            val lane = ((i % 7) - 3) / 3.4f
            val wobble = if (variable) sin((t * 6.28f + i) * 2.1f) * 0.22f else sin((t * 6.28f + i)) * 0.08f
            val x = cx + dx * travel * (along * 0.9f - 0.05f) + px * halfWidth * lane * taper * (1f + wobble)
            val y = cy + dy * travel * (along * 0.9f - 0.05f) + py * halfWidth * lane * taper * (1f + wobble)
            val baseR = (5f + (1f - along) * 10f) * pulse
            val alpha = (0.42f * (1f - along) * (1f - along) + 0.06f).coerceIn(0.05f, 0.48f)
            drawCircle(color = smokeTint.copy(alpha = alpha), radius = baseR, center = Offset(x, y))
            drawCircle(
                color = smokeHighlight.copy(alpha = alpha * 0.4f),
                radius = baseR * 0.4f,
                center = Offset(x - 1f, y - 1f),
            )
        }
    }
}

@Deprecated("Use WindSmokeCloud", ReplaceWith("WindSmokeCloud(directionDeg, speedKmh = 12.0, gustKmh = null, modifier)"))
@Composable
fun WindArrowAnimation(
    directionDeg: Float,
    modifier: Modifier = Modifier,
    color: Color = HuntColors.Bronze,
) {
    WindSmokeCloud(
        directionFromDeg = directionDeg,
        speedKmh = 14.0,
        gustKmh = null,
        modifier = modifier,
        color = color,
    )
}
