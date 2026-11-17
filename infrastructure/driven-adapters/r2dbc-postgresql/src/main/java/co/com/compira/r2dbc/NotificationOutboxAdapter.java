package co.com.compira.r2dbc;
import co.com.compira.model.notification.NotificationDispatch;
import co.com.compira.model.notification.gateways.NotificationOutboxGateway;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
@Repository
public class NotificationOutboxAdapter implements NotificationOutboxGateway {
    private final DatabaseClient database;
    public NotificationOutboxAdapter(DatabaseClient database) { this.database = database; }
    public Flux<NotificationDispatch> lockPending() {
        return database.sql("SELECT notification_id, recipient_id FROM notification_outbox WHERE delivered_at IS NULL AND available_at <= CURRENT_TIMESTAMP ORDER BY available_at, notification_id LIMIT 20 FOR UPDATE SKIP LOCKED")
            .map(row -> new NotificationDispatch(row.get("notification_id", Long.class), row.get("recipient_id", UUID.class))).all();
    }
    public Mono<Void> delivered(long id) {
        return database.sql("UPDATE notification_outbox SET delivered_at = CURRENT_TIMESTAMP WHERE notification_id = :id")
            .bind("id", id).then();
    }
    public Mono<Void> retryLater(long id) {
        return database.sql("UPDATE notification_outbox SET attempts = attempts + 1, available_at = CURRENT_TIMESTAMP + LEAST(300, (attempts + 1) * 5) * INTERVAL '1 second' WHERE notification_id = :id")
            .bind("id", id).then();
    }
}
