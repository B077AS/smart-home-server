package smart.home.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuccessResponse {
    private String message;
    private Integer status;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}