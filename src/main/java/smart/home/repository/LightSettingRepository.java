package smart.home.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import smart.home.entity.LightSetting;

@Repository
public interface LightSettingRepository extends JpaRepository<LightSetting, String> {
}
