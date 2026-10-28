package co.com.compira.api.task.dto;

import co.com.compira.api.task.TaskValidationMessage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CreateTaskRequest(
        @NotBlank(message = TaskValidationMessage.TITLE_REQUIRED)
        @Size(max = 150, message = TaskValidationMessage.TITLE_LENGTH)
        String title,
        @NotBlank(message = TaskValidationMessage.DESCRIPTION_REQUIRED)
        @Size(max = 2000, message = TaskValidationMessage.DESCRIPTION_LENGTH)
        String description,
        @NotNull(message = TaskValidationMessage.DUE_DATE_REQUIRED)
        OffsetDateTime dueDate,
        @Email(message = TaskValidationMessage.RESPONSIBLE_EMAIL_INVALID)
        String responsibleEmail, @NotNull(message = TaskValidationMessage.TEAM_REQUIRED) java.util.UUID teamId) {
}
