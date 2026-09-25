package co.com.compira.usecase.gettaskhistory;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskUser;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class GetTaskHistoryUseCase {
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskAuthorization taskAuthorization;

    public GetTaskHistoryUseCase(TaskRepositoryGateway taskRepositoryGateway,
                                 TaskUserDirectoryGateway taskUserDirectoryGateway) {
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Flux<TaskHistoryEntry> execute(String actorEmail, UUID taskId) {
        return taskAuthorization.requireActor(actorEmail)
                .flatMap(actor -> loadTask(taskId).flatMap(task -> ensureScope(actor, task)))
                .flatMapMany(task -> taskRepositoryGateway.findHistory(task.id()));
    }

    private Mono<Task> ensureScope(TaskUser actor, Task task) {
        boolean isAdministrator = actor.hasRole(RoleCode.ADMINISTRATOR.name());
        boolean isOwningCoordinator = actor.hasRole(RoleCode.COORDINATOR.name())
                && actor.id().equals(task.createdByUserId());
        if (isAdministrator || isOwningCoordinator) {
            return Mono.just(task);
        }
        return Mono.error(new CompiraException(
                TaskErrorCode.ACTOR_NOT_COORDINATOR,
                TaskMessage.ACTOR_NOT_COORDINATOR,
                ErrorCategory.FORBIDDEN));
    }

    private Mono<Task> loadTask(UUID taskId) {
        return taskRepositoryGateway.findById(taskId)
                .switchIfEmpty(Mono.error(new CompiraException(
                        TaskErrorCode.TASK_NOT_FOUND,
                        TaskMessage.TASK_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)));
    }
}
