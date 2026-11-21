package uk.hunttrack.feature.emergency

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import uk.hunttrack.domain.emergency.EmergencyText

/** Hands a draft to the phone's own Messages app. Does not send it. */
fun openEmergencySms(context: Context, phone: String, body: String): Boolean {
    val address = EmergencyText.smsAddress(phone)
    if (address.none(Char::isDigit)) return false
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$address")).apply {
        putExtra("sms_body", body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
