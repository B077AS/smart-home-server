package smart.home.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "light_presets")
public class LightPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String sourceDevice;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String settingsJson;

    @Column(nullable = false)
    private boolean isDefault;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
