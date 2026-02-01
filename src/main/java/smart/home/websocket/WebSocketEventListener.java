package smart.home.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import smart.home.dto.GarageDoorStatus;
import smart.home.service.GarageDoorStateTracker;
import smart.home.service.ZigbeeDeviceService;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ZigbeeDeviceService zigbeeDeviceService;
    private final GarageDoorStateTracker stateTracker;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        log.info("WebSocket connected: sessionId={}", sessionId);
    }

    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();

        log.info("WebSocket subscribed: sessionId={}, destination={}", sessionId, destination);

        // When client subscribes to any topic, send initial data
        if (destination != null && destination.startsWith("/topic/")) {
            sendInitialData(sessionId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        log.info("WebSocket disconnected: sessionId={}", sessionId);
    }

    private void sendInitialData(String sessionId) {
        try {
            log.info("Sending initial cached data to session: {}", sessionId);

            GarageDoorStatus status = stateTracker.getCurrentStatus();
            messagingTemplate.convertAndSend("/topic/garage/status", (Object) status);
            log.debug("Sent garage status: {}", status.getState());

            Map<String, Object> tiltData = zigbeeDeviceService.getTiltSensorState();
            if (!tiltData.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/zigbee/TiltSensor", (Object) tiltData);
                log.debug("Sent tilt sensor data: contact={}, battery={}",
                        tiltData.get("contact"), tiltData.get("battery"));
            } else {
                log.debug("No tilt sensor data available");
            }

            Map<String, Object> vibrationData = zigbeeDeviceService.getVibrationSensorState();
            if (!vibrationData.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/zigbee/VibrationSensor", (Object) vibrationData);
                log.debug("Sent vibration sensor data: vibration={}, battery={}",
                        vibrationData.get("vibration"), vibrationData.get("battery"));
            } else {
                log.debug("No vibration sensor data available");
            }

            Map<String, Object> plugData = zigbeeDeviceService.getPlugState();
            if (!plugData.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/zigbee/GarageDoorPlug", (Object) plugData);
                log.debug("Sent plug data: state={}, power={}",
                        plugData.get("state"), plugData.get("power"));
            } else {
                log.debug("No plug data available");
            }

            log.info("Initial data broadcast complete for session: {}", sessionId);

        } catch (Exception e) {
            log.error("Error sending initial data to session {}: {}", sessionId, e.getMessage(), e);
        }
    }
}