package co.com.compira.api.task;

import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskHistoryEvent;
import co.com.compira.model.task.TaskObservation;
import co.com.compira.model.task.TaskStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class TaskApiTestData {
    public static final UUID TASK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID RESPONSIBLE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID CREATOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final String ACTOR_EMAIL = "coordinator@compira.co";

    private TaskApiTestData() {
    }

    public static OffsetDateTime referenceDate() {
        return OffsetDateTime.parse("2026-09-24T10:00:00Z");
    }

    public static Task task(TaskStatus status) {
        return new Task(
                TASK_ID,
                "Preparar informe",
                "Detalle",
                referenceDate().plusDays(2),
                status,
                RESPONSIBLE_ID,
                CREATOR_ID,
                referenceDate(),
                referenceDate());
    }

    public static TaskObservation observation() {
        return new TaskObservation(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                TASK_ID,
                RESPONSIBLE_ID,
                "Avance del 50%",
                referenceDate());
    }

    public static TaskHistoryEntry historyEntry() {
        return new TaskHistoryEntry(
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                TASK_ID,
                TaskHistoryEvent.STATUS_CHANGED,
                CREATOR_ID,
                TaskStatus.PENDING.name(),
                TaskStatus.IN_PROGRESS.name(),
                null,
                referenceDate());
    }
}
