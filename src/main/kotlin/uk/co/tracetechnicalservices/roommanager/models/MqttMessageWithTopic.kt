package uk.co.tracetechnicalservices.roommanager.models

import org.eclipse.paho.client.mqttv3.MqttMessage

class MqttMessageWithTopic(var message: MqttMessage, var topic: String)
