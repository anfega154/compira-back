package co.com.compira.model.task;

import java.util.UUID;

public record ReassignTaskCommand(
        String actorEmail,
        UUID taskId,
        String newResponsibleEmail) {
}
