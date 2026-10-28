package co.com.compira.r2dbc;

import co.com.compira.model.notification.NotificationType;
import co.com.compira.model.notification.TaskNotification;
import co.com.compira.model.notification.gateways.NotificationRepositoryGateway;
import co.com.compira.model.task.Task;
import co.com.compira.r2dbc.mapper.NotificationDataMapper;
import co.com.compira.r2dbc.mapper.TaskDataMapper;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class NotificationRepositoryAdapter implements NotificationRepositoryGateway {
    private static final String INSERT = """
            INSERT INTO task_notifications (task_id, recipient_id, task_title, type, event_key, deliverable)
            VALUES (:taskId, :recipientId, :title, :type, :eventKey, :deliverable)
            ON CONFLICT (event_key, recipient_id) DO NOTHING
            """;
    private static final String SELECT = """
            SELECT id, task_id, task_title, type, created_at, read_at FROM task_notifications
            WHERE recipient_id = :recipientId AND deliverable AND id < :beforeId
            ORDER BY id DESC LIMIT :limit
            """;
    private static final String MARK_READ = """
            UPDATE task_notifications SET read_at = CURRENT_TIMESTAMP
            WHERE id = :notificationId AND recipient_id = :recipientId AND read_at IS NULL
            """;
    private static final String MARK_ALL_READ = """
            UPDATE task_notifications SET read_at = CURRENT_TIMESTAMP
            WHERE recipient_id = :recipientId AND deliverable AND read_at IS NULL
            """;
    private static final String CANDIDATES = """
            SELECT t.* FROM tasks t JOIN task_teams tt ON tt.task_id = t.id JOIN teams tm ON tm.id = tt.team_id
            WHERE t.status IN ('PENDING', 'IN_PROGRESS', 'DELAYED') AND t.due_date <= :upcoming
              AND (t.due_date <= :now OR t.responsible_user_id IS NOT NULL)
              AND NOT EXISTS (
                  SELECT 1 FROM task_notifications n
                  WHERE n.task_id = t.id
                    AND n.type = CASE WHEN t.due_date <= :now THEN 'OVERDUE' ELSE 'DUE_SOON' END
                    AND n.recipient_id = CASE WHEN t.due_date <= :now THEN tm.coordinator_user_id ELSE t.responsible_user_id END
                    AND n.event_key = t.id::text || ':' || floor(extract(epoch FROM t.due_date))::bigint::text || ':' ||
                        CASE WHEN t.due_date <= :now THEN 'OVERDUE' ELSE 'DUE_SOON' END
              )
            ORDER BY t.due_date LIMIT :limit FOR UPDATE OF t, tm SKIP LOCKED
            """;
    private final DatabaseClient database;
    private final NotificationDataMapper notifications;
    private final TaskDataMapper tasks;

    public NotificationRepositoryAdapter(DatabaseClient database, NotificationDataMapper notifications, TaskDataMapper tasks) {
        this.database = database;
        this.notifications = notifications;
        this.tasks = tasks;
    }

    @Override
    public Mono<Void> save(Task task, UUID recipientId, NotificationType type, String eventKey, boolean deliverable) {
        return database.sql(INSERT).bind("taskId", task.id()).bind("recipientId", recipientId)
                .bind("title", task.title()).bind("type", type.name()).bind("eventKey", eventKey)
                .bind("deliverable", deliverable).fetch().rowsUpdated().then();
    }

    @Override
    public Flux<TaskNotification> findByRecipient(UUID recipientId, long beforeId, int limit) {
        return database.sql(SELECT).bind("recipientId", recipientId).bind("beforeId", beforeId)
                .bind("limit", limit).fetch().all().map(notifications::toNotification);
    }

    @Override
    public Mono<Long> markAsRead(UUID recipientId, long notificationId) {
        return database.sql(MARK_READ).bind("recipientId", recipientId).bind("notificationId", notificationId)
                .fetch().rowsUpdated();
    }

    @Override
    public Mono<Long> markAllAsRead(UUID recipientId) {
        return database.sql(MARK_ALL_READ).bind("recipientId", recipientId).fetch().rowsUpdated();
    }

    @Override
    public Flux<Task> lockAlertCandidates(OffsetDateTime now, OffsetDateTime upcoming, int limit) {
        return database.sql(CANDIDATES).bind("now", now).bind("upcoming", upcoming).bind("limit", limit)
                .fetch().all().map(tasks::toTask);
    }
}
