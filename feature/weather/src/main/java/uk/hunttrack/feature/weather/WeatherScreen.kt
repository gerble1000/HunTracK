package uk.hunttrack.feature.weather

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.hunttrack.core.common.bearingToCardinal
import uk.hunttrack.core.common.format1
import uk.hunttrack.core.common.toMph
import uk.hunttrack.core.common.weatherCodeLabel
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.MetricRow
import uk.hunttrack.core.designsystem.components.RegisterLazyListScrollToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.astronomy.MoonPhase
import uk.hunttrack.domain.model.DailyForecast
import uk.hunttrack.domain.model.HourlyForecast
import uk.hunttrack.domain.model.WindUnit
import uk.hunttrack.domain.weather.Daylight
import uk.hunttrack.domain.weather.HourlyForecastLabels
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherRoute(viewModel: WeatherViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh(silent = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    WeatherScreen(state, viewModel::refresh, viewModel::setWindUnit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    state: WeatherUiState,
    onRefresh: () -> Unit,
    onWindUnit: (WindUnit) -> Unit = {},
) {
    ScreenBackground(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        RegisterLazyListScrollToTop(listState)
        PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text("Weather", style = MaterialTheme.typography.headlineLarge)
                    Text("UK forecast · live · Open-Meteo", style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
                    state.weather?.let { w ->
                        Text(
                            "Updated ${relativeAge(w.fetchedAtEpochMs, state.nowEpochMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = HuntColors.Gold,
                        )
                    }
                }

                val alerts = state.weather?.daily?.flatMap { day ->
                    buildList {
                        if (day.frostRisk) add("Frost risk")
                        if (day.fogRisk) add("Fog")
                        if (day.stormRisk) add("Storm alert")
                    }
                }?.distinct().orEmpty()

                if (alerts.isNotEmpty()) {
                    item {
                        GlassPanel(Modifier.fillMaxWidth()) {
                            SectionLabel("Alerts")
                            Text(alerts.joinToString(" · "), color = HuntColors.Gold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Wind speed", style = MaterialTheme.typography.titleSmall, color = HuntColors.Mist)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.prefs.windUnit == WindUnit.KMH,
                                onClick = { onWindUnit(WindUnit.KMH) },
                                label = { Text("km/h") },
                                colors = windUnitChipColors(),
                            )
                            FilterChip(
                                selected = state.prefs.windUnit == WindUnit.MPH,
                                onClick = { onWindUnit(WindUnit.MPH) },
                                label = { Text("mph") },
                                colors = windUnitChipColors(),
                            )
                        }
                    }
                }

                item {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        SectionLabel("Current")
                        val w = state.weather
                        if (w == null) {
                            Text(state.error ?: "Loading…")
                        } else {
                            val nowMs = System.currentTimeMillis()
                            val currentIsDay = Daylight.isDay(
                                apiIsDay = null,
                                latitude = w.latitude,
                                longitude = w.longitude,
                                epochMs = nowMs,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                WeatherIcon(
                                    w.weatherCode,
                                    Modifier.size(56.dp),
                                    isDay = currentIsDay,
                                    epochMs = nowMs,
                                )
                                Column {
                                    Text(weatherCodeLabel(w.weatherCode), style = MaterialTheme.typography.titleLarge)
                                    val temp = if (state.prefs.useCelsius) "${w.temperatureC.format1()}°C" else "${(w.temperatureC * 9 / 5 + 32).format1()}°F"
                                    Text(temp, style = MaterialTheme.typography.displayMedium, color = HuntColors.Bronze)
                                }
                            }
                            MetricRow("Wind", formatWind(w.windSpeedKmh, w.windDirectionDeg, state.prefs.windUnit))
                            MetricRow("Humidity", "${w.humidityPercent}%")
                            MetricRow("Pressure", "${w.pressureHpa.format1()} hPa")
                        }
                    }
                }

                item {
                    SectionLabel("Next 24 hours")
                    val hours = HourlyForecastLabels.upcoming(
                        state.weather?.hourly.orEmpty(),
                        System.currentTimeMillis(),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(hours, key = { it.epochMs }) { hour ->
                            HourCard(hour, state.prefs.useCelsius, state.prefs.windUnit)
                        }
                    }
                }

                item { SectionLabel("Five day") }
                items(state.weather?.daily.orEmpty()) { day ->
                    DayCard(day, state.prefs.useCelsius, state.prefs.windUnit)
                }
            }
        }
    }
}

