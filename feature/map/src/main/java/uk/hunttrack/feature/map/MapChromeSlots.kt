package uk.hunttrack.feature.map

/**
 * Vertical order of the map screen while a saved boundary is on display.
 *
 * Home and the other tool buttons are drawn on top of the map. The boundary
 * name and its close control have to be a separate band above that map, or
 * those buttons cover them.
 */
/** Pixels of air around a framed fence so the tool row on the map does not sit on its top edge. */
internal const val BoundaryFocusPaddingPx = 180

internal enum class MapChromeSlot {
    BOUNDARY_NAME,
    MAP,
}

internal fun mapChromeSlots(boundaryFocused: Boolean): List<MapChromeSlot> = buildList {
    if (boundaryFocused) add(MapChromeSlot.BOUNDARY_NAME)
    add(MapChromeSlot.MAP)
}

/** Name band is clear of the tool row when it is laid out above the map. */
internal fun boundaryNameClearsToolRow(slots: List<MapChromeSlot>): Boolean {
    val name = slots.indexOf(MapChromeSlot.BOUNDARY_NAME)
    val map = slots.indexOf(MapChromeSlot.MAP)
    if (name < 0) return true
    return map >= 0 && name < map
}
