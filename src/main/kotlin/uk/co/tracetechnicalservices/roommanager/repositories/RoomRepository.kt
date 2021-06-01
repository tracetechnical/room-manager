package uk.co.tracetechnicalservices.roommanager.repositories

import org.springframework.stereotype.Component
import uk.co.tracetechnicalservices.roommanager.models.Room
import java.util.Optional

@Component
class RoomRepository {
    val rooms: MutableList<Room> = emptyList<Room>().toMutableList()

    fun getAll(): List<Room> {
        return rooms.toList();
    }

    fun getByName(name: String): Optional<Room> {
        return rooms.stream().filter { a -> a.name == name}.findFirst()
    }

    fun put(room: Room): Unit {
        rooms.add(room)
    }
}