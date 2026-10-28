package co.com.compira.usecase.notifications;

import co.com.compira.model.notification.NotificationType;
import co.com.compira.model.notification.gateways.NotificationRepositoryGateway;
import co.com.compira.model.organization.gateways.OrganizationSettingsGateway;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.usecase.task.TaskTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TaskNotificationsUseCaseTest {
    private final NotificationRepositoryGateway notifications = mock(NotificationRepositoryGateway.class);
    private final OrganizationSettingsGateway settings = mock(OrganizationSettingsGateway.class);
    private final TaskUserDirectoryGateway users = mock(TaskUserDirectoryGateway.class);
    private final TaskNotificationsUseCase useCase = new TaskNotificationsUseCase(notifications, settings, users);

    @BeforeEach
    void configure() {
        when(settings.get()).thenReturn(Mono.just(NotificationTestData.settings(true)));
        when(notifications.save(any(), any(), any(), anyString(), anyBoolean())).thenReturn(Mono.empty());
        when(users.findByEmail(TaskTestData.COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.collaborator()));
    }

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {"ASSIGNED", "REASSIGNED"})
    void persistsAssignmentForTheRecipientWithoutRequiringAnActiveConnection(NotificationType type) {
        var task = TaskTestData.task(TaskStatus.PENDING);
        StepVerifier.create(useCase.assignment(task, TaskTestData.COLLABORATOR_ID, type, NotificationTestData.EVENT_ID)).verifyComplete();
        verify(notifications).save(task, TaskTestData.COLLABORATOR_ID, type, NotificationTestData.EVENT_ID.toString(), true);
    }

    @Test
    void doesNotMakeDisabledEventsDeliverable() {
        when(settings.get()).thenReturn(Mono.just(NotificationTestData.settings(false)));
        StepVerifier.create(useCase.assignment(TaskTestData.task(TaskStatus.PENDING), TaskTestData.COLLABORATOR_ID,
                NotificationType.ASSIGNED, NotificationTestData.EVENT_ID)).verifyComplete();
        verify(notifications).save(any(), eq(TaskTestData.COLLABORATOR_ID), eq(NotificationType.ASSIGNED), anyString(), eq(false));
        StepVerifier.create(useCase.list(TaskTestData.COLLABORATOR_EMAIL, Long.MAX_VALUE)).verifyComplete();
        verify(notifications, never()).findByRecipient(any(), anyLong(), anyInt());
    }

    @Test
    void retrievesOnlyAuthenticatedRecipientsPersistedNotifications() {
        when(notifications.findByRecipient(TaskTestData.COLLABORATOR_ID, 30, 50))
                .thenReturn(Flux.just(NotificationTestData.notification()));
        StepVerifier.create(useCase.list(TaskTestData.COLLABORATOR_EMAIL, 30))
                .expectNext(NotificationTestData.notification()).verifyComplete();
    }

    @Test
    void propagatesStorageFailureInsteadOfAcknowledgingAssignment() {
        when(notifications.save(any(), any(), any(), anyString(), anyBoolean())).thenReturn(Mono.error(new IllegalStateException("database unavailable")));
        StepVerifier.create(useCase.assignment(TaskTestData.task(TaskStatus.PENDING), TaskTestData.COLLABORATOR_ID,
                NotificationType.ASSIGNED, NotificationTestData.EVENT_ID)).expectError(IllegalStateException.class).verify();
    }

    @Test
    void marksSingleNotificationAsReadForRecipient() {
        when(notifications.markAsRead(TaskTestData.COLLABORATOR_ID, 7L)).thenReturn(Mono.just(1L));
        StepVerifier.create(useCase.markRead(TaskTestData.COLLABORATOR_EMAIL, 7L)).verifyComplete();
        verify(notifications).markAsRead(TaskTestData.COLLABORATOR_ID, 7L);
    }

    @Test
    void marksAllNotificationsAsReadForRecipient() {
        when(notifications.markAllAsRead(TaskTestData.COLLABORATOR_ID)).thenReturn(Mono.just(3L));
        StepVerifier.create(useCase.markAllRead(TaskTestData.COLLABORATOR_EMAIL)).verifyComplete();
        verify(notifications).markAllAsRead(TaskTestData.COLLABORATOR_ID);
    }

    @Test
    void forbidsAdministratorFromMarkingNotificationsAsRead() {
        var admin = new co.com.compira.model.task.TaskUser(
                java.util.UUID.fromString("99999999-9999-9999-9999-999999999999"),
                "admin@compira.co", "Admin", "Only",
                java.util.List.of(co.com.compira.model.auth.RoleCode.ADMINISTRATOR.name()));
        when(users.findByEmail("admin@compira.co")).thenReturn(Mono.just(admin));
        StepVerifier.create(useCase.markAllRead("admin@compira.co"))
                .expectErrorMatches(error -> error instanceof co.com.compira.model.common.error.CompiraException failure
                        && failure.getErrorCategory() == co.com.compira.model.common.error.ErrorCategory.FORBIDDEN)
                .verify();
        verify(notifications, never()).markAllAsRead(any());
    }
}
