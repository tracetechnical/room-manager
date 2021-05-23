import io.reactivex.subjects.PublishSubject;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class Application {
    private static final String LONG_DASHES = "--------------------------------------------------------------------------------------------------------------------------------------------";

    public static void main(String args[]) throws InterruptedException {
        MqttService mqttService = new MqttService();
        PublishSubject<MqttMessage> on = PublishSubject.create();
        PublishSubject<MqttMessage> off = PublishSubject.create();
        PublishSubject<MqttMessage> onDining = PublishSubject.create();
        PublishSubject<MqttMessage> offDining = PublishSubject.create();

        mqttService.registerListener("lighting/switches/1/short", on);
        mqttService.registerListener("lighting/switches/2/short", off);
        mqttService.registerListener("lighting/switches/3/short", onDining);
        mqttService.registerListener("lighting/switches/4/short", offDining);

        on.subscribe(a -> {
            if (a.toString().equals("1")) {
                System.out.println("Dim Up");
                mqttService.publish("lighting/dimmerGroup/2/level", "1024");
                mqttService.publish("lighting/dimmerGroup/3/level", "1024");
            }
        });
        off.subscribe(a -> {
            if (a.toString().equals("1")) {
                System.out.println("Dim Down Off");
                mqttService.publish("lighting/dimmerGroup/2/level", "0");
                mqttService.publish("lighting/dimmerGroup/3/level", "0");
            }
            if (a.toString().equals("2")) {
                System.out.println("Dim Down Low");
                mqttService.publish("lighting/dimmerGroup/2/level", "10");
                mqttService.publish("lighting/dimmerGroup/3/level", "10");
            }
        });
        onDining.subscribe(a -> {
            if (a.toString().equals("1")) {
                System.out.println("Dim Up");
                mqttService.publish("lighting/dimmerGroup/0/level", "1024");
            }
        });
        offDining.subscribe(a -> {
            if (a.toString().equals("1")) {
                System.out.println("Dim Down Off");
                mqttService.publish("lighting/dimmerGroup/0/level", "0");
            }
            if (a.toString().equals("2")) {
                System.out.println("Dim Down Low");
                mqttService.publish("lighting/dimmerGroup/0/level", "10");
            }
        });

        while (true) {
            Thread.sleep(500);
        }

        // stop all GPIO activity/threads by shutting down the GPIO controller
        // (this method will forcefully shutdown all GPIO monitoring threads and scheduled tasks)
        // gpio.shutdown();   <--- implement this method call if you wish to terminate the Pi4J GPIO controller
    }
}

