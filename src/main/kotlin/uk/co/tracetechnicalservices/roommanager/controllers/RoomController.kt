package uk.co.tracetechnicalservices.roommanager.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.bind.annotation.*
import uk.co.tracetechnicalservices.roommanager.models.*
import uk.co.tracetechnicalservices.roommanager.repositories.DimmerGroupRepository
import uk.co.tracetechnicalservices.roommanager.repositories.RoomRepository
import uk.co.tracetechnicalservices.roommanager.services.MqttService
import java.util.*

@RestController
@RequestMapping("rooms/{name}")
class RoomController(
    val dimmerGroupRepository: DimmerGroupRepository,
    val roomRepository: RoomRepository,
    val mqttService: MqttService
) {
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
            { r: Room ->
                r.roomPresets[body.preset]?.dimmerGroupLevels?.forEach { (groupName, level) ->
                    dimmerGroupRepository.getByName("$name-$groupName").ifPresent { group -> group.level = level }
                }
                mqttService.publish("lighting/room/${r.name}/preset", body.preset)
            },
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
                dimmerGroupRepository.getByName("$name-$groupName").ifPresent { it.level = body.level }
                val group = r.dimmerGroups[groupName]
                if (group != null) {
                    mqttService.publish("lighting/dimmerGroup/${group.groupIdx}/level", "${body.level}")
                }
            },
            Unit
        )
    }

    @PostMapping("globalLevel")
    fun setRoomGlobalLevel(@PathVariable("name") name: String, @RequestBody body: DimmerGroupLevelRequest) {
        val om = ObjectMapper()
        loadRoomByName(
            name,
            { room ->
                val roomGroupIndicies = room.dimmerGroups.values.stream().mapToInt { it.groupIdx }.toArray()
                roomGroupIndicies.forEach { it ->
                    dimmerGroupRepository.getById(it).ifPresent { group -> group.level = body.level }
                }
                val req = DimmerGlobalRequest(roomGroupIndicies.toTypedArray(), body.level)
                mqttService.publish("lighting/dimmerGroup/global", om.writeValueAsString(req))
            }, Unit
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