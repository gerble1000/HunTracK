package uk.hunttrack.feature.calls

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.sin
import uk.hunttrack.core.designsystem.components.ClickableGlassCard
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.HintingOptionRow
import uk.hunttrack.core.designsystem.components.HuntButton
import uk.hunttrack.core.designsystem.components.HuntTextButton
import uk.hunttrack.core.designsystem.components.RegisterLazyListScrollToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.catalog.UkFieldCatalog
import uk.hunttrack.domain.model.AnimalCall

@Composable
fun CallsRoute(viewModel: CallsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CallsScreen(
        state = state,
        onTab = viewModel::setTab,
        onSelect = viewModel::select,
        onPlay = viewModel::playSelected,
        onStop = viewModel::stopPlayback,
        onLoop = viewModel::toggleLoop,
        onStartRec = viewModel::startRecording,
        onStopRec = viewModel::stopAndSaveRecording,
        onImport = viewModel::importCustomCall,
        onRecordingPermissionDenied = viewModel::onRecordingPermissionDenied,
        onLog = viewModel::logCallUse,
        onDeleteJournal = viewModel::deleteJournal,
        onDownloadPack = viewModel::downloadPack,
        onDeleteCustomCall = viewModel::deleteLastCustomCall,
        onDeleteCall = viewModel::deleteCustomCall,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(
    state: CallsUiState,
    onTab: (CallsTab) -> Unit,
    onSelect: (AnimalCall?) -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onLoop: () -> Unit,
    onStartRec: () -> Unit,
    onStopRec: (name: String, speciesId: String, notes: String) -> Unit,
    onImport: (Uri, name: String, speciesId: String, notes: String) -> Unit,
    onRecordingPermissionDenied: (permanently: Boolean) -> Unit,
    onLog: (response: String, distanceM: Int?, notes: String) -> Unit,
    onDeleteJournal: (Long) -> Unit,
    onDownloadPack: (String) -> Unit,
    onDeleteCustomCall: () -> Unit = {},
    onDeleteCall: (AnimalCall) -> Unit = {},
) {
    var recName by remember { mutableStateOf("") }
    var recSpecies by remember { mutableStateOf("red_deer") }
    var recNotes by remember { mutableStateOf("") }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAudioLabel by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    var micPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var micPermissionRequested by remember { mutableStateOf(false) }
    LaunchedEffect(state.tab) {
        if (state.tab == CallsTab.RECORD) {
            micPermissionGranted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micPermissionGranted = granted
        if (granted) {
            onStartRec()
        } else {
            val permanently = micPermissionRequested && activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } == true
            onRecordingPermissionDenied(permanently)
        }
    }
    val speciesOptions = remember { UkFieldCatalog.species }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedAudioUri = uri
            selectedAudioLabel = uri.lastPathSegment ?: "Selected audio"
        }
    }

    ScreenBackground(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        RegisterLazyListScrollToTop(listState)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Animal calls", style = MaterialTheme.typography.headlineLarge, color = HuntColors.Cream)
                Text(
                    "Play built-in calls or add your own audio files and recordings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HuntColors.Mist,
                )
            }
            state.statusMessage?.let { msg ->
                item {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Text(msg, color = HuntColors.Gold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            item {
                HintingOptionRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CallsTab.entries.forEach { t ->
                        FilterChip(
                            selected = state.tab == t,
                            onClick = { onTab(t) },
                            label = {
                                Text(
                                    when (t) {
                                        CallsTab.RECORD -> "Add call"
                                        else -> t.name.lowercase().replaceFirstChar { it.titlecase() }
                                    },
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HuntColors.Bronze,
                                selectedLabelColor = HuntColors.Ink,
                                containerColor = HuntColors.Slate,
                                labelColor = HuntColors.Cream,
                            ),
                        )
                    }
                }
            }
            when (state.tab) {
                CallsTab.LIBRARY -> {
                    item {
                        ClickableGlassCard(
                            onClick = { onTab(CallsTab.RECORD) },
                            hint = "Upload or record your own call ›",
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "+ Add custom call",
                                style = MaterialTheme.typography.titleLarge,
                                color = HuntColors.BronzeBright,
                            )
                            Text(
                                "Upload an audio file from your phone or record one in the field.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HuntColors.Mist,
                            )
                        }
                    }
                    val customCalls = state.calls.filter { !it.isBuiltIn }
                    val builtInCalls = state.calls.filter { it.isBuiltIn }
                    if (customCalls.isNotEmpty()) {
                        item { SectionLabel("Your custom calls") }
                        items(customCalls, key = { it.id }) { call ->
                            CallLibraryCard(call = call, onSelect = onSelect, onDelete = onDeleteCall)
                        }
                    }
                    item { SectionLabel("Built-in library") }
                    items(builtInCalls, key = { it.id }) { call ->
                        CallLibraryCard(call = call, onSelect = onSelect)
                    }
                }
                CallsTab.JOURNAL -> {
                    if (state.journal.isEmpty()) {
                        item { Text("No call journal entries yet.", color = HuntColors.Mist) }
                    }
                    items(state.journal) { entry ->
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Text(entry.response.ifBlank { "Response logged" }, style = MaterialTheme.typography.titleMedium, color = HuntColors.Cream)
                            Text(entry.notes, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
                            entry.distanceEstimateMeters?.let {
                                Text("Distance ~${it}m", style = MaterialTheme.typography.bodySmall, color = HuntColors.Mist)
                            }
                            HuntTextButton(onClick = { onDeleteJournal(entry.id) }, text = "Delete")
                        }
                    }
                }
                CallsTab.PACKS -> {
                    item {
                        Text(
                            "Downloads real CC0 / open wildlife recordings to your device for offline play.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HuntColors.Mist,
                        )
                    }
                    items(state.packs) { pack ->
                        GlassPanel(Modifier.fillMaxWidth()) {
                            Text(pack.name, style = MaterialTheme.typography.titleLarge, color = HuntColors.Cream)
                            Text(pack.description, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
                            Text(
                                "${pack.recordingCount} recordings · ~${pack.sizeBytes / 1024} KB",
                                style = MaterialTheme.typography.bodySmall,
                                color = HuntColors.Mist,
                            )
                            if (state.downloadingPackId == pack.id) {
                                LinearProgressIndicator(
                                    progress = { pack.downloadProgress.coerceIn(0.05f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    color = HuntColors.Bronze,
                                    trackColor = HuntColors.Slate,
                                )
                                Text("Downloading…", color = HuntColors.Gold)
                            } else if (pack.downloaded) {
                                Text("Downloaded · offline ready", color = HuntColors.Success)
                            } else {
                                HuntButton(
                                    onClick = { onDownloadPack(pack.id) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    text = "Download real audio",
                                )
                            }
                        }
                    }
                }
                CallsTab.RECORD -> {
                    item {
                        Text(
                            "Add a custom call — upload audio or record with the mic.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HuntColors.Mist,
                        )
                    }
                    item {
                        GlassPanel(Modifier.fillMaxWidth()) {
                            SectionLabel("Upload audio file")
                            Text(
                                "Create a custom call from an MP3, M4A, WAV or other audio file on your phone.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HuntColors.Mist,
                            )
                            OutlinedTextField(
                                value = recName,
                                onValueChange = { recName = it },
                                label = { Text("Call name") },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                            Text(
                                "Species",
                                style = MaterialTheme.typography.labelLarge,
                                color = HuntColors.Cream,
                                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                            )
                            HintingOptionRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                speciesOptions.forEach { species ->
                                    FilterChip(
                                        selected = recSpecies == species.id,
                                        onClick = { recSpecies = species.id },
                                        label = { Text(species.commonName) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = HuntColors.Bronze,
                                            selectedLabelColor = HuntColors.Ink,
                                            containerColor = HuntColors.Slate,
                                            labelColor = HuntColors.Cream,
                                        ),
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = recNotes,
                                onValueChange = { recNotes = it },
                                label = { Text("Notes / when to use") },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                            HuntButton(
                                onClick = { pickAudio.launch(arrayOf("audio/*")) },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                text = if (selectedAudioUri == null) "Choose audio file" else "Change audio file",
                            )
                            selectedAudioLabel?.let { label ->
                                Text(
                                    "Selected: $label",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HuntColors.Gold,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            HuntButton(
                                onClick = {
                                    val uri = selectedAudioUri ?: return@HuntButton
                                    onImport(uri, recName, recSpecies, recNotes)
                                    selectedAudioUri = null
                                    selectedAudioLabel = null
                                    recName = ""
                                    recNotes = ""
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                text = if (state.importingAudio) "Saving…" else "Save custom call",
                                enabled = selectedAudioUri != null && !state.importingAudio,
                            )
                            Button(
                                onClick = onDeleteCustomCall,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HuntColors.Danger,
                                    contentColor = HuntColors.Cream,
                                ),
                            ) {
                                Text("Delete call")
                            }
                            Text(
                                "Deletes the most recently added custom call — active once a call is saved.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HuntColors.Mist,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    item {
                        GlassPanel(Modifier.fillMaxWidth()) {
                            SectionLabel("Record in field")
                            Text(
                                "Or record your own call with the microphone.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HuntColors.Mist,
                            )
                            Waveform(state.wavePhase, Modifier.fillMaxWidth().height(48.dp).padding(vertical = 12.dp))
                            if (!state.recording) {
                                HuntButton(
                                    onClick = {
                                        if (micPermissionGranted) {
                                            onStartRec()
                                        } else {
                                            micPermissionRequested = true
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    text = "Start recording",
                                )
                            } else {
                                HuntButton(
                                    onClick = { onStopRec(recName, recSpecies, recNotes) },
                                    modifier = Modifier.fillMaxWidth(),
                                    text = "Stop & save recording",
                                )
                            }
                            Text(
                                "Uses the call name and species selected above. Saved as AAC on device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HuntColors.Mist,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    state.selected?.let { call ->
        var response by remember { mutableStateOf("") }
        var distance by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        val ready = !call.filePath.isNullOrBlank()
        ModalBottomSheet(
            onDismissRequest = { onSelect(null); onStop() },
            containerColor = HuntColors.ForestMid,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(call.name, style = MaterialTheme.typography.headlineMedium, color = HuntColors.Cream)
                Text(call.description, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
                if (call.credit.isNotBlank()) {
                    Text(call.credit, style = MaterialTheme.typography.bodySmall, color = HuntColors.Bronze)
                }
                Waveform(state.wavePhase, Modifier.fillMaxWidth().height(56.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HuntButton(onClick = onPlay, text = if (ready) "Play" else "Get pack")
                    HuntButton(onClick = onStop, text = "Stop")
                    FilterChip(
                        selected = state.looping,
                        onClick = onLoop,
                        label = { Text("Loop") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HuntColors.Bronze,
                            selectedLabelColor = HuntColors.Ink,
                            labelColor = HuntColors.Cream,
                        ),
                    )
                }
                if (!ready) {
                    Text("This call needs its offline pack before play.", color = HuntColors.Gold)
                }
                SectionLabel("Call journal")
                OutlinedTextField(response, { response = it }, label = { Text("Response heard") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(distance, { distance = it }, label = { Text("Distance (m)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                HuntButton(
                    onClick = {
                        onLog(response, distance.toIntOrNull(), notes)
                        onSelect(null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    text = "Save journal entry",
                )
                if (!call.isBuiltIn) {
                    Button(
                        onClick = {
                            onDeleteCall(call)
                            onSelect(null)
                            onStop()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HuntColors.Danger,
                            contentColor = HuntColors.Cream,
                        ),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete this call")
                    }
                }
            }
        }
    }
}

@Composable
private fun CallLibraryCard(
    call: AnimalCall,
    onSelect: (AnimalCall) -> Unit,
    onDelete: (AnimalCall) -> Unit = {},
) {
    val ready = !call.filePath.isNullOrBlank()
    ClickableGlassCard(
        onClick = { onSelect(call) },
        hint = "Tap to play & log ›",
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(call.name, style = MaterialTheme.typography.titleLarge, color = HuntColors.Cream)
                Text(
                    "${call.category} · ${UkFieldCatalog.speciesById(call.speciesId)?.commonName ?: call.speciesId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = HuntColors.Mist,
                )
                if (call.description.isNotBlank()) {
                    Text(call.description, style = MaterialTheme.typography.bodyMedium, color = HuntColors.Mist)
                }
                Text(
                    when {
                        !call.isBuiltIn && ready -> "Custom call · ${call.category.lowercase()} · ready to play"
                        ready -> "Ready · real recording"
                        else -> "Needs pack download"
                    },
                    color = if (ready) HuntColors.Success else HuntColors.Gold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (!call.isBuiltIn) {
                IconButton(onClick = { onDelete(call) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete ${call.name}",
                        tint = HuntColors.Danger,
                    )
                }
            }
        }
    }
}

@Composable
private fun Waveform(phase: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val mid = size.height / 2
        val steps = 48
        for (i in 0 until steps) {
            val x = size.width * i / steps
            val amp = (sin((i * 0.45f + phase * 12f).toDouble()) * size.height * 0.35).toFloat()
            drawLine(HuntColors.BronzeBright, Offset(x, mid - amp), Offset(x, mid + amp), strokeWidth = 3f)
        }
    }
}
