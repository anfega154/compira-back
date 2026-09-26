package co.com.compira.model.task;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Task(
        UUID id,
        String title,
        String description,
        OffsetDateTime dueDate,
        TaskStatus status,
        UUID responsibleUserId,
        UUID createdByUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public boolean isOverdue(OffsetDateTime reference) {
        return dueDate != null
                && !dueDate.isAfter(reference)
                && (status == TaskStatus.PENDING || status == TaskStatus.IN_PROGRESS || status == TaskStatus.DELAYED);
    }
}
