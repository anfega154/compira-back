package co.com.compira.usecase.teams;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.model.team.gateways.TeamRepositoryGateway;
import co.com.compira.usecase.notifications.NotificationTestData;
import co.com.compira.usecase.task.TaskTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TeamsUseCaseTest {
    private final TeamRepositoryGateway teams = mock(TeamRepositoryGateway.class);
    private final TaskRepositoryGateway tasks = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway users = mock(TaskUserDirectoryGateway.class);
    private final TeamsUseCase useCase = new TeamsUseCase(teams, tasks, users);

    @Test
    void rejectsResponsibleAfterCollaboratorRoleIsRemoved() {
        var previous = TaskTestData.collaborator();
        var actor = new co.com.compira.model.task.TaskUser(previous.id(), previous.email(), previous.firstName(), previous.lastName(), java.util.List.of());
        when(users.findByEmail(actor.email())).thenReturn(Mono.just(actor));
        when(tasks.findById(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        StepVerifier.create(useCase.requireTaskAccess(actor.email(), TaskTestData.TASK_ID))
                .expectError(CompiraException.class).verify();
    }

    @Test
    void rejectsFormerCoordinatorAfterTeamCoordinatorChanges() {
        when(teams.findByTaskId(TaskTestData.TASK_ID)).thenReturn(Mono.just(NotificationTestData.team()));
        StepVerifier.create(useCase.requireTaskCoordinator(TaskTestData.TASK_ID, TaskTestData.COORDINATOR_ID))
                .expectError(CompiraException.class).verify();
        StepVerifier.create(useCase.requireTaskCoordinator(TaskTestData.TASK_ID, NotificationTestData.CURRENT_COORDINATOR)).verifyComplete();
    }

    @Test
    void rejectsUnrelatedCollaboratorFromReadingTaskDetails() {
        when(users.findByEmail(TaskTestData.OTHER_COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.otherCollaborator()));
        when(tasks.findById(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(teams.findByTaskId(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.team()));
        StepVerifier.create(useCase.requireTaskAccess(TaskTestData.OTHER_COLLABORATOR_EMAIL, TaskTestData.TASK_ID))
                .expectError(CompiraException.class).verify();
    }

    @Test
    void requiresAdministratorToCreateTeams() {
        when(users.findByEmail(TaskTestData.COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.collaborator()));
        when(users.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.coordinator()));
        StepVerifier.create(useCase.create(TaskTestData.COLLABORATOR_EMAIL, "Operaciones", TaskTestData.COORDINATOR_EMAIL))
                .expectError(CompiraException.class).verify();
        verifyNoInteractions(teams);
    }

    @Test
    void allowsAdministratorToCreateTeamWithValidCoordinator() {
        when(users.findByEmail("admin@compira.co")).thenReturn(Mono.just(TaskTestData.administrator()));
        when(users.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.coordinator()));
        when(teams.create("Operaciones", TaskTestData.COORDINATOR_ID)).thenReturn(Mono.just(TaskTestData.team()));
        StepVerifier.create(useCase.create("admin@compira.co", "Operaciones", TaskTestData.COORDINATOR_EMAIL))
                .expectNext(TaskTestData.team()).verifyComplete();
    }

    @Test
    void rejectsResponsiblesWhoDoNotBelongToTaskTeam() {
        when(teams.findByMemberId(TaskTestData.COLLABORATOR_ID)).thenReturn(Mono.empty());
        StepVerifier.create(useCase.requireMember(TaskTestData.TEAM_ID, TaskTestData.COLLABORATOR_ID))
                .expectError(CompiraException.class).verify();
    }
}
