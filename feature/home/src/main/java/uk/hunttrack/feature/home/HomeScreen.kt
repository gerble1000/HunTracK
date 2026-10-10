package uk.hunttrack.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import uk.hunttrack.core.common.bearingToCardinal
import uk.hunttrack.core.common.format1
import uk.hunttrack.core.common.formatCoordinates
import uk.hunttrack.core.common.formatVisibility
import uk.hunttrack.core.common.moonPhaseLabel
import uk.hunttrack.core.common.toMph
import uk.hunttrack.core.common.weatherCodeLabel
import uk.hunttrack.core.designsystem.animation.GpsSignalBadge
import uk.hunttrack.core.designsystem.animation.GpsSignalRadar
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.MetricRow
import uk.hunttrack.core.designsystem.components.RegisterLazyListScrollToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.geo.OsGridRef
import uk.hunttrack.domain.model.ActivityLevel
import uk.hunttrack.domain.model.TideDataSource
import uk.hunttrack.domain.model.TideEventType
import uk.hunttrack.domain.model.WindUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    viewModel: HomeViewModel = hiltViewModel(),
    onOpenEmergency: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh(silent = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    HomeScreen(state = state, onRefresh = viewModel::refresh, onOpenEmergency = onOpenEmergency)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onOpenEmergency: () -> Unit = {},
) {
    ScreenBackground(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        RegisterLazyListScrollToTop(listState)
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text("HunTracK", style = MaterialTheme.typography.headlineLarge, color = HuntColors.Bronze)
                    Text("Field dashboard · live updates", style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
                }

                item {
                    OutlinedButton(
                        onClick = onOpenEmergency,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(2.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = HuntColors.Danger,
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        Text(
                            "IN CASE OF EMERGENCY",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = HuntColors.Danger,
                        )
                    }
                }

                item {
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        SectionLabel("Hunting conditions")
                        val level = state.activity?.level ?: ActivityLevel.MODERATE
                        val color = when (level) {
                            ActivityLevel.HIGH -> HuntColors.Success
                            ActivityLevel.MODERATE -> HuntColors.Gold
                            ActivityLevel.LOW -> HuntColors.Danger
                        }
                        Text(state.activity?.title ?: "Hunting conditions", style = MaterialTheme.typography.titleLarge)
                        Text(level.name, style = MaterialTheme.typography.displayMedium, color = color)
                        Text(
                            "Reassessed every minute · weather every 5 min",
                            style = MaterialTheme.typography.labelMedium,
                            color = HuntColors.Mist,
                        )
                        Spacer(Modifier.height(8.dp))
                        state.activity?.reasons?.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                item {
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        SectionLabel("Weather")
                        val w = state.weather
                        if (w == null) {
                            Text(state.error ?: "Loading weather…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(
                                "Updated ${relativeAge(w.fetchedAtEpochMs, state.nowEpochMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = HuntColors.Gold,
                            )
                            val temp = if (state.prefs.useCelsius) "${w.temperatureC.format1()}°C" else "${(w.temperatureC * 9 / 5 + 32).format1()}°F"
                            val feels = if (state.prefs.useCelsius) "${w.feelsLikeC.format1()}°C" else "${(w.feelsLikeC * 9 / 5 + 32).format1()}°F"
                            val wind = if (state.prefs.windUnit == WindUnit.MPH) {
                                "${w.windSpeedKmh.toMph().format1()} mph ${bearingToCardinal(w.windDirectionDeg)}"
                            } else {
                                "${w.windSpeedKmh.format1()} km/h ${bearingToCardinal(w.windDirectionDeg)}"
                            }
                            Text(weatherCodeLabel(w.weatherCode), style = MaterialTheme.typography.titleLarge)
                            MetricRow("Temperature", temp)
                            MetricRow("Feels like", feels)
                            MetricRow("Humidity", "${w.humidityPercent}%")
                            MetricRow("Pressure", "${w.pressureHpa.format1()} hPa")
                            MetricRow("Wind", wind)
                            w.windGustKmh?.let {
                                val gust = if (state.prefs.windUnit == WindUnit.MPH) "${it.toMph().format1()} mph" else "${it.format1()} km/h"
                                MetricRow("Gusts", gust)
                            }
                            w.rainProbabilityPercent?.let { MetricRow("Rain chance", "$it%") }
                            w.visibilityKm?.let {
                                MetricRow("Visibility", formatVisibility(it, state.prefs.unitSystem))
                            }
                        }
                    }
                }

                if (state.prefs.tidesEnabled) {
                    item {
                        GlassPanel(modifier = Modifier.fillMaxWidth()) {
                            SectionLabel("Tide times")
                            val forecast = state.tideForecast
                            if (forecast == null) {
                                Text(
                                    "Select a port in Settings → UK tide times",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HuntColors.Mist,
                                )
                            } else {
                                Text(
                                    forecast.station.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = HuntColors.Cream,
                                )
                                Text(
                                    when (forecast.source) {
                                        TideDataSource.LIVE -> "Live · TideTimes.org.uk"
                                        TideDataSource.CACHE -> "Cached · TideTimes.org.uk"
                                        TideDataSource.CALCULATED -> "Approximate · offline estimate"
                                        TideDataSource.UNAVAILABLE -> "Unavailable"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HuntColors.Mist,
                                )
                                val upcoming = forecast.events
                                    .filter { it.epochMs >= state.nowEpochMs - 30 * 60 * 1000L }
                                    .take(4)
                                if (upcoming.isEmpty()) {
                                    Text(
                                        "No upcoming tides — pull to refresh",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = HuntColors.Mist,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                } else {
                                    Spacer(Modifier.height(8.dp))
                                    upcoming.forEach { event ->
                                        val label = when (event.type) {
                                            TideEventType.HIGH -> "High water"
                                            TideEventType.LOW -> "Low water"
                                            TideEventType.UNKNOWN -> "Tide"
                                        }
                                        val time = formatTideTime(event.epochMs)
                                        val height = String.format(Locale.UK, "%.1f m", event.heightMeters)
                                        val isNext = event == upcoming.first()
                                        MetricRow(
                                            label = if (isNext) "$label (next)" else label,
                                            value = "$time · $height",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        SectionLabel("Astronomy & sun")
                        val a = state.astronomy
                        if (a == null) {
                            Text("Calculating…", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            MetricRow("Sunrise", formatTime(a.sunriseEpochMs))
                            MetricRow("Sunset", formatTime(a.sunsetEpochMs))
                            MetricRow("Golden hour", formatTime(a.goldenHourStartEpochMs))
                            MetricRow("Fox & rabbit from", formatTime(a.goldenHourEndEpochMs))
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = HuntColors.GlassBorder.copy(alpha = 0.5f),
                            )
                            MetricRow("Moon", moonPhaseLabel(a.moonPhase))
                            MetricRow("Illumination", "${(a.moonIllumination * 100).toInt()}%")
                            MetricRow("Moonrise", formatTime(a.moonriseEpochMs))
                            MetricRow("Moonset", formatTime(a.moonsetEpochMs))
                            Text(
                                "Times use 24-hour clock (local time)",
                                style = MaterialTheme.typography.bodySmall,
                                color = HuntColors.Mist,
                            )
                        }
                    }
                }

                item {
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        val gps = state.gps
                        val osGridForDisplay = gps
                            ?.takeIf { state.prefs.showOsGridRef }
                            ?.let { OsGridRef.latLngToOsGrid(it.latitude, it.longitude) }
                        val osGridForCopy = gps?.let {
                            OsGridRef.latLngToOsGrid(it.latitude, it.longitude)
                        }
                        var showCopySheet by remember { mutableStateOf(false) }

                        // Top: title + signal status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "GPS",
                                style = MaterialTheme.typography.labelMedium,
                                color = HuntColors.Bronze,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GpsSignalRadar(
                                    accuracyMeters = gps?.accuracyMeters,
                                    size = 34.dp,
                                )
                                GpsSignalBadge(accuracyMeters = gps?.accuracyMeters)
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        if (gps == null) {
                            Text("Acquiring fix…", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Using UK fallback until satellite lock",
                                style = MaterialTheme.typography.bodySmall,
                                color = HuntColors.Mist,
                            )
                        } else {
                            val coordsCardinal = formatCoordinates(gps.latitude, gps.longitude)
                            val coordsDecimal = String.format(
                                Locale.UK,
                                "%.5f, %.5f",
                                gps.latitude,
                                gps.longitude,
                            )

                            Text(
                                coordsCardinal,
                                style = MaterialTheme.typography.titleMedium,
                                color = HuntColors.Cream,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (osGridForDisplay != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    osGridForDisplay,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HuntColors.BronzeBright,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { showCopySheet = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HuntColors.Bronze,
                                    contentColor = HuntColors.Ink,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Copy location")
                            }

                            if (showCopySheet) {
                                CopyGpsFormatSheet(
                                    cardinal = coordsCardinal,
                                    decimal = coordsDecimal,
                                    osGrid = osGridForCopy,
                                    onDismiss = { showCopySheet = false },
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider(
                                color = HuntColors.GlassBorder.copy(alpha = 0.45f),
                            )
                            Spacer(Modifier.height(4.dp))

                            MetricRow("Accuracy", "±${gps.accuracyMeters.toInt()} m")
                            gps.altitudeMeters?.let { MetricRow("Elevation", "${it.toInt()} m") }
                            gps.bearingDegrees?.let {
                                MetricRow(
                                    "Heading",
                                    "${it.toInt()}° ${bearingToCardinal(it.toDouble())}",
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Guidance only — verify local regulations and conditions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Mist,
                    )
                    Spacer(Modifier.height(72.dp))
                }
            }
        }
    }
}

private fun formatTideTime(epochMs: Long): String =
    SimpleDateFormat("EEE HH:mm", Locale.UK).apply {
        timeZone = java.util.TimeZone.getDefault()
    }.format(Date(epochMs))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyGpsFormatSheet(
    cardinal: String,
    decimal: String,
    osGrid: String?,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var justCopied by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(justCopied) {
        if (justCopied != null) {
            delay(700)
            onDismiss()
        }
    }

    fun copy(label: String, text: String) {
        clipboard.setText(AnnotatedString(text))
        justCopied = label
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HuntColors.ForestMid,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Copy location", style = MaterialTheme.typography.headlineSmall, color = HuntColors.Cream)
            Text(
                "Choose a format to copy to the clipboard",
                style = MaterialTheme.typography.bodySmall,
                color = HuntColors.Mist,
            )
            Spacer(Modifier.height(4.dp))

            CopyFormatOption(
                title = "Cardinal GPS",
                subtitle = cardinal,
                copied = justCopied == "Cardinal GPS",
                onClick = { copy("Cardinal GPS", cardinal) },
            )
            CopyFormatOption(
                title = "Decimal GPS",
                subtitle = decimal,
                copied = justCopied == "Decimal GPS",
                onClick = { copy("Decimal GPS", decimal) },
            )
            if (osGrid != null) {
                CopyFormatOption(
                    title = "OS Grid",
                    subtitle = osGrid,
                    copied = justCopied == "OS Grid",
                    onClick = { copy("OS Grid", osGrid) },
                )
            } else {
                Text(
                    "OS Grid unavailable outside Great Britain",
                    style = MaterialTheme.typography.bodySmall,
                    color = HuntColors.Mist,
                )
            }

            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun CopyFormatOption(
    title: String,
    subtitle: String,
    copied: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val accent = if (copied) HuntColors.Success else HuntColors.BronzeBright
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, accent.copy(alpha = 0.75f), shape)
            .background(
                if (copied) HuntColors.Success.copy(alpha = 0.14f)
                else HuntColors.ForestDeep.copy(alpha = 0.55f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.titleSmall,
                color = HuntColors.Cream,
            )
        }
        Icon(
            imageVector = if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
            contentDescription = if (copied) "Copied $title" else "Copy $title",
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun formatTime(epochMs: Long?): String {
    if (epochMs == null) return "—"
    // Explicit 24-hour clock in the device's local timezone (UK hunters expect HH:mm)
    return SimpleDateFormat("HH:mm", Locale.UK).apply {
        timeZone = java.util.TimeZone.getDefault()
    }.format(Date(epochMs))
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
