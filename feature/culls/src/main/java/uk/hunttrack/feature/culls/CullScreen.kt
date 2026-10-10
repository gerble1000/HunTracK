package uk.hunttrack.feature.culls

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import uk.hunttrack.core.designsystem.components.ClickableGlassCard
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.HintingOptionRow
import uk.hunttrack.core.designsystem.components.HuntButton
import uk.hunttrack.core.designsystem.components.RegisterLazyListScrollToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.catalog.UkFieldCatalog
import uk.hunttrack.domain.model.CarcassTestStatus
import uk.hunttrack.domain.model.CullAgeClass
import uk.hunttrack.domain.model.CullRecord
import uk.hunttrack.domain.model.CullSex

@Composable
fun CullRoute(
    viewModel: CullViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CullScreen(
        state = state,
        onFilterYear = viewModel::setFilterYear,
        onFilterSpecies = viewModel::setFilterSpecies,
        onAdd = viewModel::openNew,
        onEdit = viewModel::openEdit,
        onDismissEditor = viewModel::dismissEditor,
        onUpdateDraft = viewModel::updateDraft,
        onUseGps = viewModel::useCurrentGps,
        onApplyOsGrid = viewModel::applyOsGridInput,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onShareCsv = { viewModel.shareCsv(state.filtered) },
        onClearMessage = viewModel::clearMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CullScreen(
    state: CullUiState,
    onFilterYear: (Int?) -> Unit,
    onFilterSpecies: (String?) -> Unit,
    onAdd: () -> Unit,
    onEdit: (CullRecord) -> Unit,
    onDismissEditor: () -> Unit,
    onUpdateDraft: ((CullRecord) -> CullRecord) -> Unit,
    onUseGps: () -> Unit,
    onApplyOsGrid: (String) -> Unit,
    onSave: (placeMapPin: Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onShareCsv: () -> Unit,
    onClearMessage: () -> Unit,
) {
    val dateFmt = remember { SimpleDateFormat("dd MMM yy", Locale.UK) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.message) {
        if (state.message != null) {
            kotlinx.coroutines.delay(2500)
            onClearMessage()
        }
    }

    ScreenBackground(Modifier.fillMaxSize()) {
        RegisterLazyListScrollToTop(listState)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Cull log", style = MaterialTheme.typography.headlineLarge, color = HuntColors.Cream)
                Text(
                    "Spreadsheet view for bag records — map pins optional",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HuntColors.Mist,
                )
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text(
                        "Total in view: ${state.yearTotal}",
                        style = MaterialTheme.typography.titleLarge,
                        color = HuntColors.BronzeBright,
                    )
                    Text(
                        "${state.filtered.size} rows",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Mist,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HuntButton(onClick = onAdd, text = "Add cull", modifier = Modifier.weight(1f))
                        HuntButton(onClick = onShareCsv, text = "Share CSV", modifier = Modifier.weight(1f))
                    }
                }
            }
            item {
                SectionLabel("Year")
                HintingOptionRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.filterYear == null,
                        onClick = { onFilterYear(null) },
                        label = { Text("All") },
                    )
                    state.availableYears.forEach { year ->
                        FilterChip(
                            selected = state.filterYear == year,
                            onClick = { onFilterYear(year) },
                            label = { Text(year.toString()) },
                        )
                    }
                }
            }
            item {
                SectionLabel("Species")
                HintingOptionRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.filterSpeciesId == null,
                        onClick = { onFilterSpecies(null) },
                        label = { Text("All") },
                    )
                    UkFieldCatalog.cullLogSpecies.forEach { sp ->
                        FilterChip(
                            selected = state.filterSpeciesId == sp.id,
                            onClick = { onFilterSpecies(sp.id) },
                            label = { Text(sp.commonName) },
                        )
                    }
                }
            }
            item {
                state.message?.let {
                    Text(it, color = HuntColors.BronzeBright, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                GlassPanel(Modifier.fillMaxWidth(), compact = true) {
                    val scroll = rememberScrollState()
                    Column(Modifier.horizontalScroll(scroll)) {
                        CullHeaderRow()
                        if (state.filtered.isEmpty()) {
                            Text(
                                "No culls yet — tap Add cull",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HuntColors.Mist,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        } else {
                            state.filtered.forEach { record ->
                                CullDataRow(
                                    record = record,
                                    dateLabel = dateFmt.format(Date(record.culledAtEpochMs)),
                                    onClick = { onEdit(record) },
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (state.showEditor) {
        val draft = state.editing
        if (draft != null) {
            CullEditorSheet(
                draft = draft,
                onDismiss = onDismissEditor,
                onUpdate = onUpdateDraft,
                onUseGps = onUseGps,
                onApplyOsGrid = onApplyOsGrid,
                onSave = onSave,
                onDelete = if (draft.id != 0L) ({ onDelete(draft.id) }) else null,
            )
        }
    }
}

@Composable
private fun CullHeaderRow() {
    Row(
        Modifier.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HeaderCell("Date", 72)
        HeaderCell("Species", 100)
        HeaderCell("Sex", 56)
        HeaderCell("Age", 64)
        HeaderCell("Qty", 36)
        HeaderCell("OS Grid", 120)
        HeaderCell("Test", 88)
    }
}

@Composable
private fun HeaderCell(label: String, width: Int) {
    Text(
        label,
        modifier = Modifier.width(width.dp),
        style = MaterialTheme.typography.labelMedium,
        color = HuntColors.Mist,
        maxLines = 1,
    )
}

@Composable
private fun CullDataRow(
    record: CullRecord,
    dateLabel: String,
    onClick: () -> Unit,
) {
    ClickableGlassCard(
        onClick = onClick,
        hint = "Edit ›",
        modifier = Modifier
            .padding(vertical = 3.dp)
            .width(560.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Cell(dateLabel, 72)
            Cell(record.speciesLabel, 100)
            Cell(record.sex.label, 56)
            Cell(record.ageClass.label, 64)
            Cell(record.count.toString(), 36)
            Text(
                record.osGridRef.ifBlank { "—" },
                modifier = Modifier.width(120.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = HuntColors.BronzeBright,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Cell(record.carcassTestStatus.label, 88)
        }
    }
}

@Composable
private fun Cell(text: String, width: Int) {
    Text(
        text,
        modifier = Modifier.width(width.dp),
        style = MaterialTheme.typography.bodySmall,
        color = HuntColors.Cream,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CullEditorSheet(
    draft: CullRecord,
    onDismiss: () -> Unit,
    onUpdate: ((CullRecord) -> CullRecord) -> Unit,
    onUseGps: () -> Unit,
    onApplyOsGrid: (String) -> Unit,
    onSave: (placeMapPin: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var osGridInput by remember(draft.id) { mutableStateOf(draft.osGridRef) }
    var placePin by remember { mutableStateOf(draft.linkedMarkerId != null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(draft.osGridRef) {
        if (draft.osGridRef.isNotBlank()) osGridInput = draft.osGridRef
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HuntColors.ForestMid,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (draft.id == 0L) "Add cull" else "Edit cull",
                style = MaterialTheme.typography.headlineMedium,
                color = HuntColors.Cream,
            )
            SectionLabel("Species")
            HintingOptionRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UkFieldCatalog.cullLogSpecies.forEach { sp ->
                    FilterChip(
                        selected = draft.speciesId == sp.id,
                        onClick = {
                            onUpdate {
                                it.copy(speciesId = sp.id, speciesLabel = sp.commonName)
                            }
                        },
                        label = { Text(sp.commonName) },
                    )
                }
            }
            SectionLabel("Sex")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CullSex.entries.forEach { sex ->
                    FilterChip(
                        selected = draft.sex == sex,
                        onClick = { onUpdate { it.copy(sex = sex) } },
                        label = { Text(sex.label) },
                    )
                }
            }
            SectionLabel("Age class")
            HintingOptionRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CullAgeClass.entries.forEach { age ->
                    FilterChip(
                        selected = draft.ageClass == age,
                        onClick = { onUpdate { it.copy(ageClass = age) } },
                        label = { Text(age.label) },
                    )
                }
            }
            OutlinedTextField(
                value = draft.count.toString(),
                onValueChange = { raw ->
                    val n = raw.filter { it.isDigit() }.toIntOrNull() ?: 1
                    onUpdate { it.copy(count = n.coerceIn(1, 999)) }
                },
                label = { Text("Count") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.locationName,
                onValueChange = { v -> onUpdate { it.copy(locationName = v) } },
                label = { Text("Location name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            SectionLabel("Carcass test")
            HintingOptionRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CarcassTestStatus.entries.forEach { status ->
                    FilterChip(
                        selected = draft.carcassTestStatus == status,
                        onClick = { onUpdate { it.copy(carcassTestStatus = status) } },
                        label = { Text(status.label) },
                    )
                }
            }
            OutlinedTextField(
                value = osGridInput,
                onValueChange = { osGridInput = it },
                label = { Text("OS Grid Reference") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("SU 37290 15552") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onApplyOsGrid(osGridInput) }) {
                    Text("Apply grid", color = HuntColors.BronzeBright)
                }
                TextButton(onClick = onUseGps) {
                    Text("Use GPS", color = HuntColors.BronzeBright)
                }
            }
            if (draft.osGridRef.isNotBlank()) {
                Text(draft.osGridRef, color = HuntColors.BronzeBright, style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { v -> onUpdate { it.copy(notes = v) } },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Also place map pin", color = HuntColors.Cream)
                Switch(checked = placePin, onCheckedChange = { placePin = it })
            }
            Button(onClick = { onSave(placePin) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save cull")
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete", color = HuntColors.Danger)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
