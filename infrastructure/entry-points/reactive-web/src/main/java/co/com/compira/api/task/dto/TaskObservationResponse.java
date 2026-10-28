package co.com.compira.api.task.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskObservationResponse(
        UUID id,
        UUID taskId,
        UUID authorUserId,
        String content,
        OffsetDateTime createdAt) {
}
