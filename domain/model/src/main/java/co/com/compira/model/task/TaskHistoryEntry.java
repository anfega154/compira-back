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
        OffsetDateTime createdAt,
        String actorName,
        String actorEmail) {
    public TaskHistoryEntry(UUID id, UUID taskId, TaskHistoryEvent event, UUID actorUserId,
                            String previousValue, String newValue, String detail, OffsetDateTime createdAt) {
        this(id, taskId, event, actorUserId, previousValue, newValue, detail, createdAt, null, null);
    }
}
