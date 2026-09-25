package co.com.compira.model.task;

import java.time.OffsetDateTime;

public record CreateTaskCommand(
        String actorEmail,
        String title,
        String description,
        OffsetDateTime dueDate,
        String responsibleEmail) {
}
