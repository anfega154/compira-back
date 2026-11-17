package co.com.compira.usecase.notifications;

import co.com.compira.model.notification.NotificationType;
import co.com.compira.model.notification.TaskNotification;
import co.com.compira.model.organization.OrganizationSettings;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.team.Team;
import co.com.compira.usecase.task.TaskTestData;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class NotificationTestData {
    public static final UUID CURRENT_COORDINATOR = UUID.fromString("66666666-6666-6666-6666-666666666666");
    public static final UUID EVENT_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    public static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-26T12:00:00Z");

    private NotificationTestData() { }

    public static OrganizationSettings settings(boolean enabled) {
        return new OrganizationSettings("America/Bogota", enabled);
    }

    public static Team team() {
        return new Team(TaskTestData.TEAM_ID, "Operaciones", CURRENT_COORDINATOR, "current@compira.co");
    }

    public static Task task(TaskStatus status, OffsetDateTime dueDate) {
        Task original = TaskTestData.task(status);
        return new Task(original.id(), original.title(), original.description(), dueDate, status,
                original.responsibleUserId(), original.createdByUserId(), original.createdAt(), original.updatedAt());
    }

    public static TaskNotification notification() {
        return new TaskNotification(1L, TaskTestData.TASK_ID, "Preparar informe", NotificationType.ASSIGNED, NOW, null);
    }
}
