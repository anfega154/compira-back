package co.com.compira.model.task;

import java.util.UUID;

public record AddTaskObservationCommand(
        String actorEmail,
        UUID taskId,
        String content) {
}