@Composable
private fun HourCard(
    hour: HourlyForecast,
    useCelsius: Boolean,
    windUnit: WindUnit,
) {
    GlassPanel(Modifier.padding(vertical = 4.dp).widthIn(min = 112.dp)) {
        Text(
            HourlyForecastLabels.offsetLabel(hour.epochMs, System.currentTimeMillis()),
            style = MaterialTheme.typography.labelMedium,
        )
        WeatherIcon(
            hour.weatherCode,
            Modifier.size(32.dp),
            showDayNight = false,
        )
        val temp = if (useCelsius) "${hour.temperatureC.toInt()}°" else "${(hour.temperatureC * 9 / 5 + 32).toInt()}°"
        Text(temp, style = MaterialTheme.typography.titleMedium)
        Text("${hour.rainProbabilityPercent}%", style = MaterialTheme.typography.bodySmall)
        Text(
            formatWindSpeed(hour.windSpeedKmh, windUnit),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            softWrap = false,
        )
        Text(bearingToCardinal(hour.windDirectionDeg), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DayCard(day: DailyForecast, useCelsius: Boolean, windUnit: WindUnit) {
    GlassPanel(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(SimpleDateFormat("EEE d MMM", Locale.UK).format(Date(day.epochMs)), style = MaterialTheme.typography.titleMedium)
        MetricRow("Range", formatTempRange(day.tempMinC, day.tempMaxC, useCelsius))
        MetricRow("Rain", "${day.rainProbabilityPercent}%")
        MetricRow("Wind max", formatWindSpeed(day.windSpeedMaxKmh, windUnit))
        val flags = buildList {
            if (day.frostRisk) add("Frost")
            if (day.fogRisk) add("Fog")
            if (day.stormRisk) add("Storm")
        }
        if (flags.isNotEmpty()) {
            Text(flags.joinToString(" · "), color = HuntColors.Gold, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun WeatherIcon(
    code: Int,
    modifier: Modifier = Modifier,
    isDay: Boolean = true,
    epochMs: Long = 0L,
    showDayNight: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "wx")
    val t by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(modifier) {
        if (!showDayNight) {
            drawHourlyCondition(code, t)
            return@Canvas
        }
        when {
            code >= 95 -> {
                drawCircle(HuntColors.SlateLight, radius = size.minDimension * 0.28f, center = center)
                drawLine(HuntColors.Gold, Offset(center.x, center.y), Offset(center.x + 8f, center.y + 20f), 3f)
            }
            !showDayNight && (code in 71..77 || code in 85..86) -> drawSnow()
            code in 61..82 || code in 51..55 -> {
                drawCircle(HuntColors.Mist, radius = size.minDimension * 0.28f, center = Offset(center.x, center.y - 6f))
                repeat(3) { i ->
                    val x = center.x - 10f + i * 10f
                    val y = center.y + 8f + ((t + i * 0.2f) % 1f) * 16f
                    drawLine(HuntColors.Bronze, Offset(x, y), Offset(x, y + 8f), 2f)
                }
            }
            code in 45..48 -> {
                drawCircle(HuntColors.Mist.copy(alpha = 0.4f + 0.3f * t), radius = size.minDimension * 0.4f)
            }
            !showDayNight && code == 0 -> Unit
            !showDayNight && code in 1..3 -> {
                drawCircle(
                    HuntColors.Mist.copy(alpha = if (code == 3) 0.88f else 0.78f),
                    radius = size.minDimension * if (code == 3) 0.28f else 0.18f,
                    center = Offset(center.x + size.minDimension * 0.12f, center.y + size.minDimension * 0.08f),
                )
            }
            !isDay && code in 71..77 || !isDay && code in 85..86 -> drawSnow()
            !isDay && (code == 0 || code in 1..3) -> {
                drawStars()
                val phase = MoonPhase.fraction(epochMs)
                if (MoonPhase.nameOf(phase) != MoonPhase.Name.NEW) {
                    drawPhasedMoon(phase, MoonPhase.illumination(epochMs))
                }
                if (code in 1..3) {
                    drawCircle(
                        HuntColors.Mist.copy(alpha = if (code == 3) 0.88f else 0.78f),
                        radius = size.minDimension * if (code == 3) 0.28f else 0.18f,
                        center = Offset(center.x + size.minDimension * 0.12f, center.y + size.minDimension * 0.08f),
                    )
                }
            }
            else -> {
                drawCircle(HuntColors.Gold, radius = size.minDimension * 0.22f)
                repeat(6) { i ->
                    val ang = (i * 60f + t * 30f) * Math.PI / 180
                    val r = size.minDimension * 0.38f
                    drawLine(
                        HuntColors.Gold,
                        Offset(center.x + kotlin.math.cos(ang).toFloat() * r * 0.6f, center.y + kotlin.math.sin(ang).toFloat() * r * 0.6f),
                        Offset(center.x + kotlin.math.cos(ang).toFloat() * r, center.y + kotlin.math.sin(ang).toFloat() * r),
                        2f,
                    )
                }
            }
        }
    }
}

/** Condition only. No sun, moon or single disc that can be read as either. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHourlyCondition(code: Int, t: Float) {
    when {
        code == 0 -> Unit
        code >= 95 -> {
            drawCloud(topBias = 0.14f)
            drawBolt()
        }
        code in 71..77 || code in 85..86 -> {
            drawCloud(topBias = 0.16f)
            drawSnowfall(t)
        }
        code in 51..67 || code in 80..82 -> {
            drawCloud(topBias = 0.16f)
            drawRainStreaks(t)
        }
        code in 45..48 -> drawFogBands(t)
        else -> drawCloud()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloud(
    topBias: Float = 0f,
    color: Color = HuntColors.Cream,
) {
    val s = size.minDimension
    val cx = center.x
    val cy = center.y - s * topBias
    val puff = s * 0.20f
    drawCircle(color, puff * 0.92f, Offset(cx - puff * 1.15f, cy + puff * 0.22f))
    drawCircle(color, puff * 1.22f, Offset(cx - puff * 0.08f, cy - puff * 0.42f))
    drawCircle(color, puff * 0.98f, Offset(cx + puff * 1.05f, cy + puff * 0.08f))
    drawOval(
        color,
        topLeft = Offset(cx - puff * 1.85f, cy - puff * 0.02f),
        size = Size(puff * 3.7f, puff * 1.15f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRainStreaks(t: Float) {
    val s = size.minDimension
    repeat(3) { i ->
        val x = center.x - s * 0.24f + i * s * 0.24f
        val y = center.y + s * 0.12f + ((t + i * 0.22f) % 1f) * s * 0.20f
        drawLine(HuntColors.BronzeBright, Offset(x, y), Offset(x, y + s * 0.18f), strokeWidth = s * 0.055f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnowfall(t: Float) {
    val s = size.minDimension
    repeat(3) { i ->
        val x = center.x - s * 0.22f + i * s * 0.22f
        val y = center.y + s * 0.16f + ((t * 0.35f + i * 0.2f) % 1f) * s * 0.12f
        drawFlake(Offset(x, y), s * 0.07f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlake(at: Offset, radius: Float) {
    val arm = radius
    val stroke = radius * 0.38f
    drawLine(HuntColors.Cream, Offset(at.x - arm, at.y), Offset(at.x + arm, at.y), stroke)
    drawLine(HuntColors.Cream, Offset(at.x, at.y - arm), Offset(at.x, at.y + arm), stroke)
    val d = arm * 0.72f
    drawLine(HuntColors.Cream, Offset(at.x - d, at.y - d), Offset(at.x + d, at.y + d), stroke)
    drawLine(HuntColors.Cream, Offset(at.x - d, at.y + d), Offset(at.x + d, at.y - d), stroke)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFogBands(t: Float) {
    val s = size.minDimension
    val color = HuntColors.Cream.copy(alpha = 0.55f + 0.25f * t)
    repeat(3) { i ->
        val y = center.y - s * 0.30f + i * s * 0.26f
        val inset = if (i == 1) s * 0.04f else s * 0.12f
        drawRoundRect(
            color,
            topLeft = Offset(center.x - s * 0.44f + inset, y),
            size = Size(s * 0.88f - inset * 2f, s * 0.13f),
            cornerRadius = CornerRadius(s * 0.07f, s * 0.07f),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBolt() {
    val s = size.minDimension
    val x = center.x + s * 0.02f
    val y = center.y + s * 0.02f
    val bolt = Path().apply {
        moveTo(x + s * 0.06f, y)
        lineTo(x - s * 0.10f, y + s * 0.18f)
        lineTo(x + s * 0.02f, y + s * 0.18f)
        lineTo(x - s * 0.08f, y + s * 0.40f)
        lineTo(x + s * 0.16f, y + s * 0.14f)
        lineTo(x + s * 0.04f, y + s * 0.14f)
        close()
    }
    drawPath(bolt, HuntColors.Gold)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnow() {
    repeat(4) { i ->
        val x = center.x - 8f + (i % 2) * 12f
        val y = center.y - 6f + (i / 2) * 12f
        drawCircle(HuntColors.Cream, radius = 2.2f, center = Offset(x, y))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStars() {
    val span = size.minDimension
    listOf(
        -0.55f to -0.48f,
        0.52f to -0.42f,
        0.58f to 0.22f,
        -0.48f to 0.50f,
        0.08f to -0.62f,
    ).forEachIndexed { index, (x, y) ->
        drawCircle(
            HuntColors.Cream,
            radius = span * if (index == 0) 0.045f else 0.032f,
            center = Offset(center.x + x * span * 0.48f, center.y + y * span * 0.48f),
        )
    }
}

/** Northern hemisphere: waxing is lit on the right. Phase 0 is new, 0.5 is full. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPhasedMoon(phase: Double, illumination: Double) {
    val r = size.minDimension * 0.16f
    val wrapped = ((phase % 1.0) + 1.0) % 1.0
    val litRight = wrapped <= 0.5
    val disc = Path().apply {
        addOval(Rect(center.x - r, center.y - r, center.x + r, center.y + r))
    }
    // A straight terminator is readable on a chip a few millimetres wide.
    // The dark fraction follows illumination, with a small floor so a gibbous
    // does not collapse into a featureless disc.
    val darkFraction = ((1.0 - illumination) * 1.35).coerceIn(0.12, 0.92)
    val darkWidth = (darkFraction * 2.0 * r).toFloat()
    clipPath(disc) {
        if (litRight) {
            drawRect(
                HuntColors.Cream,
                topLeft = Offset(center.x - r + darkWidth, center.y - r),
                size = androidx.compose.ui.geometry.Size(2f * r - darkWidth, 2f * r),
            )
        } else {
            drawRect(
                HuntColors.Cream,
                topLeft = Offset(center.x - r, center.y - r),
                size = androidx.compose.ui.geometry.Size(2f * r - darkWidth, 2f * r),
            )
        }
    }
}

private fun formatWind(speedKmh: Double, dir: Double, unit: WindUnit): String {
    return "${formatWindSpeed(speedKmh, unit)} ${bearingToCardinal(dir)}"
}

private fun formatWindSpeed(speedKmh: Double, unit: WindUnit): String {
    return if (unit == WindUnit.MPH) {
        "${speedKmh.toMph().format1()} mph"
    } else {
        "${speedKmh.format1()} km/h"
    }
}

@Composable
private fun windUnitChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = HuntColors.Bronze,
    selectedLabelColor = HuntColors.Ink,
    containerColor = HuntColors.Slate,
    labelColor = HuntColors.Cream,
)

private fun formatTempRange(min: Double, max: Double, useCelsius: Boolean): String {
    return if (useCelsius) {
        "${min.toInt()}° / ${max.toInt()}°C"
    } else {
        "${(min * 9 / 5 + 32).toInt()}° / ${(max * 9 / 5 + 32).toInt()}°F"
    }
}

private fun relativeAge(fetchedAtEpochMs: Long, nowEpochMs: Long): String {
    val mins = ((nowEpochMs - fetchedAtEpochMs).coerceAtLeast(0) / 60_000L).toInt()
    return when {
        mins < 1 -> "just now"
        mins == 1 -> "1 min ago"
        mins < 60 -> "$mins min ago"
        else -> "${mins / 60}h ago"
    }
}

