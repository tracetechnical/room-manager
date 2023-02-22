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
import uk.co.tracetechnicalservices.roommanager.models.MqttMessageWithTopic
import uk.co.tracetechnicalservices.roommanager.models.RoomList
import uk.co.tracetechnicalservices.roommanager.repositories.DimmerGroupRepository
import uk.co.tracetechnicalservices.roommanager.repositories.RoomRepository
import java.net.InetAddress
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*

@Service
class SetupService(
    private val mqttService: MqttService,
    private val roomRepository: RoomRepository,
    private val dimmerGroupRepository: DimmerGroupRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    val obj = jacksonObjectMapper()
    val heartbeatReceiver: PublishSubject<MqttMessageWithTopic> = PublishSubject.create()
    val masterReceiver: PublishSubject<MqttMessageWithTopic> = PublishSubject.create()
    val masterLifeReceiver: PublishSubject<MqttMessageWithTopic> = PublishSubject.create()
    val hostsReceiver: PublishSubject<MqttMessageWithTopic> = PublishSubject.create()
    var tickCounter = 0
    var master = ""
    var masterLife = ""
    val hostname = InetAddress.getLocalHost().hostName!!
    var hostList: MutableMap<String,String> = mutableMapOf()
    var deadHostSet: MutableSet<String> = mutableSetOf()

    @EventListener
    fun setup(e: ApplicationReadyEvent) {
        mqttService.connect()
        while(!mqttService.isConnected())
        loadConfig()
        mqttService.registerListener("time/tick", heartbeatReceiver)
        mqttService.registerListener("apps/roommanager/master/host", masterReceiver)
        mqttService.registerListener("apps/roommanager/master/life", masterLifeReceiver)
        mqttService.registerListener("apps/roommanager/master/candidates/+", hostsReceiver)
        heartbeatReceiver.subscribe {
            tickCounter = 0
        }
        masterReceiver.subscribe {
            master = String(it.message.payload)
            println("Master is $master")
        }
        masterLifeReceiver.subscribe {
            masterLife = String(it.message.payload)
        }
        hostsReceiver.subscribe {
            val parts = it.topic.split("/")
            val name = parts[parts.size - 1]
            val time = String(it.message.payload)
            if (!isDead(time)) {
                if(!hostList.containsKey(name)) {
                    println("${name} is alive")
                }
                hostList[name] = time
                if(deadHostSet.contains(name)) {
                    deadHostSet.remove(name)
                }
            } else {
                if(!deadHostSet.contains(name)) {
                    println("${name} is dead")
                    deadHostSet.add(name)
                }
                if(hostList.containsKey(name)) {
                    hostList.remove(name)
                }
            }
        }
    }

    @Scheduled(initialDelay = 30000, fixedDelay = 5000)
    fun checkMaster() {
        if(master === "") {
            println("No master set")
            takeOwnership()
        } else {
            if(master != hostname) {
                if (isDead(masterLife)) {
                    var availableHosts = hostList.filter { !isDead(it.value) }.keys.sorted()
                    println("-------- Candidates -------")
                    availableHosts.forEach{ println(it)}
                    println("---------- Deads ----------")
                    hostList.filter { isDead(it.value) }.forEach{ println(it)}
                    println("---------------------------")
                    if(availableHosts.size > 0) {
                        var topHost = availableHosts[0]
                        if (topHost == hostname) {
                            takeOwnership()
                        } else {
                            println("$topHost should take over")
                        }
                    } else {
                        println("no available hosts")
                    }
                }
            }
        }
    }

    fun isDead(timestamp: String): Boolean {
        var date = Instant.now()
        try {
            date = Instant.parse(timestamp)
        } catch (e: Exception) {
        }
        var now = Instant.now()
        var d = (now.toEpochMilli() - date.toEpochMilli()) / 1000
        return d > 20
    }
    fun takeOwnership() {
        println("$hostname, taking ownership due to failed master")
        mqttService.publish("apps/roommanager/master/host", hostname)
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

    @Scheduled(fixedDelay = 5000)
    fun lifepulse() {
        val date = Date(System.currentTimeMillis())
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        sdf.timeZone = TimeZone.getTimeZone("GMT")

        mqttService.publish("apps/roommanager/master/candidates/$hostname",sdf.format(date))
        if(isMaster()) {
            mqttService.publish("apps/roommanager/master/life", sdf.format(date))
            mqttService.publish("apps/roommanager/life", sdf.format(date))
            mqttService.setMaster()
        } else {
            mqttService.unsetMaster()
        }
    }

    private fun isMaster(): Boolean {
        return master == hostname
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
                val ph: PublishSubject<MqttMessageWithTopic> = PublishSubject.create()
                mqttService.registerListener("lighting/room/${room.name}/preset", ph)
                ph.subscribe { presetNameMsg: MqttMessageWithTopic ->
                    val presetName = presetNameMsg.message.payload.toString()
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
