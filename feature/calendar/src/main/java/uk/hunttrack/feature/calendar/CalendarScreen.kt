package uk.hunttrack.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.RegisterLazyListScrollToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.geo.CalendarDates
import uk.hunttrack.domain.model.CalendarDayEntry

@Composable
fun CalendarRoute(
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CalendarScreen(
        state = state,
        periodLabel = viewModel.periodLabel(state),
        speciesOptions = viewModel.speciesOptions(),
        onViewMode = viewModel::setViewMode,
        onStepPeriod = viewModel::stepPeriod,
        onGoToToday = viewModel::goToToday,
        onSelectDate = { date, openDay -> viewModel.selectDate(date, openDay) },
        onSelectMonth = viewModel::selectMonth,
        onDraftTitle = viewModel::updateDraftTitle,
        onDraftNotes = viewModel::updateDraftNotes,
        onDraftLocation = viewModel::updateDraftLocation,
        onDraftSpecies = viewModel::updateDraftSpeciesId,
        onSave = viewModel::saveDayEntry,
        onClear = viewModel::clearDayEntry,
    )
}

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    periodLabel: String,
    speciesOptions: List<uk.hunttrack.domain.model.Species>,
    onViewMode: (CalendarViewMode) -> Unit,
    onStepPeriod: (Boolean) -> Unit,
    onGoToToday: () -> Unit,
    onSelectDate: (LocalDate, Boolean) -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onDraftTitle: (String) -> Unit,
    onDraftNotes: (String) -> Unit,
    onDraftLocation: (String) -> Unit,
    onDraftSpecies: (String?) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
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
                Text("Field calendar", style = MaterialTheme.typography.headlineLarge, color = HuntColors.Cream)
                Text(
                    "Plan hunts, log outings, and note what happened each day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HuntColors.Mist,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.viewMode == CalendarViewMode.DAY,
                        onClick = { onViewMode(CalendarViewMode.DAY) },
                        label = { Text("Day") },
                    )
                    FilterChip(
                        selected = state.viewMode == CalendarViewMode.MONTH,
                        onClick = { onViewMode(CalendarViewMode.MONTH) },
                        label = { Text("Month") },
                    )
                    FilterChip(
                        selected = state.viewMode == CalendarViewMode.YEAR,
                        onClick = { onViewMode(CalendarViewMode.YEAR) },
                        label = { Text("Year") },
                    )
                }
            }
            item {
                CalendarNavBar(
                    label = periodLabel,
                    onPrev = { onStepPeriod(false) },
                    onNext = { onStepPeriod(true) },
                    onToday = onGoToToday,
                )
            }
            item {
                when (state.viewMode) {
                    CalendarViewMode.DAY -> DayDetailPanel(
                        state = state,
                        speciesOptions = speciesOptions,
                        onDraftTitle = onDraftTitle,
                        onDraftNotes = onDraftNotes,
                        onDraftLocation = onDraftLocation,
                        onDraftSpecies = onDraftSpecies,
                        onSave = onSave,
                        onClear = onClear,
                    )
                    CalendarViewMode.MONTH -> MonthGridPanel(
                        selectedDate = state.selectedDate,
                        entriesByDay = state.entriesByDay,
                        onSelectDate = { onSelectDate(it, true) },
                    )
                    CalendarViewMode.YEAR -> YearGridPanel(
                        year = state.selectedDate.year,
                        entriesByDay = state.entriesByDay,
                        onSelectMonth = onSelectMonth,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarNavBar(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous", tint = HuntColors.BronzeBright)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.titleMedium, color = HuntColors.Cream, textAlign = TextAlign.Center)
                TextButton(onClick = onToday) {
                    Text("Today", color = HuntColors.BronzeBright)
                }
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next", tint = HuntColors.BronzeBright)
            }
        }
    }
}

