package smart.home.controller.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smart.home.entity.PlugScheduleConfig;
import smart.home.service.PlugScheduleService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/schedule")
public class ScheduleWebController {

    private final PlugScheduleService scheduleService;

    @GetMapping("/weekday")
    public ResponseEntity<PlugScheduleConfig> getWeekdaySchedule() {
        return ResponseEntity.ok(scheduleService.getWeekdaySchedule());
    }

    @GetMapping("/weekend")
    public ResponseEntity<PlugScheduleConfig> getWeekendSchedule() {
        return ResponseEntity.ok(scheduleService.getWeekendSchedule());
    }

    @GetMapping("/exceptions")
    public ResponseEntity<List<PlugScheduleConfig>> getExceptions() {
        return ResponseEntity.ok(scheduleService.getAllExceptions());
    }

    @PostMapping("/weekday")
    public ResponseEntity<?> updateWeekdaySchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime onTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime offTime) {
        try {
            scheduleService.updateDefaultSchedule("DEFAULT_WEEKDAY", onTime, offTime);
            return ResponseEntity.ok(Map.of("success", true, "message", "Weekday schedule updated"));
        } catch (Exception e) {
            log.error("Failed to update weekday schedule", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/weekend")
    public ResponseEntity<?> updateWeekendSchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime onTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime offTime) {
        try {
            scheduleService.updateDefaultSchedule("DEFAULT_WEEKEND", onTime, offTime);
            return ResponseEntity.ok(Map.of("success", true, "message", "Weekend schedule updated"));
        } catch (Exception e) {
            log.error("Failed to update weekend schedule", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/exception")
    public ResponseEntity<?> createException(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime onTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime offTime,
            @RequestParam(required = false) String description) {
        try {
            PlugScheduleConfig exception = scheduleService.createException(date, onTime, offTime, description);
            return ResponseEntity.ok(Map.of("success", true, "message", "Exception created", "exception", exception));
        } catch (Exception e) {
            log.error("Failed to create exception", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/exception/{id}")
    public ResponseEntity<?> deleteException(@PathVariable Long id) {
        try {
            scheduleService.deleteException(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Exception deleted"));
        } catch (Exception e) {
            log.error("Failed to delete exception", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/toggle/{type}")
    public ResponseEntity<?> toggleSchedule(
            @PathVariable String type,
            @RequestParam boolean enabled) {
        try {
            scheduleService.toggleSchedule("DEFAULT_" + type.toUpperCase(), enabled);
            return ResponseEntity.ok(Map.of("success", true, "message", type + " schedule " + (enabled ? "enabled" : "disabled")));
        } catch (Exception e) {
            log.error("Failed to toggle schedule", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/rebuild")
    public ResponseEntity<?> rebuildSchedules() {
        try {
            scheduleService.rebuildAllSchedules();
            return ResponseEntity.ok(Map.of("success", true, "message", "Schedules rebuilt"));
        } catch (Exception e) {
            log.error("Failed to rebuild schedules", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}