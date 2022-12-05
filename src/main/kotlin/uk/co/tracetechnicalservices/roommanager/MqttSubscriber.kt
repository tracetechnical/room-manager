package uk.co.tracetechnicalservices.roommanager

import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken

class MqttSubscriber(var listeners: Map<String, PublishSubject<MqttMessage>>) : MqttCallback {
    override fun connectionLost(throwable: Throwable) {
        println("Lost connection, exiting to restart service")
        System.exit(1)
    }

    override fun messageArrived(path: String, mqttMessage: MqttMessage) {
        val pub = listeners[path]
        if (pub != null) {
            pub.onNext(mqttMessage)
        }
    }

    override fun deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken) {}
}
