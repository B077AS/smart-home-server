package smart.home.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smart.home.entity.LightSetting;
import smart.home.repository.LightSettingRepository;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LightSettingService {

    public static final double MAX_TRANSITION_SECONDS = 60;

    private final LightSettingRepository settingRepository;

    public double getTransitionSeconds(String deviceName) {
        return settingRepository.findById(deviceName)
                .map(LightSetting::getTransitionSeconds)
                .orElse(0.0);
    }

    public Map<String, Double> getAllTransitionSeconds() {
        Map<String, Double> result = new LinkedHashMap<>();
        settingRepository.findAll().forEach(s -> result.put(s.getDeviceName(), s.getTransitionSeconds()));
        return result;
    }

    @Transactional
    public double setTransitionSeconds(String deviceName, double seconds) {
        if (Double.isNaN(seconds) || seconds < 0 || seconds > MAX_TRANSITION_SECONDS) {
            throw new IllegalArgumentException("Invalid transition '" + seconds + "'. Must be between 0 and " + MAX_TRANSITION_SECONDS + " seconds");
        }
        settingRepository.save(LightSetting.builder().deviceName(deviceName).transitionSeconds(seconds).build());
        log.info("Set transition for {} to {}s", deviceName, seconds);
        return seconds;
    }
}
