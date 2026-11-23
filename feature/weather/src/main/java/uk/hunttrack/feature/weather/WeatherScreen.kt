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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
                            HourCard(
                                hour,
                                state.prefs.useCelsius,
                                state.prefs.windUnit,
                                latitude = state.weather?.latitude ?: 0.0,
                                longitude = state.weather?.longitude ?: 0.0,
                            )
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
    latitude: Double,
    longitude: Double,
) {
    val isDay = Daylight.isSunlitHour(latitude, longitude, hour.epochMs)
    GlassPanel(Modifier.padding(vertical = 4.dp).widthIn(min = 112.dp)) {
        Text(
            HourlyForecastLabels.offsetLabel(hour.epochMs, System.currentTimeMillis()),
            style = MaterialTheme.typography.labelMedium,
        )
        WeatherIcon(
            hour.weatherCode,
            Modifier.size(28.dp).padding(vertical = 4.dp),
            isDay = isDay,
            epochMs = hour.epochMs,
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
) {
    val transition = rememberInfiniteTransition(label = "wx")
    val t by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(modifier) {
        when {
            code >= 95 -> {
                drawCircle(HuntColors.SlateLight, radius = size.minDimension * 0.28f, center = center)
                drawLine(HuntColors.Gold, Offset(center.x, center.y), Offset(center.x + 8f, center.y + 20f), 3f)
            }
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
            !isDay && code in 71..77 || !isDay && code in 85..86 -> {
                repeat(4) { i ->
                    val x = center.x - 8f + (i % 2) * 12f
                    val y = center.y - 6f + (i / 2) * 12f
                    drawCircle(HuntColors.Cream, radius = 2.2f, center = Offset(x, y))
                }
            }
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

