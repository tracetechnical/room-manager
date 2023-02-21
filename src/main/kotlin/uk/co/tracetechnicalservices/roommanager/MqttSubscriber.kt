package uk.co.tracetechnicalservices.roommanager

import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttMessage
import uk.co.tracetechnicalservices.roommanager.models.MqttMessageWithTopic
import java.util.*
import java.util.stream.Collectors

class MqttSubscriber(var listeners: Map<String, PublishSubject<MqttMessageWithTopic>>) : MqttCallback {
    override fun connectionLost(throwable: Throwable) {
        println("Lost connection, exiting to restart service")
        System.exit(1)
    }

    override fun messageArrived(path: String, mqttMessage: MqttMessage) {
        var s = path
        val messageTopic = s
        val stringStream = listeners
            .keys
            .stream()
            .filter { ks: String -> isWildCardMatch(messageTopic, ks) }
            .collect(Collectors.toList())
        if (stringStream.size > 0) {
            s = stringStream[0]
        }
        val pub: PublishSubject<MqttMessageWithTopic>? = listeners[s]
        if (pub != null) {
//            println("Rx'd $s as '$mqttMessage'")
            pub.onNext(MqttMessageWithTopic(mqttMessage, messageTopic))
        }
    }

    private fun isWildCardMatch(topic: String, listener: String): Boolean {
        val listenerSegments = Arrays.asList(*listener.split("/".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray())
        val topicSegments = Arrays.asList(*topic.split("/".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray())
        if (listenerSegments.contains("+")) {
            if (listenerSegments.size == topicSegments.size) {
                var pass = 0
                for (i in listenerSegments.indices) {
                    if (listenerSegments[i] == "+") {
                        pass++
                    } else {
                        if (listenerSegments[i] == topicSegments[i]) {
                            pass++
                        }
                    }
                }
                return pass == listenerSegments.size
            }
        }
        return false
    }


    override fun deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken) {}
}
