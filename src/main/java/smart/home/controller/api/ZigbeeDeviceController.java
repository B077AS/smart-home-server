package smart.home.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smart.home.dto.ErrorResponse;
import smart.home.dto.GarageDoorStatus;
import smart.home.service.GarageDoorStateTracker;
import smart.home.service.ZigbeeDeviceService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class ZigbeeDeviceController {

    private final ZigbeeDeviceService zigbeeDeviceService;
    private final GarageDoorStateTracker stateTracker;

    @GetMapping("/vibration-sensor")
    public ResponseEntity<?> getVibrationSensor() {
        try {
            Map<String, Object> state = zigbeeDeviceService.getVibrationSensorState();
            if (state.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorResponse.builder()
                                .message("No data available for vibration sensor")
                                .error("NOT_FOUND")
                                .status(404)
                                .build());
            }
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            log.error("Error getting vibration sensor state", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .message("Failed to retrieve vibration sensor data")
                            .error(e.getMessage())
                            .status(500)
                            .build());
        }
    }

    @GetMapping("/tilt-sensor")
    public ResponseEntity<?> getTiltSensor() {
        try {
            Map<String, Object> state = zigbeeDeviceService.getTiltSensorState();
            if (state.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorResponse.builder()
                                .message("No data available for tilt sensor")
                                .error("NOT_FOUND")
                                .status(404)
                                .build());
            }
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            log.error("Error getting tilt sensor state", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .message("Failed to retrieve tilt sensor data")
                            .error(e.getMessage())
                            .status(500)
                            .build());
        }
    }

    @GetMapping("/plug")
    public ResponseEntity<?> getPlug() {
        try {
            Map<String, Object> state = zigbeeDeviceService.getPlugState();
            if (state.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorResponse.builder()
                                .message("No data available for plug")
                                .error("NOT_FOUND")
                                .status(404)
                                .build());
            }
            return ResponseEntity.ok(state);
        } catch (Exception e) {
            log.error("Error getting plug state", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .message("Failed to retrieve plug data")
                            .error(e.getMessage())
                            .status(500)
                            .build());
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllDevices() {
        try {
            Map<String, Map<String, Object>> allStates = zigbeeDeviceService.getAllDeviceStates();
            return ResponseEntity.ok(allStates);
        } catch (Exception e) {
            log.error("Error getting all device states", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .message("Failed to retrieve device data")
                            .error(e.getMessage())
                            .status(500)
                            .build());
        }
    }

    @GetMapping("/all-with-status")
    public ResponseEntity<?> getAllDevicesWithStatus() {
        try {
            Map<String, Object> response = new HashMap<>();

            GarageDoorStatus garageStatus = stateTracker.getCurrentStatus();
            response.put("garageStatus", garageStatus);

            response.put("tiltSensor", zigbeeDeviceService.getTiltSensorState());
            response.put("vibrationSensor", zigbeeDeviceService.getVibrationSensorState());
            response.put("plug", zigbeeDeviceService.getPlugState());

            response.put("cacheStatus", zigbeeDeviceService.getCacheStatus());

            log.debug("Returning cached sensor data with status: garage={}, tilt={}, vibration={}, plug={}",
                    garageStatus.getState(),
                    !zigbeeDeviceService.getTiltSensorState().isEmpty(),
                    !zigbeeDeviceService.getVibrationSensorState().isEmpty(),
                    !zigbeeDeviceService.getPlugState().isEmpty()
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting cached sensor data with status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .message("Failed to retrieve device data with status")
                            .error(e.getMessage())
                            .status(500)
                            .build());
        }
    }

    @GetMapping("/cache-status")
    public ResponseEntity<Map<String, Boolean>> getCacheStatus() {
        return ResponseEntity.ok(zigbeeDeviceService.getCacheStatus());
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshDevices() {
        try {
            zigbeeDeviceService.refreshDevices();
            return ResponseEntity.ok(Map.of(
                    "message", "Device refresh requested",
                    "status", "success"
            ));
        } catch (Exception e) {
            log.error("Error refreshing devices", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .message("Failed to refresh devices")
                            .error(e.getMessage())
                            .status(500)
                            .build());
        }
    }
}