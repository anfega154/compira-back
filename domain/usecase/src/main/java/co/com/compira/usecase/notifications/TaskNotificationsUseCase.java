package co.com.compira.usecase.notifications;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.notification.NotificationType;
import co.com.compira.model.notification.TaskNotification;
import co.com.compira.model.notification.gateways.NotificationRepositoryGateway;
import co.com.compira.model.organization.gateways.OrganizationSettingsGateway;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Flux;
import co.com.compira.model.team.gateways.TeamRepositoryGateway;
import reactor.core.publisher.Mono;
import java.util.UUID;

public class TaskNotificationsUseCase {
    private static final String FORBIDDEN_CODE = "NOTIFICATION_001";
    private static final String FORBIDDEN = "Acceso no permitido";
    private static final String TASK_WITHOUT_TEAM_CODE = "TEAM_001";
    private static final String TASK_WITHOUT_TEAM = "La tarea no tiene equipo";
    public static final int PAGE_SIZE = 50;
    private final NotificationRepositoryGateway notifications;
    private final OrganizationSettingsGateway settings;
    private final TaskAuthorization authorization;
    private final TeamRepositoryGateway teams;

    public TaskNotificationsUseCase(NotificationRepositoryGateway notifications, OrganizationSettingsGateway settings,
                                    TaskUserDirectoryGateway users, TeamRepositoryGateway teams) {
        this.teams = teams;
        this.notifications = notifications;
        this.settings = settings;
        this.authorization = new TaskAuthorization(users);
    }

    public Mono<Void> assignment(Task task, UUID recipientId, NotificationType type, UUID eventId) {
        return settings.get().flatMap(configuration -> notifications.save(task, recipientId, type,
                eventId.toString(), configuration.notificationsEnabled()));
    }

    public Mono<Void> coordinator(Task task, NotificationType type, UUID eventId) {
        return teams.findByTaskId(task.id())
                .switchIfEmpty(Mono.error(new CompiraException(TASK_WITHOUT_TEAM_CODE, TASK_WITHOUT_TEAM, ErrorCategory.CONFLICT)))
                .flatMap(team -> assignment(task, team.coordinatorUserId(), type, eventId));
    }

    public Flux<TaskNotification> list(String email, long beforeId) {
        return authorization.requireActor(email)
                .filter(user -> user.hasRole(RoleCode.COLLABORATOR.name()) || user.hasRole(RoleCode.COORDINATOR.name()))
                .switchIfEmpty(Mono.error(new CompiraException(FORBIDDEN_CODE, FORBIDDEN, ErrorCategory.FORBIDDEN)))
                .flatMapMany(user -> settings.get().flatMapMany(configuration -> configuration.notificationsEnabled()
                        ? notifications.findByRecipient(user.id(), beforeId, PAGE_SIZE) : Flux.empty()));
    }

    public Mono<Void> markRead(String email, long notificationId) {
        return requireRecipient(email)
                .flatMap(user -> notifications.markAsRead(user.id(), notificationId))
                .then();
    }

    public Mono<Void> markAllRead(String email) {
        return requireRecipient(email)
                .flatMap(user -> notifications.markAllAsRead(user.id()))
                .then();
    }

    private Mono<co.com.compira.model.task.TaskUser> requireRecipient(String email) {
        return authorization.requireActor(email)
                .filter(user -> user.hasRole(RoleCode.COLLABORATOR.name()) || user.hasRole(RoleCode.COORDINATOR.name()))
                .switchIfEmpty(Mono.error(new CompiraException(FORBIDDEN_CODE, FORBIDDEN, ErrorCategory.FORBIDDEN)));
    }
}
