package co.com.compira.usecase.assigntask;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.task.AssignTaskCommand;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskStatus;
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

class AssignTaskUseCaseTest {
    private final TaskRepositoryGateway taskRepositoryGateway = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway taskUserDirectoryGateway = mock(TaskUserDirectoryGateway.class);
    private final AssignTaskUseCase useCase = new AssignTaskUseCase(taskRepositoryGateway, taskUserDirectoryGateway);

    @Test
    void shouldAssignResponsibleWhenValid() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(taskRepositoryGateway.updateResponsible(eq(TaskTestData.TASK_ID), eq(TaskTestData.COLLABORATOR_ID)))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(taskRepositoryGateway.appendHistory(any(TaskHistoryEntry.class)))
                .thenReturn(Mono.just(mock(TaskHistoryEntry.class)));

        StepVerifier.create(useCase.execute(new AssignTaskCommand(
                        TaskTestData.COORDINATOR_EMAIL, TaskTestData.TASK_ID, TaskTestData.COLLABORATOR_EMAIL)))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldRejectAssignWhenTaskClosed() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.CLOSED)));

        StepVerifier.create(useCase.execute(new AssignTaskCommand(
                        TaskTestData.COORDINATOR_EMAIL, TaskTestData.TASK_ID, TaskTestData.COLLABORATOR_EMAIL)))
                .expectErrorMatches(error -> error instanceof CompiraException compiraException
                        && TaskErrorCode.TASK_ALREADY_CLOSED.equals(compiraException.getCode()))
                .verify();
    }
}
