package uk.co.tracetechnicalservices.roommanager.models

data class Room(
    val id: Int,
    val name: String,
    val switches: List<Switch>,
    val dimmerGroups: Map<String, DimmerGroup>,
    val lights: List<Light>,
    val roomPresets: Map<String, RoomPreset>
)