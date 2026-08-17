package co.com.compira.r2dbc;

import co.com.compira.model.auth.ApplicationUser;
import co.com.compira.model.auth.RegisterUserCommand;
import co.com.compira.model.auth.UserStatus;
import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.r2dbc.mapper.ApplicationUserDataMapper;
import co.com.compira.model.auth.AuthenticationErrorCode;
import co.com.compira.model.auth.AuthenticationMessage;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class AuthenticationUserRepositoryAdapter implements ApplicationUserRepositoryGateway {
    private static final String DEFAULT_ROLE_CODE = "USER";
    private static final String UPSERT_USER_QUERY = """
            INSERT INTO users (
                cognito_sub,
                email,
                first_name,
                last_name,
                phone_number,
                preferred_mfa_channel,
                status
            ) VALUES (
                :cognitoSub,
                :email,
                :firstName,
                :lastName,
                :phoneNumber,
                :preferredMfaChannel,
                :status
            )
            ON CONFLICT (cognito_sub) DO UPDATE SET
                email = EXCLUDED.email,
                first_name = EXCLUDED.first_name,
                last_name = EXCLUDED.last_name,
                phone_number = EXCLUDED.phone_number,
                preferred_mfa_channel = EXCLUDED.preferred_mfa_channel,
                status = EXCLUDED.status,
                updated_at = CURRENT_TIMESTAMP
            RETURNING *
            """;
    private static final String ASSIGN_ROLE_QUERY = """
            INSERT INTO user_roles (user_id, role_id)
            SELECT :userId, id
            FROM roles
            WHERE code = :roleCode
            ON CONFLICT (user_id, role_id) DO NOTHING
            """;
    private static final String SELECT_USER_BY_EMAIL_QUERY = "SELECT * FROM users WHERE email = :email";
    private static final String SELECT_ROLES_BY_USER_ID_QUERY = """
            SELECT r.code
            FROM roles r
            INNER JOIN user_roles ur ON ur.role_id = r.id
            WHERE ur.user_id = :userId
            ORDER BY r.code
            """;
    private static final String ACTIVATE_USER_QUERY = """
            UPDATE users
            SET status = :status,
                updated_at = CURRENT_TIMESTAMP
            WHERE email = :email
            RETURNING *
            """;
    private static final String UPDATE_LAST_LOGIN_QUERY = """
            UPDATE users
            SET last_login_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE email = :email
            RETURNING *
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transactionalOperator;
    private final ApplicationUserDataMapper applicationUserDataMapper;

    public AuthenticationUserRepositoryAdapter(DatabaseClient databaseClient,
                                               TransactionalOperator transactionalOperator,
                                               ApplicationUserDataMapper applicationUserDataMapper) {
        this.databaseClient = databaseClient;
        this.transactionalOperator = transactionalOperator;
        this.applicationUserDataMapper = applicationUserDataMapper;
    }

    @Override
    public Mono<ApplicationUser> createPendingUser(RegisterUserCommand command, String cognitoSub) {
        return databaseClient.sql(UPSERT_USER_QUERY)
                .bind("cognitoSub", cognitoSub)
                .bind("email", command.email())
                .bind("firstName", command.firstName())
                .bind("lastName", command.lastName())
                .bind("phoneNumber", command.phoneNumber())
                .bind("preferredMfaChannel", command.preferredMfaChannel().name())
                .bind("status", UserStatus.PENDING_CONFIRMATION.name())
                .fetch()
                .one()
                .switchIfEmpty(Mono.error(new CompiraException(
                        AuthenticationErrorCode.GENERIC_AUTHENTICATION_ERROR,
                        AuthenticationMessage.GENERIC_AUTHENTICATION_ERROR,
                        ErrorCategory.INTERNAL_SERVER_ERROR)))
                .flatMap(row -> assignDefaultRole((UUID) row.get("id"))
                        .then(buildApplicationUser(row)))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<ApplicationUser> findByEmail(String email) {
        return selectUserByEmail(email)
                .flatMap(this::buildApplicationUser);
    }

    @Override
    public Mono<ApplicationUser> activateUser(String email) {
        return databaseClient.sql(ACTIVATE_USER_QUERY)
                .bind("status", UserStatus.ACTIVE.name())
                .bind("email", email)
                .fetch()
                .one()
                .switchIfEmpty(Mono.error(new CompiraException(
                        AuthenticationErrorCode.LOCAL_USER_NOT_FOUND,
                        AuthenticationMessage.LOCAL_USER_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)))
                .flatMap(this::buildApplicationUser);
    }

    @Override
    public Mono<ApplicationUser> updateLastLogin(String email) {
        return databaseClient.sql(UPDATE_LAST_LOGIN_QUERY)
                .bind("email", email)
                .fetch()
                .one()
                .switchIfEmpty(Mono.error(new CompiraException(
                        AuthenticationErrorCode.LOCAL_USER_NOT_FOUND,
                        AuthenticationMessage.LOCAL_USER_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)))
                .flatMap(this::buildApplicationUser);
    }

    private Mono<Map<String, Object>> selectUserByEmail(String email) {
        return databaseClient.sql(SELECT_USER_BY_EMAIL_QUERY)
                .bind("email", email)
                .fetch()
                .one();
    }

    private Mono<Void> assignDefaultRole(UUID userId) {
        return databaseClient.sql(ASSIGN_ROLE_QUERY)
                .bind("userId", userId)
                .bind("roleCode", DEFAULT_ROLE_CODE)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<ApplicationUser> buildApplicationUser(Map<String, Object> row) {
        UUID userId = (UUID) row.get("id");
        return findRoles(userId)
                .map(roles -> applicationUserDataMapper.fromRow(row, roles))
                .map(applicationUserDataMapper::toDomain);
    }

    private Mono<List<String>> findRoles(UUID userId) {
        return databaseClient.sql(SELECT_ROLES_BY_USER_ID_QUERY)
                .bind("userId", userId)
                .map((row, rowMetadata) -> row.get("code", String.class))
                .all()
                .collectList();
    }
}
