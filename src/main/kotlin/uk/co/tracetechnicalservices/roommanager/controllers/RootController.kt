package uk.co.tracetechnicalservices.roommanager.controllers

import org.springframework.web.bind.annotation.*
import uk.co.tracetechnicalservices.roommanager.models.Room
import uk.co.tracetechnicalservices.roommanager.repositories.RoomRepository
import uk.co.tracetechnicalservices.roommanager.services.MqttService

@RestController
class RootController(private val roomRepository: RoomRepository) {
    @GetMapping("rooms")
    fun getAllRooms(): List<Room> {
        return roomRepository.getAll()
    }
}