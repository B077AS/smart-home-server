package smart.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import smart.home.entity.GarageDoorState;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GarageDoorStatus {
    private GarageDoorState state;
    private Integer estimatedProgress;
    private Double currentPower;
    private Boolean tiltSensorOpen;
    private Boolean vibrationDetected;
    private String vibrationAction;
    private LocalDateTime lastUpdate;
    private LocalDateTime operationStartTime;
    private Long operationDurationMs;
    private String message;
    private Boolean warningStuck;
}