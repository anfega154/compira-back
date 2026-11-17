package co.com.compira.model.task;

import java.util.UUID;

public record ApproveTaskCommand(
        String actorEmail,
        UUID taskId) {
}
