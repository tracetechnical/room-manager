package uk.co.tracetechnicalservices.roommanager.services

import com.fasterxml.jackson.databind.ObjectMapper
import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.springframework.stereotype.Service
import uk.co.tracetechnicalservices.roommanager.models.*
import uk.co.tracetechnicalservices.roommanager.repositories.DimmerGroupRepository
import uk.co.tracetechnicalservices.roommanager.repositories.RoomRepository
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.timer
import kotlin.concurrent.timerTask

@Service
class SetupService(private val mqttService: MqttService,
                   private val roomRepository: RoomRepository,
                   private val dimmerGroupRepository: DimmerGroupRepository) {
    init {
        setup()
        runSetup()
        setupSwitches()
    }

    private fun setup() {
        roomRepository.put(
            Room(
                0,
                "Porch",
                emptyList(),
                mapOf(
                    "All" to DimmerGroup(0, 0)
                ),
                listOf(
                    Light(0, "Porch", 1, "All"),
                ),
                mapOf(
                    "All On" to RoomPreset(mapOf("Porch" to 1024))
                )
            ))
        roomRepository.put(
            Room(
                1,
                "Living Room",
                listOf(
                    Switch(1, "Living Up", EActions.TOGGLE_UP),
                    Switch(2, "Living Down", EActions.TOGGLE_DOWN)
                ),
                mapOf(
                    "TV" to DimmerGroup(1, 0),
                    "Around TV" to DimmerGroup(2, 0),
                    "Sofa" to DimmerGroup(3, 0)
                ),
                listOf(
                    Light(0, "Front Left", 2, "Around TV"),
                    Light(1, "Front Right", 5, "Sofa"),
                    Light(2, "Mid Left", 3, "TV"),
                    Light(3, "Mid Right", 6, "Sofa"),
                    Light(4, "Rear Left", 4, "Around TV"),
                    Light(5, "Rear Right", 7, "Sofa")
                ),
                mapOf(
                    "All On" to RoomPreset(mapOf("Around TV" to 1024, "TV" to 1024, "Sofa" to 1024)),
                    "Dim" to RoomPreset(mapOf("Around TV" to 10, "TV" to 10, "Sofa" to 10)),
                    "All Off" to RoomPreset(mapOf("Around TV" to 0, "TV" to 0, "Sofa" to 0)),
                    "TV" to RoomPreset(mapOf("Around TV" to 100, "TV" to 0, "Sofa" to 100))
                )
            ))
        roomRepository.put(
            Room(
                2,
                "Dining Room",
                listOf(
                    Switch(3, "Dining Up", EActions.TOGGLE_UP),
                    Switch(4, "Dining Down", EActions.TOGGLE_DOWN)
                ),
                mapOf(
                    "Table" to DimmerGroup(4, 0),
                    "Reading Corner" to DimmerGroup(5, 0),
                    "Rest of Room" to DimmerGroup(6, 0)
                ),
                listOf(
                    Light(0, "Front Left", 8, "Rest of Room"),
                    Light(1, "Front Right", 11, "Rest of Room"),
                    Light(2, "Mid Left", 9, "Table"),
                    Light(3, "Mid Right", 12, "Table"),
                    Light(4, "Rear Left", 10, "Rest of Room"),
                    Light(5, "Rear Right", 13, "Reading Corner")
                ),
                mapOf(
                    "All On" to RoomPreset(mapOf("Table" to 1024, "Reading Corner" to 1024, "Rest of Room" to 1024)),
                    "Dim" to RoomPreset(mapOf("Table" to 10, "Reading Corner" to 10, "Rest of Room" to 10)),
                    "All Off" to RoomPreset(mapOf("Table" to 0, "Reading Corner" to 0, "Rest of Room" to 0)),
                    "Dinner" to RoomPreset(mapOf("Table" to 1024, "Reading Corner" to 0, "Rest of Room" to 0)),
                    "Reading Corner" to RoomPreset(mapOf("Table" to 0, "Reading Corner" to 1024, "Rest of Room" to 0))
                )
            )
        )
        roomRepository.put(
            Room(
                2,
                "Master Bedroom",
                listOf(
                    Switch(7, "Master Bedroom Up", EActions.TOGGLE_UP),
                    Switch(8, "Master Bedroom Down", EActions.TOGGLE_DOWN)
                ),
                mapOf(
                    "Kate" to DimmerGroup(11, 0),
                    "Alex" to DimmerGroup(12, 0),
                    "Reading" to DimmerGroup(13, 0),
                    "Rest of Room" to DimmerGroup(14, 0)
                ),
                listOf(
                    Light(0, "Front Left", 24, "Rest of Room"),
                    Light(1, "Front Right", 25, "Rest of Room"),
                    Light(2, "Mid Left", 26, "Rest of Room"),
                    Light(3, "Mid Right", 27, "Alex"),
                    Light(4, "Rear Left", 28, "Reading"),
                    Light(5, "Rear Right", 29, "Kate")
                ),
                mapOf(
                    "All On" to RoomPreset(mapOf("Kate" to 1024, "Reading" to 1024, "Alex" to 1024, "Rest of Room" to 1024)),
                    "Dim" to RoomPreset(mapOf("Kate" to 10, "Reading" to 10, "Alex" to 10, "Rest of Room" to 10)),
                    "All Off" to RoomPreset(mapOf("Kate" to 0, "Reading" to 0, "Alex" to 0, "Rest of Room" to 0)),
                    "Reading" to RoomPreset(mapOf("Reading" to 50))
                )
            )
        )
    }

    private fun runSetup() {
        val obj = ObjectMapper()
        roomRepository.getAll().stream().forEach { room ->
            room.switches.forEach { switch ->
                mqttService.publish("lighting/switch/${switch.channel}/action", switch.action.toString())
                mqttService.publish("lighting/switch/${switch.channel}/name", switch.name)
            }
            room.dimmerGroups.forEach { group ->
                dimmerGroupRepository.put("${room.name}-${group.key}", DimmerGroup(group.value.groupIdx, group.value.level))
                mqttService.publish("lighting/dimmerGroup/${group.value.groupIdx}/name", group.key)
            }
            room.lights.forEach { light ->
                val groupIdx = room.dimmerGroups[light.groupName]?.groupIdx
                mqttService.publish("lighting/dmx/${light.dmxPairIdx}", "$groupIdx")
                mqttService.publish("lighting/dmx/${light.dmxPairIdx}/name", light.name)
                mqttService.publish("lighting/dmx/${light.dmxPairIdx}/groupName", light.groupName)
            }
            room.roomPresets.forEach { roomPreset ->
                val ph: PublishSubject<MqttMessage> = PublishSubject.create()
                mqttService.registerListener("lighting/room/${room.name}/preset", ph)
                ph.subscribe { a: MqttMessage ->
                    val preset = room.roomPresets.get(a.toString())
                    preset?.dimmerGroupLevels?.forEach {
                        val groupIdx = room.dimmerGroups[it.key]?.groupIdx
                        mqttService.publish("lighting/dimmerGroup/$groupIdx/level", "${it.value}")
                    }
                }
                mqttService.publish(
                    "lighting/room/${room.name}/presets/${roomPreset.key}",
                    obj.writeValueAsString(roomPreset.value.dimmerGroupLevels)
                )
            }
        }
    }

    private fun setupSwitches() {
        var level = 0
        SwitchManager(1, mqttService)
            .setup()
              .setupActionOnPressAndHold {
                if (level <= 1024) {
                    level += 2;
                    (1..3).forEach { mqttService.publish("lighting/dimmerGroup/$it/level", "$level") }
                }
            }
            .setupActionOnShortPress(1) { mqttService.publish("lighting/room/Living Room/preset", "All On") }
        SwitchManager(2, mqttService)
            .setup()
            .setupActionOnPressAndHold {
                if (level >= 2) {
                    level -= 2
                    (1..3).forEach { mqttService.publish("lighting/dimmerGroup/$it/level", "$level") }
                }
            }
            .setupActionOnShortPress(1) { mqttService.publish("lighting/room/Living Room/preset", "All Off") }
            .setupActionOnShortPress(2) { mqttService.publish("lighting/room/Living Room/preset", "Dim") }
            .setupActionOnShortPress(3) { mqttService.publish("lighting/room/Living Room/preset", "TV") }

        SwitchManager(3, mqttService)
            .setup()
            .setupActionOnPressAndHold {
                if (level <= 1020) {
                    level += 4;
                    (4..6).forEach { mqttService.publish("lighting/dimmerGroup/$it/level", "$level") }
                }
            }
            .setupActionOnShortPress(1) { mqttService.publish("lighting/room/Dining Room/preset", "All On") }
            .setupActionOnShortPress(2) { mqttService.publish("lighting/room/Dining Room/preset", "Dinner") }
            .setupActionOnShortPress(3) { mqttService.publish("lighting/room/Dining Room/preset", "Reading Corner") }

        SwitchManager(4, mqttService)
            .setup()
            .setupActionOnPressAndHold {
                if (level >= 4) {
                    level -= 4;
                    (4..6).forEach { mqttService.publish("lighting/dimmerGroup/$it/level", "$level") }
                }
            }
            .setupActionOnShortPress(1) { mqttService.publish("lighting/room/Dining Room/preset", "All Off") }
            .setupActionOnShortPress(2) { mqttService.publish("lighting/room/Dining Room/preset", "Dim") }
            .setupActionOnShortPress(3) { partyMode() }
    }

    private fun partyMode() {
        val timers: MutableList<Timer> = emptyList<Timer>().toMutableList()
        (1..6).forEach { j ->
            timers.add(
                fixedRateTimer("N", false, (j * 100).toLong(), 6 * 100) {
                    (1..6).forEach { i ->
                        mqttService.publish("lighting/dimmerGroup/$i/level", if (i == j) "1024" else "0")
                    }
                }
            )
        }
        Timer().schedule(
            timerTask { timers.forEach { i -> i.cancel() } }, 10000)
    }
}
