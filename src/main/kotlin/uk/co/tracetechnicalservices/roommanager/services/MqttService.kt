package uk.co.tracetechnicalservices.roommanager.services

import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.LivenessState
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import uk.co.tracetechnicalservices.roommanager.MqttSubscriber
import uk.co.tracetechnicalservices.roommanager.models.MqttMessageWithTopic
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.system.exitProcess

@Service
class MqttService(private val eventPublisher: ApplicationEventPublisher) {
    private var transmitEnabled = false
    private val broker = "tcp://mqtt.io.home:1883"
    private val clientId = "RoomManager-${Random.nextLong(0L,9999L)}"
    private val connOpts = MqttConnectOptions()
    private var rxClient: MqttAsyncClient? = null
    private var txClient: MqttClient? = null
    private val rxPersistence = MemoryPersistence()
    private val txPersistence = MemoryPersistence()
    private var listeners: ConcurrentHashMap<String, PublishSubject<MqttMessageWithTopic>> = ConcurrentHashMap()
    private val mqttCallback = MqttSubscriber(listeners)

    init {
        connOpts.isCleanSession = true
        connOpts.isAutomaticReconnect = true
        connOpts.connectionTimeout = 0
        connOpts.keepAliveInterval = 0
    }

    fun setMaster() {
        transmitEnabled = true
    }
    fun unsetMaster() {
        transmitEnabled = false
    }

    fun connect() {
        try {
            rxClient = MqttAsyncClient(broker, clientId + "rx", rxPersistence)
            println("Connecting to broker (Rx): $broker")
            connectToRx()

            txClient = MqttClient(broker, clientId + "tx", txPersistence)
            println("Connecting to broker (Tx): $broker")
            connectToTx()
        } catch (me: MqttException) {
            println("Did not get a connection, exiting to restart service")
            exitProcess(1)
        }
    }

    fun registerListener(path: String, publishSubject: PublishSubject<MqttMessageWithTopic>) {
        try {
            println(path)
            rxClient!!.subscribe(path, 0)
            listeners[path] = publishSubject
            println(">> $path")
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun publish(topic: String, content: String) {
//        if(!transmitEnabled && topic.contains("apps/roommanager/")) {
//            println("Using life topic bypass")
//        }
        if(transmitEnabled || topic.contains("apps/roommanager/")) {
            if (txClient != null && txClient!!.isConnected) {
                try {
                    txClient!!.publish(topic, content.toByteArray(), 0, true)
                } catch (me: MqttException) {
                    handleException(me)
                }
            }
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

        if (me.getReasonCode() === 32104) {
            println("Client not connected, restarting service")
            System.exit(2)
        }
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

    fun isConnected(): Boolean {
        if (rxClient == null || txClient == null) {
            return false
        }
        return rxClient!!.isConnected && txClient!!.isConnected

    }
}
