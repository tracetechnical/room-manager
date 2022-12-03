package uk.co.tracetechnicalservices.roommanager.services

import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.LivenessState
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import uk.co.tracetechnicalservices.roommanager.MqttSubscriber

@Service
class MqttService(private val eventPublisher: ApplicationEventPublisher) {
    private val broker = "tcp://192.168.10.229:1883"
    private val clientId = "RoomManager"
    private val connOpts = MqttConnectOptions()
    private var rxClient: MqttAsyncClient? = null
    private var txClient: MqttClient? = null
    private val rxPersistence = MemoryPersistence()
    private val txPersistence = MemoryPersistence()
    private var listeners: MutableMap<String, PublishSubject<MqttMessage>> = LinkedHashMap()
    private val mqttCallback = MqttSubscriber(listeners)

    init {
        connOpts.isCleanSession = true
        connOpts.isAutomaticReconnect = true
        connOpts.connectionTimeout = 0
        connOpts.keepAliveInterval = 30
    }

    fun connect() {
        try {
            rxClient = MqttAsyncClient(broker, clientId + "rx", rxPersistence)
            println("Connecting to broker (Rx): $broker")
            connectToRx()
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
            message.setRetained(true)
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
        AvailabilityChangeEvent.publish(this.eventPublisher, me, LivenessState.BROKEN)
        println("reason " + me.reasonCode)
        println("msg " + me.message)
        println("loc " + me.localizedMessage)
        println("cause " + me.cause)
        println("excep $me")
        me.printStackTrace()
    }

    private fun connectToTx() {
        println("Perform TX connect")
        txClient!!.connect(connOpts)
        println("Connected")
    }

    private fun connectToRx() {
        println("Perform RX connect")
        val conToken = rxClient!!.connect(connOpts)
        conToken.waitForCompletion()
        println("Connected")
        rxClient!!.setCallback(mqttCallback)
        println("Set callback")
    }
}
