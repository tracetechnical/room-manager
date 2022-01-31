package uk.co.tracetechnicalservices.roommanager.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.LivenessState
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import uk.co.tracetechnicalservices.roommanager.models.RoomList
import uk.co.tracetechnicalservices.roommanager.repositories.DimmerGroupRepository
import uk.co.tracetechnicalservices.roommanager.repositories.RoomRepository
import java.net.URL

@Service
class SetupService(
    private val mqttService: MqttService,
    private val roomRepository: RoomRepository,
    private val dimmerGroupRepository: DimmerGroupRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    val obj = jacksonObjectMapper()
    val heartbeatReceiver: PublishSubject<MqttMessage> = PublishSubject.create()
    var tickCounter = 0

    @EventListener
    fun setup(e: ApplicationReadyEvent) {
        mqttService.connect()
        loadConfig()
        mqttService.registerListener("time/tick", heartbeatReceiver)
        heartbeatReceiver.subscribe {
            tickCounter = 0
        }
    }

    @Scheduled(fixedDelay = 1000)
    fun heartbeat() {
        if(tickCounter == 30) {
            AvailabilityChangeEvent.publish(this.eventPublisher, Exception("Heartbeat lost"), LivenessState.BROKEN)
        }
        if(tickCounter <= 30) {
            tickCounter++
        }
    }

    fun loadConfig() {
        roomRepository.clear()
        val data = getConfigData()
        addConfigToRepository(data)
        runSetup()
        setupSwitches()
    }

    fun getConfigData(): RoomList {
        return obj.readValue(
            URL("https://raw.githubusercontent.com/tracetechnical/49bw-lx-config/master/config.json")
        )
    }

    private fun addConfigToRepository(configData: RoomList) {
        configData.rooms.forEach { roomRepository.put(it) }
    }

    private fun runSetup() {
        roomRepository.getAll().forEach { room ->
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
                ph.subscribe { presetNameMsg: MqttMessage ->
                    val presetName = presetNameMsg.toString()
                    val preset = room.roomPresets[presetName]
                    room.currentPreset = presetName
                    preset?.dimmerGroupLevels?.forEach {
                        val groupIdx = room.dimmerGroups[it.key]?.groupIdx
                        room.dimmerGroups[it.key]?.level = it.value
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

        SwitchManager(3, mqttService)
            .setup()
            .setupActionOnPressAndHold {
                if (level <= 1020) {
                    level += 4;
                    (4..6).forEach { mqttService.publish("lighting/dimmerGroup/$it/level", "$level") }
                }
            }

        SwitchManager(4, mqttService)
            .setup()
            .setupActionOnPressAndHold {
                if (level >= 4) {
                    level -= 4;
                    (4..6).forEach { mqttService.publish("lighting/dimmerGroup/$it/level", "$level") }
                }
            }
    }
}
