package smart.home.controller.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smart.home.service.AppSettingsService;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/notifications")
public class NotificationSettingsController {

    private final AppSettingsService appSettingsService;

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings() {
        boolean garageTriggerEnabled = appSettingsService.getBoolean(
                AppSettingsService.GARAGE_TRIGGER_EMAIL_ENABLED, false);
        boolean doorOpenEnabled = appSettingsService.getBoolean(
                AppSettingsService.GARAGE_DOOR_OPEN_EMAIL_ENABLED, false);
        return ResponseEntity.ok(Map.of(
                "garageTriggerEmailEnabled", garageTriggerEnabled,
                "garageDoorOpenEmailEnabled", doorOpenEnabled
        ));
    }

    @PostMapping("/toggle/garage-trigger")
    public ResponseEntity<?> toggleGarageTrigger(@RequestParam boolean enabled) {
        try {
            appSettingsService.setBoolean(AppSettingsService.GARAGE_TRIGGER_EMAIL_ENABLED, enabled);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Garage trigger email notification " + (enabled ? "enabled" : "disabled")
            ));
        } catch (Exception e) {
            log.error("Failed to toggle garage trigger notification", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/toggle/door-open")
    public ResponseEntity<?> toggleDoorOpen(@RequestParam boolean enabled) {
        try {
            appSettingsService.setBoolean(AppSettingsService.GARAGE_DOOR_OPEN_EMAIL_ENABLED, enabled);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Door open sensor notification " + (enabled ? "enabled" : "disabled")
            ));
        } catch (Exception e) {
            log.error("Failed to toggle door open notification", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
