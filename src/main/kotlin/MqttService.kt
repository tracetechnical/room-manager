import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttClient
import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.LinkedHashMap
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.IMqttToken

class  MqttService {
    var mqttCallback: MqttSubscriber
    var rxClient: MqttAsyncClient? = null
    var txClient: MqttClient? = null
    var listeners: MutableMap<String, PublishSubject<MqttMessage>>
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
            println("$message -> $topic")
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

    init {
        val rxPersistence = MemoryPersistence()
        val txPersistence = MemoryPersistence()
        listeners = LinkedHashMap()
        mqttCallback = MqttSubscriber(listeners)
        val broker = "tcp://192.168.10.229:1883"
        val clientId = "JavaSample2"
        try {
            rxClient = MqttAsyncClient(broker, clientId + "rx", rxPersistence)
            val connOpts = MqttConnectOptions()
            connOpts.isCleanSession = true
            println("Connecting to broker: $broker")
            val conToken = rxClient!!.connect(connOpts)
            conToken.waitForCompletion()
            println("Connected")
            rxClient!!.setCallback(mqttCallback)
            println("Set callback")
        } catch (me: MqttException) {
            handleException(me)
        }
        try {
            txClient = MqttClient(broker, clientId + "tx", txPersistence)
            val connOpts = MqttConnectOptions()
            connOpts.isCleanSession = true
            println("Connecting to broker: $broker")
            txClient!!.connect(connOpts)
            println("Connected")
        } catch (me: MqttException) {
            handleException(me)
        }
    }
}