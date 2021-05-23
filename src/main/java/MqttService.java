import io.reactivex.subjects.PublishSubject;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.LinkedHashMap;
import java.util.Map;

public class MqttService {
    MqttSubscriber mqttCallback;
    MqttAsyncClient rxClient;
    MqttClient txClient;
    Map<String, PublishSubject<MqttMessage>> listeners;

    public MqttService() {
        MemoryPersistence rxPersistence = new MemoryPersistence();
        MemoryPersistence txPersistence = new MemoryPersistence();
        listeners = new LinkedHashMap<>();
        mqttCallback = new MqttSubscriber(listeners);
        String broker = "tcp://192.168.10.229:1883";
        String clientId = "JavaSample2";
        try {
            rxClient = new MqttAsyncClient(broker, clientId + "rx", rxPersistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            System.out.println("Connecting to broker: " + broker);
            IMqttToken conToken = rxClient.connect(connOpts);
            conToken.waitForCompletion();
            System.out.println("Connected");
            rxClient.setCallback(mqttCallback);
            System.out.println("Set callback");
        } catch (MqttException me) {
            handleException(me);
        }
        try {
            txClient = new MqttClient(broker, clientId + "tx", txPersistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            System.out.println("Connecting to broker: " + broker);
            txClient.connect(connOpts);
            System.out.println("Connected");
        } catch (MqttException me) {
            handleException(me);
        }
    }

    public void registerListener(String path, PublishSubject<MqttMessage> publishSubject) {
        try {
            rxClient.subscribe(path, 0);
            this.listeners.put(path,publishSubject);
            System.out.println(">> " + path);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    public void publish(String topic, String content) {
        try {
            MqttMessage message = new MqttMessage(content.getBytes());
            message.setQos(0);
            System.out.println(message + " -> " + topic);
            txClient.publish(topic, message);
        } catch (MqttException me) {
            handleException(me);
        }
    }

    public void disconnect() {
        try {
            rxClient.disconnect();
        } catch (MqttException me) {
            handleException(me);
        }
    }

    private void handleException(MqttException me) {
        System.out.println("reason " + me.getReasonCode());
        System.out.println("msg " + me.getMessage());
        System.out.println("loc " + me.getLocalizedMessage());
        System.out.println("cause " + me.getCause());
        System.out.println("excep " + me);
        me.printStackTrace();
    }
}
