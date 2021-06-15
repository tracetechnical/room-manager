package uk.co.tracetechnicalservices.roommanager.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.springframework.stereotype.Service
import uk.co.tracetechnicalservices.roommanager.models.RoomList
import uk.co.tracetechnicalservices.roommanager.repositories.DimmerGroupRepository
import uk.co.tracetechnicalservices.roommanager.repositories.RoomRepository
import java.net.URL
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.timerTask

@Service
class SetupService(
    private val mqttService: MqttService,
    private val roomRepository: RoomRepository,
    private val dimmerGroupRepository: DimmerGroupRepository
) {
    val obj = jacksonObjectMapper()

    init {
        loadConfig()
    }

    fun loadConfig() {
        setup()
        runSetup()
        setupSwitches()
    }

    private fun setup() {
        val configData =
            obj.readValue<RoomList>(
                URL("https://raw.githubusercontent.com/tracetechnical/49bw-lx-config/master/config.json")
            )
        configData.rooms.forEach { roomRepository.put(it) }
    }

    private fun runSetup() {
        roomRepository.getAll().stream().forEach { room ->
            room.switches.forEach { switch ->
                mqttService.publish("lighting/switch/${switch.channel}/action", switch.action.toString())
                mqttService.publish("lighting/switch/${switch.channel}/name", switch.name)
            }
            room.dimmerGroups.forEach { group ->
                dimmerGroupRepository.put(
                    "${room.name}-${group.key}",
                    group.value
                )
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
            timerTask { timers.forEach { i -> i.cancel() } }, 10000
        )
    }
}
