package co.com.compira.usecase.task;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.TaskUser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TaskTestData {
    public static final UUID TASK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID COORDINATOR_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID COLLABORATOR_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID OTHER_COLLABORATOR_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    public static final String COORDINATOR_EMAIL = "coordinator@compira.co";
    public static final String COLLABORATOR_EMAIL = "collaborator@compira.co";
    public static final String OTHER_COLLABORATOR_EMAIL = "other.collaborator@compira.co";

    private TaskTestData() {
    }

    public static TaskUser coordinator() {
        return new TaskUser(COORDINATOR_ID, COORDINATOR_EMAIL, "Coord", "Uno", List.of(RoleCode.COORDINATOR.name()));
    }

    public static TaskUser administrator() {
        return new TaskUser(COORDINATOR_ID, COORDINATOR_EMAIL, "Admin", "Uno", List.of(RoleCode.ADMINISTRATOR.name()));
    }

    public static TaskUser collaborator() {
        return new TaskUser(COLLABORATOR_ID, COLLABORATOR_EMAIL, "Colab", "Uno", List.of(RoleCode.COLLABORATOR.name()));
    }

    public static TaskUser otherCollaborator() {
        return new TaskUser(OTHER_COLLABORATOR_ID, OTHER_COLLABORATOR_EMAIL, "Colab", "Dos", List.of(RoleCode.COLLABORATOR.name()));
    }

    public static OffsetDateTime now() {
        return OffsetDateTime.parse("2026-09-24T10:00:00Z");
    }

    public static Task task(TaskStatus status) {
        return new Task(
                TASK_ID,
                "Preparar informe",
                "Detalle del informe",
                now().plusDays(2),
                status,
                COLLABORATOR_ID,
                COORDINATOR_ID,
                now(),
                now());
    }

    public static Task overdueTask(TaskStatus status) {
        return new Task(
                TASK_ID,
                "Preparar informe",
                "Detalle del informe",
                now().minusDays(1),
                status,
                COLLABORATOR_ID,
                COORDINATOR_ID,
                now(),
                now());
    }
}
