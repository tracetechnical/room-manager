package uk.co.tracetechnicalservices.roommanager.services

import io.reactivex.subjects.PublishSubject
import org.eclipse.paho.client.mqttv3.MqttMessage
import uk.co.tracetechnicalservices.roommanager.models.MqttMessageWithTopic
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class SwitchManager(channelId: Int, private val mqttService: MqttService) {
    val scheduledExecutorService = Executors.newScheduledThreadPool(5)
    var longPressSubject: PublishSubject<MqttMessageWithTopic> = PublishSubject.create()
    var shortPressSubject: PublishSubject<MqttMessageWithTopic> = PublishSubject.create()
    var toggleSubject: PublishSubject<MqttMessageWithTopic> = PublishSubject.create()
    var pressAndHoldEnabled = false
    var stateChangeComplete = false
    var channelRoot = "lighting/switch/$channelId"
    private var shortActions: MutableMap<Int,() -> Unit> = mutableMapOf()
    private var longActions: MutableMap<Int,() -> Unit> = mutableMapOf()

    fun setup(): SwitchManager {
        mqttService.registerListener("$channelRoot/long", longPressSubject)
        mqttService.registerListener("$channelRoot/short", shortPressSubject)
        mqttService.registerListener("$channelRoot/toggle", toggleSubject)

        var scheduledFuture: ScheduledFuture<*> = scheduledExecutorService.schedule({}, 0, TimeUnit.MILLISECONDS)
        scheduledFuture.cancel(true)

        longPressSubject.subscribe { msg: MqttMessageWithTopic ->
            if (msg.message.payload.toString() == "1") {
                pressAndHoldEnabled = true;
            }
        }

        toggleSubject.subscribe { msg: MqttMessageWithTopic ->
            if (msg.message.payload.toString() == "true") {
                scheduledFuture = scheduledExecutorService.scheduleAtFixedRate(
                    {
                        handleScheduledCheck()
                    },
                    0,50, TimeUnit.MILLISECONDS)
            }
            if (msg.toString() == "false") {
                stateChangeComplete = false;
                if(pressAndHoldEnabled) {
                    mqttService.publish("$channelRoot/pressAndHold","false")
                    pressAndHoldEnabled = false
                }
                if(!scheduledFuture.isCancelled) {
                    scheduledFuture.cancel(true)
                }
            }
        }

        shortPressSubject.subscribe { a: MqttMessageWithTopic ->
            shortActions.entries.stream().forEach { b ->
                if(a.message.payload.toString() == "${b.key}") {
                    b.value()
                }
            }
        }
        longPressSubject.subscribe { a: MqttMessageWithTopic ->
            longActions.entries.stream().forEach { b ->
                if(a.message.payload.toString() == "${b.key}") {
                    b.value()
                }
            }
        }
        return this
    }

    private fun handleScheduledCheck() {
        if (pressAndHoldEnabled && !stateChangeComplete) {
            stateChangeComplete = true;
            mqttService.publish("$channelRoot/pressAndHold", "true")
        }
    }

    fun setupActionOnShortPress(count: Int, b: () -> Unit): SwitchManager {
        shortActions[count] = b
        return this
    }

    fun setupActionOnLongPress(count: Int, b: () -> Unit): SwitchManager {
        longActions[count] = b
        return this
    }
    fun setupActionOnPressAndHold(b: () -> Unit): SwitchManager {
        var scheduledFuture: ScheduledFuture<*> = scheduledExecutorService.schedule({}, 0, TimeUnit.MILLISECONDS)
        scheduledFuture.cancel(true)
        val ph = PublishSubject.create<MqttMessageWithTopic>()
        mqttService.registerListener("$channelRoot/pressAndHold", ph)
        ph.subscribe { a: MqttMessageWithTopic ->
            if(a.message.payload.toString() == "true") {
                scheduledFuture = scheduledExecutorService.scheduleAtFixedRate({ b() }, 0, 100, TimeUnit.MILLISECONDS)
            }
            if(a.message.payload.toString() == "false") {
                if(!scheduledFuture.isCancelled) {
                    scheduledFuture.cancel(true);
                }
            }
        }
        return this
    }
}
