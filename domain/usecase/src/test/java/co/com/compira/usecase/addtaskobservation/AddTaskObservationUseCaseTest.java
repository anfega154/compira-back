package co.com.compira.usecase.addtaskobservation;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.task.AddTaskObservationCommand;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskObservation;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.usecase.task.TaskTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AddTaskObservationUseCaseTest {
    private final TaskRepositoryGateway taskRepositoryGateway = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway taskUserDirectoryGateway = mock(TaskUserDirectoryGateway.class);
    private final AddTaskObservationUseCase useCase = new AddTaskObservationUseCase(taskRepositoryGateway, taskUserDirectoryGateway);

    @Test
    void shouldRegisterObservationWhenActorIsResponsible() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.IN_PROGRESS)));
        when(taskRepositoryGateway.saveObservation(any(TaskObservation.class)))
                .thenReturn(Mono.just(mock(TaskObservation.class)));
        when(taskRepositoryGateway.appendHistory(any(TaskHistoryEntry.class)))
                .thenReturn(Mono.just(mock(TaskHistoryEntry.class)));

        StepVerifier.create(useCase.execute(new AddTaskObservationCommand(
                        TaskTestData.COLLABORATOR_EMAIL, TaskTestData.TASK_ID, "Avance del 50%")))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldRejectWhenActorNotResponsible() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.OTHER_COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.otherCollaborator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.IN_PROGRESS)));

        StepVerifier.create(useCase.execute(new AddTaskObservationCommand(
                        TaskTestData.OTHER_COLLABORATOR_EMAIL, TaskTestData.TASK_ID, "Nota")))
                .expectErrorMatches(error -> error instanceof CompiraException compiraException
                        && TaskErrorCode.ACTOR_NOT_RESPONSIBLE.equals(compiraException.getCode()))
                .verify();
    }
}
