package uk.hunttrack.feature.culls

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.hunttrack.core.data.cull.CullRepository
import uk.hunttrack.core.data.marker.MarkerRepository
import uk.hunttrack.core.designsystem.sound.HuntSound
import uk.hunttrack.core.designsystem.sound.SoundManager
import uk.hunttrack.core.location.LocationRepository
import uk.hunttrack.domain.catalog.UkFieldCatalog
import uk.hunttrack.domain.geo.OsGridRef
import uk.hunttrack.domain.model.CullRecord
import uk.hunttrack.domain.model.FieldMarker
import uk.hunttrack.domain.model.MarkerCategory

data class CullUiState(
    val records: List<CullRecord> = emptyList(),
    val filtered: List<CullRecord> = emptyList(),
    val yearTotal: Int = 0,
    val filterYear: Int? = null,
    val filterSpeciesId: String? = null,
    val availableYears: List<Int> = emptyList(),
    val editing: CullRecord? = null,
    val showEditor: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class CullViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val cullRepository: CullRepository,
    private val markerRepository: MarkerRepository,
    private val locationRepository: LocationRepository,
    private val soundManager: SoundManager,
) : ViewModel() {

    private val filterYearState = MutableStateFlow<Int?>(Calendar.getInstance().get(Calendar.YEAR))
    private val filterSpeciesState = MutableStateFlow<String?>(null)
    private val editingState = MutableStateFlow<CullRecord?>(null)
    private val showEditorState = MutableStateFlow(false)
    private val messageState = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CullUiState> = combine(
        cullRepository.observeAll(),
        filterYearState,
        filterSpeciesState,
        editingState,
        combine(showEditorState, messageState) { show, msg -> show to msg },
    ) { records, year, speciesId, editing, showMsg ->
        val (showEditor, message) = showMsg
        val years = records.map {
            Calendar.getInstance().apply { timeInMillis = it.culledAtEpochMs }.get(Calendar.YEAR)
        }.distinct().sortedDescending()
        val filtered = records.filter { r ->
            val yOk = year == null || Calendar.getInstance().apply {
                timeInMillis = r.culledAtEpochMs
            }.get(Calendar.YEAR) == year
            val sOk = speciesId == null || r.speciesId == speciesId ||
                (speciesId == "_other" && r.speciesId == null)
            yOk && sOk
        }
        val yearTotal = filtered.sumOf { it.count.coerceAtLeast(1) }
        CullUiState(
            records = records,
            filtered = filtered,
            yearTotal = yearTotal,
            filterYear = year,
            filterSpeciesId = speciesId,
            availableYears = years.ifEmpty {
                listOf(Calendar.getInstance().get(Calendar.YEAR))
            },
            editing = editing,
            showEditor = showEditor,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CullUiState())

    fun setFilterYear(year: Int?) {
        filterYearState.value = year
    }

    fun setFilterSpecies(speciesId: String?) {
        filterSpeciesState.value = speciesId
    }

    fun openNew() {
        val fix = locationRepository.latestFix.value
            ?: locationRepository.fallbackFix()
        val grid = OsGridRef.latLngToOsGrid(fix.latitude, fix.longitude).orEmpty()
        val deer = UkFieldCatalog.species.firstOrNull()
        editingState.value = CullRecord(
            culledAtEpochMs = System.currentTimeMillis(),
            speciesId = deer?.id,
            speciesLabel = deer?.commonName ?: "Unknown",
            latitude = fix.latitude,
            longitude = fix.longitude,
            osGridRef = grid,
        )
        showEditorState.value = true
    }

    fun openEdit(record: CullRecord) {
        editingState.value = record
        showEditorState.value = true
    }

    fun dismissEditor() {
        showEditorState.value = false
        editingState.value = null
    }

    fun updateDraft(transform: (CullRecord) -> CullRecord) {
        editingState.value = editingState.value?.let(transform)
    }

    fun useCurrentGps() {
        viewModelScope.launch {
            val fix = locationRepository.bestFixOrNull()
                ?: locationRepository.lastKnown()
                ?: locationRepository.latestFix.value
                ?: locationRepository.fallbackFix()
            val grid = OsGridRef.latLngToOsGrid(fix.latitude, fix.longitude).orEmpty()
            updateDraft {
                it.copy(latitude = fix.latitude, longitude = fix.longitude, osGridRef = grid)
            }
        }
    }

    fun applyOsGridInput(raw: String) {
        val latLng = OsGridRef.osGridToLatLng(raw) ?: run {
            messageState.value = "Invalid OS Grid Reference"
            return
        }
        val display = OsGridRef.latLngToOsGrid(latLng.latitude, latLng.longitude) ?: raw.trim().uppercase()
        updateDraft {
            it.copy(
                latitude = latLng.latitude,
                longitude = latLng.longitude,
                osGridRef = display,
            )
        }
        messageState.value = null
    }

    fun save(placeMapPin: Boolean) {
        val draft = editingState.value ?: return
        if (draft.speciesLabel.isBlank()) {
            messageState.value = "Species is required"
            return
        }
        viewModelScope.launch {
            var linkedId = draft.linkedMarkerId
            if (placeMapPin && draft.latitude != null && draft.longitude != null) {
                linkedId = markerRepository.save(
                    FieldMarker(
                        id = linkedId ?: 0L,
                        category = MarkerCategory.CULL,
                        name = "${draft.speciesLabel} cull",
                        notes = draft.notes,
                        latitude = draft.latitude!!,
                        longitude = draft.longitude!!,
                        createdAtEpochMs = draft.culledAtEpochMs,
                        speciesId = draft.speciesId,
                    ),
                )
            }
            val grid = when {
                draft.osGridRef.isNotBlank() -> draft.osGridRef
                draft.latitude != null && draft.longitude != null ->
                    OsGridRef.latLngToOsGrid(draft.latitude!!, draft.longitude!!).orEmpty()
                else -> ""
            }
            cullRepository.save(
                draft.copy(
                    count = draft.count.coerceAtLeast(1),
                    osGridRef = grid,
                    linkedMarkerId = linkedId,
                ),
            )
            soundManager.play(HuntSound.SUCCESS)
            dismissEditor()
            messageState.value = "Cull saved"
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            cullRepository.delete(id)
            soundManager.play(HuntSound.SUCCESS)
            dismissEditor()
        }
    }

    fun clearMessage() {
        messageState.value = null
    }

    fun shareCsv(records: List<CullRecord>) {
        viewModelScope.launch {
            val csv = buildCsv(records)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "HunTracK cull log")
                putExtra(Intent.EXTRA_TEXT, csv)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                appContext.startActivity(
                    Intent.createChooser(intent, "Share cull CSV").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                messageState.value = "Could not share CSV"
            }
        }
    }

    private fun buildCsv(records: List<CullRecord>): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK)
        val header = listOf(
            "Date", "Species", "Sex", "Age", "Count", "OS Grid", "Latitude", "Longitude",
            "Location", "Carcass test", "Notes",
        ).joinToString(",")
        val rows = records.map { r ->
            listOf(
                df.format(Date(r.culledAtEpochMs)),
                r.speciesLabel,
                r.sex.label,
                r.ageClass.label,
                r.count.toString(),
                r.osGridRef,
                r.latitude?.toString().orEmpty(),
                r.longitude?.toString().orEmpty(),
                r.locationName,
                r.carcassTestStatus.label,
                r.notes,
            ).joinToString(",") { csvEscape(it) }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
