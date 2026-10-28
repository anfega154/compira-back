package co.com.compira.usecase.gettaskreports;

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

class GetTaskReportsUseCaseTest {
    private final TaskRepositoryGateway taskRepositoryGateway = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway taskUserDirectoryGateway = mock(TaskUserDirectoryGateway.class);
    private final TaskClockGateway taskClockGateway = TaskTestData::now;
    private final GetTaskReportsUseCase useCase =
            new GetTaskReportsUseCase(taskRepositoryGateway, taskUserDirectoryGateway, taskClockGateway);

    @Test
    void buildsPerAssigneeProductivityAndClosureRows() {
        Task closedOnTime = closed(TaskTestData.now().plusDays(1), TaskTestData.now());
        Task active = TaskTestData.task(TaskStatus.IN_PROGRESS);
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.administrator()));
        when(taskRepositoryGateway.findAll()).thenReturn(Flux.just(closedOnTime, active));
        when(taskUserDirectoryGateway.findByIds(any())).thenReturn(Flux.just(TaskTestData.collaborator()));

        StepVerifier.create(useCase.execute(TaskTestData.COORDINATOR_EMAIL))
                .assertNext(report -> {
                    if (report.rows().size() != 1) {
                        throw new AssertionError("Expected one assignee row");
                    }
                    var row = report.rows().get(0);
                    if (row.totalTasks() != 2 || row.closedTasks() != 1 || row.activeTasks() != 1) {
                        throw new AssertionError("Unexpected counts");
                    }
                    if (row.compliancePercentage() == null || row.compliancePercentage() != 100) {
                        throw new AssertionError("Expected 100 compliance");
                    }
                    if (row.averageClosureHours() == null) {
                        throw new AssertionError("Expected closure hours computed");
                    }
                })
                .verifyComplete();
    }

    @Test
    void forbidsCollaboratorFromReadingReports() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));

        StepVerifier.create(useCase.execute(TaskTestData.COLLABORATOR_EMAIL))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && failure.getErrorCategory() == ErrorCategory.FORBIDDEN)
                .verify();
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
                TaskTestData.now().minusDays(2),
                closedAt);
    }
}
