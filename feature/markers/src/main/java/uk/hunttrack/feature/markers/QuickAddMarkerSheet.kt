package uk.hunttrack.feature.markers

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import uk.hunttrack.core.designsystem.components.HintingLazyOptionRow
import uk.hunttrack.core.designsystem.markers.MarkerIconResolver
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.catalog.UkFieldCatalog
import uk.hunttrack.domain.model.MarkerCategory
import uk.hunttrack.domain.model.MarkerCategoryGroup
import uk.hunttrack.domain.model.SpeciesGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddMarkerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, category: MarkerCategory, notes: String, speciesId: String?) -> Unit,
    onSaveOfflineArea: (Double, Double) -> Unit = { _, _ -> },
    latitude: Double? = null,
    longitude: Double? = null,
    gpsReady: Boolean = true,
    placeError: String? = null,
) {
    if (!visible) return
    // M3 1.3.1 makes the whole sheet surface swipe-to-dismiss. The chip rows below are
    // horizontally swipeable; once a row hits its scroll edge, the sheet's vertical drag
    // wins the gesture and the sheet gets dismissed mid-swipe. Blocking the Hidden settle
    // stops drag-from-anywhere dismissal (the rows then scroll freely) while leaving
    // scrim tap, back button and the Cancel button working.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(MarkerCategoryGroup.WILDLIFE) }
    var category by remember { mutableStateOf(MarkerCategory.DEER) }
    var speciesGroup by remember { mutableStateOf(SpeciesGroup.DEER) }
    var selectedSpecies by remember { mutableStateOf(UkFieldCatalog.species.first { it.group == SpeciesGroup.DEER }) }

    val speciesInGroup = remember(speciesGroup) {
        UkFieldCatalog.species.filter { it.group == speciesGroup }
    }
    val mapIconRes = remember(category, selectedSpecies, group) {
        if (group == MarkerCategoryGroup.WILDLIFE) {
            MarkerIconResolver.iconForSpecies(selectedSpecies)
        } else {
            MarkerIconResolver.iconForCategory(category)
        }
    }
    val previewLabel = remember(category, selectedSpecies, group) {
        if (group == MarkerCategoryGroup.WILDLIFE) selectedSpecies.commonName else category.label
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HuntColors.ForestMid,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 12.dp),
            ) {
                Text("Mark location", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (latitude != null && longitude != null) {
                        "Saves at this point · private on device"
                    } else {
                        "Places at your current GPS position · private on device"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = HuntColors.Mist,
                )
                Spacer(Modifier.height(12.dp))

                HintingLazyOptionRow {
                    items(MarkerCategoryGroup.entries) { g ->
                        FilterChip(
                            selected = group == g,
                            onClick = {
                                group = g
                                if (g == MarkerCategoryGroup.WILDLIFE) {
                                    speciesGroup = SpeciesGroup.DEER
                                    selectedSpecies = UkFieldCatalog.species.first { it.group == SpeciesGroup.DEER }
                                    category = MarkerCategory.DEER
                                } else {
                                    category = MarkerCategory.entries.first {
                                        it.group == g && it != MarkerCategory.HOMING
                                    }
                                }
                            },
                            label = { Text(g.name.lowercase().replaceFirstChar { it.titlecase() }) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (group == MarkerCategoryGroup.WILDLIFE) {
                    HintingLazyOptionRow {
                        items(SpeciesGroup.entries) { sg ->
                            FilterChip(
                                selected = speciesGroup == sg,
                                onClick = {
                                    speciesGroup = sg
                                    val first = UkFieldCatalog.species.firstOrNull { it.group == sg }
                                    if (first != null) {
                                        selectedSpecies = first
                                        category = MarkerIconResolver.categoryForSpecies(first)
                                    }
                                },
                                label = { Text(speciesGroupLabel(sg)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HintingLazyOptionRow {
                        items(speciesInGroup, key = { it.id }) { species ->
                            FilterChip(
                                selected = selectedSpecies.id == species.id,
                                onClick = {
                                    selectedSpecies = species
                                    category = MarkerIconResolver.categoryForSpecies(species)
                                },
                                label = { Text(species.commonName) },
                            )
                        }
                    }
                } else {
                    HintingLazyOptionRow {
                        items(MarkerCategory.entries.filter { it.group == group && it != MarkerCategory.HOMING }) { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { category = c },
                                label = { Text(c.label) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(previewLabel) },
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Map marker",
                        style = MaterialTheme.typography.labelMedium,
                        color = HuntColors.Mist,
                    )
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(HuntColors.ForestDeep)
                            .border(1.dp, HuntColors.Bronze.copy(alpha = 0.75f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(mapIconRes),
                            contentDescription = "$previewLabel map icon",
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    Text(
                        previewLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.BronzeBright,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                if (latitude != null && longitude != null) {
                    val grid = uk.hunttrack.domain.geo.OsGridRef.latLngToOsGrid(latitude, longitude)
                    if (grid != null) {
                        Text(
                            grid,
                            style = MaterialTheme.typography.titleMedium,
                            color = HuntColors.BronzeBright,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Text(
                        "Pin: ${"%.5f".format(latitude)}, ${"%.5f".format(longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Mist,
                        modifier = Modifier.padding(top = if (grid != null) 2.dp else 8.dp),
                    )
                } else if (!gpsReady) {
                    Text(
                        "Waiting for GPS lock…",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Gold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (placeError != null) {
                    Text(
                        placeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Danger,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            HorizontalDivider(color = HuntColors.GlassBorder.copy(alpha = 0.5f))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 8.dp),
            ) {
                Button(
                    onClick = {
                        val speciesId = if (group == MarkerCategoryGroup.WILDLIFE) selectedSpecies.id else null
                        val resolvedCategory = if (group == MarkerCategoryGroup.WILDLIFE) {
                            MarkerIconResolver.categoryForSpecies(selectedSpecies)
                        } else {
                            category
                        }
                        val resolvedName = name.ifBlank {
                            if (group == MarkerCategoryGroup.WILDLIFE) {
                                selectedSpecies.commonName
                            } else {
                                category.label
                            }
                        }
                        onSave(resolvedName, resolvedCategory, notes, speciesId)
                    },
                    enabled = gpsReady || (latitude != null && longitude != null),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save marker here")
                }
                OutlinedButton(
                    onClick = {
                        val lat = latitude
                        val lng = longitude
                        if (lat != null && lng != null) {
                            onSaveOfflineArea(lat, lng)
                        }
                    },
                    enabled = latitude != null && longitude != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save area as offline map")
                }
                Text(
                    "Downloads a 15 sq mile pack around this point — all map types.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HuntColors.Mist,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}

private fun speciesGroupLabel(group: SpeciesGroup): String = when (group) {
    SpeciesGroup.DEER -> "Deer"
    SpeciesGroup.OTHER_QUARRY -> "Quarry"
    SpeciesGroup.BIRD -> "Birds"
    SpeciesGroup.WATERFOWL -> "Wildfowl"
    SpeciesGroup.CORVID -> "Corvids"
    SpeciesGroup.PEST -> "Pest"
}
