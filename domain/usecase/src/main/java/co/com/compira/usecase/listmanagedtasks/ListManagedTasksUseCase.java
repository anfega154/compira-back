package co.com.compira.usecase.listmanagedtasks;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskUser;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Flux;

public class ListManagedTasksUseCase {
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskAuthorization taskAuthorization;

    public ListManagedTasksUseCase(TaskRepositoryGateway taskRepositoryGateway,
                                   TaskUserDirectoryGateway taskUserDirectoryGateway) {
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Flux<Task> execute(String actorEmail) {
        return taskAuthorization.requireActor(actorEmail)
                .flatMapMany(this::resolveScope);
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
}
