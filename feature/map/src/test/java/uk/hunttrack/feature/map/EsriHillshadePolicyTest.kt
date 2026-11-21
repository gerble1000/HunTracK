package uk.hunttrack.feature.map

import org.junit.Assert.assertTrue
import org.junit.Test
import uk.hunttrack.core.common.MapLibreStyleSupport

class EsriHillshadePolicyTest {
    @Test
    fun outdoorHillshadeStopsBeforeTheEsriPlaceholderZoom() {
        // World Hillshade answers zoom 17 and above with an HTTP 200 JPEG that
        // reads "Map data not yet available", including at Wasdale. The Outdoor
        // layer must not request those tiles.
        assertTrue(MapLibreStyleSupport.ESRI_HILLSHADE_MAX_ZOOM < 17)
        assertTrue(MapLibreStyleSupport.ESRI_HILLSHADE_MAX_ZOOM >= 13)
        assertTrue(
            MapLibreStyleSupport.ESRI_HILLSHADE_TILE_URL.contains("World_Hillshade"),
        )
    }
}
