package co.com.compira.r2dbc;

import co.com.compira.model.task.TaskUser;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.r2dbc.mapper.TaskDataMapper;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class TaskUserDirectoryAdapter implements TaskUserDirectoryGateway {
    private static final String SELECT_USER_BY_EMAIL_QUERY = "SELECT * FROM users WHERE email = :email";
    private static final String SELECT_USER_BY_ID_QUERY = "SELECT * FROM users WHERE id = :id";
    private static final String SELECT_ROLES_BY_USER_ID_QUERY = """
            SELECT r.code
            FROM roles r
            INNER JOIN user_roles ur ON ur.role_id = r.id
            WHERE ur.user_id = :userId
            ORDER BY r.code
            """;

    private final DatabaseClient databaseClient;
    private final TaskDataMapper taskDataMapper;

    public TaskUserDirectoryAdapter(DatabaseClient databaseClient, TaskDataMapper taskDataMapper) {
        this.databaseClient = databaseClient;
        this.taskDataMapper = taskDataMapper;
    }

    @Override
    public Mono<TaskUser> findByEmail(String email) {
        return databaseClient.sql(SELECT_USER_BY_EMAIL_QUERY)
                .bind("email", email)
                .fetch()
                .one()
                .flatMap(this::buildTaskUser);
    }

    @Override
    public Mono<TaskUser> findById(UUID id) {
        return databaseClient.sql(SELECT_USER_BY_ID_QUERY)
                .bind("id", id)
                .fetch()
                .one()
                .flatMap(this::buildTaskUser);
    }

    private Mono<TaskUser> buildTaskUser(Map<String, Object> row) {
        UUID userId = (UUID) row.get("id");
        return findRoles(userId).map(roles -> taskDataMapper.toTaskUser(row, roles));
    }

    private Mono<List<String>> findRoles(UUID userId) {
        return databaseClient.sql(SELECT_ROLES_BY_USER_ID_QUERY)
                .bind("userId", userId)
                .map((row, metadata) -> row.get("code", String.class))
                .all()
                .collectList();
    }
}
