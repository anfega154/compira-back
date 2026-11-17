package co.com.compira.usecase.approvetask;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.task.ApproveTaskCommand;
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

class ApproveTaskUseCaseTest {
    private final co.com.compira.usecase.teams.TeamsUseCase teams = mock(co.com.compira.usecase.teams.TeamsUseCase.class);
    private final co.com.compira.usecase.notifications.TaskNotificationsUseCase notifications = mock(co.com.compira.usecase.notifications.TaskNotificationsUseCase.class);
    @org.junit.jupiter.api.BeforeEach
    void configureTeamAndNotifications() {
        when(teams.requireTaskCoordinator(any(), any())).thenReturn(Mono.empty());
        when(teams.requireTaskMember(any(), any())).thenReturn(Mono.empty());
        when(teams.requireMember(any(), any())).thenReturn(Mono.empty());
        when(teams.requireCoordinator(any(), any())).thenReturn(Mono.just(TaskTestData.team()));
        when(teams.linkNewTask(any(), any())).thenReturn(Mono.empty());
        when(notifications.assignment(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    private final TaskRepositoryGateway taskRepositoryGateway = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway taskUserDirectoryGateway = mock(TaskUserDirectoryGateway.class);
    private final ApproveTaskUseCase useCase = new ApproveTaskUseCase(taskRepositoryGateway, taskUserDirectoryGateway, teams);

    @Test
    void shouldCloseWhenTaskCompletedAndActorCoordinator() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.COMPLETED)));
        when(taskRepositoryGateway.updateStatus(eq(TaskTestData.TASK_ID), eq(TaskStatus.CLOSED.name())))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.CLOSED)));
        when(taskRepositoryGateway.appendHistory(any(TaskHistoryEntry.class)))
                .thenReturn(Mono.just(mock(TaskHistoryEntry.class)));

        StepVerifier.create(useCase.execute(new ApproveTaskCommand(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TASK_ID)))
                .assertNext(task -> assertStatus(task, TaskStatus.CLOSED))
                .verifyComplete();
    }

    @Test
    void shouldRejectWhenTaskNotCompleted() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(taskRepositoryGateway.findById(TaskTestData.TASK_ID))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.IN_PROGRESS)));

        StepVerifier.create(useCase.execute(new ApproveTaskCommand(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TASK_ID)))
                .expectErrorMatches(error -> error instanceof CompiraException compiraException
                        && TaskErrorCode.TASK_NOT_COMPLETED.equals(compiraException.getCode()))
                .verify();
    }

    private void assertStatus(Task task, TaskStatus expected) {
        if (task.status() != expected) {
            throw new AssertionError("Expected status " + expected);
        }
    }
}
