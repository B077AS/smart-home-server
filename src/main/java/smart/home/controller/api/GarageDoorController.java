package smart.home.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import smart.home.dto.ErrorResponse;
import smart.home.dto.GarageDoorStatus;
import smart.home.dto.SuccessResponse;
import smart.home.security.SecurityUtil;
import smart.home.service.GarageDoorStateTracker;
import smart.home.service.GarageService;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/garage")
public class GarageDoorController {

    private final GarageService garageService;
    private final GarageDoorStateTracker stateTracker;

    @GetMapping("/status")
    public ResponseEntity<?> getGarageDoorStatus() {
        try {
            GarageDoorStatus status = stateTracker.getCurrentStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting garage door status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .message("Failed to retrieve garage door status")
                            .error(e.getMessage())
                            .status(500)
                            .build());
        }
    }


    @PostMapping("/trigger")
    public ResponseEntity<?> trigger() {
        String username = SecurityUtil.getCurrentUser().getUsername();
        log.info("Garage trigger requested by: {}", username);

        try {
            String result = garageService.triggerGarage(username);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error triggering garage", e);
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .message("Failed to trigger garage")
                    .error(e.getMessage())
                    .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/plug/off")
    public ResponseEntity<?> turnPlugOff() {
        String username = SecurityUtil.getCurrentUser().getUsername();
        log.info("Plug OFF requested by: {}", username);

        try {
            garageService.turnPlugOff().join();
            log.info("Plug OFF successful");
            return ResponseEntity.ok(SuccessResponse.builder()
                    .message("Plug turned OFF")
                    .status(HttpStatus.OK.value())
                    .build());
        } catch (Exception e) {
            log.error("Failed to turn plug OFF", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .message("Failed to turn plug OFF")
                            .error(e.getMessage())
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build());
        }
    }

    @PostMapping("/plug/on")
    public ResponseEntity<?> turnPlugOn() {
        String username = SecurityUtil.getCurrentUser().getUsername();
        log.info("Plug ON requested by: {}", username);

        try {
            garageService.turnPlugOn().join();
            log.info("Plug ON successful");
            return ResponseEntity.ok(SuccessResponse.builder()
                    .message("Plug turned ON")
                    .status(HttpStatus.OK.value())
                    .build());
        } catch (Exception e) {
            log.error("Failed to turn plug ON", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .message("Failed to turn plug ON")
                            .error(e.getMessage())
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build());
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetTracker() {
        try {
            stateTracker.reset();
            return ResponseEntity.ok("State tracker reset successfully");
        } catch (Exception e) {
            log.error("Error resetting tracker", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .message("Failed to reset state tracker")
                            .error(e.getMessage())
                            .status(500)
                            .build());
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> checkHealth() {
        try {
            String health = garageService.checkGarageStatus();
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Error checking garage health", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .message("Failed to check garage health")
                            .error(e.getMessage())
                            .status(500)
                            .build());
        }
    }
}