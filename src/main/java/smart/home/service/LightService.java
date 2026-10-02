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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class LightService {

    private final LightPresetService lightPresetService;
    private final Mqtt5AsyncClient mqttClient;
    private final Gson gson = new Gson();

    private static final Set<String> ALLOWED_EFFECTS = Set.of("off", "breathing", "candlelight", "fading", "flash");

    public CompletableFuture<Map<String, Object>> applyPreset(String targetDevice, UUID presetId) {
        LightPreset preset = lightPresetService.getById(presetId);
        Map<String, Object> settings = lightPresetService.parseSettings(preset);

        JsonObject payload = gson.toJsonTree(settings).getAsJsonObject();

        return publish(targetDevice, payload, settings, "preset '" + preset.getName() + "'");
    }

    public CompletableFuture<Map<String, Object>> applyDefaultPreset(String targetDevice) {
        LightPreset defaultPreset = lightPresetService.getDefaultPreset();
        return applyPreset(targetDevice, defaultPreset.getId());
    }

    public CompletableFuture<Map<String, Object>> setState(String targetDevice, Map<String, Object> settings) {
        validateEffectSettings(settings);
        JsonObject payload = gson.toJsonTree(settings).getAsJsonObject();
        return publish(targetDevice, payload, settings, "state update");
    }

    private void validateEffectSettings(Map<String, Object> settings) {
        if (settings.containsKey("effect")) {
            Object effect = settings.get("effect");
            if (!(effect instanceof String) || !ALLOWED_EFFECTS.contains(effect)) {
                throw new IllegalArgumentException("Invalid effect '" + effect + "'. Allowed: " + ALLOWED_EFFECTS);
            }
        }

        if (settings.containsKey("effect_speed")) {
            Object speed = settings.get("effect_speed");
            if (!(speed instanceof Number) || ((Number) speed).doubleValue() < 0 || ((Number) speed).doubleValue() > 100) {
                throw new IllegalArgumentException("Invalid effect_speed '" + speed + "'. Must be a number between 0 and 100");
            }
        }

        if (settings.containsKey("effect_colors")) {
            Object colors = settings.get("effect_colors");
            if (!(colors instanceof List<?> colorList) || colorList.isEmpty() || colorList.size() > 8) {
                throw new IllegalArgumentException("Invalid effect_colors: must be an array of 1-8 color objects");
            }
            for (Object entry : colorList) {
                if (!(entry instanceof Map<?, ?> color) || !isValidColorChannel(color.get("r"))
                        || !isValidColorChannel(color.get("g")) || !isValidColorChannel(color.get("b"))) {
                    throw new IllegalArgumentException("Invalid effect_colors entry: each color needs r/g/b numbers between 0 and 255");
                }
            }
        }
    }

    private boolean isValidColorChannel(Object value) {
        return value instanceof Number number && number.doubleValue() >= 0 && number.doubleValue() <= 255;
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
