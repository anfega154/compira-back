package co.com.compira.api.notification;

import java.time.OffsetDateTime;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationResponse(String id, UUID taskId, String taskTitle,
        @Schema(allowableValues = {"ASSIGNED", "REASSIGNED", "DUE_SOON", "OVERDUE", "TASK_COMPLETED", "OBSERVATION_ADDED"}) String type, OffsetDateTime createdAt, OffsetDateTime readAt) {
}
