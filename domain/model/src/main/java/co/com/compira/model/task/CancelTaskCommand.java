package co.com.compira.model.task;

import java.util.UUID;

public record CancelTaskCommand(
        String actorEmail,
        UUID taskId,
        String reason) {
}
