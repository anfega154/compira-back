package co.com.compira.usecase.notifications;
import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.notification.RealtimeTicket;
import co.com.compira.model.notification.gateways.NotificationRealtimeGateway;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Mono;
import java.time.Instant;
public class NotificationRealtimeUseCase {
    private final TaskAuthorization authorization;
    private final NotificationRealtimeGateway realtime;
    public NotificationRealtimeUseCase(TaskUserDirectoryGateway users, NotificationRealtimeGateway realtime) {
        this.authorization = new TaskAuthorization(users); this.realtime = realtime;
    }
    public Mono<RealtimeTicket> ticket(String email, Instant expiresAt) {
        return authorization.requireActor(email)
            .filter(user -> user.hasRole(RoleCode.COORDINATOR.name()) || user.hasRole(RoleCode.COLLABORATOR.name()))
            .switchIfEmpty(Mono.error(new CompiraException("NOTIFICATION_001", "Acceso no permitido", ErrorCategory.FORBIDDEN)))
            .flatMap(user -> realtime.ticket(user.id(), expiresAt));
    }
}
