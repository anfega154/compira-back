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
    private final UpdateTaskStatusUseCase useCase = new UpdateTaskStatusUseCase(taskRepositoryGateway, taskUserDirectoryGateway);

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
}
