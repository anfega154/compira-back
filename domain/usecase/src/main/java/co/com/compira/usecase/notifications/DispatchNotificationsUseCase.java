package co.com.compira.usecase.notifications;
import co.com.compira.model.notification.gateways.NotificationOutboxGateway;
import co.com.compira.model.notification.gateways.NotificationRealtimeGateway;
import reactor.core.publisher.Mono;
public class DispatchNotificationsUseCase {
    private final NotificationOutboxGateway outbox;
    private final NotificationRealtimeGateway realtime;
    public DispatchNotificationsUseCase(NotificationOutboxGateway outbox, NotificationRealtimeGateway realtime) {
        this.outbox = outbox; this.realtime = realtime;
    }
    public Mono<Void> execute() {
        if (!realtime.enabled()) return Mono.empty();
        return outbox.lockPending().concatMap(event -> realtime.publish(event)
            .then(Mono.defer(() -> outbox.delivered(event.id())))
            .onErrorResume(error -> outbox.retryLater(event.id()))).then();
    }
}
