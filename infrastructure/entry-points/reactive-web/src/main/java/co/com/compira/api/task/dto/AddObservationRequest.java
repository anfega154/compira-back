package co.com.compira.api.task.dto;

import co.com.compira.api.task.TaskValidationMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddObservationRequest(
        @NotBlank(message = TaskValidationMessage.OBSERVATION_REQUIRED)
        @Size(max = 2000, message = TaskValidationMessage.OBSERVATION_LENGTH)
        String content) {
}
