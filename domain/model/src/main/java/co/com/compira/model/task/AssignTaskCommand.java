package co.com.compira.model.task;

import java.util.UUID;

public record AssignTaskCommand(
        String actorEmail,
        UUID taskId,
        String responsibleEmail) {
}
