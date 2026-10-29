package co.com.compira.model.task;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskObservation(
        UUID id,
        UUID taskId,
        UUID authorUserId,
        String content,
        OffsetDateTime createdAt,
        String authorName,
        String authorEmail) {
    public TaskObservation(UUID id, UUID taskId, UUID authorUserId, String content, OffsetDateTime createdAt) {
        this(id, taskId, authorUserId, content, createdAt, null, null);
    }
}
