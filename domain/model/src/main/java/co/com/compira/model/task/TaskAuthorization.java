package co.com.compira.model.task;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Mono;

public class TaskAuthorization {
    private final TaskUserDirectoryGateway taskUserDirectoryGateway;

    public TaskAuthorization(TaskUserDirectoryGateway taskUserDirectoryGateway) {
        this.taskUserDirectoryGateway = taskUserDirectoryGateway;
    }

    public Mono<TaskUser> requireCoordinator(String actorEmail) {
        return requireActor(actorEmail)
                .flatMap(actor -> actor.hasRole(RoleCode.COORDINATOR.name())
                        ? Mono.just(actor)
                        : Mono.error(new CompiraException(
                                TaskErrorCode.ACTOR_NOT_COORDINATOR,
                                TaskMessage.ACTOR_NOT_COORDINATOR,
                                ErrorCategory.FORBIDDEN)));
    }

    public Mono<TaskUser> requireActor(String actorEmail) {
        return taskUserDirectoryGateway.findByEmail(actorEmail)
                .switchIfEmpty(Mono.error(new CompiraException(
                        TaskErrorCode.ACTOR_NOT_FOUND,
                        TaskMessage.ACTOR_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)));
    }

    public Mono<TaskUser> resolveCollaborator(String email) {
        return taskUserDirectoryGateway.findByEmail(email)
                .switchIfEmpty(Mono.error(new CompiraException(
                        TaskErrorCode.RESPONSIBLE_NOT_FOUND,
                        TaskMessage.RESPONSIBLE_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)))
                .flatMap(candidate -> candidate.hasRole(RoleCode.COLLABORATOR.name())
                        ? Mono.just(candidate)
                        : Mono.error(new CompiraException(
                                TaskErrorCode.RESPONSIBLE_NOT_COLLABORATOR,
                                TaskMessage.RESPONSIBLE_NOT_COLLABORATOR,
                                ErrorCategory.BAD_REQUEST)));
    }
}
