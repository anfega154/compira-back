package co.com.compira.usecase.updatetaskstatus;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskHistoryEvent;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.TaskStatusPolicy;
import co.com.compira.model.task.TaskUser;
import co.com.compira.model.task.UpdateTaskStatusCommand;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class UpdateTaskStatusUseCase {
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskAuthorization taskAuthorization;

    public UpdateTaskStatusUseCase(TaskRepositoryGateway taskRepositoryGateway,
                                   TaskUserDirectoryGateway taskUserDirectoryGateway) {
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Mono<Task> execute(UpdateTaskStatusCommand command) {
        return taskAuthorization.requireActor(command.actorEmail())
                .flatMap(actor -> loadTask(command.taskId())
                        .flatMap(task -> applyTransition(actor, task, command.targetStatus())));
    }

    private Mono<Task> applyTransition(TaskUser actor, Task task, TaskStatus targetStatus) {
        if (targetStatus == TaskStatus.CLOSED) {
            return Mono.error(new CompiraException(
                    TaskErrorCode.CLOSE_NOT_ALLOWED_FOR_COLLABORATOR,
                    TaskMessage.CLOSE_NOT_ALLOWED_FOR_COLLABORATOR,
                    ErrorCategory.FORBIDDEN));
        }
        if (!actor.id().equals(task.responsibleUserId())) {
            return Mono.error(new CompiraException(
                    TaskErrorCode.ACTOR_NOT_RESPONSIBLE,
                    TaskMessage.ACTOR_NOT_RESPONSIBLE,
                    ErrorCategory.FORBIDDEN));
        }
        if (!TaskStatusPolicy.isCollaboratorTransitionAllowed(task.status(), targetStatus)) {
            return Mono.error(new CompiraException(
                    TaskErrorCode.INVALID_STATUS_TRANSITION,
                    TaskMessage.INVALID_STATUS_TRANSITION,
                    ErrorCategory.CONFLICT));
        }

        return taskRepositoryGateway.updateStatus(task.id(), targetStatus.name())
                .flatMap(updatedTask -> taskRepositoryGateway.appendHistory(new TaskHistoryEntry(
                                null,
                                task.id(),
                                TaskHistoryEvent.STATUS_CHANGED,
                                actor.id(),
                                task.status().name(),
                                targetStatus.name(),
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
