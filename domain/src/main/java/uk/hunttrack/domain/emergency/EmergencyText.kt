package uk.hunttrack.domain.emergency

import java.util.Locale
import uk.hunttrack.domain.geo.OsGridRef
import uk.hunttrack.domain.model.EmergencyContact

/**
 * Text the hunter sends from their own phone to their own saved contact.
 * HunTracK only fills the draft. It does not send the message.
 */
object EmergencyText {
    fun phoneNumber(contacts: List<EmergencyContact>): String? {
        val raw = contacts.firstOrNull { it.phone.any(Char::isDigit) }?.phone ?: return null
        val address = smsAddress(raw)
        return address.takeIf { it.any(Char::isDigit) }
    }

    /** Digits, with a leading + kept, for an smsto: / sms: address. */
    fun smsAddress(phone: String): String = buildString {
        phone.trim().forEach { c ->
            if (c == '+' && isEmpty()) append(c)
            else if (c.isDigit()) append(c)
        }
    }

    fun message(latitude: Double?, longitude: Double?): String {
        val grid = if (latitude != null && longitude != null) {
            OsGridRef.latLngToOsGrid(latitude, longitude) ?: "unavailable"
        } else {
            "unavailable"
        }
        val coordinates = if (latitude != null && longitude != null) {
            String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
        } else {
            "unavailable"
        }
        return "Help! I need medical aid. My location is\nGrid reference: $grid\nCoordinates: $coordinates"
    }
}
