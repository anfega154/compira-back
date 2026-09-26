package co.com.compira.r2dbc;

import co.com.compira.model.notification.NotificationType;
import co.com.compira.model.organization.OrganizationSettings;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.r2dbc.mapper.NotificationDataMapper;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static co.com.compira.r2dbc.NotificationPersistenceTestData.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class NotificationPersistenceTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withEnv("POSTGRES_HOST_AUTH_METHOD", "trust");
    private static DatabaseClient database;
    private static TransactionalOperator transactions;
    private NotificationRepositoryAdapter notifications;
    private TaskRepositoryAdapter tasks;
    private TeamRepositoryAdapter teams;

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
        notifications = new NotificationRepositoryAdapter(database, new NotificationDataMapper(), new TaskDataMapper());
        tasks = new TaskRepositoryAdapter(database, new TaskDataMapper());
        teams = new TeamRepositoryAdapter(database, new co.com.compira.r2dbc.mapper.TeamDataMapper());
        StepVerifier.create(database.sql("TRUNCATE task_notifications, task_history, task_observations, task_teams, team_members, teams, tasks, user_roles, users CASCADE")
                .then().then(database.sql(FIXTURES).then())).verifyComplete();
    }

    @Test
    void persistsDeduplicatedEventsAndIsolatesRecipients() {
        StepVerifier.create(tasks.findById(TASK).flatMap(task -> Mono.when(
                        notifications.save(task, COLLABORATOR, NotificationType.ASSIGNED, "assignment-1", true),
                        notifications.save(task, COLLABORATOR, NotificationType.ASSIGNED, "assignment-1", true)))
                .thenMany(notifications.findByRecipient(COLLABORATOR, Long.MAX_VALUE, 50)))
                .assertNext(notification -> { assertEquals(TASK, notification.taskId()); assertEquals("Informe", notification.taskTitle()); })
                .verifyComplete();
        StepVerifier.create(notifications.findByRecipient(COORDINATOR, Long.MAX_VALUE, 50)).verifyComplete();
    }

    @Test
    void excludesSuppressedEventsAndSupportsExclusivePagination() {
        StepVerifier.create(tasks.findById(TASK).flatMap(task -> notifications.save(task, COLLABORATOR, NotificationType.ASSIGNED, "hidden", false)
                        .then(notifications.save(task, COLLABORATOR, NotificationType.REASSIGNED, "visible", true)))
                .thenMany(notifications.findByRecipient(COLLABORATOR, Long.MAX_VALUE, 50))
                .single().flatMapMany(notification -> notifications.findByRecipient(COLLABORATOR, notification.id(), 50)))
                .verifyComplete();
    }

    @Test
    void selectsExactly24HourBoundaryAndStopsSelectingAfterRecordingAlert() {
        StepVerifier.create(notifications.lockAlertCandidates(NOW.minusSeconds(1), NOW.plusHours(24).minusSeconds(1), 100)
                .as(transactions::transactional)).verifyComplete();
        StepVerifier.create(notifications.lockAlertCandidates(NOW, NOW.plusHours(24), 100)
                .concatMap(task -> notifications.save(task, COLLABORATOR, NotificationType.DUE_SOON,
                        task.id() + ":" + task.dueDate().toEpochSecond() + ":DUE_SOON", true).thenReturn(task))
                .as(transactions::transactional)).expectNextCount(1).verifyComplete();
        StepVerifier.create(notifications.lockAlertCandidates(NOW, NOW.plusHours(24), 100)
                .as(transactions::transactional)).verifyComplete();
    }

    @Test
    void rollsBackStateAndNotificationTogether() {
        StepVerifier.create(tasks.updateStatus(TASK, TaskStatus.DELAYED.name())
                .flatMap(task -> notifications.save(task, CURRENT_COORDINATOR, NotificationType.OVERDUE, "rollback", true))
                .then(Mono.error(new IllegalStateException("forced failure")))
                .as(transactions::transactional)).expectError(IllegalStateException.class).verify();
        StepVerifier.create(tasks.findById(TASK)).assertNext(task -> assertEquals(TaskStatus.PENDING, task.status())).verifyComplete();
        StepVerifier.create(notifications.findByRecipient(CURRENT_COORDINATOR, Long.MAX_VALUE, 50)).verifyComplete();
    }

    @Test
    void resolvesCurrentTeamCoordinatorAndMaintainsSingleMembership() {
        StepVerifier.create(teams.findByTaskId(TASK)).assertNext(team -> assertEquals(CURRENT_COORDINATOR, team.coordinatorUserId())).verifyComplete();
        StepVerifier.create(teams.changeCoordinator(TEAM, COORDINATOR).then(teams.findByTaskId(TASK)))
                .assertNext(team -> assertEquals(COORDINATOR, team.coordinatorUserId())).verifyComplete();
        StepVerifier.create(teams.addMember(TEAM, COLLABORATOR)).expectError(co.com.compira.model.common.error.CompiraException.class).verify();
    }

    @Test
    void persistsGlobalSettings() {
        OrganizationSettingsAdapter settings = new OrganizationSettingsAdapter(database);
        StepVerifier.create(settings.save(new OrganizationSettings("America/Bogota", true)).then(settings.get()))
                .assertNext(configuration -> { assertEquals("America/Bogota", configuration.timeZone()); assertTrue(configuration.notificationsEnabled()); })
                .verifyComplete();
    }
}
