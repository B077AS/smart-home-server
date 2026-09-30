package smart.home.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smart.home.entity.LightPreset;
import smart.home.repository.LightPresetRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LightPresetService {

    private final LightPresetRepository presetRepository;
    private final ZigbeeDeviceService zigbeeDeviceService;
    private final Gson gson = new Gson();

    // Settings worth snapshotting into a preset — excludes read-only/reporting fields
    // like linkquality, device_temperature, power_outage_count, and the raw "state" on/off.
    private static final Set<String> PRESET_KEYS = Set.of(
            "color_temp", "color", "effect", "effect_speed", "power_on_behavior",
            "dimming_range_minimum", "dimming_range_maximum",
            "transition_curve_curvature", "transition_initial_brightness", "level_config"
    );

    @Transactional
    public LightPreset createFromDevice(String deviceName, String name) {
        Map<String, Object> state = zigbeeDeviceService.getDeviceData(deviceName);
        if (state.isEmpty()) {
            throw new IllegalStateException("No cached data for '" + deviceName + "' yet — refresh and try again");
        }

        Map<String, Object> settings = new LinkedHashMap<>();
        for (String key : PRESET_KEYS) {
            if (state.containsKey(key)) {
                settings.put(key, state.get(key));
            }
        }
        if (settings.isEmpty()) {
            throw new IllegalStateException("No light settings available yet for '" + deviceName + "'");
        }

        LightPreset preset = LightPreset.builder()
                .name(name)
                .sourceDevice(deviceName)
                .settingsJson(gson.toJson(settings))
                .isDefault(false)
                .createdAt(LocalDateTime.now())
                .build();

        LightPreset saved = presetRepository.save(preset);
        log.info("Saved light preset '{}' (id={}) from {}: {}", name, saved.getId(), deviceName, settings);
        return saved;
    }

    public List<LightPreset> listPresets() {
        return presetRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public LightPreset setDefault(UUID id) {
        LightPreset preset = presetRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Preset not found: " + id));

        presetRepository.findByIsDefaultTrue().ifPresent(current -> {
            if (!current.getId().equals(id)) {
                current.setDefault(false);
                presetRepository.save(current);
            }
        });

        preset.setDefault(true);
        LightPreset saved = presetRepository.save(preset);
        log.info("Preset '{}' (id={}) set as default", saved.getName(), saved.getId());
        return saved;
    }

    @Transactional
    public void deletePreset(UUID id) {
        if (!presetRepository.existsById(id)) {
            throw new NoSuchElementException("Preset not found: " + id);
        }
        presetRepository.deleteById(id);
        log.info("Deleted preset id={}", id);
    }

    public LightPreset getDefaultPreset() {
        return presetRepository.findByIsDefaultTrue()
                .orElseThrow(() -> new IllegalStateException("No default preset set — save one and mark it as default first"));
    }

    public LightPreset getById(UUID id) {
        return presetRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Preset not found: " + id));
    }

    public Map<String, Object> parseSettings(LightPreset preset) {
        return gson.fromJson(preset.getSettingsJson(), new TypeToken<Map<String, Object>>() {}.getType());
    }
}
