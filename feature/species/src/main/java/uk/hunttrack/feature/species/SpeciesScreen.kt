package uk.hunttrack.feature.species

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.annotation.DrawableRes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import uk.hunttrack.core.common.formatSpeciesProse
import uk.hunttrack.core.designsystem.components.ClickableGlassCard
import uk.hunttrack.core.designsystem.components.HintingLazyOptionRow
import uk.hunttrack.core.designsystem.markers.MarkerIconResolver
import uk.hunttrack.core.designsystem.species.SpeciesImageResolver
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.HuntButton
import uk.hunttrack.core.designsystem.components.BackToTopButton
import uk.hunttrack.core.designsystem.components.RegisterLazyListScrollToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.catalog.UkFieldCatalog
import uk.hunttrack.domain.model.LegalSeason
import uk.hunttrack.domain.model.Species
import uk.hunttrack.domain.model.SpeciesGroup
import uk.hunttrack.domain.model.UkNation
import uk.hunttrack.domain.season.SeasonStatus

@Composable
fun SpeciesRoute(viewModel: SpeciesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SpeciesScreen(
        state = state,
        onQuery = viewModel::setQuery,
        onGroup = viewModel::setGroup,
        onNation = viewModel::setNation,
        onSelect = viewModel::select,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesScreen(
    state: SpeciesUiState,
    onQuery: (String) -> Unit,
    onGroup: (SpeciesGroup?) -> Unit,
    onNation: (UkNation) -> Unit,
    onSelect: (Species?) -> Unit,
) {
    ScreenBackground(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        RegisterLazyListScrollToTop(listState)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("UK Species", style = MaterialTheme.typography.headlineLarge, color = HuntColors.Cream)
                Text("Tap any species for ID guide & seasons", style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search species") },
                    singleLine = true,
                )
            }
            item {
                HintingLazyOptionRow {
                    item {
                        FilterChip(
                            selected = state.groupFilter == null,
                            onClick = { onGroup(null) },
                            label = { Text("All") },
                            colors = chipColors(),
                        )
                    }
                    items(SpeciesGroup.entries) { g ->
                        FilterChip(
                            selected = state.groupFilter == g,
                            onClick = { onGroup(g) },
                            label = {
                                Text(
                                    when (g) {
                                        SpeciesGroup.DEER -> "Deer"
                                        SpeciesGroup.OTHER_QUARRY -> "Other quarry"
                                        SpeciesGroup.BIRD -> "Gamebirds"
                                        SpeciesGroup.WATERFOWL -> "Waterfowl"
                                        SpeciesGroup.CORVID -> "Corvids"
                                        SpeciesGroup.PEST -> "Pest"
                                    },
                                )
                            },
                            colors = chipColors(),
                        )
                    }
                }
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text(state.disclaimer, style = MaterialTheme.typography.bodySmall, color = HuntColors.Gold)
                }
            }
            items(state.species, key = { it.id }) { species ->
                val seasonPreview = seasonPreviewLine(species.id, state.nation)
                ClickableGlassCard(
                    onClick = { onSelect(species) },
                    hint = "Tap for full ID & legal seasons ›",
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SpeciesIcon(
                            species = species,
                            size = 52.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(species.commonName, style = MaterialTheme.typography.titleLarge, color = HuntColors.Cream)
                            Text(species.scientificName, style = MaterialTheme.typography.bodySmall, color = HuntColors.Mist)
                            SpeciesProseText(
                                text = species.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = HuntColors.Mist,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            if (seasonPreview != null) {
                                Text(
                                    seasonPreview,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HuntColors.BronzeBright,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.selected?.let { species ->
        var showGooseInfo by remember(species.id) { mutableStateOf(false) }
        val relatedCalls = remember(species.id) { UkFieldCatalog.callsFor(species.id) }

        if (showGooseInfo) {
            AlertDialog(
                onDismissRequest = { showGooseInfo = false },
                containerColor = HuntColors.ForestMid,
                title = {
                    Text("Geese — more information", color = HuntColors.Cream)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        UkFieldCatalog.gooseMoreInformation.split("\n\n").forEach { paragraph ->
                            Text(paragraph, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGooseInfo = false }) {
                        Text("Close", color = HuntColors.BronzeBright)
                    }
                },
            )
        }

        ModalBottomSheet(
            onDismissRequest = { onSelect(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = HuntColors.ForestMid,
        ) {
            val detailListState = rememberLazyListState()
            RegisterLazyListScrollToTop(detailListState)
            Box(Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = detailListState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 56.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SpeciesIcon(species = species, size = 72.dp)
                        Column {
                            Text(species.commonName, style = MaterialTheme.typography.headlineMedium, color = HuntColors.Cream)
                            Text(species.scientificName, color = HuntColors.Bronze)
                        }
                    }
                }
                item {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        SectionLabel("Overview")
                        val overviewImageRes = when {
                            SpeciesImageResolver.hasPhoto(species.id, isMale = true) ->
                                SpeciesImageResolver.maleImage(species.id)
                            SpeciesImageResolver.hasPhoto(species.id, isMale = false) ->
                                SpeciesImageResolver.femaleImage(species.id)
                            else -> null
                        }
                        overviewImageRes?.let { imageRes ->
                            val photoScale = speciesPhotoContentScale(species.id)
                            Image(
                                painter = painterResource(imageRes),
                                contentDescription = "${species.commonName} reference photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(speciesPhotoHeight(species.id))
                                    .padding(bottom = 10.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = photoScale,
                            )
                        }
                        SpeciesProseText(
                            text = species.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HuntColors.Cream,
                        )
                    }
                }
                item { SectionLabel("Identification") }
                item {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        SpeciesSexDetailBlock(
                            label = UkFieldCatalog.maleTermFor(species.id),
                            text = species.maleTraits,
                            showPhoto = SpeciesImageResolver.hasPhoto(species.id, isMale = true),
                            imageRes = SpeciesImageResolver.maleImage(species.id),
                            contentDescription = "${UkFieldCatalog.maleTermFor(species.id)} — ${species.commonName}",
                            contentScale = speciesPhotoContentScale(species.id),
                            photoHeight = speciesPhotoHeight(species.id),
                        )
                        SpeciesSexDetailBlock(
                            label = UkFieldCatalog.femaleTermFor(species.id),
                            text = species.femaleTraits,
                            showPhoto = SpeciesImageResolver.hasPhoto(species.id, isMale = false),
                            imageRes = SpeciesImageResolver.femaleImage(species.id),
                            contentDescription = "${UkFieldCatalog.femaleTermFor(species.id)} — ${species.commonName}",
                            contentScale = speciesPhotoContentScale(species.id),
                            photoHeight = speciesPhotoHeight(species.id),
                        )
                        SpeciesDetailBlock("Young", species.youngTraits)
                        SpeciesDetailBlock("Tracks", species.tracks)
                        SpeciesDetailBlock("Droppings", species.droppings)
                    }
                }
                item {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        SectionLabel("Habitat & behaviour")
                        SpeciesDetailBlock("Habitat", species.habitat)
                        SpeciesDetailBlock("Behaviour", species.behaviour)
                        SpeciesDetailBlock("Distribution", species.distribution)
                    }
                }
                item {
                    SectionLabel("Legal seasons")
                    HintingLazyOptionRow {
                        items(UkNation.entries) { n ->
                            FilterChip(
                                selected = state.nation == n,
                                onClick = { onNation(n) },
                                label = { Text(n.name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }) },
                                colors = chipColors(),
                            )
                        }
                    }
                }
                if (UkFieldCatalog.isGooseSpecies(species.id)) {
                    item {
                        HuntButton(
                            onClick = { showGooseInfo = true },
                            text = "More information",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (relatedCalls.isNotEmpty()) {
                    item { SectionLabel("Related calls") }
                    item {
                        GlassPanel(Modifier.fillMaxWidth()) {
                            relatedCalls.forEach { call ->
                                Column(Modifier.padding(bottom = 10.dp)) {
                                    Text(call.name, style = MaterialTheme.typography.titleMedium, color = HuntColors.Cream)
                                    Text(call.description, style = MaterialTheme.typography.bodySmall, color = HuntColors.Mist)
                                    Text(
                                        "Open Calls from More to listen",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HuntColors.BronzeBright,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                items(state.seasons) { season ->
                    SeasonCard(season)
                }
                if (state.seasons.isEmpty()) {
                    item {
                        Text("No season rows for this nation — verify with local authority.", color = HuntColors.Mist)
                    }
                }
                item {
                    Text(state.disclaimer, style = MaterialTheme.typography.bodySmall, color = HuntColors.Mist)
                }
                }
                BackToTopButton(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SeasonCard(season: LegalSeason) {
    val status = SeasonStatus.status(season.openFromMonthDay, season.openToMonthDay)
    GlassPanel(Modifier.fillMaxWidth()) {
        SeasonStatusButtons(status)
        Text(
            season.sexLabel,
            style = MaterialTheme.typography.titleMedium,
            color = HuntColors.Cream,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            formatSeasonRange(season.openFromMonthDay, season.openToMonthDay),
            style = MaterialTheme.typography.headlineSmall,
            color = HuntColors.BronzeBright,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (status == SeasonStatus.State.CLOSED && season.openFromMonthDay != "—") {
            Text(
                "Currently closed — do not shoot this class.",
                color = HuntColors.Danger,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (season.notes.isNotBlank()) {
            SpeciesProseText(
                text = season.notes,
                style = MaterialTheme.typography.bodySmall,
                color = HuntColors.Gold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SeasonStatusButtons(status: SeasonStatus.State) {
    if (status == SeasonStatus.State.UNKNOWN) {
        SeasonStatusButton(
            label = "Check season dates",
            active = true,
            activeColor = HuntColors.Gold,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SeasonStatusButton(
            label = "Season open",
            active = status == SeasonStatus.State.OPEN,
            activeColor = HuntColors.Success,
            modifier = Modifier.weight(1f),
        )
        SeasonStatusButton(
            label = "Season closed",
            active = status == SeasonStatus.State.CLOSED,
            activeColor = HuntColors.Danger,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SeasonStatusButton(
    label: String,
    active: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val background = if (active) activeColor.copy(alpha = 0.28f) else HuntColors.Charcoal.copy(alpha = 0.45f)
    val borderColor = if (active) activeColor else HuntColors.SlateLight.copy(alpha = 0.5f)
    val textColor = if (active) activeColor else HuntColors.Mist.copy(alpha = 0.65f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(width = if (active) 1.5.dp else 1.dp, color = borderColor, shape = shape)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatSeasonRange(from: String, to: String): String {
    if (from == "—" || to == "—") return "Dates vary — check local authority"
    return "${formatSeasonDate(from)} – ${formatSeasonDate(to)}"
}

private fun formatSeasonDate(ddMm: String): String {
    val parts = ddMm.split("-", "/", ".")
    if (parts.size != 2) return ddMm
    val day = parts[0].trim().toIntOrNull() ?: return ddMm
    val month = parts[1].trim().toIntOrNull() ?: return ddMm
    val monthName = when (month) {
        1 -> "Jan"
        2 -> "Feb"
        3 -> "Mar"
        4 -> "Apr"
        5 -> "May"
        6 -> "Jun"
        7 -> "Jul"
        8 -> "Aug"
        9 -> "Sep"
        10 -> "Oct"
        11 -> "Nov"
        12 -> "Dec"
        else -> return ddMm
    }
    return "$day $monthName"
}

@Composable
private fun SpeciesSexDetailBlock(
    label: String,
    text: String,
    showPhoto: Boolean,
    @DrawableRes imageRes: Int,
    contentDescription: String,
    contentScale: ContentScale = ContentScale.Crop,
    photoHeight: Dp = 160.dp,
) {
    if (text.isBlank()) return
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = HuntColors.BronzeBright,
            fontWeight = FontWeight.SemiBold,
        )
        if (showPhoto) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(photoHeight)
                    .padding(top = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = contentScale,
            )
        }
        SpeciesProseText(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = HuntColors.Cream,
        )
    }
}

/** Muntjac reference photos are wide/full-body — Fit avoids over-cropping the animal. */
private fun speciesPhotoContentScale(speciesId: String): ContentScale =
    if (speciesId == "muntjac") ContentScale.Fit else ContentScale.Crop

private fun speciesPhotoHeight(speciesId: String): Dp =
    if (speciesId == "muntjac") 200.dp else 180.dp

@Composable
private fun SpeciesDetailBlock(label: String, text: String) {
    if (text.isBlank()) return
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = HuntColors.BronzeBright,
        )
        SpeciesProseText(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = HuntColors.Cream,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SpeciesProseText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: androidx.compose.ui.graphics.Color = HuntColors.Mist,
) {
    val paragraphs = formatSpeciesProse(text)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (paragraphs.isEmpty()) {
            Text(text, style = style, color = color)
        } else {
            paragraphs.forEach { paragraph ->
                Text(paragraph, style = style, color = color)
            }
        }
    }
}

@Composable
private fun SpeciesIcon(
    species: Species,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(MarkerIconResolver.iconForSpecies(species)),
        contentDescription = species.commonName,
        modifier = modifier.size(size),
    )
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = HuntColors.Bronze,
    selectedLabelColor = HuntColors.Ink,
    containerColor = HuntColors.Slate,
    labelColor = HuntColors.Cream,
)

private fun seasonPreviewLine(speciesId: String, nation: UkNation): String? {
    val rows = UkFieldCatalog.seasonsFor(speciesId, nation).ifEmpty {
        UkFieldCatalog.seasonsFor(speciesId, UkNation.ENGLAND)
    }
    if (rows.isEmpty()) return null
    // Deer: show stag/buck + hind/doe classes on the card
    val deerLike = rows.filter { it.sexLabel.contains("Stag", true) || it.sexLabel.contains("Buck", true) || it.sexLabel.contains("Hind", true) || it.sexLabel.contains("Doe", true) }
    if (deerLike.size >= 2) {
        return deerLike.joinToString(" · ") { "${it.sexLabel} ${it.openFromMonthDay}–${it.openToMonthDay}" }
    }
    val first = rows.first()
    return if (first.openFromMonthDay == "—") first.notes.takeIf { it.isNotBlank() }?.take(80)
    else "${first.sexLabel}: ${first.openFromMonthDay} – ${first.openToMonthDay}"
}
