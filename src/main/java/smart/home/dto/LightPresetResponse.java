package smart.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LightPresetResponse {
    private UUID id;
    private String name;
    private String sourceDevice;
    private boolean isDefault;
    private LocalDateTime createdAt;
    private Map<String, Object> settings;
}
