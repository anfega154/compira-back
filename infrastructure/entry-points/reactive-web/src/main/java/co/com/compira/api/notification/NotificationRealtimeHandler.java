package co.com.compira.api.notification;
import co.com.compira.api.task.TaskErrorHandler;
import co.com.compira.usecase.notifications.NotificationRealtimeUseCase;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
@Component
public class NotificationRealtimeHandler {
    public static final String PATH = NotificationHandler.BASE + "/realtime-ticket";
    private final NotificationRealtimeUseCase realtime;
    private final TaskErrorHandler errors;
    public NotificationRealtimeHandler(NotificationRealtimeUseCase realtime, TaskErrorHandler errors) { this.realtime = realtime; this.errors = errors; }
    public Mono<ServerResponse> ticket(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class)
            .flatMap(actor -> realtime.ticket(actor.getName(), actor.getToken().getExpiresAt()))
            .flatMap(ticket -> ServerResponse.ok().header("Cache-Control", "no-store").bodyValue(ticket))
            .onErrorResume(IllegalStateException.class, error -> ServerResponse.status(503).header("Cache-Control", "no-store").build())
            .onErrorResume(errors::handle);
    }
}
