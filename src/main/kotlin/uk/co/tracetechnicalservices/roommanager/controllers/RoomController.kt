package uk.co.tracetechnicalservices.roommanager.controllers

import org.springframework.web.bind.annotation.*
import uk.co.tracetechnicalservices.roommanager.models.*
import uk.co.tracetechnicalservices.roommanager.repositories.RoomRepository
import uk.co.tracetechnicalservices.roommanager.services.MqttService
import java.util.*

@RestController
@RequestMapping("rooms/{name}")
class RoomController(val roomRepository: RoomRepository, val mqttService: MqttService) {
    @GetMapping
    fun getRoomByName(@PathVariable("name") name: String): Optional<Room> {
        return roomRepository.getByName(name)
    }

    @GetMapping("presets")
    fun getAllPresets(@PathVariable("name") name: String): Map<String, RoomPreset> {
        return loadRoomByName(name, { r -> r.roomPresets }, emptyMap())
    }

    @PostMapping("preset")
    fun setRoomPreset(@PathVariable("name") name: String, @RequestBody body: RoomPresetRequest) {
        return loadRoomByName(
            name,
            { r: Room -> mqttService.publish("lighting/room/${r.name}/preset", body.preset) },
            Unit
        )
    }

    @GetMapping("dimmerGroups")
    fun getAllDimmerGroups(@PathVariable("name") name: String): Map<String, DimmerGroup> {
        return loadRoomByName(name, { r -> r.dimmerGroups }, emptyMap())
    }

    @GetMapping("dimmerGroups/{groupName}")
    fun getDimmerGroupByName(
        @PathVariable("name") name: String,
        @PathVariable("groupName") groupName: String
    ): DimmerGroup? {
        return loadRoomByName(name, { r -> r.dimmerGroups[groupName] }, null)
    }

    @PostMapping("dimmerGroups/{groupName}/level")
    fun setDimmerGroupLevel(
        @PathVariable("name") name: String,
        @PathVariable("groupName") groupName: String,
        @RequestBody body: DimmerGroupLevelRequest
    ) {
        return loadRoomByName(
            name,
            { r ->
                val group = r.dimmerGroups[groupName]
                if (group != null) {
                    mqttService.publish("lighting/dimmerGroup/${group.groupIdx}/level", "${body.level}")
                }
            },
            Unit
        )
    }

    private fun <T> loadRoomByName(name: String, b: (room: Room) -> T, empty: T): T {
        val roomOpt = roomRepository.getByName(name)
        if (roomOpt.isPresent) {
            val room = roomOpt.get()
            return b(room)
        }
        return empty
    }
}