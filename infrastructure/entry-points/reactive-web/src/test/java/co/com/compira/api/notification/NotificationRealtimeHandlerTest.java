package co.com.compira.api.notification;
import co.com.compira.api.config.SecurityTestData;
import co.com.compira.api.router.NotificationRealtimeRouter;
import co.com.compira.api.task.TaskErrorHandler;
import co.com.compira.model.notification.RealtimeTicket;
import co.com.compira.usecase.notifications.NotificationRealtimeUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class NotificationRealtimeHandlerTest {
    @Test void issuesNoStoreTicketUsingOnlyValidatedIdentityAndExpiration() {
        var useCase = mock(NotificationRealtimeUseCase.class);
        var token = SecurityTestData.token();
        when(useCase.ticket(eq(SecurityTestData.EMAIL), eq(token.getExpiresAt())))
            .thenReturn(Mono.just(new RealtimeTicket("wss://example.test/qa?ticket=one", token.getExpiresAt())));
        var principal = new JwtAuthenticationToken(token, List.of(), SecurityTestData.EMAIL);
        var client = WebTestClient.bindToRouterFunction(new NotificationRealtimeRouter().notificationRealtimeRouter(new NotificationRealtimeHandler(useCase, new TaskErrorHandler())))
            .webFilter((exchange, chain) -> chain.filter(exchange.mutate().principal(Mono.just(principal)).build())).build();
        client.post().uri(NotificationRealtimeHandler.PATH).bodyValue("{\"userId\":\"other\"}").exchange()
            .expectStatus().isOk().expectHeader().valueEquals("Cache-Control", "no-store").expectBody().jsonPath("$.url").isEqualTo("wss://example.test/qa?ticket=one");
        verify(useCase).ticket(SecurityTestData.EMAIL, token.getExpiresAt());
    }
    @Test void disabledTransportIs503NotAnEmptySuccessfulTicket() {
        var useCase = mock(NotificationRealtimeUseCase.class);
        when(useCase.ticket(anyString(), any())).thenReturn(Mono.error(new IllegalStateException("disabled")));
        var principal = new JwtAuthenticationToken(SecurityTestData.token(), List.of(), SecurityTestData.EMAIL);
        var client = WebTestClient.bindToRouterFunction(new NotificationRealtimeRouter().notificationRealtimeRouter(new NotificationRealtimeHandler(useCase, new TaskErrorHandler())))
            .webFilter((exchange, chain) -> chain.filter(exchange.mutate().principal(Mono.just(principal)).build())).build();
        client.post().uri(NotificationRealtimeHandler.PATH).exchange().expectStatus().isEqualTo(503).expectHeader().valueEquals("Cache-Control", "no-store");
    }
}
