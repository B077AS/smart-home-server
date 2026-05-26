package smart.home.service;

import com.google.gson.JsonObject;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import smart.home.entity.GarageLog;
import smart.home.entity.PlugLog;
import smart.home.repository.GarageLogRepository;
import smart.home.repository.PlugLogRepository;
import smart.home.security.SecurityUtil;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class GarageService {

    private final RestTemplate restTemplate;
    private final GarageLogRepository garageLogRepository;
    private final PlugLogRepository plugLogRepository;
    private final Mqtt5AsyncClient mqttClient;
    private final EmailService emailService;
    private final AppSettingsService appSettingsService;

    @Value("${garage.rfcat-service-url}")
    private String rfcatServiceUrl;

    @Value("${garage.secret-token}")
    private String secretToken;

    private static final String PLUG_TOPIC = "zigbee2mqtt/GarageDoorPlug/set";

    public String triggerGarage(String username, String ipAddress) {
        log.info("Triggering garage for user: {}", username);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", secretToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>("{}", headers);

        ResponseEntity<String> response = restTemplate.exchange(
                rfcatServiceUrl + "/trigger",
                HttpMethod.POST,
                entity,
                String.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Garage triggered successfully by {}", username);
            garageLogRepository.save(GarageLog.builder()
                    .username(username)
                    .timestamp(LocalDateTime.now())
                    .success(true)
                    .build());
            if (appSettingsService.getBoolean(AppSettingsService.GARAGE_TRIGGER_EMAIL_ENABLED, false)) {
                emailService.sendGarageTriggerNotification(username, ipAddress);
            }
            return response.getBody();
        } else {
            String errorMsg = "Flask service error: " + response.getBody();
            log.error("Flask service returned error: {}", response.getBody());
            garageLogRepository.save(GarageLog.builder()
                    .username(username)
                    .timestamp(LocalDateTime.now())
                    .success(false)
                    .errorMessage(errorMsg)
                    .build());
            throw new RuntimeException(errorMsg);
        }
    }

    public String checkGarageStatus() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                rfcatServiceUrl + "/health",
                String.class
        );
        return response.getBody();
    }

    public CompletableFuture<Void> turnPlugOn() {
        return turnPlugOn(SecurityUtil.getCurrentUser().getUsername());
    }

    public CompletableFuture<Void> turnPlugOn(String username) {
        return sendPlugCommand("ON", username);
    }

    public CompletableFuture<Void> turnPlugOff() {
        return turnPlugOff(SecurityUtil.getCurrentUser().getUsername());
    }

    public CompletableFuture<Void> turnPlugOff(String username) {
        return sendPlugCommand("OFF", username);
    }

    private CompletableFuture<Void> sendPlugCommand(String state, String username) {
        JsonObject command = new JsonObject();
        command.addProperty("state", state);
        String payload = command.toString();

        CompletableFuture<Void> future = new CompletableFuture<>();

        mqttClient.publishWith()
                .topic(PLUG_TOPIC)
                .payload(payload.getBytes(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .whenComplete((publish, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to send MQTT command {}: {}", state, throwable.getMessage());
                        plugLogRepository.save(PlugLog.builder()
                                .username(username)
                                .timestamp(LocalDateTime.now())
                                .action(state)
                                .success(false)
                                .errorMessage(throwable.getMessage())
                                .build());
                        future.completeExceptionally(throwable);
                    } else {
                        log.info("MQTT command {} sent successfully to topic {}", state, PLUG_TOPIC);
                        plugLogRepository.save(PlugLog.builder()
                                .username(username)
                                .timestamp(LocalDateTime.now())
                                .action(state)
                                .success(true)
                                .build());
                        future.complete(null);
                    }
                });

        return future;
    }
}