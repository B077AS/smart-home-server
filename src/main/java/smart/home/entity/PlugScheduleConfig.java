package smart.home.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "plug_schedule_config")
public class PlugScheduleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String scheduleType; // "DEFAULT_WEEKDAY", "DEFAULT_WEEKEND", "EXCEPTION"

    @Column
    private LocalDate exceptionDate; // For specific date exceptions

    @Column(nullable = false)
    private LocalTime onTime;

    @Column(nullable = false)
    private LocalTime offTime;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column
    private String description;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
        updatedAt = java.time.LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }
}