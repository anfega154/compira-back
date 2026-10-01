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
import reactor.core.publisher.Mono;
import java.util.UUID;

public class TaskNotificationsUseCase {
    private static final String FORBIDDEN_CODE = "NOTIFICATION_001";
    private static final String FORBIDDEN = "Acceso no permitido";
    public static final int PAGE_SIZE = 50;
    private final NotificationRepositoryGateway notifications;
    private final OrganizationSettingsGateway settings;
    private final TaskAuthorization authorization;

    public TaskNotificationsUseCase(NotificationRepositoryGateway notifications, OrganizationSettingsGateway settings,
                                    TaskUserDirectoryGateway users) {
        this.notifications = notifications;
        this.settings = settings;
        this.authorization = new TaskAuthorization(users);
    }

    public Mono<Void> assignment(Task task, UUID recipientId, NotificationType type, UUID eventId) {
        return settings.get().flatMap(configuration -> notifications.save(task, recipientId, type,
                eventId.toString(), configuration.notificationsEnabled()));
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
