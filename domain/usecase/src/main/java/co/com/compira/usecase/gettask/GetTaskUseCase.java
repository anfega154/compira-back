package co.com.compira.usecase.gettask;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.gateways.TaskClockGateway;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class GetTaskUseCase {
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskClockGateway taskClockGateway;
    private final TaskAuthorization taskAuthorization;

    public GetTaskUseCase(TaskRepositoryGateway taskRepositoryGateway,
                          TaskUserDirectoryGateway taskUserDirectoryGateway,
                          TaskClockGateway taskClockGateway) {
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskClockGateway = taskClockGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Mono<Task> execute(String actorEmail, UUID taskId) {
        return taskAuthorization.requireActor(actorEmail)
                .then(taskRepositoryGateway.findById(taskId))
                .switchIfEmpty(Mono.error(new CompiraException(
                        TaskErrorCode.TASK_NOT_FOUND,
                        TaskMessage.TASK_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)))
                .map(this::withDerivedStatus);
    }

    private Task withDerivedStatus(Task task) {
        if (task.isOverdue(taskClockGateway.now())) {
            return new Task(
                    task.id(),
                    task.title(),
                    task.description(),
                    task.dueDate(),
                    TaskStatus.DELAYED,
                    task.responsibleUserId(),
                    task.createdByUserId(),
                    task.createdAt(),
                    task.updatedAt());
        }
        return task;
    }
}
