package uk.co.tracetechnicalservices.roommanager.controllers

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import uk.co.tracetechnicalservices.roommanager.models.DimmerGroup
import uk.co.tracetechnicalservices.roommanager.models.Room
import uk.co.tracetechnicalservices.roommanager.repositories.DimmerGroupRepository
import uk.co.tracetechnicalservices.roommanager.repositories.RoomRepository

@RestController
class RootController(
    private val dimmerGroupRepository: DimmerGroupRepository,
    private val roomRepository: RoomRepository
) {
    @GetMapping("rooms")
    fun getAllRooms(): List<Room> {
        return roomRepository.getAll()
    }

    @GetMapping("dimmerGroups")
    fun getAllDimmerGroups(): Map<String, DimmerGroup> {
        return dimmerGroupRepository.getAll()
    }
}