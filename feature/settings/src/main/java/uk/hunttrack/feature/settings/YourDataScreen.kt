package uk.hunttrack.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.hunttrack.core.data.export.FieldBackupRepository
import uk.hunttrack.core.data.export.FieldCounts
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.HuntButton
import uk.hunttrack.core.designsystem.components.RegisterLazyListScrollToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors
import uk.hunttrack.domain.export.FieldBackupFormat

@HiltViewModel
class YourDataViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val repository: FieldBackupRepository,
) : ViewModel() {
    val counts: StateFlow<FieldCounts> = repository.observeCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FieldCounts())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun share(includeMedical: Boolean) {
        viewModelScope.launch {
            val backup = runCatching { repository.export(includeMedical) }.getOrElse {
                _message.value = "Could not build the backup."
                return@launch
            }
            val dir = File(context.cacheDir, "field-backup").apply { mkdirs() }
            val zip = File(dir, FieldBackupFormat.ZIP_NAME)
            zip.writeBytes(repository.zip(backup))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "HunTracK field backup")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(share, "Save HunTracK backup")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(chooser) }
                .onSuccess {
                    _message.value = "Backup ready: ${backup.permissions.size} permissions, " +
                        "${backup.boundaries.size} boundaries, ${backup.markers.size} markers, " +
                        "${backup.routes.size} tracks. " +
                        if (backup.includesMedicalProfile) "Medical profile included." else "Medical profile left out."
                }
                .onFailure { _message.value = "Could not open the share sheet." }
        }
    }

    fun import(uri: Uri, applyMedical: Boolean) {
        viewModelScope.launch {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes == null) {
                _message.value = "Could not read that file."
                return@launch
            }
            _message.value = runCatching { repository.importBytes(bytes, applyMedical) }
                .getOrElse { it.message ?: "Could not import that backup." }
        }
    }
}

@Composable
fun YourDataRoute(viewModel: YourDataViewModel = hiltViewModel()) {
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val includeMedical = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val applyMedical = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.import(uri, applyMedical.value)
        }
    }
    YourDataScreen(
        counts = counts,
        message = message,
        includeMedical = includeMedical.value,
        applyMedical = applyMedical.value,
        onIncludeMedical = { includeMedical.value = it },
        onApplyMedical = { applyMedical.value = it },
        onShare = { viewModel.share(includeMedical.value) },
        onImport = { picker.launch(arrayOf("application/json", "application/zip", "application/octet-stream", "*/*")) },
    )
}

@Composable
fun YourDataScreen(
    counts: FieldCounts,
    message: String?,
    includeMedical: Boolean,
    applyMedical: Boolean,
    onIncludeMedical: (Boolean) -> Unit,
    onApplyMedical: (Boolean) -> Unit,
    onShare: () -> Unit,
    onImport: () -> Unit,
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
                Text("Your data", style = MaterialTheme.typography.headlineLarge, color = HuntColors.Cream)
                Text(
                    "Export stays on this phone until you share or save it. " +
                        "The zip holds JSON plus GPX, KML and GeoJSON.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HuntColors.Mist,
                )
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("On this device")
                    Text("${counts.permissions} permissions", color = HuntColors.Cream)
                    Text("${counts.boundaries} boundaries", color = HuntColors.Cream)
                    Text("${counts.markers} markers", color = HuntColors.Cream)
                    Text("${counts.routes} tracks", color = HuntColors.Cream)
                }
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Export")
                    Text(
                        "Photos and PDFs stay on the device. The PIN and biometric unlock are never written into the file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Mist,
                    )
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Include medical profile", color = HuntColors.Cream, modifier = Modifier.weight(1f))
                        Switch(checked = includeMedical, onCheckedChange = onIncludeMedical)
                    }
                    Text(
                        "Off by default. Turn on only if you want blood type, allergies and emergency contacts in the file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Mist,
                    )
                    HuntButton(
                        onClick = onShare,
                        text = "Share backup",
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            item {
                GlassPanel(Modifier.fillMaxWidth()) {
                    SectionLabel("Import")
                    Text(
                        "Adds the permissions, boundaries, markers and tracks from a HunTracK backup. It does not delete what is already here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HuntColors.Mist,
                    )
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Apply medical profile", color = HuntColors.Cream, modifier = Modifier.weight(1f))
                        Switch(checked = applyMedical, onCheckedChange = onApplyMedical)
                    }
                    HuntButton(
                        onClick = onImport,
                        text = "Import backup",
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            if (!message.isNullOrBlank()) {
                item {
                    Text(message, color = HuntColors.Gold, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
