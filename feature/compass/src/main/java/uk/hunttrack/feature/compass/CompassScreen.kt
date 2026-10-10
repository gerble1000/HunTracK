package uk.hunttrack.feature.compass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.hunttrack.core.common.bearingToCardinal
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.MetricRow
import uk.hunttrack.core.designsystem.components.RegisterScrollStateToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.theme.HuntColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompassRoute(viewModel: CompassViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompassScreen(state)
}

@Composable
fun CompassScreen(state: CompassUiState) {
    val animatedHeading by animateFloatAsState(
        targetValue = state.magneticHeading,
        animationSpec = tween(90),
        label = "heading",
    )

    ScreenBackground(Modifier.fillMaxSize()) {
        val scrollState = rememberScrollState()
        RegisterScrollStateToTop(scrollState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Compass", style = MaterialTheme.typography.headlineLarge, color = HuntColors.Cream)
            when {
                !state.available -> Text("No compass sensors found on this device", color = HuntColors.Danger)
                !state.active -> Text("Starting sensors…", color = HuntColors.Gold)
                state.needsCalibration -> Text("Calibrate: move phone in a figure-eight", color = HuntColors.Gold)
                else -> Text(
                    "Top of phone = direction of travel",
                    color = HuntColors.Mist,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val tip = Path().apply {
                        moveTo(center.x, 6f)
                        lineTo(center.x - 16f, 34f)
                        lineTo(center.x + 16f, 34f)
                        close()
                    }
                    drawPath(tip, HuntColors.BronzeBright)
                }

                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2 * 0.88f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFC9A46A), Color(0xFF7A5A2E), Color(0xFF3E2A12)),
                            center = center,
                            radius = r + 18f,
                        ),
                        radius = r + 16f,
                        center = center,
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFF0E2C4), Color(0xFFD6C39A), Color(0xFFBFA87A)),
                            center = center,
                            radius = r,
                        ),
                        radius = r,
                        center = center,
                    )
                    drawCircle(
                        Color(0xFF2A1A0A).copy(alpha = 0.28f),
                        radius = r,
                        center = center,
                        style = Stroke(3f),
                    )

                    rotate(-animatedHeading, center) {
                        for (deg in 0 until 360 step 5) {
                            val ang = Math.toRadians(deg.toDouble() - 90.0)
                            val major = deg % 30 == 0
                            val outer = r - 6f
                            val inner = if (major) r - 28f else r - 16f
                            val c = if (deg == 0) Color(0xFFB31818) else Color(0xFF2A1A0A)
                            drawLine(
                                c,
                                Offset(
                                    center.x + cos(ang).toFloat() * inner,
                                    center.y + sin(ang).toFloat() * inner,
                                ),
                                Offset(
                                    center.x + cos(ang).toFloat() * outer,
                                    center.y + sin(ang).toFloat() * outer,
                                ),
                                strokeWidth = if (major) 3.5f else 1.5f,
                                cap = StrokeCap.Round,
                            )
                        }
                        val labelPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 34f
                            color = android.graphics.Color.parseColor("#2A1A0A")
                            isFakeBoldText = true
                        }
                        val nPaint = android.graphics.Paint(labelPaint).apply {
                            color = android.graphics.Color.parseColor("#B31818")
                            textSize = 40f
                        }
                        drawContext.canvas.nativeCanvas.apply {
                            fun label(deg: Int, text: String, paint: android.graphics.Paint) {
                                val ang = Math.toRadians(deg.toDouble() - 90.0)
                                val tx = center.x + cos(ang).toFloat() * (r - 48f)
                                val ty = center.y + sin(ang).toFloat() * (r - 48f) + 12f
                                drawText(text, tx, ty, paint)
                            }
                            label(0, "N", nPaint)
                            label(90, "E", labelPaint)
                            label(180, "S", labelPaint)
                            label(270, "W", labelPaint)
                        }
                        drawCircle(
                            Color(0xFF7A5A2E).copy(alpha = 0.45f),
                            radius = r * 0.42f,
                            center = center,
                            style = Stroke(2f),
                        )
                        val needleN = Path().apply {
                            moveTo(center.x, center.y - r + 40f)
                            lineTo(center.x - 12f, center.y + 6f)
                            lineTo(center.x + 12f, center.y + 6f)
                            close()
                        }
                        val needleS = Path().apply {
                            moveTo(center.x, center.y + r - 44f)
                            lineTo(center.x - 10f, center.y - 2f)
                            lineTo(center.x + 10f, center.y - 2f)
                            close()
                        }
                        drawPath(needleN, Color(0xFFC62828))
                        drawPath(needleS, Color(0xFF1A1208))
                        drawCircle(Color(0xFFC9A46A), radius = 14f, center = center)
                        drawCircle(Color(0xFF2A1A0A), radius = 6f, center = center)
                    }
                }
            }

            Text(
                "${animatedHeading.toInt()}° ${bearingToCardinal(animatedHeading.toDouble())}",
                style = MaterialTheme.typography.headlineMedium,
                color = HuntColors.Cream,
            )

            GlassPanel(Modifier.fillMaxWidth()) {
                MetricRow("Magnetic north", "${state.magneticHeading.toInt()}°")
                MetricRow("True north", "${state.trueHeading.toInt()}°")
                MetricRow("Grid north (approx)", "${((state.trueHeading + 0.5f) % 360f).toInt()}°")
                state.gpsHeading?.let { MetricRow("GPS heading", "${it.toInt()}°") }
                    ?: MetricRow("GPS heading", "Walk to acquire")
            }
        }
    }
}
