package co.com.compira.usecase.gettaskhistory;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.TaskUser;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.model.team.Team;
import co.com.compira.model.team.gateways.TeamRepositoryGateway;
import co.com.compira.usecase.task.TaskTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetTaskHistoryUseCaseTest {
    private static final String INHERITED_COORDINATOR_EMAIL = "inherited.coordinator@compira.co";
    private static final UUID INHERITED_COORDINATOR_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private final TaskRepositoryGateway taskRepositoryGateway = mock(TaskRepositoryGateway.class);
    private final TeamRepositoryGateway teamRepositoryGateway = mock(TeamRepositoryGateway.class);
    private final TaskUserDirectoryGateway taskUserDirectoryGateway = mock(TaskUserDirectoryGateway.class);
    private final GetTaskHistoryUseCase useCase =
            new GetTaskHistoryUseCase(taskRepositoryGateway, teamRepositoryGateway, taskUserDirectoryGateway);

    @Test
    void shouldReturnHistoryForCurrentTeamCoordinator() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(teamRepositoryGateway.findByTaskId(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.team()));
        when(taskRepositoryGateway.findHistory(TaskTestData.TASK_ID))
                .thenReturn(Flux.just(mock(TaskHistoryEntry.class)));

        StepVerifier.create(useCase.execute(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TASK_ID))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldReturnHistoryForInheritedCoordinatorWhoIsNotTheCreator() {
        TaskUser inheritedCoordinator = new TaskUser(INHERITED_COORDINATOR_ID, INHERITED_COORDINATOR_EMAIL,
                "Coord", "Heredado", List.of(RoleCode.COORDINATOR.name()));
        Team teamWithNewCoordinator = new Team(TaskTestData.TEAM_ID, "Operaciones",
                INHERITED_COORDINATOR_ID, INHERITED_COORDINATOR_EMAIL);

        when(taskUserDirectoryGateway.findByEmail(INHERITED_COORDINATOR_EMAIL))
                .thenReturn(Mono.just(inheritedCoordinator));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(teamRepositoryGateway.findByTaskId(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(teamWithNewCoordinator));
        when(taskRepositoryGateway.findHistory(TaskTestData.TASK_ID))
                .thenReturn(Flux.just(mock(TaskHistoryEntry.class)));

        StepVerifier.create(useCase.execute(INHERITED_COORDINATOR_EMAIL, TaskTestData.TASK_ID))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldReturnHistoryForAdministrator() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.administrator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(taskRepositoryGateway.findHistory(TaskTestData.TASK_ID))
                .thenReturn(Flux.just(mock(TaskHistoryEntry.class)));

        StepVerifier.create(useCase.execute(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TASK_ID))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void shouldRejectCoordinatorWhoIsNotTheCurrentTeamCoordinator() {
        TaskUser otherCoordinator = new TaskUser(INHERITED_COORDINATOR_ID, INHERITED_COORDINATOR_EMAIL,
                "Coord", "Ajeno", List.of(RoleCode.COORDINATOR.name()));

        when(taskUserDirectoryGateway.findByEmail(INHERITED_COORDINATOR_EMAIL))
                .thenReturn(Mono.just(otherCoordinator));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(teamRepositoryGateway.findByTaskId(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.team()));

        StepVerifier.create(useCase.execute(INHERITED_COORDINATOR_EMAIL, TaskTestData.TASK_ID))
                .expectErrorMatches(error -> error instanceof CompiraException compiraException
                        && TaskErrorCode.ACTOR_NOT_COORDINATOR.equals(compiraException.getCode()))
                .verify();
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
