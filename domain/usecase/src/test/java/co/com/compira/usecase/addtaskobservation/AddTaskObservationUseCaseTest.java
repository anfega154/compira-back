package co.com.compira.usecase.addtaskobservation;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.AddTaskObservationCommand;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskObservation;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.usecase.task.TaskTestData;
import co.com.compira.usecase.teams.TeamsUseCase;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AddTaskObservationUseCaseTest {
    private final TaskRepositoryGateway taskRepositoryGateway = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway taskUserDirectoryGateway = mock(TaskUserDirectoryGateway.class);
    private final TeamsUseCase teams = mock(TeamsUseCase.class);
    private final AddTaskObservationUseCase useCase =
            new AddTaskObservationUseCase(taskRepositoryGateway, taskUserDirectoryGateway, teams);

    @Test
    void shouldRegisterObservationWhenActorIsResponsibleCollaborator() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));
        when(teams.requireTaskAccess(eq(TaskTestData.COLLABORATOR_EMAIL), eq(TaskTestData.TASK_ID)))
                .thenReturn(Mono.empty());
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
    void shouldRegisterObservationWhenActorIsTaskCoordinator() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(teams.requireTaskAccess(eq(TaskTestData.COORDINATOR_EMAIL), eq(TaskTestData.TASK_ID)))
                .thenReturn(Mono.empty());
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.IN_PROGRESS)));
        when(taskRepositoryGateway.saveObservation(any(TaskObservation.class)))
                .thenReturn(Mono.just(mock(TaskObservation.class)));
        when(taskRepositoryGateway.appendHistory(any(TaskHistoryEntry.class)))
                .thenReturn(Mono.just(mock(TaskHistoryEntry.class)));

        StepVerifier.create(useCase.execute(new AddTaskObservationCommand(
                        TaskTestData.COORDINATOR_EMAIL, TaskTestData.TASK_ID, "Revisión del coordinador")))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldRejectWhenActorHasNoTaskAccess() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.OTHER_COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.otherCollaborator()));
        when(teams.requireTaskAccess(eq(TaskTestData.OTHER_COLLABORATOR_EMAIL), eq(TaskTestData.TASK_ID)))
                .thenReturn(Mono.error(new CompiraException("TEAM_001", "No tienes acceso a este equipo",
                        ErrorCategory.FORBIDDEN)));

        StepVerifier.create(useCase.execute(new AddTaskObservationCommand(
                        TaskTestData.OTHER_COLLABORATOR_EMAIL, TaskTestData.TASK_ID, "Nota")))
                .expectErrorMatches(error -> error instanceof CompiraException compiraException
                        && compiraException.getErrorCategory() == ErrorCategory.FORBIDDEN)
                .verify();
    }
}
