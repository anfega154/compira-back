package co.com.compira.api.task.dto;

import jakarta.validation.constraints.Size;

public record CancelTaskRequest(
        @Size(max = 2000)
        String reason) {
}
