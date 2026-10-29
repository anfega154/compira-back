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
                        task.id() + ":" + task.dueDate().toEpochSecond() + ":DUE_SOON", true)
                        .then(notifications.save(task, CURRENT_COORDINATOR, NotificationType.DUE_SOON,
                                task.id() + ":" + task.dueDate().toEpochSecond() + ":DUE_SOON", true)).thenReturn(task))
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
    @Test
    void rejectsRelinkingTasksThatAlreadyHaveATeam() {
        StepVerifier.create(teams.linkTask(TASK, TEAM))
                .expectErrorMatches(error -> error instanceof co.com.compira.model.common.error.CompiraException failure
                        && failure.getErrorCategory() == co.com.compira.model.common.error.ErrorCategory.CONFLICT).verify();
        StepVerifier.create(teams.findByTaskId(TASK))
                .assertNext(team -> assertEquals(TEAM, team.id())).verifyComplete();
    }


    @Test
    void persistsCoordinatorObservationAndHistoryWithOriginalAuthor() {
        var observation = new co.com.compira.model.task.TaskObservation(null, TASK, CURRENT_COORDINATOR, "Revisión del coordinador", null);
        StepVerifier.create(tasks.saveObservation(observation)
                .flatMap(saved -> tasks.appendHistory(new co.com.compira.model.task.TaskHistoryEntry(null, TASK,
                        co.com.compira.model.task.TaskHistoryEvent.OBSERVATION_ADDED, CURRENT_COORDINATOR, null, null, saved.content(), null))))
                .expectNextCount(1).verifyComplete();
        StepVerifier.create(tasks.findObservations(TASK))
                .assertNext(saved -> { assertNotNull(saved.id()); assertEquals(CURRENT_COORDINATOR, saved.authorUserId()); assertEquals(observation.content(), saved.content()); })
                .verifyComplete();
        StepVerifier.create(tasks.findHistory(TASK))
                .assertNext(saved -> { assertEquals(CURRENT_COORDINATOR, saved.actorUserId()); assertEquals(observation.content(), saved.detail()); })
                .verifyComplete();
    }
    @Test
    void stillSelectsUpcomingTaskIfOnlyCollaboratorReceivedItsLegacyAlert() {
        StepVerifier.create(tasks.findById(TASK).flatMap(task -> notifications.save(task, COLLABORATOR, NotificationType.DUE_SOON,
                        task.id() + ":" + task.dueDate().toEpochSecond() + ":DUE_SOON", true))
                .thenMany(notifications.lockAlertCandidates(NOW, NOW.plusHours(24), 100)))
                .expectNextCount(1).verifyComplete();
    }

    @Test
    void selectsUpcomingUnassignedTasksAndAvoidsDuplicateAlerts() {
        StepVerifier.create(database.sql("UPDATE tasks SET responsible_user_id = NULL").then()
                .thenMany(notifications.lockAlertCandidates(NOW, NOW.plusHours(24), 100))
                .concatMap(task -> notifications.save(task, CURRENT_COORDINATOR, NotificationType.DUE_SOON,
                        task.id() + ":" + task.dueDate().toEpochSecond() + ":DUE_SOON", true).thenReturn(task)))
                .expectNextCount(1).verifyComplete();
        StepVerifier.create(notifications.lockAlertCandidates(NOW, NOW.plusHours(24), 100)).verifyComplete();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = NotificationType.class, names = {"TASK_COMPLETED", "OBSERVATION_ADDED"})
    void newNotificationTypesRespectSchemaAndDeduplicatePerEventAndRecipient(NotificationType type) {
        StepVerifier.create(tasks.findById(TASK).flatMap(task -> notifications.save(task, CURRENT_COORDINATOR, type, "event-1", true)
                        .then(notifications.save(task, CURRENT_COORDINATOR, type, "event-1", true)))
                .thenMany(notifications.findByRecipient(CURRENT_COORDINATOR, Long.MAX_VALUE, 50)))
                .assertNext(notification -> assertEquals(type, notification.type())).verifyComplete();
    }

    @Test
    void enrichesHistoricAndNewActivityWithoutReplacingActorIdsAndKeepsAutomaticEvents() {
        var event = new co.com.compira.model.task.TaskHistoryEntry(null, TASK, co.com.compira.model.task.TaskHistoryEvent.STATUS_CHANGED,
                COLLABORATOR, "PENDING", "IN_PROGRESS", null, null);
        StepVerifier.create(database.sql("UPDATE users SET first_name = 'Ana', last_name = 'García', status = 'DISABLED' WHERE id = :id")
                .bind("id", COLLABORATOR).then()
                .then(tasks.saveObservation(new co.com.compira.model.task.TaskObservation(null, TASK, COLLABORATOR, "Nota", null))))
                .assertNext(saved -> { assertEquals("Ana García", saved.authorName()); assertEquals("collaborator@compira.co", saved.authorEmail()); }).verifyComplete();
        StepVerifier.create(tasks.findObservations(TASK))
                .assertNext(saved -> { assertEquals(COLLABORATOR, saved.authorUserId()); assertEquals("Ana García", saved.authorName()); }).verifyComplete();
        StepVerifier.create(tasks.appendHistory(event))
                .assertNext(saved -> { assertEquals(COLLABORATOR, saved.actorUserId()); assertEquals("Ana García", saved.actorName()); assertEquals("collaborator@compira.co", saved.actorEmail()); }).verifyComplete();
        StepVerifier.create(tasks.appendHistory(new co.com.compira.model.task.TaskHistoryEntry(null, TASK,
                co.com.compira.model.task.TaskHistoryEvent.STATUS_CHANGED, null, "IN_PROGRESS", "DELAYED", null, null)))
                .assertNext(saved -> { assertNull(saved.actorUserId()); assertNull(saved.actorName()); }).verifyComplete();
        StepVerifier.create(tasks.findHistory(TASK).collectList()).assertNext(list -> assertEquals(2, list.size())).verifyComplete();
    }

    @Test
    void rollsBackCompletionAndHistoryWhenCoordinatorNotificationFails() {
        var directory = new TaskUserDirectoryAdapter(database, new TaskDataMapper());
        var failedNotifications = org.mockito.Mockito.mock(co.com.compira.model.notification.gateways.NotificationRepositoryGateway.class);
        org.mockito.Mockito.when(failedNotifications.save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(Mono.error(new IllegalStateException("notification unavailable")));
        var delivery = new co.com.compira.usecase.notifications.TaskNotificationsUseCase(failedNotifications,
                new OrganizationSettingsAdapter(database), directory, teams);
        var useCase = new co.com.compira.usecase.updatetaskstatus.UpdateTaskStatusUseCase(tasks, directory, delivery);
        StepVerifier.create(database.sql("UPDATE tasks SET status = 'IN_PROGRESS'").then()
                .then(database.sql("INSERT INTO user_roles (user_id, role_id) SELECT :id, id FROM roles WHERE code = 'COLLABORATOR'").bind("id", COLLABORATOR).then())
                .then(new OrganizationSettingsAdapter(database).save(new OrganizationSettings("America/Bogota", true))))
                .expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(new co.com.compira.model.task.UpdateTaskStatusCommand("collaborator@compira.co", TASK, TaskStatus.COMPLETED))
                .as(transactions::transactional)).expectError(IllegalStateException.class).verify();
        StepVerifier.create(tasks.findById(TASK)).assertNext(task -> assertEquals(TaskStatus.IN_PROGRESS, task.status())).verifyComplete();
        StepVerifier.create(tasks.findHistory(TASK)).verifyComplete();
    }

    @Test
    void completedTaskAndDistinctObservationsNotifyOnlyItsCurrentCoordinatorWithNamedActivity() {
        var directory = new TaskUserDirectoryAdapter(database, new TaskDataMapper());
        var delivery = new co.com.compira.usecase.notifications.TaskNotificationsUseCase(notifications,
                new OrganizationSettingsAdapter(database), directory, teams);
        var completion = new co.com.compira.usecase.updatetaskstatus.UpdateTaskStatusUseCase(tasks, directory, delivery);
        var observation = new co.com.compira.usecase.addtaskobservation.AddTaskObservationUseCase(tasks, directory,
                new co.com.compira.usecase.teams.TeamsUseCase(teams, tasks, directory), delivery);
        StepVerifier.create(database.sql(ACTIVITY_READY).then()).verifyComplete();
        StepVerifier.create(completion.execute(new co.com.compira.model.task.UpdateTaskStatusCommand("collaborator@compira.co", TASK, TaskStatus.COMPLETED))
                .as(transactions::transactional)).expectNextCount(1).verifyComplete();
        var command = new co.com.compira.model.task.AddTaskObservationCommand("collaborator@compira.co", TASK, "Entrega documentada");
        StepVerifier.create(observation.execute(command).as(transactions::transactional))
                .assertNext(saved -> assertEquals("Ana García", saved.authorName())).verifyComplete();
        StepVerifier.create(observation.execute(command).as(transactions::transactional)).expectNextCount(1).verifyComplete();
        StepVerifier.create(notifications.findByRecipient(CURRENT_COORDINATOR, Long.MAX_VALUE, 50).collectList())
                .assertNext(list -> {
                    assertEquals(3, list.size());
                    assertEquals(2, list.stream().filter(n -> n.type() == NotificationType.OBSERVATION_ADDED).count());
                    assertEquals(1, list.stream().filter(n -> n.type() == NotificationType.TASK_COMPLETED).count());
                }).verifyComplete();
        StepVerifier.create(notifications.findByRecipient(COORDINATOR, Long.MAX_VALUE, 50)).verifyComplete();
        StepVerifier.create(tasks.findHistory(TASK).collectList())
                .assertNext(list -> { assertEquals(3, list.size()); assertTrue(list.stream().allMatch(h -> COLLABORATOR.equals(h.actorUserId()) && "Ana García".equals(h.actorName()))); }).verifyComplete();
    }

    @Test
    void rollsBackObservationAndHistoryIfNotificationCannotBeSaved() {
        var directory = new TaskUserDirectoryAdapter(database, new TaskDataMapper());
        var failedDelivery = org.mockito.Mockito.mock(co.com.compira.usecase.notifications.TaskNotificationsUseCase.class);
        org.mockito.Mockito.when(failedDelivery.coordinator(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.error(new IllegalStateException("delivery failed")));
        var observation = new co.com.compira.usecase.addtaskobservation.AddTaskObservationUseCase(tasks, directory,
                new co.com.compira.usecase.teams.TeamsUseCase(teams, tasks, directory), failedDelivery);
        StepVerifier.create(database.sql(ACTIVITY_READY).then()).verifyComplete();
        StepVerifier.create(observation.execute(new co.com.compira.model.task.AddTaskObservationCommand("collaborator@compira.co", TASK, "No confirmar"))
                .as(transactions::transactional)).expectError(IllegalStateException.class).verify();
        StepVerifier.create(tasks.findObservations(TASK)).verifyComplete();
        StepVerifier.create(tasks.findHistory(TASK)).verifyComplete();
        StepVerifier.create(notifications.findByRecipient(CURRENT_COORDINATOR, Long.MAX_VALUE, 50)).verifyComplete();
    }

    @Test
    void committedDeliverableNoticeAndOutboxAreAtomicAndDeduplicated() {
        var outbox = new NotificationOutboxAdapter(database);
        StepVerifier.create(tasks.findById(TASK).flatMap(task -> notifications.save(task, CURRENT_COORDINATOR, NotificationType.TASK_COMPLETED, "outbox-one", true)
            .then(notifications.save(task, CURRENT_COORDINATOR, NotificationType.TASK_COMPLETED, "outbox-one", true))
            .then(notifications.save(task, CURRENT_COORDINATOR, NotificationType.OBSERVATION_ADDED, "disabled-one", false))))
            .verifyComplete();
        StepVerifier.create(outbox.lockPending().collectList()).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
        StepVerifier.create(outbox.lockPending().concatMap(e -> outbox.retryLater(e.id()))).verifyComplete();
        StepVerifier.create(outbox.lockPending()).verifyComplete();
        StepVerifier.create(database.sql("UPDATE notification_outbox SET available_at = CURRENT_TIMESTAMP - INTERVAL '1 second'").then()
            .thenMany(outbox.lockPending()).concatMap(e -> outbox.delivered(e.id()))).verifyComplete();
        StepVerifier.create(outbox.lockPending()).verifyComplete();
    }
    @Test
    void rolledBackNoticeCannotBePublishedByTheOutbox() {
        StepVerifier.create(tasks.findById(TASK).flatMap(task -> notifications.save(task, CURRENT_COORDINATOR, NotificationType.TASK_COMPLETED, "rollback-one", true)
            .then(Mono.error(new IllegalStateException("rollback")))).as(transactions::transactional)).expectError(IllegalStateException.class).verify();
        StepVerifier.create(new NotificationOutboxAdapter(database).lockPending()).verifyComplete();
        StepVerifier.create(notifications.findByRecipient(CURRENT_COORDINATOR, Long.MAX_VALUE, 50)).verifyComplete();
    }
}
