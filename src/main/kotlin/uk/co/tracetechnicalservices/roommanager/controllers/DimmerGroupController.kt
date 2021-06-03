package uk.co.tracetechnicalservices.roommanager.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.bind.annotation.*
import uk.co.tracetechnicalservices.roommanager.models.*
import uk.co.tracetechnicalservices.roommanager.repositories.RoomRepository
import uk.co.tracetechnicalservices.roommanager.services.MqttService
import java.util.*

@RestController
@RequestMapping("dimmerGroups")
class DimmerGroupController(val roomRepository: RoomRepository, val mqttService: MqttService) {
    @PostMapping("global")
    fun setRoomGlobalLevel(@RequestBody body: DimmerGlobalRequest) {
        val om = ObjectMapper()
        mqttService.publish("lighting/dimmerGroup/global", om.writeValueAsString(body))
    }

    @PostMapping("{name}/level")
    fun setGroupLevel(@PathVariable("name") name: String,@RequestBody body: DimmerGroupLevelRequest) {
        mqttService.publish("lighting/dimmerGroup/$name/level", "${body.level}")
    }
}