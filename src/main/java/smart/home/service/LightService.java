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
    private final LightSettingService lightSettingService;
    private final ZigbeeDeviceService zigbeeDeviceService;
    private final Mqtt5AsyncClient mqttClient;
    private final Gson gson = new Gson();

    private static final Set<String> ALLOWED_EFFECTS = Set.of("off", "breathing", "candlelight", "fading", "flash");

    public CompletableFuture<Map<String, Object>> applyPreset(String targetDevice, UUID presetId) {
        LightPreset preset = lightPresetService.getById(presetId);
        Map<String, Object> settings = lightPresetService.parseSettings(preset);

        // Presets saved earlier captured the bulb's own fade (level_config); writing it
        // back would undo the built-in fade being cleared, so it is never applied.
        settings.remove("level_config");
        JsonObject payload = gson.toJsonTree(settings).getAsJsonObject();

        // Brightness, color and any effect can turn a bulb on (these bulbs report ON at
        // 1% after a color change), so a preset applied to a bulb that is off would light
        // it up. Let the bulb take the preset, then put it back off: it comes on with
        // this look the next time it is switched on.
        boolean wasOff = isOff(targetDevice);
        CompletableFuture<Map<String, Object>> applied = publish(targetDevice, payload, settings, "preset '" + preset.getName() + "'");
        return wasOff ? applied.thenCompose(result -> keepOff(targetDevice).thenApply(v -> result)) : applied;
    }

    private boolean isOff(String targetDevice) {
        return "OFF".equals(zigbeeDeviceService.getDeviceData(targetDevice).get("state"));
    }

    // Instant, regardless of the saved fade, so the bulb doesn't visibly dim down.
    private CompletableFuture<Void> keepOff(String targetDevice) {
        JsonObject command = new JsonObject();
        command.addProperty("state", "OFF");
        command.addProperty("transition", 0);

        CompletableFuture<Void> future = new CompletableFuture<>();
        mqttClient.publishWith()
                .topic("zigbee2mqtt/" + targetDevice + "/set")
                .payload(command.toString().getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .whenComplete((publish, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to keep {} off after applying a preset: {}", targetDevice, throwable.getMessage());
                        future.completeExceptionally(throwable);
                    } else {
                        log.info("Kept {} off after applying a preset", targetDevice);
                        future.complete(null);
                    }
                });
        return future;
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
        if (settings.containsKey("transition")) {
            validateTransition(settings.get("transition"));
        }

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

    private void validateTransition(Object transition) {
        double max = LightSettingService.MAX_TRANSITION_SECONDS;
        if (!(transition instanceof Number number) || number.doubleValue() < 0 || number.doubleValue() > max) {
            throw new IllegalArgumentException("Invalid transition '" + transition + "'. Must be a number of seconds between 0 and " + max);
        }
    }

    // The light's saved fade rides along on every command unless the caller sent
    // its own, so it applies no matter which client (or preset) triggered the change.
    // A fade of 0 is sent explicitly too: without it these bulbs fall back to the
    // fade built into their own level_config (1.5 s out of the box), so "Instant"
    // would not actually be instant.
    private void addStoredTransition(String targetDevice, JsonObject payload) {
        if (payload.has("transition")) {
            return;
        }
        payload.addProperty("transition", lightSettingService.getTransitionSeconds(targetDevice));
    }

    // Turns off the fade the bulb applies by itself. Commands from this app carry their
    // own explicit transition, so the bulb's built-in fade only ever shows up when the
    // bulb is switched on at the wall (or gets a command without a transition), where
    // it is unwanted. Writes both the standard Level Control times (units of 1/10 s)
    // and the Aqara-specific on/off durations (seconds), which the bulb stores itself.
    // Best effort: a bulb that doesn't know a key just ignores it.
    public void clearBulbOwnFade(String targetDevice) {
        JsonObject levelConfig = new JsonObject();
        levelConfig.addProperty("on_off_transition_time", 0);
        levelConfig.addProperty("on_transition_time", 0);
        levelConfig.addProperty("off_transition_time", 0);
        JsonObject command = new JsonObject();
        command.add("level_config", levelConfig);
        command.addProperty("off_on_duration", 0);
        command.addProperty("on_off_duration", 0);

        mqttClient.publishWith()
                .topic("zigbee2mqtt/" + targetDevice + "/set")
                .payload(command.toString().getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .whenComplete((publish, throwable) -> {
                    if (throwable != null) {
                        log.warn("Failed to clear {}'s built-in fade: {}", targetDevice, throwable.getMessage());
                    } else {
                        log.info("Cleared {}'s built-in fade: {}", targetDevice, command);
                    }
                });
    }

    private boolean isValidColorChannel(Object value) {
        return value instanceof Number number && number.doubleValue() >= 0 && number.doubleValue() <= 255;
    }

    public CompletableFuture<Void> setPower(String targetDevice, boolean on) {
        JsonObject command = new JsonObject();
        command.addProperty("state", on ? "ON" : "OFF");
        addStoredTransition(targetDevice, command);

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
                        log.info("Turned {} {}: {}", targetDevice, on ? "ON" : "OFF", command);
                        future.complete(null);
                    }
                });

        return future;
    }

    private CompletableFuture<Map<String, Object>> publish(String targetDevice, JsonObject payload, Map<String, Object> settings, String label) {
        addStoredTransition(targetDevice, payload);
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
                        log.info("Applied {} to {}: {}", label, targetDevice, payload);
                        future.complete(settings);
                    }
                });

        return future;
    }
}
