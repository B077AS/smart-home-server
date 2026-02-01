package smart.home.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import smart.home.service.GarageService;

@Slf4j
@Component
public class PlugScheduleJob implements Job {

    @Autowired
    private GarageService garageService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String action = context.getJobDetail().getJobDataMap().getString("action");
        String source = context.getJobDetail().getJobDataMap().getString("source");

        log.info("Executing scheduled plug {} from {}", action, source);

        try {
            if ("ON".equals(action)) {
                garageService.turnPlugOn("SCHEDULER").get();
            } else if ("OFF".equals(action)) {
                garageService.turnPlugOff("SCHEDULER").get();
            }
            log.info("Scheduled plug {} completed successfully", action);
        } catch (Exception e) {
            log.error("Scheduled plug {} failed", action, e);
            throw new JobExecutionException(e);
        }
    }
}