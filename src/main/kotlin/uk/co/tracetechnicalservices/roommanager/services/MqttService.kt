package uk.co.tracetechnicalservices.roommanager.services

import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttClient
import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.LinkedHashMap
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.springframework.stereotype.Service
import uk.co.tracetechnicalservices.roommanager.MqttSubscriber

@Service
class  MqttService {
    var rxClient: MqttAsyncClient? = null
    var txClient: MqttClient? = null
    val rxPersistence = MemoryPersistence()
    val txPersistence = MemoryPersistence()
    var listeners: MutableMap<String, PublishSubject<MqttMessage>> = LinkedHashMap()
    val mqttCallback = MqttSubscriber(listeners)
    val broker = "tcp://192.168.10.229:1883"
    val clientId = "JavaSample22222"
    val connOpts = MqttConnectOptions()

    init {
        connOpts.isCleanSession = true
        connOpts.isAutomaticReconnect = true
        connOpts.connectionTimeout = 0
        try {
            rxClient = MqttAsyncClient(broker, clientId + "rx", rxPersistence)
            println("Connecting to broker: $broker")
            connectToRx(connOpts, mqttCallback)
        } catch (me: MqttException) {
            handleException(me)
        }
        try {
            txClient = MqttClient(broker, clientId + "tx", txPersistence)
            println("Connecting to broker: $broker")
            connectToTx(connOpts)
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

    fun connectToTx(connOpts: MqttConnectOptions) {
        println("Perform TX connect")
        txClient!!.connect(connOpts)
        println("Connected")
    }

    fun connectToRx(
        connOpts: MqttConnectOptions,
        mqttCallback: MqttSubscriber
    ) {
        println("Perform RX connect")
        val conToken = rxClient!!.connect(connOpts)
        conToken.waitForCompletion()
        println("Connected")
        rxClient!!.setCallback(mqttCallback)
        println("Set callback")
    }
}