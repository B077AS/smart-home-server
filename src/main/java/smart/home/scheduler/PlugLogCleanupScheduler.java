package smart.home.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import smart.home.repository.PlugLogRepository;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlugLogCleanupScheduler {

    private final PlugLogRepository plugLogRepository;

    // Run on the 1st of every month at 2:30 AM
    @Scheduled(cron = "0 30 2 1 * ?")
    @Transactional
    public void cleanupOldLogs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(1);
        log.info("Cleaning up plug logs older than {}", cutoffDate);

        plugLogRepository.deleteOlderThan(cutoffDate);

        log.info("Plug log cleanup completed");
    }
}