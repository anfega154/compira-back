package co.com.compira.api.task.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        String description,
        OffsetDateTime dueDate,
        String status,
        UUID responsibleUserId,
        UUID createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
