package uk.hunttrack.feature.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.hunttrack.core.data.phase2.EmergencyRepository
import uk.hunttrack.domain.model.EmergencyContact
import uk.hunttrack.domain.model.EmergencyProfile

data class EmergencyUiState(
    val profile: EmergencyProfile = EmergencyProfile(),
    val mode: EmergencyMode = EmergencyMode.PUBLIC,
    val unlocked: Boolean = false,
    val pinInput: String = "",
    val error: String? = null,
)

enum class EmergencyMode { PUBLIC, EDIT }

@HiltViewModel
class EmergencyViewModel @Inject constructor(
    private val repository: EmergencyRepository,
) : ViewModel() {

    private val mode = MutableStateFlow(EmergencyMode.PUBLIC)
    private val unlocked = MutableStateFlow(false)
    private val pinInput = MutableStateFlow("")
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<EmergencyUiState> = combine(
        repository.observeProfile(),
        mode,
        unlocked,
        pinInput,
        error,
    ) { profile, m, u, pin, err ->
        EmergencyUiState(profile, m, u, pin, err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmergencyUiState())

    fun setPinInput(value: String) {
        pinInput.value = value.filter { it.isDigit() }.take(8)
        if (error.value != null) error.value = null
    }

    fun tryUnlockWithPin() {
        viewModelScope.launch {
            val profile = repository.getProfile()
            if (!repository.hasPin(profile.pinHash)) {
                unlocked.value = true
                mode.value = EmergencyMode.EDIT
                error.value = null
                return@launch
            }
            val entered = pinInput.value.trim()
            if (entered.length < 4) {
                error.value = "Enter your PIN (4+ digits)"
                return@launch
            }
            if (repository.verifyPin(entered, profile.pinHash)) {
                repository.migratePinHashIfNeeded(entered, profile.pinHash)
                unlocked.value = true
                mode.value = EmergencyMode.EDIT
                error.value = null
                pinInput.value = ""
            } else {
                error.value = "Incorrect PIN — try again"
            }
        }
    }

    fun unlockFromBiometric() {
        unlocked.value = true
        mode.value = EmergencyMode.EDIT
        error.value = null
    }

    fun showPublic() {
        mode.value = EmergencyMode.PUBLIC
        unlocked.value = false
        error.value = null
    }

    /**
     * @return error message if save rejected, null on success.
     */
    fun save(
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
    ) {
        viewModelScope.launch {
            val pin = newPin.filter { it.isDigit() }
            val confirm = confirmPin.filter { it.isDigit() }
            val existing = repository.getProfile()
            val hasExistingPin = repository.hasPin(existing.pinHash)

            when {
                pin.isNotEmpty() && pin.length < 4 -> {
                    onResult("PIN must be at least 4 digits")
                    return@launch
                }
                pin.isNotEmpty() && pin != confirm -> {
                    onResult("PIN and confirm PIN do not match")
                    return@launch
                }
                !hasExistingPin && pin.isEmpty() -> {
                    onResult("Set a PIN (4+ digits) to lock the editor")
                    return@launch
                }
            }

            val contacts = buildList {
                if (contactName.isNotBlank() && contactPhone.isNotBlank()) {
                    add(EmergencyContact(contactName, contactPhone, "Primary"))
                } else {
                    addAll(existing.contacts)
                }
            }
            repository.saveProfile(
                existing.copy(
                    fullName = fullName.trim(),
                    bloodType = bloodType.trim(),
                    allergies = allergies.trim(),
                    medicalInfo = medical.trim(),
                    medication = medication.trim(),
                    notes = notes.trim(),
                    contacts = contacts.ifEmpty { existing.contacts },
                    pinHash = if (pin.length >= 4) repository.hashPin(pin) else existing.pinHash,
                    biometricEnabled = biometricEnabled,
                ),
            )
            mode.value = EmergencyMode.PUBLIC
            unlocked.value = false
            error.value = null
            onResult(null)
        }
    }
}
