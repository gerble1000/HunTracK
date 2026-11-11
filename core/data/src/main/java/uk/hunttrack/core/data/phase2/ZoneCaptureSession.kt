package uk.hunttrack.core.data.phase2

import javax.inject.Inject
import javax.inject.Singleton
import uk.hunttrack.domain.model.PendingZoneCapture

/** Hands a zone draw from the boundary options over to the map. Not persisted. */
@Singleton
class ZoneCaptureSession @Inject constructor() {
    @Volatile
    private var pending: PendingZoneCapture? = null

    fun setPending(request: PendingZoneCapture) {
        pending = request
    }

    fun getPending(): PendingZoneCapture? = pending

    fun clear() {
        pending = null
    }
}
