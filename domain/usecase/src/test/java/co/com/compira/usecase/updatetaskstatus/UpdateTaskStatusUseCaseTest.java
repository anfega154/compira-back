package co.com.compira.usecase.updatetaskstatus;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.UpdateTaskStatusCommand;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.usecase.task.TaskTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpdateTaskStatusUseCaseTest {
    private final TaskRepositoryGateway taskRepositoryGateway = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway taskUserDirectoryGateway = mock(TaskUserDirectoryGateway.class);
    private final co.com.compira.usecase.notifications.TaskNotificationsUseCase notifications = mock(co.com.compira.usecase.notifications.TaskNotificationsUseCase.class);
    private final UpdateTaskStatusUseCase useCase = new UpdateTaskStatusUseCase(taskRepositoryGateway, taskUserDirectoryGateway, notifications);

    @Test
    void shouldAdvanceStatusWhenResponsibleAndTransitionValid() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(taskRepositoryGateway.updateStatus(eq(TaskTestData.TASK_ID), eq(TaskStatus.IN_PROGRESS.name())))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.IN_PROGRESS)));
        when(taskRepositoryGateway.appendHistory(any(TaskHistoryEntry.class)))
                .thenReturn(Mono.just(mock(TaskHistoryEntry.class)));

        StepVerifier.create(useCase.execute(new UpdateTaskStatusCommand(
                        TaskTestData.COLLABORATOR_EMAIL, TaskTestData.TASK_ID, TaskStatus.IN_PROGRESS)))
                .assertNext(task -> assertStatus(task, TaskStatus.IN_PROGRESS))
                .verifyComplete();
        org.mockito.Mockito.verifyNoInteractions(notifications);
    }

    @Test
    void shouldRejectCloseByCollaborator() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.COMPLETED)));

        StepVerifier.create(useCase.execute(new UpdateTaskStatusCommand(
                        TaskTestData.COLLABORATOR_EMAIL, TaskTestData.TASK_ID, TaskStatus.CLOSED)))
                .expectErrorMatches(error -> error instanceof CompiraException compiraException
                        && TaskErrorCode.CLOSE_NOT_ALLOWED_FOR_COLLABORATOR.equals(compiraException.getCode()))
                .verify();
    }

    @Test
    void shouldRejectWhenActorIsNotResponsible() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.OTHER_COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.otherCollaborator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));

        StepVerifier.create(useCase.execute(new UpdateTaskStatusCommand(
                        TaskTestData.OTHER_COLLABORATOR_EMAIL, TaskTestData.TASK_ID, TaskStatus.IN_PROGRESS)))
                .expectErrorMatches(error -> error instanceof CompiraException compiraException
                        && TaskErrorCode.ACTOR_NOT_RESPONSIBLE.equals(compiraException.getCode()))
                .verify();
    }

    @Test
    void shouldRejectInvalidTransition() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));

        StepVerifier.create(useCase.execute(new UpdateTaskStatusCommand(
                        TaskTestData.COLLABORATOR_EMAIL, TaskTestData.TASK_ID, TaskStatus.COMPLETED)))
                .expectErrorMatches(error -> error instanceof CompiraException compiraException
                        && TaskErrorCode.INVALID_STATUS_TRANSITION.equals(compiraException.getCode()))
                .verify();
    }

    private void assertStatus(Task task, TaskStatus expected) {
        if (task.status() != expected) {
            throw new AssertionError("Expected status " + expected);
        }
    }
    @Test
    void notifiesCoordinatorWhenResponsibleCompletesTask() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.collaborator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.task(TaskStatus.IN_PROGRESS)));
        when(taskRepositoryGateway.updateStatus(TaskTestData.TASK_ID, TaskStatus.COMPLETED.name())).thenReturn(Mono.just(TaskTestData.task(TaskStatus.COMPLETED)));
        var event = new TaskHistoryEntry(co.com.compira.usecase.notifications.NotificationTestData.EVENT_ID, TaskTestData.TASK_ID,
                co.com.compira.model.task.TaskHistoryEvent.STATUS_CHANGED, TaskTestData.COLLABORATOR_ID, "IN_PROGRESS", "COMPLETED", null, null);
        when(taskRepositoryGateway.appendHistory(any())).thenReturn(Mono.just(event));
        when(notifications.coordinator(any(), any(), any())).thenReturn(Mono.empty());
        StepVerifier.create(useCase.execute(new UpdateTaskStatusCommand(TaskTestData.COLLABORATOR_EMAIL, TaskTestData.TASK_ID, TaskStatus.COMPLETED)))
                .assertNext(task -> assertStatus(task, TaskStatus.COMPLETED)).verifyComplete();
        org.mockito.Mockito.verify(notifications).coordinator(org.mockito.ArgumentMatchers.argThat(task -> task.status() == TaskStatus.COMPLETED),
                eq(co.com.compira.model.notification.NotificationType.TASK_COMPLETED), eq(event.id()));
    }

    @Test
    void propagatesNotificationFailureSoTheTransactionCanRollbackCompletion() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.collaborator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.task(TaskStatus.IN_PROGRESS)));
        when(taskRepositoryGateway.updateStatus(TaskTestData.TASK_ID, "COMPLETED")).thenReturn(Mono.just(TaskTestData.task(TaskStatus.COMPLETED)));
        when(taskRepositoryGateway.appendHistory(any())).thenReturn(Mono.just(mock(TaskHistoryEntry.class)));
        when(notifications.coordinator(any(), any(), any())).thenReturn(Mono.error(new IllegalStateException("notification failed")));
        StepVerifier.create(useCase.execute(new UpdateTaskStatusCommand(TaskTestData.COLLABORATOR_EMAIL, TaskTestData.TASK_ID, TaskStatus.COMPLETED)))
                .expectError(IllegalStateException.class).verify();
    }

}
