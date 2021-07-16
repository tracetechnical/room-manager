package uk.co.tracetechnicalservices.roommanager.services

import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.springframework.stereotype.Service
import uk.co.tracetechnicalservices.roommanager.MqttSubscriber
import java.util.*

@Service
class MqttService {
    final var rxClient: MqttAsyncClient? = null
    final var txClient: MqttClient? = null
    final val rxPersistence = MemoryPersistence()
    final val txPersistence = MemoryPersistence()
    final var listeners: MutableMap<String, PublishSubject<MqttMessage>> = LinkedHashMap()
    final val mqttCallback = MqttSubscriber(listeners)
    final val broker = "tcp://192.168.10.229:1883"
    final val clientId = "JavaSample22222"
    final val connOpts = MqttConnectOptions()

    init {
        connOpts.isCleanSession = true
        connOpts.isAutomaticReconnect = true
        connOpts.connectionTimeout = 0
        try {
            rxClient = MqttAsyncClient(broker, clientId + "rx", rxPersistence)
            println("Connecting to broker (Rx): $broker")
            connectToRx(mqttCallback)
        } catch (me: MqttException) {
            handleException(me)
        }
        try {
            txClient = MqttClient(broker, clientId + "tx", txPersistence)
            println("Connecting to broker (Tx): $broker")
            connectToTx()
        } catch (me: MqttException) {
            handleException(me)
        }
    }

    fun registerListener(path: String, publishSubject: PublishSubject<MqttMessage>) {
        try {
            rxClient!!.subscribe(path, 0)
            listeners[path] = publishSubject
            println(">> $path")
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun publish(topic: String, content: String) {
        try {
            val message = MqttMessage(content.toByteArray())
            message.qos = 0
            txClient!!.publish(topic, message)
        } catch (me: MqttException) {
            handleException(me)
        }
    }

    fun disconnect() {
        try {
            rxClient!!.disconnect()
        } catch (me: MqttException) {
            handleException(me)
        }
    }

    private fun handleException(me: MqttException) {
        println("reason " + me.reasonCode)
        println("msg " + me.message)
        println("loc " + me.localizedMessage)
        println("cause " + me.cause)
        println("excep $me")
        me.printStackTrace()
    }

    final fun connectToTx() {
        println("Perform TX connect")
        txClient!!.connect(connOpts)
        println("Connected")
    }

    final fun connectToRx(mqttCallback: MqttSubscriber) {
        println("Perform RX connect")
        val conToken = rxClient!!.connect(connOpts)
        conToken.waitForCompletion()
        println("Connected")
        rxClient!!.setCallback(mqttCallback)
        println("Set callback")
    }
}