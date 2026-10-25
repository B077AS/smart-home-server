package smart.home.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "light_settings")
public class LightSetting {

    @Id
    private String deviceName;

    // Zigbee2MQTT "transition" in seconds, added to every command sent to this light.
    @Column(nullable = false)
    private double transitionSeconds;
}
