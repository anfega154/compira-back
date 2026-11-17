package co.com.compira.usecase.notifications;
import co.com.compira.model.notification.*;
import co.com.compira.model.notification.gateways.*;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.usecase.task.TaskTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class RealtimeUseCaseTest {
    final NotificationRealtimeGateway realtime = mock(NotificationRealtimeGateway.class);
    final NotificationOutboxGateway outbox = mock(NotificationOutboxGateway.class);
    final TaskUserDirectoryGateway users = mock(TaskUserDirectoryGateway.class);
    @Test void bindsTicketToAuthorizedActorNotCallerSuppliedRecipient() {
        when(users.findByEmail(TaskTestData.COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.collaborator()));
        var expiry = Instant.now().plusSeconds(3600);
        when(realtime.ticket(TaskTestData.COLLABORATOR_ID, expiry)).thenReturn(Mono.just(new RealtimeTicket("wss://example.test?ticket=one", expiry)));
        StepVerifier.create(new NotificationRealtimeUseCase(users, realtime).ticket(TaskTestData.COLLABORATOR_EMAIL, expiry)).expectNextCount(1).verifyComplete();
        verify(realtime).ticket(TaskTestData.COLLABORATOR_ID, expiry);
    }
    @Test void missingActorCannotMintTicket() {
        when(users.findByEmail(anyString())).thenReturn(Mono.empty());
        StepVerifier.create(new NotificationRealtimeUseCase(users, realtime).ticket("missing@example.test", Instant.now())).expectError().verify();
        verifyNoInteractions(realtime);
    }
    @Test void disabledTransportDoesNotReadOrConsumeOutbox() {
        StepVerifier.create(new DispatchNotificationsUseCase(outbox, realtime).execute()).verifyComplete();
        verifyNoInteractions(outbox);
    }
    @Test void failedPublishIsRetriedAndDoesNotBlockOtherRecipients() {
        var first = new NotificationDispatch(1L, TaskTestData.COLLABORATOR_ID);
        var second = new NotificationDispatch(2L, TaskTestData.COLLABORATOR_ID);
        when(realtime.enabled()).thenReturn(true);
        when(outbox.lockPending()).thenReturn(Flux.just(first, second));
        when(realtime.publish(first)).thenReturn(Mono.error(new IllegalStateException("down")));
        when(realtime.publish(second)).thenReturn(Mono.empty());
        when(outbox.delivered(anyLong())).thenReturn(Mono.empty());
        when(outbox.retryLater(anyLong())).thenReturn(Mono.empty());
        StepVerifier.create(new DispatchNotificationsUseCase(outbox, realtime).execute()).verifyComplete();
        verify(outbox).retryLater(1L); verify(outbox).delivered(2L);
        verify(outbox, never()).delivered(1L);
    }
    @Test void administratorOnlyCannotMintRecipientTicket() {
        when(users.findByEmail(anyString())).thenReturn(Mono.just(TaskTestData.administrator()));
        StepVerifier.create(new NotificationRealtimeUseCase(users, realtime).ticket("admin@example.test", Instant.now()))
            .expectError(co.com.compira.model.common.error.CompiraException.class).verify();
        verifyNoInteractions(realtime);
    }
}
