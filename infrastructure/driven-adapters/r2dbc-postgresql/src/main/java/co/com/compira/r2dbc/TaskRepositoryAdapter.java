package co.com.compira.r2dbc;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskObservation;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.r2dbc.mapper.TaskDataMapper;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class TaskRepositoryAdapter implements TaskRepositoryGateway {
    private static final String INSERT_TASK_QUERY = """
            INSERT INTO tasks (title, description, due_date, status, responsible_user_id, created_by_user_id)
            VALUES (:title, :description, :dueDate, :status, :responsibleUserId, :createdByUserId)
            RETURNING *
            """;
    private static final String UPDATE_RESPONSIBLE_QUERY = """
            UPDATE tasks
            SET responsible_user_id = :responsibleUserId,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :taskId
            RETURNING *
            """;
    private static final String UPDATE_STATUS_QUERY = """
            UPDATE tasks
            SET status = :status,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :taskId
            RETURNING *
            """;
    private static final String SELECT_TASK_BY_ID_QUERY = "SELECT * FROM tasks WHERE id = :taskId";
    private static final String SELECT_TASKS_BY_RESPONSIBLE_QUERY =
            "SELECT * FROM tasks WHERE responsible_user_id = :responsibleUserId ORDER BY due_date NULLS LAST, created_at DESC";
    private static final String SELECT_TASKS_BY_CREATOR_QUERY =
            "SELECT * FROM tasks WHERE created_by_user_id = :createdByUserId ORDER BY due_date NULLS LAST, created_at DESC";
    private static final String SELECT_ALL_TASKS_QUERY =
            "SELECT * FROM tasks ORDER BY due_date NULLS LAST, created_at DESC";
    private static final String INSERT_OBSERVATION_QUERY = """
            INSERT INTO task_observations (task_id, author_user_id, content)
            VALUES (:taskId, :authorUserId, :content)
            RETURNING *
            """;
    private static final String SELECT_OBSERVATIONS_QUERY =
            "SELECT * FROM task_observations WHERE task_id = :taskId ORDER BY created_at ASC";
    private static final String INSERT_HISTORY_QUERY = """
            INSERT INTO task_history (task_id, event, actor_user_id, previous_value, new_value, detail)
            VALUES (:taskId, :event, :actorUserId, :previousValue, :newValue, :detail)
            RETURNING *
            """;
    private static final String SELECT_HISTORY_QUERY =
            "SELECT * FROM task_history WHERE task_id = :taskId ORDER BY created_at ASC";

    private final DatabaseClient databaseClient;
    private final TaskDataMapper taskDataMapper;

    public TaskRepositoryAdapter(DatabaseClient databaseClient, TaskDataMapper taskDataMapper) {
        this.databaseClient = databaseClient;
        this.taskDataMapper = taskDataMapper;
    }

    @Override
    public Mono<Task> save(Task task) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(INSERT_TASK_QUERY)
                .bind("title", task.title())
                .bind("status", task.status().name())
                .bind("createdByUserId", task.createdByUserId());
        spec = bindNullable(spec, "description", task.description(), String.class);
        spec = bindNullable(spec, "dueDate", task.dueDate(), OffsetDateTime.class);
        spec = bindNullable(spec, "responsibleUserId", task.responsibleUserId(), UUID.class);
        return spec.fetch().one().map(taskDataMapper::toTask);
    }

    @Override
    public Mono<Task> updateResponsible(UUID taskId, UUID responsibleUserId) {
        return databaseClient.sql(UPDATE_RESPONSIBLE_QUERY)
                .bind("responsibleUserId", responsibleUserId)
                .bind("taskId", taskId)
                .fetch()
                .one()
                .map(taskDataMapper::toTask)
                .switchIfEmpty(taskNotFound());
    }

    @Override
    public Mono<Task> updateStatus(UUID taskId, String status) {
        return databaseClient.sql(UPDATE_STATUS_QUERY)
                .bind("status", status)
                .bind("taskId", taskId)
                .fetch()
                .one()
                .map(taskDataMapper::toTask)
                .switchIfEmpty(taskNotFound());
    }

    @Override
    public Mono<Task> findById(UUID taskId) {
        return databaseClient.sql(SELECT_TASK_BY_ID_QUERY)
                .bind("taskId", taskId)
                .fetch()
                .one()
                .map(taskDataMapper::toTask);
    }

    @Override
    public Flux<Task> findByResponsible(UUID responsibleUserId) {
        return databaseClient.sql(SELECT_TASKS_BY_RESPONSIBLE_QUERY)
                .bind("responsibleUserId", responsibleUserId)
                .fetch()
                .all()
                .map(taskDataMapper::toTask);
    }

    @Override
    public Flux<Task> findByCreator(UUID createdByUserId) {
        return databaseClient.sql(SELECT_TASKS_BY_CREATOR_QUERY)
                .bind("createdByUserId", createdByUserId)
                .fetch()
                .all()
                .map(taskDataMapper::toTask);
    }

    @Override
    public Flux<Task> findAll() {
        return databaseClient.sql(SELECT_ALL_TASKS_QUERY)
                .fetch()
                .all()
                .map(taskDataMapper::toTask);
    }

    @Override
    public Mono<TaskObservation> saveObservation(TaskObservation observation) {
        return databaseClient.sql(INSERT_OBSERVATION_QUERY)
                .bind("taskId", observation.taskId())
                .bind("authorUserId", observation.authorUserId())
                .bind("content", observation.content())
                .fetch()
                .one()
                .map(taskDataMapper::toObservation);
    }

    @Override
    public Flux<TaskObservation> findObservations(UUID taskId) {
        return databaseClient.sql(SELECT_OBSERVATIONS_QUERY)
                .bind("taskId", taskId)
                .fetch()
                .all()
                .map(taskDataMapper::toObservation);
    }

    @Override
    public Mono<TaskHistoryEntry> appendHistory(TaskHistoryEntry entry) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(INSERT_HISTORY_QUERY)
                .bind("taskId", entry.taskId())
                .bind("event", entry.event().name());
        spec = bindNullable(spec, "actorUserId", entry.actorUserId(), UUID.class);
        spec = bindNullable(spec, "previousValue", entry.previousValue(), String.class);
        spec = bindNullable(spec, "newValue", entry.newValue(), String.class);
        spec = bindNullable(spec, "detail", entry.detail(), String.class);
        return spec.fetch().one().map(taskDataMapper::toHistoryEntry);
    }

    @Override
    public Flux<TaskHistoryEntry> findHistory(UUID taskId) {
        return databaseClient.sql(SELECT_HISTORY_QUERY)
                .bind("taskId", taskId)
                .fetch()
                .all()
                .map(taskDataMapper::toHistoryEntry);
    }

    private DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,
                                                           String name,
                                                           Object value,
                                                           Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private Mono<Task> taskNotFound() {
        return Mono.error(new CompiraException(
                TaskErrorCode.TASK_NOT_FOUND,
                TaskMessage.TASK_NOT_FOUND,
                ErrorCategory.NOT_FOUND));
    }
}
