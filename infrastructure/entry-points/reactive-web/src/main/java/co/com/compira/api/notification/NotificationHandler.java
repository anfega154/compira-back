package co.com.compira.api.notification;

import co.com.compira.api.task.TaskErrorHandler;
import co.com.compira.usecase.notifications.TaskNotificationsUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class NotificationHandler {
    private static final String BEFORE = "before";
    private static final String INVALID_CURSOR = "Cursor inválido";
    private static final String NO_STORE = "no-store";
    private static final String EVENT_NAME = "notifications";
    private static final String BUFFERING_HEADER = "X-Accel-Buffering";
    private static final String BUFFERING_DISABLED = "no";
    public static final String BASE = "/api/v1/notifications";
    public static final String STREAM = BASE + "/stream";
    private final TaskNotificationsUseCase notifications;
    private final NotificationResponseMapper mapper;
    private final TaskErrorHandler errors;

    public NotificationHandler(TaskNotificationsUseCase notifications, NotificationResponseMapper mapper, TaskErrorHandler errors) {
        this.notifications = notifications;
        this.mapper = mapper;
        this.errors = errors;
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        return request.principal().flatMap(principal -> Mono.fromCallable(() ->
                        request.queryParam(BEFORE).map(Long::parseLong).orElse(Long.MAX_VALUE))
                .filter(cursor -> cursor > 0)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(INVALID_CURSOR)))
                .flatMap(cursor -> notifications.list(principal.getName(), cursor).map(mapper::toResponse).collectList())
                .flatMap(page -> ServerResponse.ok().header(HttpHeaders.CACHE_CONTROL, NO_STORE).bodyValue(page)))
                .onErrorResume(errors::handle);
    }

    public Mono<ServerResponse> stream(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class).flatMap(authentication -> {
            Duration remaining = Duration.between(Instant.now(), authentication.getToken().getExpiresAt());
            if (remaining.isNegative() || remaining.isZero()) {
                return ServerResponse.status(401).build();
            }
            Flux<ServerSentEvent<List<NotificationResponse>>> snapshots = Flux.interval(Duration.ZERO, Duration.ofSeconds(1))
                    .onBackpressureLatest()
                    .concatMap(tick -> notifications.list(authentication.getName(), Long.MAX_VALUE)
                            .map(mapper::toResponse).collectList(), 1)
                    .map(page -> ServerSentEvent.<List<NotificationResponse>>builder(page).event(EVENT_NAME).build())
                    .take(remaining);
            return ServerResponse.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                    .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
                    .header(BUFFERING_HEADER, BUFFERING_DISABLED)
                    .body(snapshots, ServerSentEvent.class);
        });
    }
}
