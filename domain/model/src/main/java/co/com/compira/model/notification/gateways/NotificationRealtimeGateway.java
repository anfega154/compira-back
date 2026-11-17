package co.com.compira.model.notification.gateways;
import co.com.compira.model.notification.RealtimeTicket;
import co.com.compira.model.notification.NotificationDispatch;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.UUID;
public interface NotificationRealtimeGateway {
    boolean enabled();
    Mono<RealtimeTicket> ticket(UUID userId, Instant authenticationExpiresAt);
    Mono<Void> publish(NotificationDispatch dispatch);
}
