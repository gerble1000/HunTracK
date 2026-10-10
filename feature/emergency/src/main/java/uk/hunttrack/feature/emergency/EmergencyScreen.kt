package uk.hunttrack.feature.emergency

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.hunttrack.core.designsystem.components.GlassPanel
import uk.hunttrack.core.designsystem.components.MetricRow
import uk.hunttrack.core.designsystem.components.RegisterLazyListScrollToTop
import uk.hunttrack.core.designsystem.components.ScreenBackground
import uk.hunttrack.core.designsystem.components.SectionLabel
import uk.hunttrack.core.designsystem.theme.HuntColors

@Composable
fun EmergencyRoute(viewModel: EmergencyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EmergencyScreen(
        state = state,
        onPinInput = viewModel::setPinInput,
        onUnlockPin = viewModel::tryUnlockWithPin,
        onUnlockBio = viewModel::unlockFromBiometric,
        onShowPublic = viewModel::showPublic,
        onSave = viewModel::save,
    )
}

@Composable
fun EmergencyScreen(
    state: EmergencyUiState,
    onPinInput: (String) -> Unit,
    onUnlockPin: () -> Unit,
    onUnlockBio: () -> Unit,
    onShowPublic: () -> Unit,
    onSave: (
        fullName: String,
        bloodType: String,
        allergies: String,
        medical: String,
        medication: String,
        notes: String,
        contactName: String,
        contactPhone: String,
        newPin: String,
        confirmPin: String,
        biometricEnabled: Boolean,
        onResult: (String?) -> Unit,
    ) -> Unit,
) {
    val context = LocalContext.current
    val profile = state.profile
    val hasPin = !profile.pinHash.isNullOrBlank()

    ScreenBackground(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        RegisterLazyListScrollToTop(listState)
        if (state.mode == EmergencyMode.PUBLIC) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("EMERGENCY", style = MaterialTheme.typography.displayMedium, color = HuntColors.Danger)
                    GunIceSponsor()
                    Text("Public display — edit locked", style = MaterialTheme.typography.bodyMedium)
                }
                item {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Text(profile.fullName.ifBlank { "Name not set" }, style = MaterialTheme.typography.headlineMedium)
                        MetricRow("Blood type", profile.bloodType.ifBlank { "—" })
                        MetricRow("Allergies", profile.allergies.ifBlank { "—" })
                        MetricRow("Medical", profile.medicalInfo.ifBlank { "—" })
                        MetricRow("Medication", profile.medication.ifBlank { "—" })
                        if (profile.notes.isNotBlank()) {
                            Text(profile.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                item { SectionLabel("Emergency contacts") }
                items(profile.contacts) { c ->
                    GlassPanel(Modifier.fillMaxWidth()) {
                        Text(c.name, style = MaterialTheme.typography.titleLarge)
                        Text(c.phone, style = MaterialTheme.typography.headlineMedium, color = HuntColors.Bronze)
                        if (c.relationship.isNotBlank()) Text(c.relationship, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (profile.contacts.isEmpty()) {
                    item { Text("No contacts stored yet.", color = HuntColors.Mist) }
                }
                item {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        SectionLabel("Unlock to edit")
                        if (hasPin) {
                            OutlinedTextField(
                                value = state.pinInput,
                                onValueChange = onPinInput,
                                label = { Text("PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { onUnlockPin() }),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                "No PIN set yet — open the editor to add your details and choose a PIN.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HuntColors.Mist,
                            )
                        }
                        if (state.error != null) {
                            Text(state.error, color = HuntColors.Danger)
                        }
                        Button(onClick = onUnlockPin, modifier = Modifier.fillMaxWidth()) {
                            Text(if (hasPin) "Unlock with PIN" else "Open editor (set PIN inside)")
                        }
                        if (profile.biometricEnabled) {
                            Button(
                                onClick = {
                                    val activity = context as? FragmentActivity
                                    if (activity != null) {
                                        launchBiometric(activity, onSuccess = onUnlockBio)
                                    } else {
                                        onUnlockBio()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Unlock with biometrics") }
                        }
                    }
                }
            }
        } else {
            var fullName by remember { mutableStateOf(profile.fullName) }
            var blood by remember { mutableStateOf(profile.bloodType) }
            var allergies by remember { mutableStateOf(profile.allergies) }
            var medical by remember { mutableStateOf(profile.medicalInfo) }
            var medication by remember { mutableStateOf(profile.medication) }
            var notes by remember { mutableStateOf(profile.notes) }
            var contactName by remember { mutableStateOf(profile.contacts.firstOrNull()?.name.orEmpty()) }
            var contactPhone by remember { mutableStateOf(profile.contacts.firstOrNull()?.phone.orEmpty()) }
            var newPin by remember { mutableStateOf("") }
            var confirmPin by remember { mutableStateOf("") }
            var bio by remember { mutableStateOf(profile.biometricEnabled) }
            var saveError by remember { mutableStateOf<String?>(null) }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text("Edit emergency profile", style = MaterialTheme.typography.headlineLarge)
                    TextButton(onClick = onShowPublic) { Text("Back to public view") }
                }
                item {
                    GlassPanel(Modifier.fillMaxWidth()) {
                        OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(blood, { blood = it }, label = { Text("Blood type") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(allergies, { allergies = it }, label = { Text("Allergies") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(medical, { medical = it }, label = { Text("Medical information") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(medication, { medication = it }, label = { Text("Medication") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(contactName, { contactName = it }, label = { Text("Contact name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(contactPhone, { contactPhone = it }, label = { Text("Contact phone") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            newPin,
                            { newPin = it.filter(Char::isDigit).take(8) },
                            label = { Text(if (hasPin) "Change PIN (4+ digits, blank = keep)" else "Set PIN (4+ digits)") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            confirmPin,
                            { confirmPin = it.filter(Char::isDigit).take(8) },
                            label = { Text("Confirm PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Biometric unlock", style = MaterialTheme.typography.titleMedium)
                            Switch(checked = bio, onCheckedChange = { bio = it })
                        }
                        if (saveError != null) {
                            Text(saveError!!, color = HuntColors.Danger)
                        }
                        Button(
                            onClick = {
                                onSave(
                                    fullName,
                                    blood,
                                    allergies,
                                    medical,
                                    medication,
                                    notes,
                                    contactName,
                                    contactPhone,
                                    newPin,
                                    confirmPin,
                                    bio,
                                ) { err -> saveError = err }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Save profile") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GunIceSponsor() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_gun_ice_logo),
            contentDescription = "Gun-ICE logo",
            modifier = Modifier.size(56.dp),
        )
        Text(
            "Sponsored by Gun-ICE",
            style = MaterialTheme.typography.titleMedium,
            color = HuntColors.Cream,
        )
    }
}

private fun launchBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
    val can = BiometricManager.from(activity)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
    if (can != BiometricManager.BIOMETRIC_SUCCESS) {
        onSuccess()
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock emergency editor")
            .setSubtitle("Confirm biometrics to edit profile")
            .setNegativeButtonText("Cancel")
            .build(),
    )
}
