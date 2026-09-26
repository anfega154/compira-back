package co.com.compira.usecase.gettaskhistory;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskHistoryEvent;
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

class GetTaskHistoryUseCaseTest {
    private final TaskRepositoryGateway taskRepositoryGateway = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway taskUserDirectoryGateway = mock(TaskUserDirectoryGateway.class);
    private final GetTaskHistoryUseCase useCase = new GetTaskHistoryUseCase(taskRepositoryGateway, taskUserDirectoryGateway);

    @Test
    void shouldReturnHistoryForOwningCoordinator() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(taskRepositoryGateway.findHistory(TaskTestData.TASK_ID))
                .thenReturn(Flux.just(mock(TaskHistoryEntry.class)));

        StepVerifier.create(useCase.execute(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TASK_ID))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldRejectWhenActorHasNoScope() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));

        StepVerifier.create(useCase.execute(TaskTestData.COLLABORATOR_EMAIL, TaskTestData.TASK_ID))
                .expectErrorMatches(error -> error instanceof CompiraException compiraException
                        && TaskErrorCode.ACTOR_NOT_COORDINATOR.equals(compiraException.getCode()))
                .verify();
    }
}
