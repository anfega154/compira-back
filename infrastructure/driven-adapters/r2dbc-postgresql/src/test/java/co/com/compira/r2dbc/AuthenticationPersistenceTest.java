package co.com.compira.r2dbc;

import co.com.compira.model.auth.UserStatus;
import co.com.compira.model.auth.AuthenticationErrorCode;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.r2dbc.mapper.ApplicationUserDataMapper;
import io.r2dbc.spi.ConnectionFactories;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.test.StepVerifier;

import static co.com.compira.r2dbc.AuthenticationPersistenceTestData.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class AuthenticationPersistenceTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withEnv("POSTGRES_HOST_AUTH_METHOD", "trust");
    private static DatabaseClient database;
    private static AuthenticationUserRepositoryAdapter users;

    @BeforeAll
    static void migrate() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        var connections = ConnectionFactories.get("r2dbc:postgresql://" + POSTGRES.getUsername() + ":" + POSTGRES.getPassword()
                + "@" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/" + POSTGRES.getDatabaseName());
        database = DatabaseClient.create(connections);
        users = new AuthenticationUserRepositoryAdapter(database,
                TransactionalOperator.create(new R2dbcTransactionManager(connections)), new ApplicationUserDataMapper());
    }

    @BeforeEach
    void registerPendingUser() {
        StepVerifier.create(database.sql(RESET_USERS).then()
                        .then(users.createPendingUser(registration(), SUBJECT)))
                .assertNext(user -> {
                    assertEquals(UserStatus.PENDING_CONFIRMATION, user.status());
                    assertNull(user.lastLoginAt());
                }).verifyComplete();
    }

    @Test
    void activatesPendingAccountAndPersistsLoginBeforeReturning() {
        StepVerifier.create(users.completeLogin(EMAIL))
                .assertNext(user -> {
                    assertEquals(UserStatus.ACTIVE, user.status());
                    assertNotNull(user.lastLoginAt());
                    assertEquals(SUBJECT, user.cognitoSub());
                }).verifyComplete();
        StepVerifier.create(users.findByCognitoSub(SUBJECT))
                .assertNext(user -> {
                    assertEquals(UserStatus.ACTIVE, user.status());
                    assertNotNull(user.lastLoginAt());
                    assertEquals(java.util.List.of("COLLABORATOR"), user.roles());
                }).verifyComplete();
    }

    @Test
    void supportsSubsequentLoginsWithoutChangingRolesOrIdentity() {
        StepVerifier.create(users.completeLogin(EMAIL).flatMap(first -> users.completeLogin(EMAIL)
                        .doOnNext(second -> {
                            assertEquals(UserStatus.ACTIVE, second.status());
                            assertEquals(first.user().id(), second.user().id());
                            assertEquals(first.roles(), second.roles());
                            assertFalse(second.lastLoginAt().isBefore(first.lastLoginAt()));
                        })))
                .expectNextCount(1).verifyComplete();
    }

    @Test
    void neverReactivatesDisabledAccounts() {
        StepVerifier.create(database.sql(DISABLE_USER).bind("email", EMAIL).then()
                        .then(users.completeLogin(EMAIL)))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && AuthenticationErrorCode.INVALID_CREDENTIALS.equals(failure.getCode())
                        && ErrorCategory.UNAUTHORIZED == failure.getErrorCategory()).verify();
        StepVerifier.create(users.findByCognitoSub(SUBJECT))
                .assertNext(user -> {
                    assertEquals(UserStatus.DISABLED, user.status());
                    assertNull(user.lastLoginAt());
                }).verifyComplete();
    }
}
