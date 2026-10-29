package co.com.compira.usecase.addtaskobservation;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.*;
import co.com.compira.model.task.gateways.*;
import co.com.compira.model.team.Team;
import co.com.compira.model.team.gateways.TeamRepositoryGateway;
import co.com.compira.usecase.task.TaskTestData;
import co.com.compira.usecase.teams.TeamsUseCase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AddTaskObservationAccessTest {
    private final TaskRepositoryGateway tasks = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway users = mock(TaskUserDirectoryGateway.class);
    private final TeamRepositoryGateway teams = mock(TeamRepositoryGateway.class);
    private final co.com.compira.usecase.notifications.TaskNotificationsUseCase notifications = mock(co.com.compira.usecase.notifications.TaskNotificationsUseCase.class);
    private final AddTaskObservationUseCase useCase = new AddTaskObservationUseCase(tasks, users, new TeamsUseCase(teams, tasks, users), notifications);

    @org.junit.jupiter.api.BeforeEach
    void configureNotifications() {
        when(notifications.coordinator(any(), any(), any())).thenReturn(Mono.empty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRATOR", "COORDINATOR", "COLLABORATOR"})
    void persistsAuthorizedActorAndObservationHistory(String role) {
        var actor = switch (role) {
            case "ADMINISTRATOR" -> TaskTestData.administrator();
            case "COORDINATOR" -> TaskTestData.coordinator();
            default -> TaskTestData.collaborator();
        };
        when(users.findByEmail(actor.email())).thenReturn(Mono.just(actor));
        when(tasks.findById(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.task(TaskStatus.IN_PROGRESS)));
        when(teams.findByTaskId(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.team()));
        when(tasks.saveObservation(any())).thenAnswer(call -> Mono.just(call.getArgument(0, TaskObservation.class)));
        when(tasks.appendHistory(any())).thenAnswer(call -> Mono.just(call.getArgument(0, TaskHistoryEntry.class)));
        StepVerifier.create(useCase.execute(new AddTaskObservationCommand(actor.email(), TaskTestData.TASK_ID, "Nota")))
                .assertNext(saved -> { assertEquals(actor.id(), saved.authorUserId()); assertEquals("Nota", saved.content()); })
                .verifyComplete();
        verify(notifications).coordinator(argThat(task -> task.id().equals(TaskTestData.TASK_ID)), eq(co.com.compira.model.notification.NotificationType.OBSERVATION_ADDED), any());
        verify(tasks).appendHistory(argThat(entry -> entry.actorUserId().equals(actor.id())
                && entry.event() == TaskHistoryEvent.OBSERVATION_ADDED && entry.detail().equals("Nota")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"COLLABORATOR", "COORDINATOR"})
    void rejectsUnrelatedCollaboratorAndFormerCoordinatorWithoutWriting(String role) {
        var actor = role.equals("COLLABORATOR") ? TaskTestData.otherCollaborator() : TaskTestData.coordinator();
        when(users.findByEmail(actor.email())).thenReturn(Mono.just(actor));
        when(tasks.findById(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(teams.findByTaskId(TaskTestData.TASK_ID)).thenReturn(Mono.just(new Team(TaskTestData.TEAM_ID, "Operaciones", TaskTestData.OTHER_COLLABORATOR_ID, "other@example.test")));
        StepVerifier.create(useCase.execute(new AddTaskObservationCommand(actor.email(), TaskTestData.TASK_ID, "Nota")))
                .expectErrorMatches(error -> error instanceof CompiraException exception && exception.getErrorCategory() == ErrorCategory.FORBIDDEN)
                .verify();
        verifyNoInteractions(notifications);
        verify(tasks, never()).saveObservation(any());
        verify(tasks, never()).appendHistory(any());
    }
}
