package co.com.compira.usecase.createtask;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.task.CreateTaskCommand;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.gateways.TaskClockGateway;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.usecase.task.TaskTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreateTaskUseCaseTest {
    private final TaskRepositoryGateway taskRepositoryGateway = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway taskUserDirectoryGateway = mock(TaskUserDirectoryGateway.class);
    private final TaskClockGateway taskClockGateway = () -> TaskTestData.now();
    private final CreateTaskUseCase useCase = new CreateTaskUseCase(taskRepositoryGateway, taskUserDirectoryGateway, taskClockGateway);

    @Test
    void shouldCreateTaskWhenCoordinatorAndResponsibleAreValid() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));
        when(taskRepositoryGateway.save(any(Task.class)))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(taskRepositoryGateway.appendHistory(any(TaskHistoryEntry.class)))
                .thenReturn(Mono.just(mock(TaskHistoryEntry.class)));

        CreateTaskCommand command = new CreateTaskCommand(
                TaskTestData.COORDINATOR_EMAIL, "Preparar informe", "Detalle", TaskTestData.now().plusDays(2),
                TaskTestData.COLLABORATOR_EMAIL);

        StepVerifier.create(useCase.execute(command))
                .assertNext(task -> {
                    assertStatusPending(task);
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectWhenActorIsNotCoordinator() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));

        CreateTaskCommand command = new CreateTaskCommand(
                TaskTestData.COLLABORATOR_EMAIL, "Preparar informe", null, TaskTestData.now().plusDays(2), null);

        StepVerifier.create(useCase.execute(command))
                .expectErrorMatches(error -> error instanceof CompiraException compiraException
                        && TaskErrorCode.ACTOR_NOT_COORDINATOR.equals(compiraException.getCode()))
                .verify();
    }

    @Test
    void shouldRejectWhenDueDateIsInThePast() {
        CreateTaskCommand command = new CreateTaskCommand(
                TaskTestData.COORDINATOR_EMAIL, "Preparar informe", null, TaskTestData.now().minusDays(1), null);

        StepVerifier.create(useCase.execute(command))
                .expectErrorMatches(error -> error instanceof CompiraException compiraException
                        && TaskErrorCode.DUE_DATE_IN_PAST.equals(compiraException.getCode()))
                .verify();
    }

    @Test
    void shouldRejectWhenResponsibleIsNotCollaborator() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(taskUserDirectoryGateway.findByEmail("boss@compira.co"))
                .thenReturn(Mono.just(TaskTestData.administrator()));

        CreateTaskCommand command = new CreateTaskCommand(
                TaskTestData.COORDINATOR_EMAIL, "Preparar informe", null, TaskTestData.now().plusDays(2), "boss@compira.co");

        StepVerifier.create(useCase.execute(command))
                .expectErrorMatches(error -> error instanceof CompiraException compiraException
                        && TaskErrorCode.RESPONSIBLE_NOT_COLLABORATOR.equals(compiraException.getCode()))
                .verify();
    }

    private void assertStatusPending(Task task) {
        if (task.status() != TaskStatus.PENDING) {
            throw new AssertionError("Expected PENDING status");
        }
    }
}
