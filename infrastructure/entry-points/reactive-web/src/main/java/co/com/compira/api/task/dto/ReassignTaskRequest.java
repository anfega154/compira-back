package co.com.compira.api.task.dto;

import co.com.compira.api.task.TaskValidationMessage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ReassignTaskRequest(
        @NotBlank(message = TaskValidationMessage.RESPONSIBLE_EMAIL_REQUIRED)
        @Email(message = TaskValidationMessage.RESPONSIBLE_EMAIL_INVALID)
        String newResponsibleEmail) {
}
