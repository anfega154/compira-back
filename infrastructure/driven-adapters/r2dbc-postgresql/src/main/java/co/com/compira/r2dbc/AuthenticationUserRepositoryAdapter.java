package co.com.compira.r2dbc;

import co.com.compira.model.auth.ApplicationUser;
import co.com.compira.model.auth.AuthenticationLogSanitizer;
import co.com.compira.model.auth.RegisterUserCommand;
import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.auth.UserStatus;
import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.r2dbc.mapper.ApplicationUserDataMapper;
import co.com.compira.model.auth.AuthenticationErrorCode;
import co.com.compira.model.auth.AuthenticationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class AuthenticationUserRepositoryAdapter implements ApplicationUserRepositoryGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationUserRepositoryAdapter.class);
    private static final String DEFAULT_ROLE_CODE = RoleCode.COLLABORATOR.name();
    private static final String LOG_CREATE_PENDING_USER = "Persistiendo perfil local pendiente. email={} cognitoSub={}";
    private static final String LOG_FIND_USER = "Consultando perfil local por correo. email={}";
    private static final String LOG_ACTIVATE_USER = "Activando perfil local. email={}";
    private static final String LOG_UPDATE_LAST_LOGIN = "Actualizando último ingreso local. email={}";
    private static final String LOG_DELETE_USER = "Eliminando perfil local. email={}";
    private static final String LOG_ASSIGN_ROLE = "Asignando rol local. userId={} role={}";
    private static final String LOG_BUILD_USER = "Perfil local cargado. email={} userId={}";
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
    private static final String DELETE_USER_QUERY = "DELETE FROM users WHERE email = :email";

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
        LOGGER.info(LOG_CREATE_PENDING_USER, AuthenticationLogSanitizer.maskEmail(command.email()), cognitoSub);
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
                        AuthenticationMessage.LOCAL_USER_PERSISTENCE_ERROR,
                        ErrorCategory.INTERNAL_SERVER_ERROR)))
                .flatMap(row -> assignRole((UUID) row.get("id"), resolveRoleCode(command))
                        .then(buildApplicationUser(row)))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<ApplicationUser> findByEmail(String email) {
        LOGGER.info(LOG_FIND_USER, AuthenticationLogSanitizer.maskEmail(email));
        return selectUserByEmail(email)
                .flatMap(this::buildApplicationUser);
    }

    @Override
    public Mono<ApplicationUser> activateUser(String email) {
        LOGGER.info(LOG_ACTIVATE_USER, AuthenticationLogSanitizer.maskEmail(email));
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
        LOGGER.info(LOG_UPDATE_LAST_LOGIN, AuthenticationLogSanitizer.maskEmail(email));
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

    @Override
    public Mono<Void> deleteByEmail(String email) {
        LOGGER.info(LOG_DELETE_USER, AuthenticationLogSanitizer.maskEmail(email));
        return databaseClient.sql(DELETE_USER_QUERY)
                .bind("email", email)
                .fetch()
                .rowsUpdated()
                .filter(updatedRows -> updatedRows > 0)
                .switchIfEmpty(Mono.error(new CompiraException(
                        AuthenticationErrorCode.LOCAL_USER_NOT_FOUND,
                        AuthenticationMessage.LOCAL_USER_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)))
                .then();
    }

    private Mono<Map<String, Object>> selectUserByEmail(String email) {
        return databaseClient.sql(SELECT_USER_BY_EMAIL_QUERY)
                .bind("email", email)
                .fetch()
                .one();
    }

    private Mono<Void> assignRole(UUID userId, String roleCode) {
        LOGGER.info(LOG_ASSIGN_ROLE, userId, roleCode);
        return databaseClient.sql(ASSIGN_ROLE_QUERY)
                .bind("userId", userId)
                .bind("roleCode", roleCode)
                .fetch()
                .rowsUpdated()
                .filter(updatedRows -> updatedRows > 0)
                .switchIfEmpty(Mono.error(new CompiraException(
                        AuthenticationErrorCode.INVALID_REQUEST,
                        AuthenticationMessage.INVALID_ROLE_CODE,
                        ErrorCategory.BAD_REQUEST)))
                .then();
    }

    private String resolveRoleCode(RegisterUserCommand command) {
        return command.roleCode() == null ? DEFAULT_ROLE_CODE : command.roleCode().name();
    }

    private Mono<ApplicationUser> buildApplicationUser(Map<String, Object> row) {
        UUID userId = (UUID) row.get("id");
        return findRoles(userId)
                .map(roles -> applicationUserDataMapper.fromRow(row, roles))
                .map(applicationUserDataMapper::toDomain)
                .doOnNext(user -> LOGGER.info(LOG_BUILD_USER, AuthenticationLogSanitizer.maskEmail(user.user().email()), user.user().id()));
    }

    private Mono<List<String>> findRoles(UUID userId) {
        return databaseClient.sql(SELECT_ROLES_BY_USER_ID_QUERY)
                .bind("userId", userId)
                .map((row, rowMetadata) -> row.get("code", String.class))
                .all()
                .collectList();
    }
}
