package co.com.compira.r2dbc;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.auth.UserStatus;
import co.com.compira.model.user.OrganizationUser;
import co.com.compira.model.user.gateways.UserDirectoryGateway;
import co.com.compira.r2dbc.mapper.OrganizationUserDataMapper;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class UserDirectoryAdapter implements UserDirectoryGateway {
    private static final String INVALID_ROLE_CODE = "USER_ADMIN_400";
    private static final String INVALID_ROLE_MESSAGE = "Rol inválido";
    private static final String USER_NOT_FOUND_CODE = "USER_ADMIN_404";
    private static final String USER_NOT_FOUND_MESSAGE = "Usuario no encontrado";
    private static final String EMAIL = "email";
    private static final String USER_ID = "userId";
    private static final String ROLE_CODE = "roleCode";
    private static final String SELECT_USERS = """
            SELECT u.id, u.email, u.first_name, u.last_name, u.phone_number, u.status,
                   u.created_at, u.last_login_at, t.id AS team_id, t.name AS team_name
            FROM users u
            LEFT JOIN team_members tm ON tm.user_id = u.id
            LEFT JOIN teams t ON t.id = tm.team_id
            """;
    private static final String ORDER_BY = " ORDER BY u.first_name, u.last_name, u.email";
    private static final String WHERE_EMAIL = " WHERE u.email = :email";
    private static final String SELECT_ROLES = """
            SELECT r.code
            FROM roles r
            INNER JOIN user_roles ur ON ur.role_id = r.id
            WHERE ur.user_id = :userId
            ORDER BY r.code
            """;
    private static final String DELETE_ROLES = "DELETE FROM user_roles WHERE user_id = :userId";
    private static final String INSERT_ROLE = """
            INSERT INTO user_roles (user_id, role_id)
            SELECT :userId, id FROM roles WHERE code = :roleCode
            """;
    private static final String STATUS = "status";
    private static final String UPDATE_STATUS = """
            UPDATE users
            SET status = :status,
                updated_at = CURRENT_TIMESTAMP
            WHERE email = :email
            RETURNING id
            """;

    private final DatabaseClient database;
    private final TransactionalOperator transactionalOperator;
    private final OrganizationUserDataMapper mapper;

    public UserDirectoryAdapter(DatabaseClient database,
                                TransactionalOperator transactionalOperator,
                                OrganizationUserDataMapper mapper) {
        this.database = database;
        this.transactionalOperator = transactionalOperator;
        this.mapper = mapper;
    }

    @Override
    public Flux<OrganizationUser> findAll() {
        return database.sql(SELECT_USERS + ORDER_BY).fetch().all().flatMapSequential(this::withRoles);
    }

    @Override
    public Mono<OrganizationUser> findByEmail(String email) {
        return database.sql(SELECT_USERS + WHERE_EMAIL).bind(EMAIL, email).fetch().one().flatMap(this::withRoles);
    }

    @Override
    public Mono<OrganizationUser> replaceRoles(String email, List<String> roleCodes) {
        return database.sql(SELECT_USERS + WHERE_EMAIL).bind(EMAIL, email).fetch().one()
                .map(row -> (UUID) row.get("id"))
                .flatMap(userId -> database.sql(DELETE_ROLES).bind(USER_ID, userId).fetch().rowsUpdated()
                        .thenMany(Flux.fromIterable(roleCodes).concatMap(code -> insertRole(userId, code)))
                        .then(findByEmail(email)))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<OrganizationUser> setStatus(String email, boolean active) {
        String status = active ? UserStatus.ACTIVE.name() : UserStatus.DISABLED.name();
        return database.sql(UPDATE_STATUS).bind(STATUS, status).bind(EMAIL, email).fetch().one()
                .switchIfEmpty(Mono.error(new CompiraException(
                        USER_NOT_FOUND_CODE, USER_NOT_FOUND_MESSAGE, ErrorCategory.NOT_FOUND)))
                .then(findByEmail(email))
                .as(transactionalOperator::transactional);
    }

    private Mono<Long> insertRole(UUID userId, String roleCode) {
        return database.sql(INSERT_ROLE).bind(USER_ID, userId).bind(ROLE_CODE, roleCode).fetch().rowsUpdated()
                .filter(updated -> updated > 0)
                .switchIfEmpty(Mono.error(new CompiraException(
                        INVALID_ROLE_CODE, INVALID_ROLE_MESSAGE, ErrorCategory.BAD_REQUEST)));
    }

    private Mono<OrganizationUser> withRoles(Map<String, Object> row) {
        UUID userId = (UUID) row.get("id");
        return database.sql(SELECT_ROLES).bind(USER_ID, userId)
                .map((roleRow, metadata) -> roleRow.get("code", String.class))
                .all().collectList()
                .map(roles -> mapper.toDomain(row, roles));
    }
}
