package co.com.compira.api.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(String id, UUID taskId, String taskTitle, String type, OffsetDateTime createdAt, OffsetDateTime readAt) {
}
