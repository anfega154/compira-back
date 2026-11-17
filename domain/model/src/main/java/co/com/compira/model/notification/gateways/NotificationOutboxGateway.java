package co.com.compira.model.notification.gateways;
import co.com.compira.model.notification.NotificationDispatch;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
public interface NotificationOutboxGateway {
    Flux<NotificationDispatch> lockPending();
    Mono<Void> delivered(long id);
    Mono<Void> retryLater(long id);
}
