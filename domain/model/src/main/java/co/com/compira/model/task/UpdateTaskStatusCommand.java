package co.com.compira.model.task;

import java.util.UUID;

public record UpdateTaskStatusCommand(
        String actorEmail,
        UUID taskId,
        TaskStatus targetStatus) {
}
