package uk.co.tracetechnicalservices.roommanager.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.bind.annotation.*
import uk.co.tracetechnicalservices.roommanager.models.DimmerGlobalRequest
import uk.co.tracetechnicalservices.roommanager.models.DimmerGroup
import uk.co.tracetechnicalservices.roommanager.models.DimmerGroupLevelRequest
import uk.co.tracetechnicalservices.roommanager.repositories.DimmerGroupRepository
import uk.co.tracetechnicalservices.roommanager.services.MqttService
import java.util.*

@RestController
@RequestMapping("dimmerGroups")
class DimmerGroupController(val dimmerGroupRepository: DimmerGroupRepository, val mqttService: MqttService) {
    @PostMapping("global")
    fun setRoomGlobalLevel(@RequestBody body: DimmerGlobalRequest) {
        val om = ObjectMapper()
        mqttService.publish("lighting/dimmerGroup/global", om.writeValueAsString(body))
    }

    @GetMapping("{name}")
    fun getGroup(@PathVariable("name") name: String): Optional<DimmerGroup> {
        return dimmerGroupRepository.getByName(name)
    }

    @GetMapping("{name}/level")
    fun getGroupLevel(@PathVariable("name") name: String): Int {
        return dimmerGroupRepository.getByName(name).map { it.level }.orElse(9999)
    }

    @PostMapping("{name}/level")
    fun setGroupLevel(@PathVariable("name") name: String, @RequestBody body: DimmerGroupLevelRequest) {
        dimmerGroupRepository.getByName(name).ifPresent {
            it.level = body.level
            mqttService.publish("lighting/dimmerGroup/${it.groupIdx}/level", "${body.level}")
        }
    }
}