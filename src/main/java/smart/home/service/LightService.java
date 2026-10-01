package smart.home.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smart.home.entity.LightPreset;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class LightService {

    private final LightPresetService lightPresetService;
    private final Mqtt5AsyncClient mqttClient;
    private final Gson gson = new Gson();

    public CompletableFuture<Map<String, Object>> applyPreset(String targetDevice, UUID presetId) {
        LightPreset preset = lightPresetService.getById(presetId);
        Map<String, Object> settings = lightPresetService.parseSettings(preset);

        JsonObject payload = gson.toJsonTree(settings).getAsJsonObject();

        return publish(targetDevice, payload, settings, "preset '" + preset.getName() + "'");
    }

    public CompletableFuture<Map<String, Object>> setState(String targetDevice, Map<String, Object> settings) {
        JsonObject payload = gson.toJsonTree(settings).getAsJsonObject();
        return publish(targetDevice, payload, settings, "state update");
    }

    public CompletableFuture<Void> setPower(String targetDevice, boolean on) {
        JsonObject command = new JsonObject();
        command.addProperty("state", on ? "ON" : "OFF");

        CompletableFuture<Void> future = new CompletableFuture<>();
        String topic = "zigbee2mqtt/" + targetDevice + "/set";

        mqttClient.publishWith()
                .topic(topic)
                .payload(command.toString().getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .whenComplete((publish, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to turn {} {}: {}", targetDevice, on ? "ON" : "OFF", throwable.getMessage());
                        future.completeExceptionally(throwable);
                    } else {
                        log.info("Turned {} {}", targetDevice, on ? "ON" : "OFF");
                        future.complete(null);
                    }
                });

        return future;
    }

    private CompletableFuture<Map<String, Object>> publish(String targetDevice, JsonObject payload, Map<String, Object> settings, String label) {
        String topic = "zigbee2mqtt/" + targetDevice + "/set";
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();

        mqttClient.publishWith()
                .topic(topic)
                .payload(payload.toString().getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .whenComplete((publish, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to apply {} to {}: {}", label, targetDevice, throwable.getMessage());
                        future.completeExceptionally(throwable);
                    } else {
                        log.info("Applied {} to {}: {}", label, targetDevice, settings);
                        future.complete(settings);
                    }
                });

        return future;
    }
}
