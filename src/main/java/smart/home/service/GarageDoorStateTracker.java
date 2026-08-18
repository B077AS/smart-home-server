package smart.home.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smart.home.dto.GarageDoorStatus;
import smart.home.entity.GarageDoorState;
import smart.home.entity.GarageDoorStateLog;
import smart.home.repository.GarageDoorStateLogRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GarageDoorStateTracker {

    private final GarageDoorStateLogRepository stateLogRepository;
    private final EmailService emailService;
    private final AppSettingsService appSettingsService;

    // Power consumption thresholds
    private static final double POWER_IDLE = 7.5;              // ~7W idle without light
    private static final double POWER_IDLE_WITH_LIGHT = 27.0;  // ~26-28W idle with light
    private static final double POWER_LIGHT_THRESHOLD = 20.0;  // Above this, light is likely on
    private static final double POWER_MOVING_MIN = 30.0;       // Minimum power when moving
    private static final double POWER_MOVING_SUSTAINED = 45.0; // Sustained movement threshold

    // Timing thresholds
    private static final long OPENING_TIME_TYPICAL = 13000;    // ~13 seconds to open
    private static final long CLOSING_TIME_TYPICAL = 24000;    // ~24 seconds to close
    private static final long OPENING_TIME_MAX = 20000;        // Max time before considering stuck
    private static final long CLOSING_TIME_MAX = 35000;        // Max time before considering stuck
    private static final long LIGHT_TIMEOUT = 30000;           // Light stays on ~30s after operation

    // Movement validation timeouts - IMPROVED
    private static final long MOVEMENT_CONFIRMATION_TIMEOUT = 25000; // 25 seconds grace period
    private static final long MOVEMENT_CONFIRMATION_WARN = 15000;     // Warn after 15s, don't fail yet
    private static final long MINIMAL_MOVEMENT_POWER_DROP_TIME = 3000; // Power must drop within 3s for 2cm stuck

    private static final long PLUG_TELEMETRY_TIMEOUT = 90000;

    // Vibration sensor angle thresholds - NEW
    private static final int SIGNIFICANT_ANGLE_CHANGE = 30; // Degrees - significant rotation indicating movement
    private static final int ANGLE_TOLERANCE = 5;           // Degrees - tolerance for sensor noise/fluctuations

    // State tracking
    private GarageDoorState currentState = GarageDoorState.UNKNOWN;
    private LocalDateTime lastStateChange = LocalDateTime.now();
    private LocalDateTime operationStartTime = null;
    private Double lastPowerReading = null;
    private LocalDateTime lastPowerUpdate = LocalDateTime.now();
    private Integer lastKnownProgress = null;
    private GarageDoorState lastMovementDirection = null;

    // MOVEMENT VALIDATION
    private LocalDateTime motorStartTime = null;           // When motor first engaged (power spike)
    private Boolean tiltStateAtMotorStart = null;          // Tilt sensor state when motor started
    private GarageDoorState intendedDirection = null;      // Which direction we're trying to move
    private boolean movementConfirmed = false;             // Has tilt sensor OR vibration sensor confirmed movement?

    // POWER PATTERN TRACKING for 2cm stuck detection - IMPROVED
    private Double peakPowerSinceMotorStart = null;        // Track highest power reading
    private LocalDateTime peakPowerTime = null;            // When we saw peak power
    private LocalDateTime lastHighPowerTime = null;        // Last time we saw power > 45W
    private boolean powerDropDetected = false;              // Track if power dropped after initial spike

    // Power history for better analysis
    private List<PowerReading> powerHistory = new ArrayList<>();
    private static final int POWER_HISTORY_SIZE = 10;

    // Movement tracking
    private List<PowerReading> recentReadings = new ArrayList<>();
    private static final int MAX_READINGS = 20;

    // Sensor states
    private Boolean lastTiltSensorState = null; // false = open, true = closed
    private Boolean lastVibrationDetected = null;
    private String lastVibrationAction = null;
    private LocalDateTime lastTiltChange = null;
    private LocalDateTime lastVibrationChange = null;

    // Vibration sensor angle tracking for movement confirmation
    private Integer lastAngleX = null;
    private Integer lastAngleY = null;
    private Integer lastAngleZ = null;
    private Integer angleXAtMotorStart = null;
    private Integer angleYAtMotorStart = null;
    private Integer angleZAtMotorStart = null;

    // Post-stuck recovery context — survives reset() so the next trigger can infer the correct reversal direction
    private GarageDoorState stateBeforeReset = null;
    private Boolean tiltAtReset = null;

    public synchronized GarageDoorStatus updateState(
            Map<String, Object> plugState,
            Map<String, Object> tiltSensorState,
            Map<String, Object> vibrationSensorState,
            LocalDateTime plugLastSeen
    ) {
        LocalDateTime now = LocalDateTime.now();

        // Extract current readings
        Double currentPower = extractPower(plugState);
        Boolean tiltOpen = extractTiltSensor(tiltSensorState); // false = open, true = closed
        Boolean vibrating = extractVibration(vibrationSensorState);
        String vibrationAction = extractVibrationAction(vibrationSensorState);

        // Extract vibration sensor angles
        Integer currentAngleX = extractAngle(vibrationSensorState, "angle_x");
        Integer currentAngleY = extractAngle(vibrationSensorState, "angle_y");
        Integer currentAngleZ = extractAngle(vibrationSensorState, "angle_z");

        log.debug("Update: state={}, power={}W, tilt={}, vib={}, action={}, angles=[{},{},{}]",
                currentState, currentPower, tiltOpen, vibrating, vibrationAction,
                currentAngleX, currentAngleY, currentAngleZ);

        // Track power readings
        if (currentPower != null) {
            addPowerReading(currentPower, now);
            addPowerToHistory(currentPower, now);
            lastPowerReading = currentPower;
            lastPowerUpdate = now;

            // Track peak power and high power timing
            if (motorStartTime != null) {
                if (peakPowerSinceMotorStart == null || currentPower > peakPowerSinceMotorStart) {
                    peakPowerSinceMotorStart = currentPower;
                    peakPowerTime = now;
                    log.debug("Peak power: {}W at {}", peakPowerSinceMotorStart, peakPowerTime);
                }
                if (currentPower >= POWER_MOVING_SUSTAINED) {
                    lastHighPowerTime = now;
                }

                // Detect power drop after initial spike
                if (peakPowerSinceMotorStart != null &&
                        peakPowerSinceMotorStart >= POWER_MOVING_MIN &&
                        currentPower < POWER_MOVING_MIN &&
                        currentPower >= POWER_LIGHT_THRESHOLD) {

                    if (!powerDropDetected) {
                        long timeSincePeak = Duration.between(peakPowerTime, now).toMillis();
                        log.warn("POWER DROP: {}W->{}W @T+{}ms", peakPowerSinceMotorStart, currentPower, timeSincePeak);
                        powerDropDetected = true;
                    }
                }
            }
        }

        // Track sensor changes
        boolean tiltChanged = false;
        boolean vibrationChanged = false;
        boolean significantAngleChange = false;

        if (tiltOpen != null && !tiltOpen.equals(lastTiltSensorState)) {
            log.debug("Tilt changed: {} -> {}", lastTiltSensorState, tiltOpen);
            lastTiltSensorState = tiltOpen;
            lastTiltChange = now;
            tiltChanged = true;

            // If motor is running and tilt changed, movement is confirmed
            if (motorStartTime != null && !movementConfirmed) {
                long timeSinceMotorStart = Duration.between(motorStartTime, now).toMillis();
                log.info("MOVEMENT CONFIRMED by tilt sensor: tilt changed at T+{}ms", timeSinceMotorStart);
                movementConfirmed = true;
            }
        }

        if (vibrating != null && !vibrating.equals(lastVibrationDetected)) {
            log.debug("Vibration changed: {} -> {}", lastVibrationDetected, vibrating);
            lastVibrationDetected = vibrating;
            lastVibrationChange = now;
            vibrationChanged = true;
        }

        if (vibrationAction != null && !vibrationAction.equals(lastVibrationAction)) {
            log.debug("Vibration action: {} -> {}", lastVibrationAction, vibrationAction);
            lastVibrationAction = vibrationAction;
        }

        // Track angle changes
        if (currentAngleX != null && currentAngleY != null && currentAngleZ != null) {
            // Check for significant angle change
            if (motorStartTime != null && !movementConfirmed &&
                    angleXAtMotorStart != null && angleYAtMotorStart != null && angleZAtMotorStart != null) {

                int deltaX = Math.abs(currentAngleX - angleXAtMotorStart);
                int deltaY = Math.abs(currentAngleY - angleYAtMotorStart);
                int deltaZ = Math.abs(currentAngleZ - angleZAtMotorStart);

                // Account for angle wrapping (e.g., -180 to +180 transition)
                deltaX = Math.min(deltaX, 180 - deltaX);
                deltaY = Math.min(deltaY, 180 - deltaY);
                deltaZ = Math.min(deltaZ, 180 - deltaZ);

                // Check if any axis changed significantly (beyond tolerance + threshold)
                if (deltaX > ANGLE_TOLERANCE + SIGNIFICANT_ANGLE_CHANGE ||
                        deltaY > ANGLE_TOLERANCE + SIGNIFICANT_ANGLE_CHANGE ||
                        deltaZ > ANGLE_TOLERANCE + SIGNIFICANT_ANGLE_CHANGE) {

                    long timeSinceMotorStart = Duration.between(motorStartTime, now).toMillis();
                    log.info("MOVEMENT CONFIRMED by vibration sensor: significant angle change at T+{}ms " +
                                    "(ΔX={}, ΔY={}, ΔZ={})",
                            timeSinceMotorStart, deltaX, deltaY, deltaZ);
                    movementConfirmed = true;
                    significantAngleChange = true;
                }
            }

            // Update last known angles
            lastAngleX = currentAngleX;
            lastAngleY = currentAngleY;
            lastAngleZ = currentAngleZ;
        }

        GarageDoorState newState = determineState(
                currentPower,
                tiltOpen,
                significantAngleChange,
                now,
                plugLastSeen
        );

        if (newState != currentState) {
            log.info("STATE CHANGE: {} -> {}", currentState, newState);

            // Log state change to database
            try {
                GarageDoorStateLog stateLog = GarageDoorStateLog.builder()
                        .oldState(currentState.name())
                        .newState(newState.name())
                        .timestamp(now)
                        .build();
                stateLogRepository.save(stateLog);
                log.debug("State change logged to database");
            } catch (Exception e) {
                log.error("Failed to log state change to database", e);
            }

            // Email alert when door reaches IDLE_OPEN
            if (newState == GarageDoorState.IDLE_OPEN &&
                    appSettingsService.getBoolean(AppSettingsService.GARAGE_DOOR_OPEN_EMAIL_ENABLED, false)) {
                emailService.sendGarageDoorOpenNotification();
            }

            // Track movement direction
            if (newState == GarageDoorState.OPENING || newState == GarageDoorState.CLOSING) {
                lastMovementDirection = newState;
                log.debug("Movement direction: {}", lastMovementDirection);
            }

            // Reset operation start time on new operation
            if (newState == GarageDoorState.OPENING || newState == GarageDoorState.CLOSING) {
                operationStartTime = now;
                log.debug("Operation started at {}", operationStartTime);
            } else if (newState == GarageDoorState.IDLE_CLOSED ||
                    newState == GarageDoorState.IDLE_OPEN ||
                    newState == GarageDoorState.IDLE_WITH_LIGHT) {
                log.debug("Operation completed, duration={}ms",
                        operationStartTime != null ? Duration.between(operationStartTime, now).toMillis() : 0);
            }

            currentState = newState;
            lastStateChange = now;
        }

        return buildStatus(currentPower, tiltOpen, vibrating, vibrationAction, now);
    }

    private GarageDoorState determineState(
            Double power,
            Boolean tiltOpen,
            boolean significantAngleChange,
            LocalDateTime now,
            LocalDateTime plugLastSeen
    ) {
        if (power == null) {
            log.debug("No power reading");
            return currentState;
        }

        long timeSinceStateChange = Duration.between(lastStateChange, now).toMillis();
        long timeSinceOperation = operationStartTime != null ?
                Duration.between(operationStartTime, now).toMillis() : 0;

        log.debug("Timing: stateChange={}ms, operation={}ms", timeSinceStateChange, timeSinceOperation);

        if ((currentState == GarageDoorState.OPENING || currentState == GarageDoorState.CLOSING)) {
            long plugSilenceMs = plugLastSeen != null ?
                    Duration.between(plugLastSeen, now).toMillis() : Long.MAX_VALUE;
            if (plugSilenceMs > PLUG_TELEMETRY_TIMEOUT) {
                log.warn("Lost plug telemetry for {}ms while {} — marking door UNKNOWN (power may have been cut upstream of the smart plug)",
                        plugSilenceMs, currentState);
                motorStartTime = null;
                tiltStateAtMotorStart = null;
                movementConfirmed = false;
                intendedDirection = null;
                peakPowerSinceMotorStart = null;
                peakPowerTime = null;
                lastHighPowerTime = null;
                powerDropDetected = false;
                operationStartTime = null;
                return GarageDoorState.UNKNOWN;
            }
        }

        if (power >= POWER_MOVING_MIN && motorStartTime == null) {
            log.info("MOTOR ENGAGED: power={}W", power);
            motorStartTime = now;
            tiltStateAtMotorStart = tiltOpen;
            movementConfirmed = false;
            peakPowerSinceMotorStart = power;
            peakPowerTime = now;
            lastHighPowerTime = now;
            powerDropDetected = false;

            angleXAtMotorStart = lastAngleX;
            angleYAtMotorStart = lastAngleY;
            angleZAtMotorStart = lastAngleZ;
            log.debug("Captured angles at motor start: X={}, Y={}, Z={}",
                    angleXAtMotorStart, angleYAtMotorStart, angleZAtMotorStart);

            // Determine intended direction, accounting for post-stuck recovery.
            // After a stuck event the door barely moved, so tilt reads the same as before.
            // Normal tilt inference would pick the same direction that just got stuck — wrong.
            // Use the saved stuck state to infer the reversal direction instead.
            if (stateBeforeReset == GarageDoorState.STUCK_OPENING &&
                    tiltAtReset != null && tiltAtReset.equals(tiltOpen)) {
                intendedDirection = GarageDoorState.CLOSING;
                log.info("Post-stuck recovery: reversing to CLOSING (was STUCK_OPENING, tilt unchanged)");
            } else if (stateBeforeReset == GarageDoorState.STUCK_CLOSING &&
                    tiltAtReset != null && tiltAtReset.equals(tiltOpen)) {
                intendedDirection = GarageDoorState.OPENING;
                log.info("Post-stuck recovery: reversing to OPENING (was STUCK_CLOSING, tilt unchanged)");
            } else if (tiltOpen != null) {
                intendedDirection = tiltOpen ? GarageDoorState.OPENING : GarageDoorState.CLOSING;
                log.debug("Intended direction: {} (tilt={})", intendedDirection, tiltOpen);
            } else {
                log.warn("Cannot determine direction - no tilt data");
                intendedDirection = null;
            }
            stateBeforeReset = null;
            tiltAtReset = null;
        }

        if (motorStartTime != null && !movementConfirmed) {
            long timeSinceMotorStart = Duration.between(motorStartTime, now).toMillis();
            long timeSinceHighPower = lastHighPowerTime != null ?
                    Duration.between(lastHighPowerTime, now).toMillis() : timeSinceMotorStart;
            long timeSincePeak = peakPowerTime != null ?
                    Duration.between(peakPowerTime, now).toMillis() : timeSinceMotorStart;

            // SCENARIO 1 - Minimal Movement (1-3cm) Detection
            // Pattern: Quick power spike followed by rapid drop to light-only range
            // Must happen quickly (within 3s of peak) to be considered stuck
            if (powerDropDetected &&
                    peakPowerSinceMotorStart != null &&
                    peakPowerSinceMotorStart >= POWER_MOVING_MIN &&
                    power >= POWER_LIGHT_THRESHOLD &&
                    power < POWER_MOVING_MIN &&
                    timeSincePeak <= MINIMAL_MOVEMENT_POWER_DROP_TIME &&
                    timeSinceMotorStart >= 1500) {  // At least 2s elapsed to avoid false positives

                log.warn("STUCK: MINIMAL MOVEMENT (1-3cm) | peak={}W@T+{}ms -> cur={}W@T+{}ms | dropTime={}ms (max={}ms) | tilt={} (unchanged) | Analysis: moved 1-3cm then stuck",
                        String.format("%.1f", peakPowerSinceMotorStart),
                        Duration.between(motorStartTime, peakPowerTime).toMillis(),
                        String.format("%.1f", power),
                        timeSinceMotorStart,
                        timeSincePeak,
                        MINIMAL_MOVEMENT_POWER_DROP_TIME,
                        tiltOpen != null ? (tiltOpen ? "CLOSED" : "OPEN") : "UNKNOWN");

                if (intendedDirection == GarageDoorState.OPENING) {
                    return GarageDoorState.STUCK_OPENING;
                } else if (intendedDirection == GarageDoorState.CLOSING) {
                    return GarageDoorState.STUCK_CLOSING;
                } else {
                    return GarageDoorState.STUCK_OPENING;
                }
            }

            // SCENARIO 2 - No Movement Detection with Grace Period
            // Only trigger stuck if we're past the extended grace period
            // This prevents false positives for slow-moving doors
            if (power >= POWER_MOVING_MIN && timeSinceMotorStart > MOVEMENT_CONFIRMATION_TIMEOUT) {
                log.warn("STUCK: NO MOVEMENT (0cm) | motorRunning={}ms (max={}ms) | power={}W (sustained) | tiltStart={} tiltCur={} (NO CHANGE) | angles: no significant change | Analysis: motor on but door not moving",
                        timeSinceMotorStart,
                        MOVEMENT_CONFIRMATION_TIMEOUT,
                        String.format("%.1f", power),
                        tiltStateAtMotorStart != null ? (tiltStateAtMotorStart ? "CLOSED" : "OPEN") : "UNKNOWN",
                        tiltOpen != null ? (tiltOpen ? "CLOSED" : "OPEN") : "UNKNOWN");

                if (intendedDirection == GarageDoorState.OPENING) {
                    return GarageDoorState.STUCK_OPENING;
                } else if (intendedDirection == GarageDoorState.CLOSING) {
                    return GarageDoorState.STUCK_CLOSING;
                } else {
                    return GarageDoorState.STUCK_OPENING;
                }
            }

            // Warning (not stuck yet) - for slow movement
            // Log a warning but don't change state yet
            if (power >= POWER_MOVING_MIN &&
                    timeSinceMotorStart > MOVEMENT_CONFIRMATION_WARN &&
                    timeSinceMotorStart <= MOVEMENT_CONFIRMATION_TIMEOUT) {

                log.warn("SLOW MOVEMENT: motor={}ms power={}W remaining={}ms (waiting for tilt or angle confirmation...)",
                        timeSinceMotorStart, power, MOVEMENT_CONFIRMATION_TIMEOUT - timeSinceMotorStart);
            }

            // Still within grace period, waiting for tilt or angle to change
            if (timeSinceMotorStart <= MOVEMENT_CONFIRMATION_TIMEOUT) {
                if (power < POWER_MOVING_MIN) {
                    // Motor stopped before movement was confirmed (brief recovery reversal).
                    // Reset now so the next trigger starts with a clean motor state.
                    log.info("Motor stopped during grace period without movement confirmed ({}ms) — resetting for next trigger", timeSinceMotorStart);
                    motorStartTime = null;
                    tiltStateAtMotorStart = null;
                    movementConfirmed = false;
                    intendedDirection = null;
                    peakPowerSinceMotorStart = null;
                    peakPowerTime = null;
                    lastHighPowerTime = null;
                    powerDropDetected = false;
                    angleXAtMotorStart = null;
                    angleYAtMotorStart = null;
                    angleZAtMotorStart = null;
                    // Fall through to idle/light logic
                } else {
                    log.debug("Waiting for confirmation: elapsed={}ms, grace={}ms",
                            timeSinceMotorStart, MOVEMENT_CONFIRMATION_TIMEOUT);
                    if (intendedDirection != null) {
                        return intendedDirection;
                    }
                }
            }
        }

        // MOTOR STOPPED - Reset tracking
        if (motorStartTime != null && power < POWER_MOVING_MIN) {
            log.debug("Motor stopped: power={}W", power);

            // Only reset if we're not in a stuck state
            // (stuck states should persist until user triggers again)
            if (currentState != GarageDoorState.STUCK_OPENING &&
                    currentState != GarageDoorState.STUCK_CLOSING) {
                motorStartTime = null;
                tiltStateAtMotorStart = null;
                movementConfirmed = false;
                intendedDirection = null;
                peakPowerSinceMotorStart = null;
                peakPowerTime = null;
                lastHighPowerTime = null;
                powerDropDetected = false;
                angleXAtMotorStart = null;
                angleYAtMotorStart = null;
                angleZAtMotorStart = null;
            }
        }

        // IDLE STATES (no movement)
        if (power < POWER_LIGHT_THRESHOLD) {
            log.debug("Idle without light: power={}W < {}", power, POWER_LIGHT_THRESHOLD);

            // Check if this is an interrupted operation
            if ((currentState == GarageDoorState.OPENING || currentState == GarageDoorState.CLOSING) &&
                    operationStartTime != null) {

                long operationDuration = Duration.between(operationStartTime, now).toMillis();

                if (currentState == GarageDoorState.OPENING && operationDuration < OPENING_TIME_TYPICAL - 2000) {
                    log.info("OPENING interrupted: duration={}ms, typical={}ms",
                            operationDuration, OPENING_TIME_TYPICAL);
                    lastKnownProgress = (int) ((operationDuration * 100.0) / OPENING_TIME_TYPICAL);
                    return GarageDoorState.STOPPED_PARTIAL;
                }

                if (currentState == GarageDoorState.CLOSING && operationDuration < CLOSING_TIME_TYPICAL - 2000) {
                    log.info("CLOSING interrupted: duration={}ms, typical={}ms",
                            operationDuration, CLOSING_TIME_TYPICAL);
                    lastKnownProgress = (int) ((operationDuration * 100.0) / CLOSING_TIME_TYPICAL);
                    return GarageDoorState.STOPPED_PARTIAL;
                }
            }

            // Determine if open or closed based on tilt sensor
            if (tiltOpen != null) {
                if (!tiltOpen) {
                    log.debug("Tilt indicates OPEN (contact=false)");
                    lastKnownProgress = null;
                    return GarageDoorState.IDLE_OPEN;
                } else {
                    log.debug("Tilt indicates CLOSED (contact=true)");
                    lastKnownProgress = null;
                    return GarageDoorState.IDLE_CLOSED;
                }
            }

            if (currentState == GarageDoorState.STOPPED_PARTIAL ||
                    currentState == GarageDoorState.IDLE_PARTIAL) {
                log.debug("Maintaining partial state (no sensor)");
                return GarageDoorState.IDLE_PARTIAL;
            }

            if (currentState == GarageDoorState.IDLE_OPEN ||
                    currentState == GarageDoorState.IDLE_CLOSED) {
                log.debug("No tilt data, maintaining: {}", currentState);
                return currentState;
            }

            log.debug("No tilt data, not idle -> UNKNOWN");
            return GarageDoorState.UNKNOWN;
        }

        // IDLE WITH LIGHT
        if (power >= POWER_LIGHT_THRESHOLD && power < POWER_MOVING_MIN) {
            log.debug("Light range: {}W ({}W to {}W)",
                    power, POWER_LIGHT_THRESHOLD, POWER_MOVING_MIN);

            if (operationStartTime != null &&
                    Duration.between(operationStartTime, now).toMillis() < LIGHT_TIMEOUT) {
                log.debug("Within light timeout, idle with light");

                if (currentState == GarageDoorState.STOPPED_PARTIAL) {
                    return GarageDoorState.STOPPED_PARTIAL;
                }

                if (tiltOpen != null) {
                    return !tiltOpen ? GarageDoorState.IDLE_OPEN : GarageDoorState.IDLE_CLOSED;
                }

                return GarageDoorState.IDLE_WITH_LIGHT;
            }

            log.debug("Light on but outside timeout, maintaining state");
            return currentState;
        }

        // ACTIVE MOVEMENT (power high AND movement confirmed)
        if (power >= POWER_MOVING_MIN && movementConfirmed) {
            log.debug("Movement confirmed: power={}W", power);

            if (currentState == GarageDoorState.OPENING) {
                if (timeSinceOperation > OPENING_TIME_MAX) {
                    log.warn("OPENING exceeded max: {}ms>{}ms (still moving)", timeSinceOperation, OPENING_TIME_MAX);
                }
                return GarageDoorState.OPENING;
            }

            if (currentState == GarageDoorState.CLOSING) {
                if (timeSinceOperation > CLOSING_TIME_MAX) {
                    log.warn("CLOSING exceeded max: {}ms>{}ms (still moving)", timeSinceOperation, CLOSING_TIME_MAX);
                }
                return GarageDoorState.CLOSING;
            }

            // Starting movement
            if (intendedDirection != null) {
                log.debug("Continuing: direction={}", intendedDirection);
                lastKnownProgress = null;
                return intendedDirection;
            }

            // Fallback: use tilt sensor
            if (tiltOpen != null) {
                log.debug("Direction from tilt: {}", tiltOpen ? "OPENING" : "CLOSING");
                lastKnownProgress = null;
                return tiltOpen ? GarageDoorState.OPENING : GarageDoorState.CLOSING;
            }

            log.debug("Cannot determine direction, defaulting to OPENING");
            return GarageDoorState.OPENING;
        }

        // MOTOR RUNNING, waiting for confirmation
        if (power >= POWER_MOVING_MIN && !movementConfirmed && intendedDirection != null) {
            log.debug("Motor running, waiting for tilt or angle confirmation");
            return intendedDirection;
        }

        log.debug("No state change, maintaining: {}", currentState);
        return currentState;
    }

    private GarageDoorStatus buildStatus(
            Double power,
            Boolean tiltOpen,
            Boolean vibrating,
            String vibrationAction,
            LocalDateTime now
    ) {
        GarageDoorStatus.GarageDoorStatusBuilder builder = GarageDoorStatus.builder()
                .state(currentState)
                .currentPower(power)
                .tiltSensorOpen(tiltOpen != null ? !tiltOpen : null)
                .vibrationDetected(vibrating)
                .vibrationAction(vibrationAction)
                .lastUpdate(now);

        if (operationStartTime != null) {
            long duration = Duration.between(operationStartTime, now).toMillis();
            builder.operationDurationMs(duration);
            builder.operationStartTime(operationStartTime);
        }

        Integer progress = estimateProgress(now);
        builder.estimatedProgress(progress);

        boolean stuckWarning = currentState == GarageDoorState.STUCK_OPENING ||
                currentState == GarageDoorState.STUCK_CLOSING;
        builder.warningStuck(stuckWarning);

        String message = buildStatusMessage(progress);
        builder.message(message);

        return builder.build();
    }

    private Integer estimateProgress(LocalDateTime now) {
        if (operationStartTime == null) {
            if (currentState == GarageDoorState.STOPPED_PARTIAL ||
                    currentState == GarageDoorState.IDLE_PARTIAL) {
                return lastKnownProgress;
            }
            return null;
        }

        long elapsed = Duration.between(operationStartTime, now).toMillis();

        if (currentState == GarageDoorState.OPENING) {
            int progress = (int) ((elapsed * 100.0) / OPENING_TIME_TYPICAL);
            return Math.min(progress, 95);
        }

        if (currentState == GarageDoorState.CLOSING) {
            int progress = (int) ((elapsed * 100.0) / CLOSING_TIME_TYPICAL);
            return Math.min(progress, 95);
        }

        if (currentState == GarageDoorState.IDLE_OPEN) return 100;
        if (currentState == GarageDoorState.IDLE_CLOSED) return 0;

        if (currentState == GarageDoorState.STOPPED_PARTIAL ||
                currentState == GarageDoorState.IDLE_PARTIAL) {
            return lastKnownProgress;
        }

        return null;
    }

    private String buildStatusMessage(Integer progress) {
        switch (currentState) {
            case IDLE_CLOSED:
                return "Door is closed";
            case IDLE_OPEN:
                return "Door is open";
            case IDLE_PARTIAL:
                return progress != null ?
                        String.format("Door is partially open (%d%%)", progress) :
                        "Door is partially open";
            case STOPPED_PARTIAL:
                return progress != null ?
                        String.format("Door stopped at %d%%", progress) :
                        "Door stopped";
            case IDLE_WITH_LIGHT:
                return "Door is idle (light on)";
            case OPENING:
                return progress != null ?
                        String.format("Opening... %d%%", progress) :
                        "Opening...";
            case CLOSING:
                return progress != null ?
                        String.format("Closing... %d%%", progress) :
                        "Closing...";
            case STUCK_OPENING:
                return "Door stuck opening - check for obstructions (manual intervention may be needed)";
            case STUCK_CLOSING:
                return "Door stuck closing - check for obstructions (manual intervention may be needed)";
            case UNKNOWN:
            default:
                return "Door status unknown";
        }
    }

    private void addPowerReading(double power, LocalDateTime timestamp) {
        recentReadings.add(new PowerReading(power, timestamp));
        if (recentReadings.size() > MAX_READINGS) {
            recentReadings.remove(0);
        }
    }

    private void addPowerToHistory(double power, LocalDateTime timestamp) {
        powerHistory.add(new PowerReading(power, timestamp));
        if (powerHistory.size() > POWER_HISTORY_SIZE) {
            powerHistory.remove(0);
        }
    }

    private Double extractPower(Map<String, Object> plugState) {
        if (plugState == null || plugState.isEmpty()) return null;
        Object powerObj = plugState.get("power");
        return powerObj instanceof Number ? ((Number) powerObj).doubleValue() : null;
    }

    private Boolean extractTiltSensor(Map<String, Object> tiltState) {
        if (tiltState == null || tiltState.isEmpty()) return null;
        Object contactObj = tiltState.get("contact");
        return contactObj instanceof Boolean ? (Boolean) contactObj : null;
    }

    private Boolean extractVibration(Map<String, Object> vibrationState) {
        if (vibrationState == null || vibrationState.isEmpty()) return null;
        Object vibObj = vibrationState.get("vibration");
        return vibObj instanceof Boolean ? (Boolean) vibObj : null;
    }

    private String extractVibrationAction(Map<String, Object> vibrationState) {
        if (vibrationState == null || vibrationState.isEmpty()) return null;
        Object actionObj = vibrationState.get("action");
        return actionObj instanceof String ? (String) actionObj : null;
    }

    private Integer extractAngle(Map<String, Object> vibrationState, String angleKey) {
        if (vibrationState == null || vibrationState.isEmpty()) return null;
        Object angleObj = vibrationState.get(angleKey);
        return angleObj instanceof Number ? ((Number) angleObj).intValue() : null;
    }

    public synchronized GarageDoorStatus getCurrentStatus() {
        return buildStatus(
                lastPowerReading,
                lastTiltSensorState,
                lastVibrationDetected,
                lastVibrationAction,
                LocalDateTime.now()
        );
    }

    public synchronized void reset() {
        log.info("Resetting garage door state tracker");
        // Preserve stuck context so the next motor trigger knows to reverse direction
        stateBeforeReset = currentState;
        tiltAtReset = lastTiltSensorState;
        currentState = GarageDoorState.UNKNOWN;
        lastStateChange = LocalDateTime.now();
        operationStartTime = null;
        recentReadings.clear();
        powerHistory.clear();
        lastTiltSensorState = null;
        lastVibrationDetected = null;
        lastVibrationAction = null;
        lastKnownProgress = null;
        lastMovementDirection = null;
        motorStartTime = null;
        tiltStateAtMotorStart = null;
        intendedDirection = null;
        movementConfirmed = false;
        peakPowerSinceMotorStart = null;
        peakPowerTime = null;
        lastHighPowerTime = null;
        powerDropDetected = false;
        lastAngleX = null;
        lastAngleY = null;
        lastAngleZ = null;
        angleXAtMotorStart = null;
        angleYAtMotorStart = null;
        angleZAtMotorStart = null;
    }

    private static class PowerReading {
        final double power;
        final LocalDateTime timestamp;

        PowerReading(double power, LocalDateTime timestamp) {
            this.power = power;
            this.timestamp = timestamp;
        }
    }
}