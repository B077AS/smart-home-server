package smart.home.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smart.home.entity.AppSettings;

import java.util.Optional;

public interface AppSettingsRepository extends JpaRepository<AppSettings, Long> {
    Optional<AppSettings> findBySettingKey(String settingKey);
}
