package co.com.compira.r2dbc;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.r2dbc.mapper.OrganizationUserDataMapper;
import co.com.compira.r2dbc.mapper.TaskDataMapper;
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

import java.util.List;

import static co.com.compira.r2dbc.NotificationPersistenceTestData.COLLABORATOR;
import static co.com.compira.r2dbc.NotificationPersistenceTestData.COORDINATOR;
import static co.com.compira.r2dbc.NotificationPersistenceTestData.FIXTURES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class UserDirectoryPersistenceTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withEnv("POSTGRES_HOST_AUTH_METHOD", "trust");
    private static DatabaseClient database;
    private static TransactionalOperator transactions;
    private UserDirectoryAdapter users;
    private TaskUserDirectoryAdapter directory;

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
        var connectionFactory = ConnectionFactories.get("r2dbc:postgresql://" + POSTGRES.getUsername() + ":" + POSTGRES.getPassword()
                + "@" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/" + POSTGRES.getDatabaseName());
        database = DatabaseClient.create(connectionFactory);
        transactions = TransactionalOperator.create(new R2dbcTransactionManager(connectionFactory));
    }

    @BeforeEach
    void seed() {
        users = new UserDirectoryAdapter(database, transactions, new OrganizationUserDataMapper());
        directory = new TaskUserDirectoryAdapter(database, new TaskDataMapper());
        StepVerifier.create(database.sql("TRUNCATE task_notifications, task_history, task_observations, task_teams, team_members, teams, tasks, user_roles, users CASCADE")
                .then().then(database.sql(FIXTURES).then())
                .then(database.sql("""
                        INSERT INTO user_roles (user_id, role_id)
                        SELECT '33333333-3333-3333-3333-333333333333', id FROM roles WHERE code = 'COLLABORATOR'
                        """).then()))
                .verifyComplete();
    }

    @Test
    void listsOrganizationUsersWithRolesAndTeam() {
        StepVerifier.create(users.findAll().collectList())
                .assertNext(list -> {
                    assertEquals(3, list.size());
                    var collaborator = list.stream().filter(u -> u.email().equals("collaborator@compira.co")).findFirst().orElseThrow();
                    assertTrue(collaborator.roles().contains("COLLABORATOR"));
                    assertEquals("Operaciones", collaborator.teamName());
                })
                .verifyComplete();
    }

    @Test
    void findsUserByEmail() {
        StepVerifier.create(users.findByEmail("coordinator@compira.co"))
                .assertNext(user -> assertEquals("coordinator@compira.co", user.email()))
                .verifyComplete();
    }

    @Test
    void replacesRoleSetTransactionally() {
        StepVerifier.create(users.replaceRoles("collaborator@compira.co", List.of("COORDINATOR", "COLLABORATOR")))
                .assertNext(user -> {
                    assertTrue(user.roles().contains("COORDINATOR"));
                    assertTrue(user.roles().contains("COLLABORATOR"));
                })
                .verifyComplete();
    }

    @Test
    void rejectsInvalidRoleAndRollsBack() {
        StepVerifier.create(users.replaceRoles("collaborator@compira.co", List.of("WIZARD")))
                .expectError(CompiraException.class).verify();
        StepVerifier.create(users.findByEmail("collaborator@compira.co"))
                .assertNext(user -> assertTrue(user.roles().contains("COLLABORATOR")))
                .verifyComplete();
    }

    @Test
    void resolvesAssigneesByIds() {
        StepVerifier.create(directory.findByIds(List.of(COLLABORATOR, COORDINATOR)).collectList())
                .assertNext(found -> assertEquals(2, found.size()))
                .verifyComplete();
        StepVerifier.create(directory.findByIds(List.of()).collectList())
                .assertNext(found -> assertEquals(0, found.size()))
                .verifyComplete();
    }
}
