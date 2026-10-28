package co.com.compira.usecase.notifications;

import co.com.compira.model.notification.NotificationType;
import co.com.compira.model.notification.gateways.NotificationRepositoryGateway;
import co.com.compira.model.organization.gateways.OrganizationSettingsGateway;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.gateways.TaskClockGateway;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.team.gateways.TeamRepositoryGateway;
import co.com.compira.usecase.task.TaskTestData;
import co.com.compira.usecase.taskalerts.EvaluateTaskAlertsUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EvaluateTaskAlertsUseCaseTest {
    private final NotificationRepositoryGateway notifications = mock(NotificationRepositoryGateway.class);
    private final OrganizationSettingsGateway settings = mock(OrganizationSettingsGateway.class);
    private final TaskRepositoryGateway tasks = mock(TaskRepositoryGateway.class);
    private final TeamRepositoryGateway teams = mock(TeamRepositoryGateway.class);
    private final TaskClockGateway clock = () -> NotificationTestData.NOW;
    private final EvaluateTaskAlertsUseCase useCase = new EvaluateTaskAlertsUseCase(notifications, settings, tasks, clock, teams);

    @BeforeEach
    void configure() {
        when(settings.get()).thenReturn(Mono.just(NotificationTestData.settings(true)));
        when(teams.findByTaskId(TaskTestData.TASK_ID)).thenReturn(Mono.just(NotificationTestData.team()));
        when(notifications.save(any(), any(), any(), anyString(), anyBoolean())).thenReturn(Mono.empty());
        when(tasks.updateStatus(any(), eq(TaskStatus.DELAYED.name()))).thenReturn(Mono.just(TaskTestData.task(TaskStatus.DELAYED)));
        when(tasks.appendHistory(any())).thenAnswer(invocation -> Mono.just(invocation.<TaskHistoryEntry>getArgument(0)));
    }

    @Test
    void alertsResponsibleAtExactly24HoursAndUsesOrganizationTimeZone() {
        when(notifications.lockAlertCandidates(any(), any(), eq(100))).thenReturn(Flux.just(
                NotificationTestData.task(TaskStatus.PENDING, NotificationTestData.NOW.plusHours(24))));
        StepVerifier.create(useCase.evaluate()).verifyComplete();
        verify(notifications).lockAlertCandidates(NotificationTestData.NOW.withOffsetSameInstant(java.time.ZoneOffset.ofHours(-5)),
                NotificationTestData.NOW.plusHours(24).withOffsetSameInstant(java.time.ZoneOffset.ofHours(-5)), 100);
        verify(notifications).save(any(), eq(TaskTestData.COLLABORATOR_ID), eq(NotificationType.DUE_SOON), anyString(), eq(true));
        verifyNoInteractions(tasks);
    }

    @Test
    void marksDelayedAtDeadlineAndAlertsCurrentCoordinatorNotCreator() {
        when(notifications.lockAlertCandidates(any(), any(), eq(100))).thenReturn(Flux.just(
                NotificationTestData.task(TaskStatus.IN_PROGRESS, NotificationTestData.NOW)));
        StepVerifier.create(useCase.evaluate()).verifyComplete();
        var order = inOrder(tasks, notifications);
        order.verify(tasks).updateStatus(TaskTestData.TASK_ID, TaskStatus.DELAYED.name());
        order.verify(tasks).appendHistory(any());
        order.verify(notifications).save(any(), eq(NotificationTestData.CURRENT_COORDINATOR), eq(NotificationType.OVERDUE), anyString(), eq(true));
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"COMPLETED", "CLOSED", "CANCELLED"})
    void excludesFinishedAndCancelledTasks(TaskStatus status) {
        when(notifications.lockAlertCandidates(any(), any(), eq(100))).thenReturn(Flux.just(
                NotificationTestData.task(status, NotificationTestData.NOW.minusHours(1))));
        StepVerifier.create(useCase.evaluate()).verifyComplete();
        verify(notifications, never()).save(any(), any(), any(), anyString(), anyBoolean());
        verifyNoInteractions(tasks);
    }

    @Test
    void doesNotAlertBeforeThe24HourWindow() {
        when(notifications.lockAlertCandidates(any(), any(), eq(100))).thenReturn(Flux.just(
                NotificationTestData.task(TaskStatus.PENDING, NotificationTestData.NOW.plusHours(24).plusSeconds(1))));
        StepVerifier.create(useCase.evaluate()).verifyComplete();
        verify(notifications, never()).save(any(), any(), any(), anyString(), anyBoolean());
    }

    @Test
    void advancesStateWhileNotificationsAreDisabled() {
        when(settings.get()).thenReturn(Mono.just(NotificationTestData.settings(false)));
        when(notifications.lockAlertCandidates(any(), any(), eq(100))).thenReturn(Flux.just(
                NotificationTestData.task(TaskStatus.PENDING, NotificationTestData.NOW)));
        StepVerifier.create(useCase.evaluate()).verifyComplete();
        verify(tasks).updateStatus(TaskTestData.TASK_ID, TaskStatus.DELAYED.name());
        verify(notifications).save(any(), any(), eq(NotificationType.OVERDUE), anyString(), eq(false));
    }
}
