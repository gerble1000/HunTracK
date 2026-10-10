package uk.hunttrack.core.data.tides

import kotlinx.serialization.Serializable

@Serializable
data class TideStationListDto(
    val stations: List<TideStationDto> = emptyList(),
)

@Serializable
data class TideStationDto(
    val id: String,
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class TideEventsPayloadDto(
    val stationId: String,
    val events: List<TideEventDto> = emptyList(),
)

@Serializable
data class TideEventDto(
    val epochMs: Long,
    val type: String,
    val heightMeters: Double,
    val isApproximate: Boolean,
)
