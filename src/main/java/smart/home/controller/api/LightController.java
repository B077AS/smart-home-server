package smart.home.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smart.home.dto.ErrorResponse;
import smart.home.dto.SuccessResponse;
import smart.home.security.SecurityUtil;
import smart.home.service.LightService;
import smart.home.service.ZigbeeDeviceService;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/devices/lights")
@RequiredArgsConstructor
public class LightController {

    private final ZigbeeDeviceService zigbeeDeviceService;
    private final LightService lightService;

    @GetMapping
    public ResponseEntity<Map<String, Map<String, Object>>> getLightStates() {
        return ResponseEntity.ok(zigbeeDeviceService.getLightStates());
    }

    @GetMapping("/{deviceName}")
    public ResponseEntity<?> getLightState(@PathVariable String deviceName) {
        Map<String, Object> state = zigbeeDeviceService.getDeviceData(deviceName);
        if (state.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.builder()
                            .message("No data available for " + deviceName)
                            .error("NOT_FOUND")
                            .status(404)
                            .build());
        }
        return ResponseEntity.ok(state);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshLights() {
        zigbeeDeviceService.requestLightState(ZigbeeDeviceService.OFFICE_LIGHT_1);
        zigbeeDeviceService.requestLightState(ZigbeeDeviceService.OFFICE_LIGHT_2);
        return ResponseEntity.ok(SuccessResponse.builder()
                .message("Light state refresh requested")
                .status(HttpStatus.OK.value())
                .build());
    }

    @PostMapping("/{deviceName}/apply-preset/{presetId}")
    public ResponseEntity<?> applyPreset(@PathVariable String deviceName, @PathVariable UUID presetId) {
        String username = SecurityUtil.getCurrentUser().getUsername();
        log.info("Apply preset {} to {} requested by {}", presetId, deviceName, username);

        try {
            Map<String, Object> applied = lightService.applyPreset(deviceName, presetId).join();
            return ResponseEntity.ok(Map.of(
                    "message", "Applied preset to " + deviceName,
                    "applied", applied
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.builder()
                            .message(e.getMessage())
                            .error("NOT_FOUND")
                            .status(404)
                            .build());
        } catch (Exception e) {
            log.error("Failed to apply preset {} to {}", presetId, deviceName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .message("Failed to apply preset to " + deviceName)
                            .error(e.getMessage())
                            .status(500)
                            .build());
        }
    }

    @PostMapping("/{deviceName}/on")
    public ResponseEntity<?> turnOn(@PathVariable String deviceName) {
        return setPower(deviceName, true);
    }

    @PostMapping("/{deviceName}/off")
    public ResponseEntity<?> turnOff(@PathVariable String deviceName) {
        return setPower(deviceName, false);
    }

    private ResponseEntity<?> setPower(String deviceName, boolean on) {
        String username = SecurityUtil.getCurrentUser().getUsername();
        log.info("Turn {} {} requested by {}", deviceName, on ? "ON" : "OFF", username);

        try {
            lightService.setPower(deviceName, on).join();
            return ResponseEntity.ok(SuccessResponse.builder()
                    .message(deviceName + " turned " + (on ? "ON" : "OFF"))
                    .status(HttpStatus.OK.value())
                    .build());
        } catch (Exception e) {
            log.error("Failed to turn {} {}", deviceName, on ? "ON" : "OFF", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .message("Failed to turn " + deviceName + " " + (on ? "ON" : "OFF"))
                            .error(e.getMessage())
                            .status(500)
                            .build());
        }
    }
}
