package uk.co.tracetechnicalservices.roommanager

import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RoomManagerApplication

fun main(args: Array<String>) {
    runApplication<RoomManagerApplication>(*args)
    val mqttService = MqttService()
    val onLiving = PublishSubject.create<MqttMessage>()
    val offLiving = PublishSubject.create<MqttMessage>()
    val offLongLiving = PublishSubject.create<MqttMessage>()
    val onDining = PublishSubject.create<MqttMessage>()
    val offDining = PublishSubject.create<MqttMessage>()

    mqttService.registerListener("lighting/switches/1/short", onLiving)
    mqttService.registerListener("lighting/switches/2/short", offLiving)
    mqttService.registerListener("lighting/switches/2/long", offLongLiving)
    mqttService.registerListener("lighting/switches/3/short", onDining)
    mqttService.registerListener("lighting/switches/4/short", offDining)

    onLiving.subscribe { a: MqttMessage ->
        if (a.toString() == "1") {
            mqttService.publish("lighting/dimmerGroup/global","{\"dimmerGroups\":[3,4,5],\"level\":1024}");
        }
    }
    offLiving.subscribe { a: MqttMessage ->
        if (a.toString() == "1") {
            mqttService.publish("lighting/dimmerGroup/global","{\"dimmerGroups\":[3,4,5],\"level\":0}");
        }
        if (a.toString() == "2") {
            mqttService.publish("lighting/dimmerGroup/global","{\"dimmerGroups\":[3,4,5],\"level\":10}");
        }
    }
    offLongLiving.subscribe { a: MqttMessage ->
        if (a.toString() == "1") {
            mqttService.publish("lighting/dimmerGroup/3/level", "10")
            mqttService.publish("lighting/dimmerGroup/4/level", "0")
        }
    }

    onDining.subscribe { a: MqttMessage ->
        if (a.toString() == "1") {
            mqttService.publish("lighting/dimmerGroup/global","{\"dimmerGroups\":[6,7,8,9],\"level\":1024}");
        }
    }
    offDining.subscribe { a: MqttMessage ->
        if (a.toString() == "1") {
            mqttService.publish("lighting/dimmerGroup/global","{\"dimmerGroups\":[6,7,8,9],\"level\":0}");
        }
        if (a.toString() == "2") {
            mqttService.publish("lighting/dimmerGroup/global","{\"dimmerGroups\":[6,7,8,9],\"level\":10}");
        }
    }
}
