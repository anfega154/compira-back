package co.com.compira.model.task;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskHistoryEntry(
        UUID id,
        UUID taskId,
        TaskHistoryEvent event,
        UUID actorUserId,
        String previousValue,
        String newValue,
        String detail,
        OffsetDateTime createdAt) {
}
