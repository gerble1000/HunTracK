package uk.hunttrack.feature.wind

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.hunttrack.core.common.bearingToCardinal
import uk.hunttrack.core.common.format1
import uk.hunttrack.core.common.toMph
import uk.hunttrack.core.designsystem.animation.WindDirectionDial
import uk.hunttrack.core.designsystem.components.ClickableGlassCard
import uk.hunttrack.core.designsystem.components.FieldGuideSheet
import uk.hunttrack.core.designsystem.components.FieldGuideTitleRow
import uk.hunttrack.core.designsystem.components.FieldGuides
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.HuntButton
import uk.hunttrack.core.designsystem.components.RegisterLazyListScrollToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.model.WindUnit
import uk.hunttrack.domain.scent.ScentPlumeGeometry

@Composable
fun WindRoute(
    viewModel: WindViewModel = hiltViewModel(),
    onOpenMap: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WindScreen(
        state = state,
        onSelectMarker = viewModel::selectMarker,
        onRefresh = viewModel::refresh,
        onOpenMap = onOpenMap,
    )
}

@Composable
fun WindScreen(
    state: WindUiState,
    onSelectMarker: (Long?) -> Unit,
    onRefresh: () -> Unit,
    onOpenMap: () -> Unit = {},
) {
    var showGuide by remember { mutableStateOf(false) }

    LaunchedEffect(state.prefsLoaded, state.prefs.windTrainingEnabled) {
        if (!state.prefsLoaded) return@LaunchedEffect
        if (state.prefs.windTrainingEnabled) {
            showGuide = true
        }
    }

    fun dismissGuide() {
        showGuide = false
    }

    ScreenBackground(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        RegisterLazyListScrollToTop(listState)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                FieldGuideTitleRow(
                    title = "Wind & scent",
                    subtitle = "Will animals smell you from downwind?",
                    onHelp = { showGuide = true },
                )
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Current wind")
                    val w = state.weather
                    if (w == null) {
                        Text(if (state.loading) "Loading wind…" else "No wind data", color = HuntColors.Mist)
                        HuntButton(onClick = onRefresh, text = "Retry", modifier = Modifier.padding(top = 8.dp))
                    } else {
                        val scentCardinal = bearingToCardinal((w.windDirectionDeg + 180) % 360)
                        val fromCardinal = bearingToCardinal(w.windDirectionDeg)
                        val speed = if (state.prefs.windUnit == WindUnit.MPH) {
                            "${w.windSpeedKmh.toMph().format1()} mph"
                        } else {
                            "${w.windSpeedKmh.format1()} km/h"
                        }
                        val gustText = w.windGustKmh?.let {
                            if (state.prefs.windUnit == WindUnit.MPH) {
                                "${it.toMph().format1()} mph"
                            } else {
                                "${it.format1()} km/h"
                            }
                        }
                        val characterLabel = when (state.windCharacter) {
                            WindCharacter.SLOW_NARROW -> "Light · scent pools nearby"
                            WindCharacter.FAST -> "High · wind burn plume"
                            WindCharacter.VARIABLE_WIDE -> "Gusty · scattered pockets"
                            WindCharacter.STEADY -> "Steady · long linear plume"
                        }
                        val plume = ScentPlumeGeometry.forConditions(
                            w.windSpeedKmh,
                            w.windGustKmh,
                            w.humidityPercent,
                            w.temperatureC,
                        )
                        val downwindDeg = ((w.windDirectionDeg + 180) % 360).toInt()
                        val standoffM = ScentPlumeGeometry.approachStandoffM(
                            w.windSpeedKmh,
                            w.windGustKmh,
                            w.humidityPercent,
                            w.temperatureC,
                        ).toInt()

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            WindDirectionDial(
                                directionFromDeg = w.windDirectionDeg.toFloat(),
                                speedKmh = w.windSpeedKmh,
                                gustKmh = w.windGustKmh,
                                size = 272.dp,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                speed,
                                style = MaterialTheme.typography.displaySmall,
                                color = HuntColors.Cream,
                            )
                            Text(
                                characterLabel,
                                color = HuntColors.BronzeBright,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            gustText?.let {
                                Text(
                                    "Gusts $it",
                                    color = HuntColors.Mist,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Canvas(Modifier.size(44.dp)) {
                                val tip = Offset(size.width / 2f, 2f)
                                val len = size.height - 4f
                                val headLen = len * 0.38f
                                val headHalf = len * 0.28f
                                val shaftHalf = len * 0.10f
                                val base = Offset(tip.x, tip.y + len)
                                val headBase = Offset(tip.x, tip.y + headLen)
                                val fletch = Offset(tip.x, tip.y + len * 0.82f)
                                val fletchHalf = len * 0.22f
                                val arrow = Path().apply {
                                    moveTo(tip.x, tip.y)
                                    lineTo(headBase.x + headHalf, headBase.y)
                                    lineTo(headBase.x + shaftHalf, headBase.y)
                                    lineTo(fletch.x + shaftHalf, fletch.y)
                                    lineTo(base.x + fletchHalf, base.y)
                                    lineTo(base.x, base.y)
                                    lineTo(base.x - fletchHalf, base.y)
                                    lineTo(fletch.x - shaftHalf, fletch.y)
                                    lineTo(headBase.x - shaftHalf, headBase.y)
                                    lineTo(headBase.x - headHalf, headBase.y)
                                    close()
                                }
                                drawPath(arrow, HuntColors.BronzeBright)
                                drawPath(
                                    arrow,
                                    HuntColors.Ink.copy(alpha = 0.5f),
                                    style = Stroke(width = 2f),
                                )
                            }
                            Text(
                                "This direction....",
                                style = MaterialTheme.typography.titleLarge,
                                color = HuntColors.BronzeBright,
                            )
                            Text(
                                scentCardinal,
                                style = MaterialTheme.typography.displaySmall,
                                color = HuntColors.Cream,
                            )
                            Text(
                                "$downwindDeg° downwind",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HuntColors.Mist,
                            )
                            Text(
                                "Plume length ~${plume.lengthM.toInt()} m",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HuntColors.Cream,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Wind comes from $fromCardinal (${w.windDirectionDeg.toInt()}°)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HuntColors.Gold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "Follow the arrow on the dial — that is where your smell drifts",
                                style = MaterialTheme.typography.bodySmall,
                                color = HuntColors.Mist,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                "Your scent travels downwind towards $downwindDeg°. " +
                                    "Approach quarry from downwind (into the face of the wind) and keep a " +
                                    "minimum standoff of $standoffM m. " +
                                    "The same plume is drawn as smoke on the map.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HuntColors.Mist,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
            if (state.markers.isEmpty()) {
                item {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Text(
                            "No markers placed yet. Drop markers on the map to evaluate live scent risk.",
                            color = HuntColors.Mist,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            items(state.markers, key = { it.id }) { marker ->
                val selected = state.selectedMarkerId == marker.id
                val eval = evaluateMarkerScent(
                    state.weather,
                    state.hunterGps,
                    marker.latitude,
                    marker.longitude,
                )
                ClickableGlassCard(
                    onClick = { onSelectMarker(if (selected) null else marker.id) },
                    selected = selected,
                    hint = if (selected) {
                        "Chosen · show route on Map ›"
                    } else {
                        "Choose for scent check & approach ›"
                    },
                ) {
                    Text(marker.name, style = MaterialTheme.typography.titleLarge, color = HuntColors.Cream)
                    Text(marker.category.label, color = HuntColors.Mist, style = MaterialTheme.typography.bodyMedium)
                    when (eval.risk) {
                        ScentRisk.RISK -> Text(
                            "RISK",
                            color = HuntColors.Danger,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        ScentRisk.CLEAR -> Text(
                            "CLEAR",
                            color = HuntColors.Success,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        ScentRisk.UNKNOWN -> Text(
                            "Waiting for wind or GPS",
                            color = HuntColors.Mist,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    eval.distanceM?.let { distance ->
                        Text(
                            "${distance.toInt()} m away · ${eval.bearingDeg?.toInt()}° ${bearingToCardinal(eval.bearingDeg ?: 0.0)}",
                            color = HuntColors.Mist,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (selected) {
                        when (eval.risk) {
                            ScentRisk.RISK -> Text(
                                "WARNING: Your scent cone is drifting straight towards this target location. Consider circling around for an upwind approach.",
                                color = HuntColors.Danger,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            ScentRisk.CLEAR -> Text(
                                "Clear: The estimated scent plume is away from this target. Wind can change; verify conditions in the field.",
                                color = HuntColors.Success,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            ScentRisk.UNKNOWN -> Text(
                                "Scent risk is unavailable until GPS and recent local wind data are available.",
                                color = HuntColors.Mist,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (state.weather != null && eval.risk != ScentRisk.UNKNOWN) {
                            HuntButton(
                                onClick = onOpenMap,
                                text = "Show approach on map",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                            )
                        }
                    }
                    Text(
                        "${"%.5f".format(marker.latitude)}, ${"%.5f".format(marker.longitude)}",
                        color = HuntColors.Mist,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

        }
    }

    FieldGuideSheet(
        visible = showGuide,
        title = "Wind & scent — training",
        subtitle = "Short field guide. Tap ? anytime. Turn auto-open on/off in Settings.",
        sections = FieldGuides.wind,
        onDismiss = { dismissGuide() },
        confirmLabel = "Close",
    )
}
