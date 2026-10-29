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
    private static final String TASK_WITH_TEAM_SELECT = """
            SELECT tasks.*, t.id AS team_id, t.name AS team_name
            FROM tasks
            LEFT JOIN task_teams tt ON tt.task_id = tasks.id
            LEFT JOIN teams t ON t.id = tt.team_id
            """;
    private static final String SELECT_TASK_BY_ID_QUERY = TASK_WITH_TEAM_SELECT + " WHERE tasks.id = :taskId";
    private static final String SELECT_TASKS_BY_RESPONSIBLE_QUERY =
            TASK_WITH_TEAM_SELECT + " WHERE tasks.responsible_user_id = :responsibleUserId ORDER BY tasks.due_date NULLS LAST, tasks.created_at DESC";
    private static final String SELECT_TASKS_BY_CREATOR_QUERY =
            TASK_WITH_TEAM_SELECT + " WHERE t.coordinator_user_id = :createdByUserId ORDER BY tasks.due_date NULLS LAST, tasks.created_at DESC";
    private static final String SELECT_ALL_TASKS_QUERY =
            TASK_WITH_TEAM_SELECT + " ORDER BY tasks.due_date NULLS LAST, tasks.created_at DESC";
    private static final String OBSERVATION_SELECT = """
            SELECT o.*, NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), '') AS author_name,
                   u.email AS author_email FROM %s o LEFT JOIN users u ON u.id = o.author_user_id
            """;
    private static final String HISTORY_SELECT = """
            SELECT h.*, NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), '') AS actor_name,
                   u.email AS actor_email FROM %s h LEFT JOIN users u ON u.id = h.actor_user_id
            """;
    private static final String INSERT_OBSERVATION_QUERY = """
            WITH saved AS (
                INSERT INTO task_observations (task_id, author_user_id, content)
                VALUES (:taskId, :authorUserId, :content) RETURNING *
            )
            """ + OBSERVATION_SELECT.formatted("saved");
    private static final String SELECT_OBSERVATIONS_QUERY = OBSERVATION_SELECT.formatted("task_observations")
            + " WHERE o.task_id = :taskId ORDER BY o.created_at ASC, o.id ASC";
    private static final String INSERT_HISTORY_QUERY = """
            WITH saved AS (
                INSERT INTO task_history (task_id, event, actor_user_id, previous_value, new_value, detail)
                VALUES (:taskId, :event, :actorUserId, :previousValue, :newValue, :detail) RETURNING *
            )
            """ + HISTORY_SELECT.formatted("saved");
    private static final String SELECT_HISTORY_QUERY = HISTORY_SELECT.formatted("task_history")
            + " WHERE h.task_id = :taskId ORDER BY h.created_at ASC, h.id ASC";

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
    public Flux<Task> findByCoordinator(UUID createdByUserId) {
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
