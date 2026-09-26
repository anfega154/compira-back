package co.com.compira.usecase.taskalerts;

import co.com.compira.model.notification.NotificationType;
import co.com.compira.model.notification.gateways.NotificationRepositoryGateway;
import co.com.compira.model.organization.OrganizationSettings;
import co.com.compira.model.organization.gateways.OrganizationSettingsGateway;
import co.com.compira.model.task.Task;
import co.com.compira.model.team.gateways.TeamRepositoryGateway;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskHistoryEvent;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.gateways.TaskClockGateway;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import reactor.core.publisher.Mono;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public class EvaluateTaskAlertsUseCase {
    private static final int BATCH_SIZE = 100;
    private static final int NOTICE_HOURS = 24;
    private final NotificationRepositoryGateway notifications;
    private final OrganizationSettingsGateway settings;
    private final TaskRepositoryGateway tasks;
    private final TaskClockGateway clock;
    private final TeamRepositoryGateway teams;

    public EvaluateTaskAlertsUseCase(NotificationRepositoryGateway notifications, OrganizationSettingsGateway settings,
                                     TaskRepositoryGateway tasks, TaskClockGateway clock, TeamRepositoryGateway teams) {
        this.notifications = notifications;
        this.settings = settings;
        this.tasks = tasks;
        this.clock = clock;
        this.teams = teams;
    }

    public Mono<Void> evaluate() {
        return Mono.defer(() -> settings.get().filter(configuration -> configuration.timeZone() != null)
                .flatMap(configuration -> {
                    OffsetDateTime now = clock.now().atZoneSameInstant(ZoneId.of(configuration.timeZone())).toOffsetDateTime();
                    return notifications.lockAlertCandidates(now, now.plusHours(NOTICE_HOURS), BATCH_SIZE)
                            .concatMap(task -> evaluateTask(task, now, configuration)).then();
                }));
    }

    private Mono<Void> evaluateTask(Task task, OffsetDateTime now, OrganizationSettings configuration) {
        if (task.dueDate() == null || !task.status().isActive()) {
            return Mono.empty();
        }
        boolean overdue = !task.dueDate().isAfter(now);
        if (!overdue && task.dueDate().isAfter(now.plusHours(NOTICE_HOURS))) {
            return Mono.empty();
        }
        NotificationType type = overdue ? NotificationType.OVERDUE : NotificationType.DUE_SOON;
        if (!overdue && task.responsibleUserId() == null) {
            return Mono.empty();
        }
        String eventKey = task.id() + ":" + task.dueDate().toEpochSecond() + ":" + type.name();
        Mono<Void> transition = overdue && task.status() != TaskStatus.DELAYED
                ? tasks.updateStatus(task.id(), TaskStatus.DELAYED.name())
                    .flatMap(updated -> tasks.appendHistory(new TaskHistoryEntry(null, task.id(),
                            TaskHistoryEvent.STATUS_CHANGED, null, task.status().name(), TaskStatus.DELAYED.name(), null, now)))
                    .then()
                : Mono.empty();
        return teams.findByTaskId(task.id()).flatMap(team -> transition.then(Mono.defer(() -> notifications.save(task,
                overdue ? team.coordinatorUserId() : task.responsibleUserId(), type, eventKey, configuration.notificationsEnabled()))));
    }
}
