package co.com.compira.r2dbc;

import co.com.compira.model.team.Team;
import co.com.compira.r2dbc.mapper.TeamDataMapper;
import co.com.compira.model.team.gateways.TeamRepositoryGateway;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Repository
public class TeamRepositoryAdapter implements TeamRepositoryGateway {
    private static final String MEMBER_CONFLICT_CODE = "TEAM_002";
    private static final String MEMBER_CONFLICT = "El colaborador ya tiene un equipo o el equipo no existe";
    private static final String TASK_CONFLICT_CODE = "TEAM_003";
    private static final String TASK_CONFLICT = "La tarea ya tiene un equipo o no existe";
    private static final String TASK_ID = "taskId";
    private static final String MEMBER_ID = "memberId";
    private static final String NAME = "name";
    private static final String SELECT = "SELECT t.id, t.name, t.coordinator_user_id, u.email FROM teams t JOIN users u ON u.id = t.coordinator_user_id";
    private static final String ID = "id";
    private static final String TEAM_ID = "teamId";
    private static final String COORDINATOR_ID = "coordinatorId";
    private final DatabaseClient database;
    private final TeamDataMapper mapper;

    public TeamRepositoryAdapter(DatabaseClient database, TeamDataMapper mapper) {
        this.database = database;
        this.mapper = mapper;
    }

    private Flux<Team> select(DatabaseClient.GenericExecuteSpec query) {
        return query.fetch().all().map(mapper::toTeam);
    }

    public Flux<Team> findAll() {
        return select(database.sql(SELECT + " ORDER BY t.name, t.id"));
    }

    public Flux<Team> findByCoordinator(UUID coordinatorId) {
        return select(database.sql(SELECT + " WHERE t.coordinator_user_id = :coordinatorId ORDER BY t.name, t.id")
                .bind(COORDINATOR_ID, coordinatorId));
    }

    public Mono<Team> findById(UUID teamId) {
        return select(database.sql(SELECT + " WHERE t.id = :teamId").bind(TEAM_ID, teamId)).singleOrEmpty();
    }

    public Mono<Team> findByTaskId(UUID taskId) {
        return select(database.sql(SELECT + " JOIN task_teams tt ON tt.team_id = t.id WHERE tt.task_id = :taskId")
                .bind(TASK_ID, taskId)).singleOrEmpty();
    }

    public Mono<Team> findByMemberId(UUID memberId) {
        return select(database.sql(SELECT + " JOIN team_members m ON m.team_id = t.id WHERE m.user_id = :memberId")
                .bind(MEMBER_ID, memberId)).singleOrEmpty();
    }

    public Mono<Team> create(String name, UUID coordinatorId) {
        return database.sql("INSERT INTO teams (name, coordinator_user_id) VALUES (:name, :coordinatorId) RETURNING id")
                .bind(NAME, name).bind(COORDINATOR_ID, coordinatorId).map((row, metadata) -> row.get(ID, UUID.class))
                .one().flatMap(this::findById);
    }

    public Mono<Team> changeCoordinator(UUID teamId, UUID coordinatorId) {
        return database.sql("UPDATE teams SET coordinator_user_id = :coordinatorId WHERE id = :teamId")
                .bind(COORDINATOR_ID, coordinatorId).bind(TEAM_ID, teamId).fetch().rowsUpdated().then(findById(teamId));
    }

    public Mono<Void> addMember(UUID teamId, UUID memberId) {
        return database.sql("INSERT INTO team_members (user_id, team_id) VALUES (:memberId, :teamId)")
                .bind(MEMBER_ID, memberId).bind(TEAM_ID, teamId).fetch().rowsUpdated()
                .onErrorMap(DataIntegrityViolationException.class, cause -> new CompiraException(
                        MEMBER_CONFLICT_CODE, MEMBER_CONFLICT, ErrorCategory.CONFLICT, cause)).then();
    }

    public Mono<Void> linkTask(UUID taskId, UUID teamId) {
        return database.sql("INSERT INTO task_teams (task_id, team_id) VALUES (:taskId, :teamId)")
                .bind(TASK_ID, taskId).bind(TEAM_ID, teamId).fetch().rowsUpdated()
                .onErrorMap(DataIntegrityViolationException.class, cause -> new CompiraException(
                        TASK_CONFLICT_CODE, TASK_CONFLICT, ErrorCategory.CONFLICT, cause)).then();
    }
}
