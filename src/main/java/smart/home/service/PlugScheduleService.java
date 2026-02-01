package smart.home.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smart.home.entity.PlugScheduleConfig;
import smart.home.repository.PlugScheduleConfigRepository;
import smart.home.scheduler.PlugScheduleJob;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlugScheduleService {

    private final PlugScheduleConfigRepository scheduleRepository;
    private final Scheduler scheduler;

    @PostConstruct
    public void initialize() {
        log.info("Initializing plug schedule service");
        initializeDefaultSchedules();
        rebuildAllSchedules();
    }

    private void initializeDefaultSchedules() {
        // Default weekday schedule: OFF at midnight, ON at 6am
        if (scheduleRepository.findByScheduleType("DEFAULT_WEEKDAY").isEmpty()) {
            scheduleRepository.save(PlugScheduleConfig.builder()
                    .scheduleType("DEFAULT_WEEKDAY")
                    .offTime(LocalTime.MIDNIGHT)
                    .onTime(LocalTime.of(6, 0))
                    .enabled(false)
                    .description("Weekday default schedule")
                    .build());
        }

        // Default weekend schedule: OFF at midnight, ON at 7am
        if (scheduleRepository.findByScheduleType("DEFAULT_WEEKEND").isEmpty()) {
            scheduleRepository.save(PlugScheduleConfig.builder()
                    .scheduleType("DEFAULT_WEEKEND")
                    .offTime(LocalTime.MIDNIGHT)
                    .onTime(LocalTime.of(7, 0))
                    .enabled(false)
                    .description("Weekend default schedule")
                    .build());
        }
    }

    @Transactional
    public void rebuildAllSchedules() {
        try {
            log.info("Rebuilding all Quartz schedules");

            // Clear existing schedules
            scheduler.clear();

            // Get default schedules
            Optional<PlugScheduleConfig> weekdayOpt = scheduleRepository.findByScheduleType("DEFAULT_WEEKDAY");
            Optional<PlugScheduleConfig> weekendOpt = scheduleRepository.findByScheduleType("DEFAULT_WEEKEND");

            if (weekdayOpt.isPresent() && weekdayOpt.get().getEnabled()) {
                scheduleWeekdayJobs(weekdayOpt.get());
            }

            if (weekendOpt.isPresent() && weekendOpt.get().getEnabled()) {
                scheduleWeekendJobs(weekendOpt.get());
            }

            // Schedule exceptions (they will override defaults on specific dates)
            List<PlugScheduleConfig> exceptions = scheduleRepository
                    .findAllByExceptionDateIsNotNullAndEnabledOrderByExceptionDateDesc(true);

            for (PlugScheduleConfig exception : exceptions) {
                if (exception.getExceptionDate().isAfter(LocalDate.now().minusDays(1))) {
                    scheduleExceptionJobs(exception);
                }
            }

            log.info("Schedule rebuild complete");
        } catch (SchedulerException e) {
            log.error("Failed to rebuild schedules", e);
            throw new RuntimeException("Failed to rebuild schedules", e);
        }
    }

    private void scheduleWeekdayJobs(PlugScheduleConfig config) throws SchedulerException {
        // Monday-Friday
        for (int day = 2; day <= 6; day++) { // Quartz: 1=SUN, 2=MON, ..., 7=SAT
            scheduleJob("WEEKDAY_OFF", "OFF", day, config.getOffTime());
            scheduleJob("WEEKDAY_ON", "ON", day, config.getOnTime());
        }
    }

    private void scheduleWeekendJobs(PlugScheduleConfig config) throws SchedulerException {
        // Saturday and Sunday
        scheduleJob("WEEKEND_OFF_SAT", "OFF", 7, config.getOffTime()); // Saturday
        scheduleJob("WEEKEND_ON_SAT", "ON", 7, config.getOnTime());
        scheduleJob("WEEKEND_OFF_SUN", "OFF", 1, config.getOffTime()); // Sunday
        scheduleJob("WEEKEND_ON_SUN", "ON", 1, config.getOnTime());
    }

    private void scheduleJob(String jobName, String action, int dayOfWeek, LocalTime time) throws SchedulerException {
        JobDetail job = JobBuilder.newJob(PlugScheduleJob.class)
                .withIdentity(jobName + "_" + dayOfWeek, "PLUG_SCHEDULE")
                .usingJobData("action", action)
                .usingJobData("source", jobName)
                .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName + "_TRIGGER_" + dayOfWeek, "PLUG_SCHEDULE")
                .withSchedule(CronScheduleBuilder.cronSchedule(
                        String.format("0 %d %d ? * %d", time.getMinute(), time.getHour(), dayOfWeek)))
                .build();

        scheduler.scheduleJob(job, trigger);
        log.info("Scheduled {} on day {} at {}", action, dayOfWeek, time);
    }

    private void scheduleExceptionJobs(PlugScheduleConfig config) throws SchedulerException {
        LocalDate date = config.getExceptionDate();
        String dateStr = date.toString();

        // OFF job
        JobDetail offJob = JobBuilder.newJob(PlugScheduleJob.class)
                .withIdentity("EXCEPTION_OFF_" + dateStr, "PLUG_SCHEDULE")
                .usingJobData("action", "OFF")
                .usingJobData("source", "EXCEPTION_" + dateStr)
                .build();

        Date offFireTime = Date.from(date.atTime(config.getOffTime())
                .atZone(ZoneId.systemDefault()).toInstant());

        Trigger offTrigger = TriggerBuilder.newTrigger()
                .withIdentity("EXCEPTION_OFF_TRIGGER_" + dateStr, "PLUG_SCHEDULE")
                .startAt(offFireTime)
                .build();

        scheduler.scheduleJob(offJob, offTrigger);

        // ON job
        JobDetail onJob = JobBuilder.newJob(PlugScheduleJob.class)
                .withIdentity("EXCEPTION_ON_" + dateStr, "PLUG_SCHEDULE")
                .usingJobData("action", "ON")
                .usingJobData("source", "EXCEPTION_" + dateStr)
                .build();

        Date onFireTime = Date.from(date.atTime(config.getOnTime())
                .atZone(ZoneId.systemDefault()).toInstant());

        Trigger onTrigger = TriggerBuilder.newTrigger()
                .withIdentity("EXCEPTION_ON_TRIGGER_" + dateStr, "PLUG_SCHEDULE")
                .startAt(onFireTime)
                .build();

        scheduler.scheduleJob(onJob, onTrigger);

        log.info("Scheduled exception for {} - OFF at {}, ON at {}",
                date, config.getOffTime(), config.getOnTime());
    }

    public PlugScheduleConfig getWeekdaySchedule() {
        return scheduleRepository.findByScheduleType("DEFAULT_WEEKDAY")
                .orElseThrow(() -> new RuntimeException("Weekday schedule not found"));
    }

    public PlugScheduleConfig getWeekendSchedule() {
        return scheduleRepository.findByScheduleType("DEFAULT_WEEKEND")
                .orElseThrow(() -> new RuntimeException("Weekend schedule not found"));
    }

    public List<PlugScheduleConfig> getAllExceptions() {
        return scheduleRepository
                .findAllByExceptionDateIsNotNullAndEnabledOrderByExceptionDateDesc(true);
    }

    @Transactional
    public void updateDefaultSchedule(String type, LocalTime onTime, LocalTime offTime) {
        PlugScheduleConfig config = scheduleRepository.findByScheduleType(type)
                .orElseThrow(() -> new RuntimeException("Schedule not found: " + type));

        config.setOnTime(onTime);
        config.setOffTime(offTime);
        scheduleRepository.save(config);

        rebuildAllSchedules();
    }

    @Transactional
    public PlugScheduleConfig createException(LocalDate date, LocalTime onTime, LocalTime offTime, String description) {
        // Check if exception already exists
        Optional<PlugScheduleConfig> existing = scheduleRepository
                .findByExceptionDateAndEnabled(date, true);

        if (existing.isPresent()) {
            throw new RuntimeException("Exception already exists for " + date);
        }

        PlugScheduleConfig exception = scheduleRepository.save(PlugScheduleConfig.builder()
                .scheduleType("EXCEPTION")
                .exceptionDate(date)
                .onTime(onTime)
                .offTime(offTime)
                .enabled(true)
                .description(description)
                .build());

        rebuildAllSchedules();
        return exception;
    }

    @Transactional
    public void deleteException(Long id) {
        scheduleRepository.deleteById(id);
        rebuildAllSchedules();
    }

    @Transactional
    public void toggleSchedule(String type, boolean enabled) {
        PlugScheduleConfig config = scheduleRepository.findByScheduleType(type)
                .orElseThrow(() -> new RuntimeException("Schedule not found: " + type));

        config.setEnabled(enabled);
        scheduleRepository.save(config);
        rebuildAllSchedules();
    }
}