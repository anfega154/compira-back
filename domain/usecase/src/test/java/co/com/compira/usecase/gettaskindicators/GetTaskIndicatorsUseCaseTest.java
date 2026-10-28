package co.com.compira.usecase.gettaskindicators;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.gateways.TaskClockGateway;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.usecase.task.TaskTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetTaskIndicatorsUseCaseTest {
    private final TaskRepositoryGateway taskRepositoryGateway = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway taskUserDirectoryGateway = mock(TaskUserDirectoryGateway.class);
    private final TaskClockGateway taskClockGateway = TaskTestData::now;
    private final GetTaskIndicatorsUseCase useCase =
            new GetTaskIndicatorsUseCase(taskRepositoryGateway, taskUserDirectoryGateway, taskClockGateway);

    @Test
    void computesOverdueDueSoonAndWorkloadForAdministratorScope() {
        Task overdue = TaskTestData.overdueTask(TaskStatus.IN_PROGRESS);
        Task dueSoon = dueWithinHours(10, TaskStatus.PENDING);
        Task later = TaskTestData.task(TaskStatus.PENDING);
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.administrator()));
        when(taskRepositoryGateway.findAll()).thenReturn(Flux.just(overdue, dueSoon, later));
        when(taskUserDirectoryGateway.findByIds(any())).thenReturn(Flux.just(TaskTestData.collaborator()));

        StepVerifier.create(useCase.execute(TaskTestData.COORDINATOR_EMAIL))
                .assertNext(indicators -> {
                    if (indicators.totalTasks() != 3) {
                        throw new AssertionError("Expected 3 tasks");
                    }
                    if (indicators.overdueCount() != 1) {
                        throw new AssertionError("Expected 1 overdue");
                    }
                    if (indicators.dueSoonCount() != 1) {
                        throw new AssertionError("Expected 1 due soon");
                    }
                    long workloadForCollaborator = indicators.workloadByAssignee().stream()
                            .filter(entry -> entry.assigneeId().equals(TaskTestData.COLLABORATOR_ID))
                            .mapToLong(entry -> entry.taskCount()).sum();
                    if (workloadForCollaborator != 3) {
                        throw new AssertionError("Expected workload 3 for collaborator");
                    }
                    boolean hasName = indicators.workloadByAssignee().stream()
                            .anyMatch(entry -> entry.assigneeEmail() != null && entry.assigneeEmail().equals(TaskTestData.COLLABORATOR_EMAIL));
                    if (!hasName) {
                        throw new AssertionError("Expected assignee email resolved");
                    }
                })
                .verifyComplete();
    }

    @Test
    void computesCompliancePercentageFromClosedTasks() {
        Task closedOnTime = closed(TaskTestData.now().plusDays(1), TaskTestData.now());
        Task closedLate = closed(TaskTestData.now().minusDays(1), TaskTestData.now());
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.administrator()));
        when(taskRepositoryGateway.findAll()).thenReturn(Flux.just(closedOnTime, closedLate));
        when(taskUserDirectoryGateway.findByIds(any())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(TaskTestData.COORDINATOR_EMAIL))
                .assertNext(indicators -> {
                    if (indicators.closedCount() != 2 || indicators.closedOnTimeCount() != 1) {
                        throw new AssertionError("Expected 2 closed, 1 on time");
                    }
                    if (indicators.compliancePercentage() == null || indicators.compliancePercentage() != 50) {
                        throw new AssertionError("Expected compliance 50");
                    }
                })
                .verifyComplete();
    }

    @Test
    void forbidsCollaboratorFromReadingIndicators() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));

        StepVerifier.create(useCase.execute(TaskTestData.COLLABORATOR_EMAIL))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && failure.getErrorCategory() == ErrorCategory.FORBIDDEN)
                .verify();
    }

    private Task dueWithinHours(int hours, TaskStatus status) {
        return new Task(
                java.util.UUID.randomUUID(),
                "Tarea próxima",
                "Detalle",
                TaskTestData.now().plusHours(hours),
                status,
                TaskTestData.COLLABORATOR_ID,
                TaskTestData.COORDINATOR_ID,
                TaskTestData.now(),
                TaskTestData.now());
    }

    private Task closed(java.time.OffsetDateTime dueDate, java.time.OffsetDateTime closedAt) {
        return new Task(
                java.util.UUID.randomUUID(),
                "Tarea cerrada",
                "Detalle",
                dueDate,
                TaskStatus.CLOSED,
                TaskTestData.COLLABORATOR_ID,
                TaskTestData.COORDINATOR_ID,
                TaskTestData.now().minusDays(3),
                closedAt);
    }
}
