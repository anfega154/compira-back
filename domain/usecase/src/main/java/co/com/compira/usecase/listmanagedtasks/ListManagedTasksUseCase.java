package co.com.compira.usecase.listmanagedtasks;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.TaskUser;
import co.com.compira.model.task.gateways.TaskClockGateway;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ListManagedTasksUseCase {
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskClockGateway taskClockGateway;
    private final TaskAuthorization taskAuthorization;

    public ListManagedTasksUseCase(TaskRepositoryGateway taskRepositoryGateway,
                                   TaskUserDirectoryGateway taskUserDirectoryGateway,
                                   TaskClockGateway taskClockGateway) {
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskClockGateway = taskClockGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Flux<Task> execute(String actorEmail) {
        return taskAuthorization.requireActor(actorEmail)
                .flatMapMany(this::resolveScope)
                .map(this::withDerivedStatus);
    }

    private Flux<Task> resolveScope(TaskUser actor) {
        if (actor.hasRole(RoleCode.ADMINISTRATOR.name())) {
            return taskRepositoryGateway.findAll();
        }
        if (actor.hasRole(RoleCode.COORDINATOR.name())) {
            return taskRepositoryGateway.findByCoordinator(actor.id());
        }
        return Flux.error(new CompiraException(
                TaskErrorCode.ACTOR_NOT_COORDINATOR,
                TaskMessage.ACTOR_NOT_COORDINATOR,
                ErrorCategory.FORBIDDEN));
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
