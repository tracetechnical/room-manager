import kotlin.Throws
import java.lang.InterruptedException
import kotlin.jvm.JvmStatic
import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.client.mqttv3.MqttMessage

object Application {
    private const val LONG_DASHES =
        "--------------------------------------------------------------------------------------------------------------------------------------------"

    @Throws(InterruptedException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val mqttService = MqttService()
        val on = PublishSubject.create<MqttMessage>()
        val off = PublishSubject.create<MqttMessage>()
        val onDining = PublishSubject.create<MqttMessage>()
        val offDining = PublishSubject.create<MqttMessage>()
        mqttService.registerListener("lighting/switches/1/short", on)
        mqttService.registerListener("lighting/switches/2/short", off)
        mqttService.registerListener("lighting/switches/3/short", onDining)
        mqttService.registerListener("lighting/switches/4/short", offDining)
        on.subscribe { a: MqttMessage ->
            if (a.toString() == "1") {
                println("Dim Up")
                mqttService.publish("lighting/dimmerGroup/2/level", "1024")
                mqttService.publish("lighting/dimmerGroup/3/level", "1024")
            }
        }
        off.subscribe { a: MqttMessage ->
            if (a.toString() == "1") {
                println("Dim Down Off")
                mqttService.publish("lighting/dimmerGroup/2/level", "0")
                mqttService.publish("lighting/dimmerGroup/3/level", "0")
            }
            if (a.toString() == "2") {
                println("Dim Down Low")
                mqttService.publish("lighting/dimmerGroup/2/level", "10")
                mqttService.publish("lighting/dimmerGroup/3/level", "10")
            }
        }
        onDining.subscribe { a: MqttMessage ->
            if (a.toString() == "1") {
                println("Dim Up")
                mqttService.publish("lighting/dimmerGroup/0/level", "1024")
            }
        }
        offDining.subscribe { a: MqttMessage ->
            if (a.toString() == "1") {
                println("Dim Down Off")
                mqttService.publish("lighting/dimmerGroup/0/level", "0")
            }
            if (a.toString() == "2") {
                println("Dim Down Low")
                mqttService.publish("lighting/dimmerGroup/0/level", "10")
            }
        }
        while (true) {
            Thread.sleep(500)
        }

        // stop all GPIO activity/threads by shutting down the GPIO controller
        // (this method will forcefully shutdown all GPIO monitoring threads and scheduled tasks)
        // gpio.shutdown();   <--- implement this method call if you wish to terminate the Pi4J GPIO controller
    }
}