package smart.home.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import smart.home.entity.PlugScheduleConfig;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlugScheduleConfigRepository extends JpaRepository<PlugScheduleConfig, Long> {

    Optional<PlugScheduleConfig> findByScheduleType(String scheduleType);

    Optional<PlugScheduleConfig> findByExceptionDateAndEnabled(LocalDate date, Boolean enabled);

    List<PlugScheduleConfig> findAllByExceptionDateIsNotNullAndEnabledOrderByExceptionDateDesc(Boolean enabled);
}