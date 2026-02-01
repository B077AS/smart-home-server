package smart.home.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "plug_logs")
public class PlugLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private String action; // "ON" or "OFF"

    @Column(nullable = false)
    private boolean success;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}