package smart.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqttStatusResponse {
    private Boolean connected;
    private String state;
    private String clientId;
    private String message;
}