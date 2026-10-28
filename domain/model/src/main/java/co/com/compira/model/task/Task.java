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
        OffsetDateTime updatedAt,
        UUID teamId,
        String teamName) {

    /**
     * Constructor de compatibilidad para tareas sin información de equipo
     * (p. ej. recién creadas antes de vincularse a un equipo, o estados derivados en memoria).
     */
    public Task(
            UUID id,
            String title,
            String description,
            OffsetDateTime dueDate,
            TaskStatus status,
            UUID responsibleUserId,
            UUID createdByUserId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
        this(id, title, description, dueDate, status, responsibleUserId, createdByUserId, createdAt, updatedAt, null, null);
    }

    public boolean isOverdue(OffsetDateTime reference) {
        return dueDate != null
                && !dueDate.isAfter(reference)
                && (status == TaskStatus.PENDING || status == TaskStatus.IN_PROGRESS || status == TaskStatus.DELAYED);
    }
}
