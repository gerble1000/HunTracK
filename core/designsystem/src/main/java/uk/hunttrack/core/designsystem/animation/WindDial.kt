package uk.hunttrack.core.designsystem.animation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.hunttrack.core.designsystem.theme.HuntColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Compass-style wind dial: cardinal points, wind-FROM tip, scent plume & classic arrow downwind.
 *
 * @param directionFromDeg meteorological wind direction (degrees the wind comes FROM).
 */
@Composable
fun WindDirectionDial(
    directionFromDeg: Float,
    speedKmh: Double,
    gustKmh: Double?,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
) {
    val heading by animateFloatAsState(
        targetValue = ((directionFromDeg % 360f) + 360f) % 360f,
        animationSpec = tween(420),
        label = "windFrom",
    )
    val gust = gustKmh ?: speedKmh
    val variable = (gust - speedKmh) >= 8.0
    val slow = speedKmh < 10.0
    val durationMs = when {
        speedKmh <= 5.0 -> 4200
        speedKmh <= 15.0 -> 3200
        speedKmh <= 25.0 -> 2400
        speedKmh <= 35.0 -> 1600
        speedKmh <= 45.0 -> 1100
        else -> 750
    }

    val transition = rememberInfiniteTransition(label = "windDial")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
        label = "drift",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            tween((durationMs * 0.65f).toInt().coerceAtLeast(500), easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val r = this.size.minDimension / 2f * 0.92f
            val center = Offset(cx, cy)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFC9A46A), Color(0xFF6E4E24), Color(0xFF2A1A0A)),
                    center = center,
                    radius = r + 14f,
                ),
                radius = r + 12f,
                center = center,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1A3324), Color(0xFF0E1F16), Color(0xFF07140E)),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
            drawCircle(
                HuntColors.Bronze.copy(alpha = 0.55f),
                radius = r,
                center = center,
                style = Stroke(2.5f),
            )
            drawCircle(
                HuntColors.BronzeBright.copy(alpha = 0.25f),
                radius = r * 0.78f,
                center = center,
                style = Stroke(1.2f),
            )

            for (deg in 0 until 360 step 5) {
                val ang = Math.toRadians(deg.toDouble() - 90.0)
                val major = deg % 45 == 0
                val cardinal = deg % 90 == 0
                val outer = r - 8f
                val inner = when {
                    cardinal -> r - 34f
                    major -> r - 24f
                    else -> r - 16f
                }
                val tickColor = when {
                    deg == 0 -> Color(0xFFE05A45)
                    cardinal -> HuntColors.Cream
                    major -> HuntColors.Bronze
                    else -> HuntColors.SlateLight.copy(alpha = 0.75f)
                }
                drawLine(
                    tickColor,
                    Offset(cx + cos(ang).toFloat() * inner, cy + sin(ang).toFloat() * inner),
                    Offset(cx + cos(ang).toFloat() * outer, cy + sin(ang).toFloat() * outer),
                    strokeWidth = if (cardinal) 3.5f else if (major) 2.2f else 1.2f,
                    cap = StrokeCap.Round,
                )
            }

            val labelPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = r * 0.12f
                color = android.graphics.Color.parseColor("#F6F1E6")
                isFakeBoldText = true
            }
            val nPaint = android.graphics.Paint(labelPaint).apply {
                color = android.graphics.Color.parseColor("#E05A45")
                textSize = r * 0.14f
            }
            drawContext.canvas.nativeCanvas.apply {
                fun label(deg: Int, text: String, paint: android.graphics.Paint) {
                    val ang = Math.toRadians(deg.toDouble() - 90.0)
                    val tx = cx + cos(ang).toFloat() * (r - 48f)
                    val ty = cy + sin(ang).toFloat() * (r - 48f) + paint.textSize * 0.35f
                    drawText(text, tx, ty, paint)
                }
                label(0, "N", nPaint)
                label(90, "E", labelPaint)
                label(180, "S", labelPaint)
                label(270, "W", labelPaint)
            }

            val fromRad = Math.toRadians(heading.toDouble() - 90.0)
            val scentDeg = (heading + 180f) % 360f
            val scentRad = Math.toRadians(scentDeg.toDouble() - 90.0)
            val fromDx = cos(fromRad).toFloat()
            val fromDy = sin(fromRad).toFloat()
            val scentDx = cos(scentRad).toFloat()
            val scentDy = sin(scentRad).toFloat()
            val px = -scentDy
            val py = scentDx

            val travel = r * 0.52f
            // Fine drifting threads — many small puffs, light magenta
            val puffCount = if (variable) 48 else if (slow) 32 else 40
            val halfWidth = r * (if (variable) 0.14f else if (slow) 0.06f else 0.09f)
            val smokeTint = Color(0xFFE8B4D4)
            val smokeHighlight = Color(0xFFF5D6EA)
            repeat(puffCount) { i ->
                val phase = (drift + i * 0.022f) % 1f
                val along = phase
                val taper = (1f - along) * (1f - along)
                val lane = ((i % 7) - 3) / 3.4f
                val wobble = if (variable) {
                    sin((drift * 6.28f + i) * 2.4f) * 0.22f
                } else {
                    sin((drift * 6.28f + i * 0.7f)) * 0.08f
                }
                val x = cx + scentDx * travel * (along * 0.95f - 0.08f) +
                    px * halfWidth * lane * taper * (1f + wobble)
                val y = cy + scentDy * travel * (along * 0.95f - 0.08f) +
                    py * halfWidth * lane * taper * (1f + wobble)
                val baseR = (3.2f + (1f - along) * 7.5f) * pulse
                val alpha = (0.38f * (1f - along) * (1f - along) + 0.05f).coerceIn(0.04f, 0.42f)
                drawCircle(smokeTint.copy(alpha = alpha), radius = baseR, center = Offset(x, y))
                drawCircle(
                    smokeHighlight.copy(alpha = alpha * 0.45f),
                    radius = baseR * 0.4f,
                    center = Offset(x - 0.8f, y - 0.8f),
                )
            }

            // Wind comes from — small inward tip
            val fromOuter = Offset(cx + fromDx * (r - 8f), cy + fromDy * (r - 8f))
            val fromInner = Offset(cx + fromDx * (r - 42f), cy + fromDy * (r - 42f))
            val fromPx = -fromDy
            val fromPy = fromDx
            drawPath(
                Path().apply {
                    moveTo(fromInner.x, fromInner.y)
                    lineTo(fromOuter.x + fromPx * 11f, fromOuter.y + fromPy * 11f)
                    lineTo(fromOuter.x - fromPx * 11f, fromOuter.y - fromPy * 11f)
                    close()
                },
                HuntColors.Gold.copy(alpha = 0.85f),
            )

            // Classic arrow (head + shaft + fletching) — points where scent travels
            val dir = Offset(scentDx, scentDy)
            val perp = Offset(px, py)
            val tip = Offset(cx + scentDx * travel * 0.95f, cy + scentDy * travel * 0.95f)
            val arrowLen = travel * 1.15f
            val headLen = arrowLen * 0.34f
            val headHalf = arrowLen * 0.20f
            val shaftHalf = arrowLen * 0.06f
            val shaftBase = tip - dir * arrowLen
            val headBase = tip - dir * headLen
            val fletchJoin = shaftBase + dir * (arrowLen * 0.16f)
            val fletchHalf = arrowLen * 0.16f

            val classicArrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(headBase.x + perp.x * headHalf, headBase.y + perp.y * headHalf)
                lineTo(headBase.x + perp.x * shaftHalf, headBase.y + perp.y * shaftHalf)
                lineTo(fletchJoin.x + perp.x * shaftHalf, fletchJoin.y + perp.y * shaftHalf)
                lineTo(shaftBase.x + perp.x * fletchHalf, shaftBase.y + perp.y * fletchHalf)
                lineTo(shaftBase.x, shaftBase.y)
                lineTo(shaftBase.x - perp.x * fletchHalf, shaftBase.y - perp.y * fletchHalf)
                lineTo(fletchJoin.x - perp.x * shaftHalf, fletchJoin.y - perp.y * shaftHalf)
                lineTo(headBase.x - perp.x * shaftHalf, headBase.y - perp.y * shaftHalf)
                lineTo(headBase.x - perp.x * headHalf, headBase.y - perp.y * headHalf)
                close()
            }
            drawPath(
                classicArrow,
                Brush.linearGradient(
                    colors = listOf(HuntColors.Gold, HuntColors.BronzeBright, HuntColors.Bronze),
                    start = shaftBase,
                    end = tip,
                ),
            )
            drawPath(
                classicArrow,
                HuntColors.Ink.copy(alpha = 0.55f),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round),
            )

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(HuntColors.BronzeBright, HuntColors.Bronze, Color(0xFF3E2A12)),
                    center = center,
                    radius = r * 0.11f,
                ),
                radius = r * 0.09f,
                center = center,
            )

            val fromCue = android.graphics.Paint().apply {
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = r * 0.05f
                color = android.graphics.Color.parseColor("#F0D48A")
                isFakeBoldText = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                "Wind from",
                cx + fromDx * (r - 56f),
                cy + fromDy * (r - 56f) + fromCue.textSize * 0.35f,
                fromCue,
            )
        }
    }
}

/** Vector arithmetic helpers for arrow geometry. */
private operator fun Offset.minus(other: Offset) = Offset(x - other.x, y - other.y)
private operator fun Offset.times(scale: Float) = Offset(x * scale, y * scale)
private operator fun Offset.plus(other: Offset) = Offset(x + other.x, y + other.y)
