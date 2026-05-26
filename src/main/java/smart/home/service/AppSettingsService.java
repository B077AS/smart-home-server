package smart.home.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smart.home.entity.AppSettings;
import smart.home.repository.AppSettingsRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppSettingsService {

    public static final String GARAGE_TRIGGER_EMAIL_ENABLED  = "garage.trigger.email.enabled";
    public static final String GARAGE_DOOR_OPEN_EMAIL_ENABLED = "garage.door.open.email.enabled";

    private final AppSettingsRepository appSettingsRepository;

    public String get(String key, String defaultValue) {
        return appSettingsRepository.findBySettingKey(key)
                .map(AppSettings::getSettingValue)
                .orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return appSettingsRepository.findBySettingKey(key)
                .map(s -> Boolean.parseBoolean(s.getSettingValue()))
                .orElse(defaultValue);
    }

    public void set(String key, String value) {
        AppSettings setting = appSettingsRepository.findBySettingKey(key)
                .orElseGet(() -> AppSettings.builder().settingKey(key).build());
        setting.setSettingValue(value);
        appSettingsRepository.save(setting);
        log.info("Setting updated: {} = {}", key, value);
    }

    public void setBoolean(String key, boolean value) {
        set(key, String.valueOf(value));
    }
}
