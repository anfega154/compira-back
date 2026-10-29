package co.com.compira.realtime;
import co.com.compira.model.notification.NotificationDispatch;
import co.com.compira.model.notification.RealtimeTicket;
import co.com.compira.model.notification.gateways.NotificationRealtimeGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import java.time.Instant;
import java.time.Duration;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.*;
@Component
public class AwsRealtimeAdapter implements NotificationRealtimeGateway, AutoCloseable {
    private final boolean enabled;
    private final String url, table, queue;
    private final DynamoDbAsyncClient dynamo;
    private final SqsAsyncClient sqs;
    @org.springframework.beans.factory.annotation.Autowired
    public AwsRealtimeAdapter(@Value("${notifications.realtime.enabled:false}") boolean enabled,
            @Value("${notifications.realtime.url:}") String url, @Value("${notifications.realtime.table:}") String table,
            @Value("${notifications.realtime.queue-url:}") String queue, @Value("${adapters.cognito.region:us-east-1}") String region) {
        this(enabled, url, table, queue, DynamoDbAsyncClient.builder().region(Region.of(region)).build(), SqsAsyncClient.builder().region(Region.of(region)).build());
    }
    AwsRealtimeAdapter(boolean enabled, String url, String table, String queue, DynamoDbAsyncClient dynamo, SqsAsyncClient sqs) {
        if (enabled && (!url.startsWith("wss://") || table.isBlank() || queue.isBlank())) throw new IllegalArgumentException("Configuración realtime incompleta");
        this.enabled = enabled; this.url = url; this.table = table; this.queue = queue; this.dynamo = dynamo; this.sqs = sqs;
    }
    public boolean enabled() { return enabled; }
    public Mono<RealtimeTicket> ticket(UUID userId, Instant authenticationExpiresAt) {
        return Mono.defer(() -> {
            if (!enabled) return Mono.error(new IllegalStateException("Canal en tiempo real deshabilitado"));
            Instant now = Instant.now();
            if (authenticationExpiresAt == null || !authenticationExpiresAt.isAfter(now)) return Mono.error(new IllegalArgumentException("Sesión vencida"));
            Instant expires = authenticationExpiresAt.isBefore(now.plusSeconds(7200)) ? authenticationExpiresAt : now.plusSeconds(7200);
            byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            String hash;
            try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ticket.getBytes(java.nio.charset.StandardCharsets.US_ASCII))); }
            catch (java.security.NoSuchAlgorithmException impossible) { return Mono.error(impossible); }
            var item = Map.of("pk", AttributeValue.fromS("TICKET#" + hash), "userIdTicket", AttributeValue.fromS(userId.toString()),
                "expiresAt", AttributeValue.fromN(Long.toString(Math.min(now.plusSeconds(60).getEpochSecond(), expires.getEpochSecond()))),
                "authExpiresAt", AttributeValue.fromN(Long.toString(expires.getEpochSecond())));
            return Mono.fromFuture(() -> dynamo.putItem(PutItemRequest.builder().tableName(table).item(item).conditionExpression("attribute_not_exists(pk)").build()))
                .timeout(Duration.ofSeconds(5)).thenReturn(new RealtimeTicket(url + "?ticket=" + ticket, expires));
        });
    }
    public Mono<Void> publish(NotificationDispatch dispatch) {
        String body = "{\"version\":1,\"recipientId\":\"" + dispatch.recipientId() + "\",\"notificationId\":\"" + dispatch.id() + "\"}";
        return Mono.fromFuture(() -> sqs.sendMessage(SendMessageRequest.builder().queueUrl(queue).messageBody(body).build()))
            .timeout(Duration.ofSeconds(5)).then();
    }
    public void close() { dynamo.close(); sqs.close(); }
}
