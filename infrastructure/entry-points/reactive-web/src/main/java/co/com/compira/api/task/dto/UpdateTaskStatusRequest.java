package co.com.compira.api.task.dto;

import co.com.compira.api.task.TaskValidationMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateTaskStatusRequest(
        @NotBlank(message = TaskValidationMessage.STATUS_REQUIRED)
        @Pattern(regexp = "^(PENDING|IN_PROGRESS|COMPLETED)$", message = TaskValidationMessage.STATUS_INVALID)
        String status) {
}
