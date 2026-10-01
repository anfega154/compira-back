package co.com.compira.model.notification.gateways;

import co.com.compira.model.notification.NotificationType;
import co.com.compira.model.notification.TaskNotification;
import co.com.compira.model.task.Task;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface NotificationRepositoryGateway {
    Mono<Void> save(Task task, UUID recipientId, NotificationType type, String eventKey, boolean deliverable);
    Flux<TaskNotification> findByRecipient(UUID recipientId, long beforeId, int limit);
    Mono<Long> markAsRead(UUID recipientId, long notificationId);
    Mono<Long> markAllAsRead(UUID recipientId);
    Flux<Task> lockAlertCandidates(OffsetDateTime now, OffsetDateTime upcoming, int limit);
}
