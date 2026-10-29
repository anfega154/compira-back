package co.com.compira.realtime;
import co.com.compira.model.notification.NotificationDispatch;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
class AwsRealtimeAdapterTest {
    final DynamoDbAsyncClient dynamo = mock(DynamoDbAsyncClient.class);
    final SqsAsyncClient sqs = mock(SqsAsyncClient.class);
    final UUID user = UUID.randomUUID();
    AwsRealtimeAdapter adapter(boolean enabled) { return new AwsRealtimeAdapter(enabled, "wss://example.test/qa", "tickets", "https://sqs.example.test/queue", dynamo, sqs); }
    @Test void mintsOneUseOpaqueTicketStoresOnlyHashAndCapsAuthExpiry() {
        when(dynamo.putItem(any(PutItemRequest.class))).thenReturn(CompletableFuture.completedFuture(PutItemResponse.builder().build()));
        StepVerifier.create(adapter(true).ticket(user, Instant.now().plusSeconds(9000)))
            .assertNext(ticket -> { assertTrue(ticket.url().matches("wss://example.test/qa\\?ticket=[A-Za-z0-9_-]{43}")); assertTrue(ticket.expiresAt().isBefore(Instant.now().plusSeconds(7201))); }).verifyComplete();
        var saved = ArgumentCaptor.forClass(PutItemRequest.class); verify(dynamo).putItem(saved.capture());
        var item = saved.getValue().item(); assertTrue(item.get("pk").s().matches("TICKET#[a-f0-9]{64}")); assertEquals(user.toString(), item.get("userIdTicket").s());
        assertTrue(Long.parseLong(item.get("expiresAt").n()) <= Instant.now().plusSeconds(60).getEpochSecond());
        assertEquals(4, item.size());
    }
    @Test void neverExtendsActualAuthenticationLifetime() {
        when(dynamo.putItem(any(PutItemRequest.class))).thenReturn(CompletableFuture.completedFuture(PutItemResponse.builder().build()));
        var expiry = Instant.now().plusSeconds(20);
        StepVerifier.create(adapter(true).ticket(user, expiry)).assertNext(t -> assertEquals(expiry, t.expiresAt())).verifyComplete();
    }
    @Test void rejectsDisabledOrExpiredSessionWithoutWriting() {
        StepVerifier.create(adapter(false).ticket(user, Instant.now().plusSeconds(60))).expectError(IllegalStateException.class).verify();
        StepVerifier.create(adapter(true).ticket(user, Instant.now().minusSeconds(1))).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(adapter(true).ticket(user, null)).expectError(IllegalArgumentException.class).verify(); verifyNoInteractions(dynamo);
    }
    @Test void transientDynamoFailureDoesNotReturnUnusableTicket() {
        when(dynamo.putItem(any(PutItemRequest.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("down")));
        StepVerifier.create(adapter(true).ticket(user, Instant.now().plusSeconds(60))).expectError(IllegalStateException.class).verify();
    }
    @Test void publishesOnlyOpaqueRecipientEnvelopeAndPropagatesQueueFailure() {
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));
        StepVerifier.create(adapter(true).publish(new NotificationDispatch(12L, user))).verifyComplete();
        var request = ArgumentCaptor.forClass(SendMessageRequest.class); verify(sqs).sendMessage(request.capture());
        assertEquals("{\"version\":1,\"recipientId\":\"" + user + "\",\"notificationId\":\"12\"}", request.getValue().messageBody());
        when(sqs.sendMessage(any(SendMessageRequest.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("queue down")));
        StepVerifier.create(adapter(true).publish(new NotificationDispatch(13L, user))).expectError(IllegalStateException.class).verify();
    }
    @Test void validatesEnabledConfigurationAndClosesClients() {
        assertThrows(IllegalArgumentException.class, () -> new AwsRealtimeAdapter(true, "http://bad", "", "", dynamo, sqs));
        adapter(false).close(); verify(dynamo).close(); verify(sqs).close();
    }
}
