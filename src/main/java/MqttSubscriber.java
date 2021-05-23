import io.reactivex.subjects.PublishSubject;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Map;

public class MqttSubscriber implements MqttCallback {
    Map<String, PublishSubject<MqttMessage>> listeners;

    public MqttSubscriber(Map<String, PublishSubject<MqttMessage>> listeners) {
        this.listeners = listeners;
    }

    @Override
    public void connectionLost(Throwable throwable) {

    }

    @Override
    public void messageArrived(String path, MqttMessage mqttMessage) {
        PublishSubject<MqttMessage> pub = listeners.get(path);
        if(pub != null) {
            System.out.println(path + " -> " + mqttMessage.toString());
            pub.onNext(mqttMessage);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
    }
}
