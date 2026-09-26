package co.com.compira.model.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskNotification(long id, UUID taskId, String taskTitle, NotificationType type,
                               OffsetDateTime createdAt) {
}
