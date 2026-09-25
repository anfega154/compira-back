package co.com.compira.usecase.canceltask;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.task.CancelTaskCommand;
import co.com.compira.model.task.Task;
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

class CancelTaskUseCaseTest {
    private final TaskRepositoryGateway taskRepositoryGateway = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway taskUserDirectoryGateway = mock(TaskUserDirectoryGateway.class);
    private final CancelTaskUseCase useCase = new CancelTaskUseCase(taskRepositoryGateway, taskUserDirectoryGateway);

    @Test
    void shouldCancelWhenNotClosed() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.IN_PROGRESS)));
        when(taskRepositoryGateway.updateStatus(eq(TaskTestData.TASK_ID), eq(TaskStatus.CANCELLED.name())))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.CANCELLED)));
        when(taskRepositoryGateway.appendHistory(any(TaskHistoryEntry.class)))
                .thenReturn(Mono.just(mock(TaskHistoryEntry.class)));

        StepVerifier.create(useCase.execute(new CancelTaskCommand(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TASK_ID, "Ya no aplica")))
                .assertNext(task -> assertStatus(task, TaskStatus.CANCELLED))
                .verifyComplete();
    }

    @Test
    void shouldRejectCancelWhenClosed() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.CLOSED)));

        StepVerifier.create(useCase.execute(new CancelTaskCommand(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TASK_ID, null)))
                .expectErrorMatches(error -> error instanceof CompiraException compiraException
                        && TaskErrorCode.TASK_ALREADY_CLOSED.equals(compiraException.getCode()))
                .verify();
    }

    private void assertStatus(Task task, TaskStatus expected) {
        if (task.status() != expected) {
            throw new AssertionError("Expected status " + expected);
        }
    }
}
