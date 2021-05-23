package uk.co.tracetechnicalservices.roommanager

import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken

class MqttSubscriber(var listeners: Map<String, PublishSubject<MqttMessage>>) : MqttCallback {
    override fun connectionLost(throwable: Throwable) {}
    override fun messageArrived(path: String, mqttMessage: MqttMessage) {
        val pub = listeners[path]
        if (pub != null) {
            println("$path -> $mqttMessage")
            pub.onNext(mqttMessage)
        }
    }

    override fun deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken) {}
}