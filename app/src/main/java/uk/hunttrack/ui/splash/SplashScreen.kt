package uk.hunttrack.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import uk.hunttrack.R
import uk.hunttrack.core.designsystem.sound.HuntSound
import uk.hunttrack.core.designsystem.sound.SoundManager
import kotlin.math.cos
import kotlin.math.sin

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SplashSoundEntryPoint {
    fun soundManager(): SoundManager
}

/** Stag roar only once per process. */
private object SplashRoarGate {
    @Volatile
    var hasPlayed: Boolean = false
}

/** Master brand splash — full-bleed art shown on cold start before the home dashboard. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val soundManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SplashSoundEntryPoint::class.java,
        ).soundManager()
    }
    var visible by remember { mutableStateOf(false) }
    var roared by remember { mutableStateOf(SplashRoarGate.hasPlayed) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "splashFade",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 1.04f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "splashScale",
    )

    LaunchedEffect(Unit) {
        delay(40)
        visible = true
        if (!SplashRoarGate.hasPlayed && !roared) {
            SplashRoarGate.hasPlayed = true
            roared = true
            delay(280)
            soundManager.play(HuntSound.APP_LOADED)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050808))
            .clickable { onFinished() },
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_master),
            contentDescription = "HunTracK",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha },
        ) {
            SplashVintageCompass(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = maxHeight * 0.52f, end = 10.dp)
                    .size(92.dp),
            )
        }
    }
}

/** Antique brass pocket-compass — patina dial, wind rose, classic needle. */
@Composable
private fun SplashVintageCompass(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f * 0.86f
        val center = this.center

        // Outer worn brass bezel
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF8B6914), Color(0xFF4A3520), Color(0xFF2A1E10)),
                center = center,
                radius = r + 18f,
            ),
            radius = r + 16f,
            center = center,
        )
        drawCircle(
            Color(0xFF1A1208).copy(alpha = 0.55f),
            radius = r + 16f,
            center = center,
            style = Stroke(3f),
        )
        drawCircle(
            Color(0xFFD4AF6A).copy(alpha = 0.35f),
            radius = r + 11f,
            center = center,
            style = Stroke(1.2f),
        )

        // Aged ivory dial
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF2E8D0), Color(0xFFD8C8A8), Color(0xFFC4B090)),
                center = Offset(center.x - r * 0.12f, center.y - r * 0.15f),
                radius = r * 1.05f,
            ),
            radius = r,
            center = center,
        )
        drawCircle(
            Color(0xFF3D2E18).copy(alpha = 0.28f),
            radius = r,
            center = center,
            style = Stroke(2f),
        )

        // Inner compass ring
        drawCircle(
            Color(0xFF6B4E28).copy(alpha = 0.45f),
            radius = r * 0.72f,
            center = center,
            style = Stroke(1.5f),
        )

        // Sixteen-point wind rose (antique style)
        for (i in 0 until 16) {
            val deg = i * 22.5
            val ang = Math.toRadians(deg - 90.0)
            val major = i % 4 == 0
            val mid = i % 2 == 0
            val outer = r - 6f
            val inner = when {
                major -> r - 26f
                mid -> r - 18f
                else -> r - 13f
            }
            val stroke = when {
                major -> 2.4f
                mid -> 1.6f
                else -> 1f
            }
            val tickColor = when {
                deg == 0.0 -> Color(0xFF8B1A1A)
                major -> Color(0xFF2A1A0A)
                else -> Color(0xFF4A3820).copy(alpha = 0.75f)
            }
            drawLine(
                tickColor,
                Offset(center.x + cos(ang).toFloat() * inner, center.y + sin(ang).toFloat() * inner),
                Offset(center.x + cos(ang).toFloat() * outer, center.y + sin(ang).toFloat() * outer),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }

        // Ornate intercardinal triangles
        val spread = Math.toRadians(18.0)
        for (deg in listOf(45, 135, 225, 315)) {
            val ang = Math.toRadians(deg.toDouble() - 90.0)
            val tip = Offset(
                center.x + cos(ang).toFloat() * (r - 30f),
                center.y + sin(ang).toFloat() * (r - 30f),
            )
            val left = Offset(
                center.x + cos(ang + spread).toFloat() * (r - 42f),
                center.y + sin(ang + spread).toFloat() * (r - 42f),
            )
            val right = Offset(
                center.x + cos(ang - spread).toFloat() * (r - 42f),
                center.y + sin(ang - spread).toFloat() * (r - 42f),
            )
            drawPath(
                Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(left.x, left.y)
                    lineTo(right.x, right.y)
                    close()
                },
                Color(0xFF2A1A0A).copy(alpha = 0.55f),
            )
        }

        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
            textSize = 20f
            color = android.graphics.Color.parseColor("#2A1A0A")
        }
        val northPaint = android.graphics.Paint(labelPaint).apply {
            color = android.graphics.Color.parseColor("#8B1A1A")
            textSize = 24f
        }
        drawContext.canvas.nativeCanvas.apply {
            fun label(deg: Int, text: String, paint: android.graphics.Paint) {
                val ang = Math.toRadians(deg.toDouble() - 90.0)
                val tx = center.x + cos(ang).toFloat() * (r - 38f)
                val ty = center.y + sin(ang).toFloat() * (r - 38f) + 7f
                drawText(text, tx, ty, paint)
            }
            label(0, "N", northPaint)
            label(90, "E", labelPaint)
            label(180, "S", labelPaint)
            label(270, "W", labelPaint)
        }

        // Vintage bar needle — north red, south oxidised brass
        val needleNorth = Path().apply {
            moveTo(center.x, center.y - r + 32f)
            lineTo(center.x - 6f, center.y - 2f)
            lineTo(center.x, center.y + 6f)
            lineTo(center.x + 6f, center.y - 2f)
            close()
        }
        val needleSouth = Path().apply {
            moveTo(center.x, center.y + r - 34f)
            lineTo(center.x - 5f, center.y + 2f)
            lineTo(center.x, center.y - 4f)
            lineTo(center.x + 5f, center.y + 2f)
            close()
        }
        drawPath(needleNorth, Color(0xFF9E2A2A))
        drawPath(needleNorth, Color(0xFF5C1515), style = Stroke(1f))
        drawPath(needleSouth, Color(0xFF3A2A14))
        drawPath(needleSouth, Color(0xFF1A1208), style = Stroke(0.8f))

        // Centre jewel & screw cap
        drawCircle(Color(0xFF6B4E28), radius = 12f, center = center)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE8D4A8), Color(0xFF9A7848)),
                center = center,
                radius = 10f,
            ),
            radius = 9f,
            center = center,
        )
        drawCircle(Color(0xFF2A1A0A), radius = 3f, center = center)
        drawLine(
            Color(0xFF2A1A0A).copy(alpha = 0.5f),
            Offset(center.x - 5f, center.y),
            Offset(center.x + 5f, center.y),
            strokeWidth = 0.8f,
        )

        // Glass dome highlight
        drawArc(
            color = Color.White.copy(alpha = 0.12f),
            startAngle = 200f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(center.x - r * 0.75f, center.y - r * 0.85f),
            size = Size(r * 1.5f, r * 1.1f),
            style = Stroke(width = 6f),
        )
    }
}
