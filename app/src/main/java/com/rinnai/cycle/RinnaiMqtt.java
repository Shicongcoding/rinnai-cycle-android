package com.rinnai.cycle;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import javax.net.ssl.SSLSocketFactory;

public final class RinnaiMqtt {
    private static final String BROKER = "ssl://mqtt.rinnai.com.cn:8883";
    private static final String PREFIX = "rinnai/SR/01/SR";

    private RinnaiMqtt() {
    }

    public static void send(Config config, String data) throws Exception {
        String clientId = "a:rinnai:SR:01:SR:" + config.phone + ":" + System.currentTimeMillis();
        MqttClient client = new MqttClient(BROKER, clientId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName("a:rinnai:SR:01:SR:" + config.phone);
        options.setPassword(md5Upper(config.password).toCharArray());
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setConnectionTimeout(15);
        options.setKeepAliveInterval(60);
        options.setSocketFactory(SSLSocketFactory.getDefault());
        try {
            client.connect(options);
            publish(client, PREFIX + "/" + config.phone + "/sys/", epoch());
            publish(client, PREFIX + "/" + config.mac + "/set/", control(config, data));
            Thread.sleep(800);
        } finally {
            try {
                if (client.isConnected()) client.disconnect();
            } catch (Exception ignored) {
            }
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void publish(MqttClient client, String topic, String payload) throws Exception {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        message.setRetained(false);
        client.publish(topic, message);
    }

    private static String epoch() throws Exception {
        JSONObject o = new JSONObject();
        o.put("ptn", "JA4");
        o.put("epoch", System.currentTimeMillis());
        return o.toString();
    }

    private static String control(Config c, String data) throws Exception {
        JSONObject enl = new JSONObject();
        enl.put("id", "temporaryCycleInsulationSetting");
        enl.put("data", data);

        JSONArray enlArray = new JSONArray();
        enlArray.put(enl);

        JSONObject o = new JSONObject();
        o.put("ptn", "J00");
        o.put("code", c.authCode);
        o.put("id", c.classId);
        o.put("sum", "1");
        o.put("enl", enlArray);
        return o.toString();
    }

    private static String md5Upper(String value) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format(Locale.US, "%02X", b));
        }
        return sb.toString();
    }
}
