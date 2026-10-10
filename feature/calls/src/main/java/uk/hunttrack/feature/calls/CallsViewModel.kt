package uk.hunttrack.feature.calls

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.hunttrack.core.data.audio.CallAudioEngine
import uk.hunttrack.core.data.phase2.CallsRepository
import uk.hunttrack.core.data.weather.WeatherRepository
import uk.hunttrack.core.location.LocationRepository
import uk.hunttrack.domain.model.AnimalCall
import uk.hunttrack.domain.model.AudioPack
import uk.hunttrack.domain.model.CallJournalEntry

data class CallsUiState(
    val calls: List<AnimalCall> = emptyList(),
    val journal: List<CallJournalEntry> = emptyList(),
    val packs: List<AudioPack> = emptyList(),
    val selected: AnimalCall? = null,
    val playing: Boolean = false,
    val looping: Boolean = false,
    val recording: Boolean = false,
    val wavePhase: Float = 0f,
    val tab: CallsTab = CallsTab.LIBRARY,
    val statusMessage: String? = null,
    val downloadingPackId: String? = null,
    val importingAudio: Boolean = false,
)

enum class CallsTab { LIBRARY, JOURNAL, PACKS, RECORD }

@HiltViewModel
class CallsViewModel @Inject constructor(
    private val callsRepository: CallsRepository,
    private val audioEngine: CallAudioEngine,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val selected = MutableStateFlow<AnimalCall?>(null)
    private val playing = MutableStateFlow(false)
    private val looping = MutableStateFlow(false)
    private val recording = MutableStateFlow(false)
    private val wave = MutableStateFlow(0f)
    private val tab = MutableStateFlow(CallsTab.LIBRARY)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val downloadingPackId = MutableStateFlow<String?>(null)
    private val importingAudio = MutableStateFlow(false)
    private var recordFile: File? = null

    private data class PlaybackFlags(
        val playing: Boolean,
        val looping: Boolean,
        val recording: Boolean,
        val wave: Float,
    )

    private data class TabFlags(
        val tab: CallsTab,
        val status: String?,
        val downloading: String?,
        val importing: Boolean,
    )

    val uiState: StateFlow<CallsUiState> = combine(
        callsRepository.observeBuiltInCalls(),
        callsRepository.observeUserCalls(),
        callsRepository.observeJournal(),
        callsRepository.observePacks(),
        combine(
            combine(
                tab,
                statusMessage,
                downloadingPackId,
                importingAudio,
            ) { t, status, downloading, importing ->
                TabFlags(t, status, downloading, importing)
            },
            combine(
                selected,
                combine(playing, looping, recording, wave) { p, l, r, w -> PlaybackFlags(p, l, r, w) },
            ) { sel, playback -> sel to playback },
        ) { tabFlags, selection -> tabFlags to selection },
    ) { builtIn, userCalls, journal, packs, extras ->
        val (tabFlags, selection) = extras
        val (sel, playback) = selection
        val resolvedSelected = sel?.let { s ->
            (builtIn + userCalls).find { it.id == s.id } ?: s
        }
        CallsUiState(
            calls = builtIn + userCalls,
            journal = journal,
            packs = packs,
            selected = resolvedSelected,
            playing = playback.playing,
            looping = playback.looping,
            recording = playback.recording,
            wavePhase = playback.wave,
            tab = tabFlags.tab,
            statusMessage = tabFlags.status,
            downloadingPackId = tabFlags.downloading,
            importingAudio = tabFlags.importing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallsUiState())

    init {
        viewModelScope.launch {
            while (true) {
                if (playing.value || recording.value) {
                    wave.value = (wave.value + 0.08f) % 1f
                }
                delay(50)
            }
        }
    }

    fun setTab(value: CallsTab) {
        tab.value = value
    }

    fun select(call: AnimalCall?) {
        selected.value = call
        statusMessage.value = null
    }

    fun toggleLoop() {
        looping.value = !looping.value
    }

    fun playSelected() {
        val call = selected.value ?: return
        viewModelScope.launch {
            val path = call.filePath
            if (path.isNullOrBlank() || !File(path).exists()) {
                statusMessage.value = "Download the audio pack first (Packs tab) for real animal recordings."
                tab.value = CallsTab.PACKS
                return@launch
            }
            audioEngine.playFile(path, looping.value)
            playing.value = true
            statusMessage.value = null
        }
    }

    fun stopPlayback() {
        audioEngine.stop()
        playing.value = false
    }

    fun startRecording() {
        if (!audioEngine.hasRecordPermission()) {
            statusMessage.value = "Microphone permission is required to record calls."
            return
        }
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "call_${System.currentTimeMillis()}.m4a")
        val result = audioEngine.startRecording(file)
        if (result.isFailure) {
            recording.value = false
            recordFile = null
            statusMessage.value = "Could not start recording: ${
                result.exceptionOrNull()?.message ?: "check microphone permission"
            }"
            return
        }
        recordFile = file
        recording.value = true
        statusMessage.value = null
    }

    fun onRecordingPermissionDenied(permanently: Boolean) {
        statusMessage.value = if (permanently) {
            "Microphone access was denied. Enable it in Settings › Apps › HunTracK › Permissions."
        } else {
            "Microphone permission is required to record calls."
        }
    }

    fun stopAndSaveRecording(name: String, speciesId: String, notes: String) {
        viewModelScope.launch {
            audioEngine.stopRecording()
            recording.value = false
            val file = recordFile
            recordFile = null
            if (file == null || !file.exists() || file.length() < 512) {
                file?.delete()
                statusMessage.value = "Recording failed — grant microphone access and try again."
                return@launch
            }
            val fix = locationRepository.lastKnown()
            val weather = runCatching {
                weatherRepository.getWeather(
                    fix?.latitude ?: locationRepository.fallbackFix().latitude,
                    fix?.longitude ?: locationRepository.fallbackFix().longitude,
                )
            }.getOrNull()
            callsRepository.addUserCall(
                name = name.ifBlank { "Field recording" },
                speciesId = speciesId,
                category = "Recorded",
                description = notes,
                filePath = file.absolutePath,
                latitude = fix?.latitude,
                longitude = fix?.longitude,
                weatherJson = weather?.let { """{"temp":${it.temperatureC},"wind":${it.windSpeedKmh}}""" },
                notes = notes,
            )
            tab.value = CallsTab.LIBRARY
            statusMessage.value = "Recording saved to your library."
        }
    }

    fun importCustomCall(uri: Uri, name: String, speciesId: String, notes: String) {
        viewModelScope.launch {
            importingAudio.value = true
            statusMessage.value = "Importing audio…"
            val fix = locationRepository.lastKnown()
            val weather = runCatching {
                weatherRepository.getWeather(
                    fix?.latitude ?: locationRepository.fallbackFix().latitude,
                    fix?.longitude ?: locationRepository.fallbackFix().longitude,
                )
            }.getOrNull()
            val result = callsRepository.importUserCall(
                sourceUri = uri,
                name = name.ifBlank { "Custom call" },
                speciesId = speciesId,
                description = notes,
                latitude = fix?.latitude,
                longitude = fix?.longitude,
                weatherJson = weather?.let { """{"temp":${it.temperatureC},"wind":${it.windSpeedKmh}}""" },
                notes = notes,
            )
            importingAudio.value = false
            if (result.isSuccess) {
                tab.value = CallsTab.LIBRARY
                statusMessage.value = "Custom call saved — ready to play."
            } else {
                statusMessage.value = "Import failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            }
        }
    }

    fun logCallUse(response: String, distanceM: Int?, notes: String) {
        viewModelScope.launch {
            val call = selected.value
            val fix = locationRepository.lastKnown()
            callsRepository.addJournal(
                CallJournalEntry(
                    callId = call?.id,
                    speciesId = call?.speciesId,
                    notes = notes,
                    response = response,
                    distanceEstimateMeters = distanceM,
                    latitude = fix?.latitude,
                    longitude = fix?.longitude,
                    weatherJson = null,
                    recordedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun deleteJournal(id: Long) {
        viewModelScope.launch { callsRepository.deleteJournal(id) }
    }

    /** Delete a specific custom call. */
    fun deleteCustomCall(call: AnimalCall) {
        viewModelScope.launch {
            val id = call.id.removePrefix("user_").toLongOrNull()
            if (id == null) {
                statusMessage.value = "Could not delete that call."
                return@launch
            }
            callsRepository.deleteUserCall(id)
            if (selected.value?.id == call.id) {
                selected.value = null
            }
            statusMessage.value = "Deleted '${call.name}' from your custom calls."
        }
    }

    /** Delete the most recently added custom call (last in the list). */
    fun deleteLastCustomCall() {
        viewModelScope.launch {
            val last = uiState.value.calls.firstOrNull { !it.isBuiltIn }
            if (last == null) {
                statusMessage.value = "No custom calls to delete yet — save a call first."
                return@launch
            }
            val id = last.id.removePrefix("user_").toLongOrNull()
            if (id == null) {
                statusMessage.value = "Could not delete that call."
                return@launch
            }
            callsRepository.deleteUserCall(id)
            if (selected.value?.id == last.id) {
                selected.value = null
            }
            statusMessage.value = "Deleted '${last.name}' from your custom calls."
        }
    }

    fun downloadPack(id: String) {
        viewModelScope.launch {
            downloadingPackId.value = id
            statusMessage.value = "Downloading real animal recordings…"
            val result = callsRepository.downloadPack(id)
            downloadingPackId.value = null
            statusMessage.value = if (result.isSuccess) {
                "Pack ready — real field recordings unlocked."
            } else {
                "Download failed: ${result.exceptionOrNull()?.message ?: "network error"}"
            }
        }
    }

    override fun onCleared() {
        audioEngine.stop()
        audioEngine.stopRecording()
        super.onCleared()
    }
}
