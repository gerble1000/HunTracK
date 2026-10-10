package uk.hunttrack.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.hunttrack.core.data.phase2.CalendarRepository
import uk.hunttrack.domain.catalog.UkFieldCatalog
import uk.hunttrack.domain.geo.CalendarDates
import uk.hunttrack.domain.model.CalendarDayEntry
import uk.hunttrack.domain.model.Species

enum class CalendarViewMode { DAY, MONTH, YEAR }

data class CalendarUiState(
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val selectedDate: LocalDate = CalendarDates.today(),
    val entriesByDay: Map<Long, CalendarDayEntry> = emptyMap(),
    val draftTitle: String = "",
    val draftNotes: String = "",
    val draftLocation: String = "",
    val draftSpeciesId: String? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository,
) : ViewModel() {

    private val viewMode = MutableStateFlow(CalendarViewMode.MONTH)
    private val selectedDate = MutableStateFlow(CalendarDates.today())
    private val entriesByDay = MutableStateFlow<Map<Long, CalendarDayEntry>>(emptyMap())
    private val draftTitle = MutableStateFlow("")
    private val draftNotes = MutableStateFlow("")
    private val draftLocation = MutableStateFlow("")
    private val draftSpeciesId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            repository.observeEntries().collect { entries ->
                entriesByDay.value = entries.associateBy { it.dayStartEpochMs }
                loadDraftForDate(selectedDate.value)
            }
        }
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        combine(viewMode, selectedDate, entriesByDay) { mode, date, entries ->
            Triple(mode, date, entries)
        },
        combine(draftTitle, draftNotes, draftLocation, draftSpeciesId) { title, notes, location, species ->
            DraftFields(title, notes, location, species)
        },
    ) { (mode, date, entries), draft ->
        CalendarUiState(
            viewMode = mode,
            selectedDate = date,
            entriesByDay = entries,
            draftTitle = draft.title,
            draftNotes = draft.notes,
            draftLocation = draft.location,
            draftSpeciesId = draft.speciesId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    private data class DraftFields(
        val title: String,
        val notes: String,
        val location: String,
        val speciesId: String?,
    )

    fun setViewMode(mode: CalendarViewMode) {
        viewMode.value = mode
    }

    fun selectDate(date: LocalDate, openDayView: Boolean = false) {
        selectedDate.value = date
        loadDraftForDate(date)
        if (openDayView) {
            viewMode.value = CalendarViewMode.DAY
        }
    }

    fun selectMonth(yearMonth: YearMonth) {
        selectedDate.value = yearMonth.atDay(1)
        viewMode.value = CalendarViewMode.MONTH
    }

    fun goToToday() {
        selectDate(CalendarDates.today(), openDayView = viewMode.value == CalendarViewMode.DAY)
    }

    fun stepPeriod(forward: Boolean) {
        val step = if (forward) 1 else -1
        when (viewMode.value) {
            CalendarViewMode.DAY -> selectDate(selectedDate.value.plusDays(step.toLong()))
            CalendarViewMode.MONTH -> {
                val ym = YearMonth.from(selectedDate.value).plusMonths(step.toLong())
                selectedDate.value = ym.atDay(1)
            }
            CalendarViewMode.YEAR -> {
                selectedDate.value = selectedDate.value.plusYears(step.toLong())
            }
        }
    }

    fun updateDraftTitle(value: String) {
        draftTitle.value = value
    }

    fun updateDraftNotes(value: String) {
        draftNotes.value = value
    }

    fun updateDraftLocation(value: String) {
        draftLocation.value = value
    }

    fun updateDraftSpeciesId(value: String?) {
        draftSpeciesId.value = value
    }

    fun saveDayEntry() {
        viewModelScope.launch {
            val date = selectedDate.value
            val dayStart = CalendarDates.dayStartEpochMs(date)
            val title = draftTitle.value.trim()
            val notes = draftNotes.value.trim()
            val location = draftLocation.value.trim()
            val speciesId = draftSpeciesId.value?.takeIf { it.isNotBlank() }

            if (title.isBlank() && notes.isBlank() && location.isBlank() && speciesId == null) {
                repository.deleteEntry(dayStart)
                return@launch
            }

            repository.saveEntry(
                CalendarDayEntry(
                    id = entriesByDay.value[dayStart]?.id ?: 0L,
                    dayStartEpochMs = dayStart,
                    title = title,
                    notes = notes,
                    speciesId = speciesId,
                    location = location,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun clearDayEntry() {
        viewModelScope.launch {
            val dayStart = CalendarDates.dayStartEpochMs(selectedDate.value)
            repository.deleteEntry(dayStart)
            draftTitle.value = ""
            draftNotes.value = ""
            draftLocation.value = ""
            draftSpeciesId.value = null
        }
    }

    fun periodLabel(state: CalendarUiState): String =
        when (state.viewMode) {
            CalendarViewMode.DAY -> state.selectedDate.format(DAY_FORMAT)
            CalendarViewMode.MONTH -> state.selectedDate.format(MONTH_FORMAT)
            CalendarViewMode.YEAR -> state.selectedDate.year.toString()
        }

    fun speciesOptions(): List<Species> = UkFieldCatalog.species

    private fun loadDraftForDate(date: LocalDate) {
        val entry = entriesByDay.value[CalendarDates.dayStartEpochMs(date)]
        draftTitle.value = entry?.title.orEmpty()
        draftNotes.value = entry?.notes.orEmpty()
        draftLocation.value = entry?.location.orEmpty()
        draftSpeciesId.value = entry?.speciesId
    }

    companion object {
        private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK)
        private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)
    }
}
