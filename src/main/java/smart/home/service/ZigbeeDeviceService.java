package smart.home.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import smart.home.dto.GarageDoorStatus;

import jakarta.annotation.PostConstruct;
import org.springframework.context.event.EventListener;
import smart.home.config.MqttConnectedEvent;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZigbeeDeviceService {

    private final Mqtt3AsyncClient mqttClient;
    private final Gson gson = new Gson();
    private final SimpMessagingTemplate messagingTemplate;
    private final GarageDoorStateTracker stateTracker;

    // Store latest state for each device
    private final Map<String, Map<String, Object>> deviceStates = new ConcurrentHashMap<>();

    private static final String VIBRATION_SENSOR = "VibrationSensor";
    private static final String TILT_SENSOR = "TiltSensor";
    private static final String PLUG = "GarageDoorPlug";

    @PostConstruct
    public void init() {
        if (mqttClient.getState().isConnected()) {
            subscribeToDevices();
        }
    }

    @EventListener
    public void onMqttConnected(MqttConnectedEvent event) {
        log.info("MQTT connected — subscribing to Zigbee devices");
        subscribeToDevices();
    }

    private void subscribeToDevices() {
        // Subscribe to all devices
        CompletableFuture<Void> vibrationSub = subscribeToDevice(VIBRATION_SENSOR);
        CompletableFuture<Void> tiltSub = subscribeToDevice(TILT_SENSOR);
        CompletableFuture<Void> plugSub = subscribeToDevice(PLUG);

        // Wait for all subscriptions to complete, then request initial states
        CompletableFuture.allOf(vibrationSub, tiltSub, plugSub)
                .thenRun(() -> {
                    log.info("Subscribed to all Zigbee devices");

                    requestInitialStates();
                })
                .exceptionally(throwable -> {
                    log.error("Error during device subscription", throwable);
                    return null;
                });
    }

    private CompletableFuture<Void> subscribeToDevice(String deviceName) {
        String topic = "zigbee2mqtt/" + deviceName;
        CompletableFuture<Void> future = new CompletableFuture<>();

        mqttClient.subscribeWith()
                .topicFilter(topic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback(publish -> {
                    String payload = new String(
                            publish.getPayloadAsBytes(),
                            StandardCharsets.UTF_8
                    );

                    Map<String, Object> data = gson.fromJson(payload, Map.class);

                    // Store in cache
                    deviceStates.put(deviceName, data);
                    log.debug("Cached {} data: {} fields", deviceName, data.size());

                    log.info("DEVICE: {}", deviceName);
                    log.info("DATA: {}", payload);

                    updateGarageDoorTracker();

                    // Forward to WebSocket if available
                    if (messagingTemplate != null) {
                        messagingTemplate.convertAndSend(
                                "/topic/zigbee/" + deviceName,
                                (Object) data
                        );

                        // Also send garage door status update
                        if (stateTracker != null) {
                            GarageDoorStatus status = stateTracker.getCurrentStatus();
                            messagingTemplate.convertAndSend(
                                    "/topic/garage/status",
                                    status
                            );
                        }
                    }
                })
                .send()
                .whenComplete((subAck, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to subscribe to {}: {}", topic, throwable.getMessage());
                        future.completeExceptionally(throwable);
                    } else {
                        log.info("Subscribed to {}", topic);
                        future.complete(null);
                    }
                });

        return future;
    }

    private void updateGarageDoorTracker() {
        if (stateTracker != null) {
            Map<String, Object> plugState = deviceStates.get(PLUG);
            Map<String, Object> tiltState = deviceStates.get(TILT_SENSOR);
            Map<String, Object> vibrationState = deviceStates.get(VIBRATION_SENSOR);

            stateTracker.updateState(plugState, tiltState, vibrationState);
        }
    }

    private void requestInitialStates() {
        log.info("Requesting initial device states...");

        requestPlugState();

        // Tilt sensor: only battery can be queried
        requestTiltSensorBattery();
    }

    private void requestPlugState() {
        JsonObject request = new JsonObject();
        request.addProperty("state", "");
        publishGetRequest(PLUG, request);
    }

    private void requestTiltSensorBattery() {
        JsonObject request = new JsonObject();
        request.addProperty("battery", "");
        publishGetRequest(TILT_SENSOR, request);
    }

    private void publishGetRequest(String deviceName, JsonObject payload) {
        String topic = "zigbee2mqtt/" + deviceName + "/get";

        mqttClient.publishWith()
                .topic(topic)
                .payload(payload.toString().getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .whenComplete((publish, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to request state from {}: {}", deviceName, throwable.getMessage());
                    } else {
                        log.info("State request sent to {}", deviceName);
                    }
                });
    }

    public Map<String, Object> getVibrationSensorState() {
        return deviceStates.getOrDefault(VIBRATION_SENSOR, new HashMap<>());
    }

    public Map<String, Object> getTiltSensorState() {
        return deviceStates.getOrDefault(TILT_SENSOR, new HashMap<>());
    }

    public Map<String, Object> getPlugState() {
        return deviceStates.getOrDefault(PLUG, new HashMap<>());
    }

    public Map<String, Map<String, Object>> getAllDeviceStates() {
        Map<String, Map<String, Object>> allStates = new HashMap<>();
        allStates.put("vibrationSensor", getVibrationSensorState());
        allStates.put("tiltSensor", getTiltSensorState());
        allStates.put("plug", getPlugState());
        return allStates;
    }

    public void refreshDevices() {
        log.info("Manual refresh requested");
        requestPlugState();
        requestTiltSensorBattery();
    }


    public Map<String, Object> getDeviceData(String deviceName) {
        return deviceStates.getOrDefault(deviceName, new HashMap<>());
    }

    public boolean hasDeviceData(String deviceName) {
        Map<String, Object> data = deviceStates.get(deviceName);
        return data != null && !data.isEmpty();
    }

    public Map<String, Boolean> getCacheStatus() {
        Map<String, Boolean> status = new HashMap<>();
        status.put("tiltSensor", hasDeviceData(TILT_SENSOR));
        status.put("vibrationSensor", hasDeviceData(VIBRATION_SENSOR));
        status.put("plug", hasDeviceData(PLUG));
        return status;
    }
}