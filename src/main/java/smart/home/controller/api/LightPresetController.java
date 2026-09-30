package smart.home.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smart.home.dto.CreatePresetRequest;
import smart.home.dto.ErrorResponse;
import smart.home.dto.LightPresetResponse;
import smart.home.entity.LightPreset;
import smart.home.security.SecurityUtil;
import smart.home.service.LightPresetService;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/light-presets")
@RequiredArgsConstructor
public class LightPresetController {

    private final LightPresetService lightPresetService;

    @GetMapping
    public ResponseEntity<List<LightPresetResponse>> listPresets() {
        List<LightPresetResponse> presets = lightPresetService.listPresets().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(presets);
    }

    @PostMapping
    public ResponseEntity<?> createPreset(@RequestBody CreatePresetRequest request) {
        String username = SecurityUtil.getCurrentUser().getUsername();

        if (request.getDeviceName() == null || request.getDeviceName().isBlank()
                || request.getName() == null || request.getName().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.builder()
                            .message("deviceName and name are required")
                            .error("BAD_REQUEST")
                            .status(400)
                            .build());
        }

        log.info("Preset '{}' from {} requested by {}", request.getName(), request.getDeviceName(), username);

        try {
            LightPreset preset = lightPresetService.createFromDevice(request.getDeviceName(), request.getName().trim());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(preset));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.builder()
                            .message(e.getMessage())
                            .error("CONFLICT")
                            .status(409)
                            .build());
        } catch (Exception e) {
            log.error("Failed to create preset", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .message("Failed to create preset")
                            .error(e.getMessage())
                            .status(500)
                            .build());
        }
    }

    @PostMapping("/{id}/default")
    public ResponseEntity<?> setDefault(@PathVariable UUID id) {
        try {
            LightPreset preset = lightPresetService.setDefault(id);
            return ResponseEntity.ok(toResponse(preset));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.builder()
                            .message(e.getMessage())
                            .error("NOT_FOUND")
                            .status(404)
                            .build());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePreset(@PathVariable UUID id) {
        try {
            lightPresetService.deletePreset(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.builder()
                            .message(e.getMessage())
                            .error("NOT_FOUND")
                            .status(404)
                            .build());
        }
    }

    private LightPresetResponse toResponse(LightPreset preset) {
        return LightPresetResponse.builder()
                .id(preset.getId())
                .name(preset.getName())
                .sourceDevice(preset.getSourceDevice())
                .isDefault(preset.isDefault())
                .createdAt(preset.getCreatedAt())
                .settings(lightPresetService.parseSettings(preset))
                .build();
    }
}
