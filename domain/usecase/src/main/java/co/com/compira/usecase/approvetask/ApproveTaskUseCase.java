package co.com.compira.usecase.approvetask;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.ApproveTaskCommand;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskHistoryEvent;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.TaskStatusPolicy;
import co.com.compira.model.task.TaskUser;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Mono;
import co.com.compira.usecase.teams.TeamsUseCase;

import java.util.UUID;

public class ApproveTaskUseCase {
    private final TeamsUseCase teams;
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskAuthorization taskAuthorization;

    public ApproveTaskUseCase(TaskRepositoryGateway taskRepositoryGateway,
                              TaskUserDirectoryGateway taskUserDirectoryGateway, TeamsUseCase teams) {
        this.teams = teams;
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Mono<Task> execute(ApproveTaskCommand command) {
        return taskAuthorization.requireCoordinator(command.actorEmail())
                .flatMap(coordinator -> teams.requireTaskCoordinator(command.taskId(), coordinator.id()).then(loadTask(command.taskId()))
                        .flatMap(task -> approve(coordinator, task)));
    }

    private Mono<Task> approve(TaskUser coordinator, Task task) {
        if (!TaskStatusPolicy.isClosable(task.status())) {
            return Mono.error(new CompiraException(
                    TaskErrorCode.TASK_NOT_COMPLETED,
                    TaskMessage.TASK_NOT_COMPLETED,
                    ErrorCategory.CONFLICT));
        }

        return taskRepositoryGateway.updateStatus(task.id(), TaskStatus.CLOSED.name())
                .flatMap(updatedTask -> taskRepositoryGateway.appendHistory(new TaskHistoryEntry(
                                null,
                                task.id(),
                                TaskHistoryEvent.CLOSED,
                                coordinator.id(),
                                TaskStatus.COMPLETED.name(),
                                TaskStatus.CLOSED.name(),
                                null,
                                null))
                        .thenReturn(updatedTask));
    }

    private Mono<Task> loadTask(UUID taskId) {
        return taskRepositoryGateway.findById(taskId)
                .switchIfEmpty(Mono.error(new CompiraException(
                        TaskErrorCode.TASK_NOT_FOUND,
                        TaskMessage.TASK_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)));
    }
}
