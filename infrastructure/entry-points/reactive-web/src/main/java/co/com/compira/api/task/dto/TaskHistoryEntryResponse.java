package co.com.compira.api.task.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskHistoryEntryResponse(
        UUID id,
        UUID taskId,
        String event,
        UUID actorUserId,
        String previousValue,
        String newValue,
        String detail,
        OffsetDateTime createdAt) {
}
