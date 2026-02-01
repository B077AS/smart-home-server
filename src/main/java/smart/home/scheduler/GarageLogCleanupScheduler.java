package smart.home.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import smart.home.repository.GarageLogRepository;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class GarageLogCleanupScheduler {

    private final GarageLogRepository garageLogRepository;

    // Run on the 1st of every month at 2 AM
    @Scheduled(cron = "0 0 2 1 * ?")
    @Transactional
    public void cleanupOldLogs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(1);
        log.info("Cleaning up garage logs older than {}", cutoffDate);

        garageLogRepository.deleteOlderThan(cutoffDate);

        log.info("Garage log cleanup completed");
    }
}