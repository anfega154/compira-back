package co.com.compira.usecase.canceltask;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.CancelTaskCommand;
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

import java.util.UUID;

public class CancelTaskUseCase {
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskAuthorization taskAuthorization;

    public CancelTaskUseCase(TaskRepositoryGateway taskRepositoryGateway,
                             TaskUserDirectoryGateway taskUserDirectoryGateway) {
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Mono<Task> execute(CancelTaskCommand command) {
        return taskAuthorization.requireCoordinator(command.actorEmail())
                .flatMap(coordinator -> loadTask(command.taskId())
                        .flatMap(task -> cancel(coordinator, task, command.reason())));
    }

    private Mono<Task> cancel(TaskUser coordinator, Task task, String reason) {
        if (!TaskStatusPolicy.isCancellable(task.status())) {
            return Mono.error(new CompiraException(
                    TaskErrorCode.TASK_ALREADY_CLOSED,
                    TaskMessage.TASK_ALREADY_CLOSED,
                    ErrorCategory.CONFLICT));
        }

        return taskRepositoryGateway.updateStatus(task.id(), TaskStatus.CANCELLED.name())
                .flatMap(updatedTask -> taskRepositoryGateway.appendHistory(new TaskHistoryEntry(
                                null,
                                task.id(),
                                TaskHistoryEvent.CANCELLED,
                                coordinator.id(),
                                task.status().name(),
                                TaskStatus.CANCELLED.name(),
                                reason,
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