@Composable
private fun MonthGridPanel(
    selectedDate: LocalDate,
    entriesByDay: Map<Long, CalendarDayEntry>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val yearMonth = YearMonth.from(selectedDate)
    val cells = CalendarDates.monthGrid(yearMonth)
    val today = CalendarDates.today()

    GlassPanel(Modifier.fillMaxWidth()) {
        SectionLabel(yearMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)))
        Row(Modifier.fillMaxWidth()) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = HuntColors.Mist,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                week.forEach { date ->
                    MonthDayCell(
                        date = date,
                        isSelected = date == selectedDate,
                        isToday = date == today,
                        hasEntry = date?.let { entriesByDay[CalendarDates.dayStartEpochMs(it)]?.hasContent == true } == true,
                        onClick = { if (date != null) onSelectDate(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text(
            "Tap a day to open details",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = HuntColors.Mist,
        )
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate?,
    isSelected: Boolean,
    isToday: Boolean,
    hasEntry: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(shape)
            .then(
                when {
                    isSelected -> Modifier
                        .background(HuntColors.Bronze.copy(alpha = 0.35f))
                        .border(1.dp, HuntColors.BronzeBright, shape)
                    isToday -> Modifier.border(1.dp, HuntColors.BronzeBright.copy(alpha = 0.6f), shape)
                    else -> Modifier
                },
            )
            .clickable(enabled = date != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (date != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) HuntColors.Cream else HuntColors.Mist,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
                if (hasEntry) {
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(6.dp)
                            .background(HuntColors.BronzeBright, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun YearGridPanel(
    year: Int,
    entriesByDay: Map<Long, CalendarDayEntry>,
    onSelectMonth: (YearMonth) -> Unit,
) {
    val months = (1..12).map { YearMonth.of(year, it) }
    GlassPanel(Modifier.fillMaxWidth()) {
        SectionLabel(year.toString())
        months.chunked(3).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { ym ->
                    YearMonthCell(
                        yearMonth = ym,
                        entryCount = entriesInMonth(ym, entriesByDay),
                        isCurrentMonth = ym == YearMonth.from(CalendarDates.today()),
                        onClick = { onSelectMonth(ym) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
        Text(
            "Tap a month to open the grid",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = HuntColors.Mist,
        )
    }
}

@Composable
private fun YearMonthCell(
    yearMonth: YearMonth,
    entryCount: Int,
    isCurrentMonth: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(HuntColors.Charcoal.copy(alpha = 0.55f))
            .then(
                if (isCurrentMonth) Modifier.border(1.dp, HuntColors.BronzeBright, shape) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            yearMonth.month.getDisplayName(TextStyle.SHORT, Locale.UK),
            style = MaterialTheme.typography.titleMedium,
            color = HuntColors.Cream,
        )
        Text(
            if (entryCount > 0) "$entryCount day${if (entryCount == 1) "" else "s"} noted" else "No notes",
            style = MaterialTheme.typography.bodySmall,
            color = if (entryCount > 0) HuntColors.BronzeBright else HuntColors.Mist,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayDetailPanel(
    state: CalendarUiState,
    speciesOptions: List<uk.hunttrack.domain.model.Species>,
    onDraftTitle: (String) -> Unit,
    onDraftNotes: (String) -> Unit,
    onDraftLocation: (String) -> Unit,
    onDraftSpecies: (String?) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    val dayStart = CalendarDates.dayStartEpochMs(state.selectedDate)
    val saved = state.entriesByDay[dayStart]

    GlassPanel(Modifier.fillMaxWidth()) {
        SectionLabel("Day details")
        Text(
            state.selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK)),
            style = MaterialTheme.typography.titleMedium,
            color = HuntColors.Cream,
        )
        if (saved?.hasContent == true) {
            Text(
                "Last saved notes on this day",
                style = MaterialTheme.typography.bodySmall,
                color = HuntColors.Mist,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        OutlinedTextField(
            value = state.draftTitle,
            onValueChange = onDraftTitle,
            label = { Text("Title") },
            placeholder = { Text("Morning stalk, permission visit…") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.draftLocation,
            onValueChange = onDraftLocation,
            label = { Text("Location / beat") },
            modifier = Modifier.fillMaxWidth(),
        )
        SectionLabel("Species (optional)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = state.draftSpeciesId == null,
                onClick = { onDraftSpecies(null) },
                label = { Text("None") },
            )
            speciesOptions.take(8).forEach { species ->
                FilterChip(
                    selected = state.draftSpeciesId == species.id,
                    onClick = {
                        onDraftSpecies(if (state.draftSpeciesId == species.id) null else species.id)
                    },
                    label = { Text(species.commonName) },
                )
            }
        }
        OutlinedTextField(
            value = state.draftNotes,
            onValueChange = onDraftNotes,
            label = { Text("Notes") },
            placeholder = { Text("Weather, sightings, kit, plans…") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                Text("Save day")
            }
            TextButton(onClick = onClear) {
                Text("Clear", color = HuntColors.Mist)
            }
        }
    }
}

private fun entriesInMonth(yearMonth: YearMonth, entriesByDay: Map<Long, CalendarDayEntry>): Int {
    val start = yearMonth.atDay(1)
    val end = yearMonth.atEndOfMonth()
    var count = 0
    var date = start
    while (!date.isAfter(end)) {
        if (entriesByDay[CalendarDates.dayStartEpochMs(date)]?.hasContent == true) count++
        date = date.plusDays(1)
    }
    return count
}
