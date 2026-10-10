package uk.hunttrack.core.data.phase2

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.hunttrack.core.data.db.EmergencyDao
import uk.hunttrack.core.data.db.EmergencyProfileEntity
import uk.hunttrack.domain.model.EmergencyContact
import uk.hunttrack.domain.model.EmergencyProfile
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Singleton
class EmergencyRepository @Inject constructor(
    private val dao: EmergencyDao,
    private val json: Json,
) {
    fun observeProfile(): Flow<EmergencyProfile> =
        dao.observe().map { it?.toDomain(json) ?: EmergencyProfile() }

    suspend fun getProfile(): EmergencyProfile = dao.get()?.toDomain(json) ?: EmergencyProfile()

    suspend fun saveProfile(profile: EmergencyProfile) {
        dao.upsert(profile.toEntity(json))
    }

    fun hashPin(pin: String): String {
        val bytes = pin.trim().toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }
    }

    /** Older builds formatted bytes with sign extension; still accept for unlock. */
    private fun legacyHashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, hash: String?): Boolean {
        val stored = hash?.trim().orEmpty()
        if (stored.isEmpty()) return false
        val entered = pin.trim()
        if (entered.isEmpty()) return false
        return hashPin(entered) == stored || legacyHashPin(entered) == stored
    }

    fun hasPin(hash: String?): Boolean = !hash.isNullOrBlank()

    /** Rewrite legacy hashes to the current stable format after a successful unlock. */
    suspend fun migratePinHashIfNeeded(pin: String, stored: String?) {
        val entered = pin.trim()
        val modern = hashPin(entered)
        if (stored.isNullOrBlank() || stored == modern) return
        if (legacyHashPin(entered) == stored) {
            val profile = getProfile()
            saveProfile(profile.copy(pinHash = modern))
        }
    }
}

@kotlinx.serialization.Serializable
private data class ContactDto(val name: String, val phone: String, val relationship: String = "")

private fun EmergencyProfileEntity.toDomain(json: Json): EmergencyProfile {
    val contacts = runCatching {
        json.decodeFromString<List<ContactDto>>(contactsJson).map {
            EmergencyContact(it.name, it.phone, it.relationship)
        }
    }.getOrDefault(emptyList())
    return EmergencyProfile(
        fullName = fullName,
        bloodType = bloodType,
        allergies = allergies,
        medicalInfo = medicalInfo,
        medication = medication,
        notes = notes,
        contacts = contacts,
        pinHash = pinHash?.takeIf { it.isNotBlank() },
        biometricEnabled = biometricEnabled,
    )
}

private fun EmergencyProfile.toEntity(json: Json) = EmergencyProfileEntity(
    id = 1,
    fullName = fullName,
    bloodType = bloodType,
    allergies = allergies,
    medicalInfo = medicalInfo,
    medication = medication,
    notes = notes,
    contactsJson = json.encodeToString(contacts.map { ContactDto(it.name, it.phone, it.relationship) }),
    pinHash = pinHash?.takeIf { it.isNotBlank() },
    biometricEnabled = biometricEnabled,
)
