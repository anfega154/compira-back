package co.com.compira.usecase.listassignedtasks;

import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.usecase.task.TaskTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListAssignedTasksUseCaseTest {
    private final TaskRepositoryGateway taskRepositoryGateway = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway taskUserDirectoryGateway = mock(TaskUserDirectoryGateway.class);
    private final ListAssignedTasksUseCase useCase = new ListAssignedTasksUseCase(taskRepositoryGateway, taskUserDirectoryGateway);

    @Test
    void returnsAssignedTasksWithTheirPersistedStatus() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));
        when(taskRepositoryGateway.findByResponsible(TaskTestData.COLLABORATOR_ID))
                .thenReturn(Flux.just(TaskTestData.overdueTask(TaskStatus.IN_PROGRESS)));

        StepVerifier.create(useCase.execute(TaskTestData.COLLABORATOR_EMAIL))
                .assertNext(task -> assertStatus(task, TaskStatus.IN_PROGRESS))
                .verifyComplete();
    }

    @Test
    void returnsAssignedTasksUnchangedWhenNotOverdue() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));
        when(taskRepositoryGateway.findByResponsible(TaskTestData.COLLABORATOR_ID))
                .thenReturn(Flux.just(TaskTestData.task(TaskStatus.IN_PROGRESS)));

        StepVerifier.create(useCase.execute(TaskTestData.COLLABORATOR_EMAIL))
                .assertNext(task -> assertStatus(task, TaskStatus.IN_PROGRESS))
                .verifyComplete();
    }

    private void assertStatus(Task task, TaskStatus expected) {
        if (task.status() != expected) {
            throw new AssertionError("Expected status " + expected);
        }
    }
}
