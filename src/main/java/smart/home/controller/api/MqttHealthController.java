package smart.home.controller.api;

import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smart.home.dto.MqttStatusResponse;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mqtt")
public class MqttHealthController {

    private final Mqtt3AsyncClient mqttClient;

    @GetMapping("/status")
    public ResponseEntity<MqttStatusResponse> checkMqttStatus() {
        try {
            boolean isConnected = mqttClient.getState().isConnected();
            String state = mqttClient.getState().toString();
            String clientId = mqttClient.getConfig().getClientIdentifier().toString();

            MqttStatusResponse response = MqttStatusResponse.builder()
                    .connected(isConnected)
                    .state(state)
                    .clientId(clientId)
                    .build();

            if (isConnected) {
                log.debug("MQTT status check: Connected");
                return ResponseEntity.ok(response);
            } else {
                log.warn("MQTT status check: Not connected - State: {}", state);
                response.setMessage("MQTT client is not connected");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
            }

        } catch (Exception e) {
            log.error("Error checking MQTT status", e);
            MqttStatusResponse errorResponse = MqttStatusResponse.builder()
                    .connected(false)
                    .message("Error checking MQTT status: " + e.getMessage())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}