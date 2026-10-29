package co.com.compira.realtime;
import co.com.compira.model.notification.NotificationDispatch;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
public class AwsRealtimeSimulationProbe {
    public static void main(String[] args) {
        URI endpoint = URI.create(System.getProperty("realtime.simulation.url"));
        if (!endpoint.getHost().equals("127.0.0.1")) throw new IllegalArgumentException("Probe is restricted to local fixtures");
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("fixture-access", "fixture-secret"));
        var dynamo = DynamoDbAsyncClient.builder().endpointOverride(endpoint).region(Region.US_EAST_1).credentialsProvider(credentials).build();
        var sqs = SqsAsyncClient.builder().endpointOverride(endpoint).region(Region.US_EAST_1).credentialsProvider(credentials).build();
        var user = UUID.fromString("11111111-1111-1111-1111-111111111111");
        try (var adapter = new AwsRealtimeAdapter(true, "wss://fixture.test/qa", "tickets", "https://fixture.test/queue", dynamo, sqs)) {
            var ticket = adapter.ticket(user, Instant.now().plusSeconds(3600)).block();
            if (ticket == null || !ticket.url().contains("?ticket=")) throw new AssertionError("Missing ticket");
            adapter.publish(new NotificationDispatch(12L, user)).block();
            boolean failed = false;
            try { new AwsRealtimeAdapter(true, "wss://fixture.test/qa", "unavailable", "https://fixture.test/queue", dynamo, sqs).ticket(user, Instant.now().plusSeconds(60)).block(); }
            catch (RuntimeException expected) { failed = true; }
            if (!failed) throw new AssertionError("Unavailable ticket store must fail");
            failed = false;
            try { new AwsRealtimeAdapter(true, "wss://fixture.test/qa", "tickets", "https://fixture.test/unavailable", dynamo, sqs).publish(new NotificationDispatch(13L, user)).block(); }
            catch (RuntimeException expected) { failed = true; }
            if (!failed) throw new AssertionError("Unavailable queue must fail so outbox can retry");
            System.out.println("PASS realtime SDK HTTP: hashed ticket, queue envelope, table failure, queue failure; no AWS credentials used");
        }
    }
}
