package uk.hunttrack.feature.emergency

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the full-screen beacon is on screen. Distress mode itself is stored
 * by [DistressNotifications] so it survives the hunter leaving for Messages.
 * Nothing here starts an activity from the background.
 */
object DistressMode {
    const val EXTRA_SHOW_BEACON = "uk.hunttrack.extra.SHOW_DISTRESS_BEACON"

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun showBeacon() {
        _visible.value = true
    }

    fun hideBeacon() {
        _visible.value = false
    }
}
