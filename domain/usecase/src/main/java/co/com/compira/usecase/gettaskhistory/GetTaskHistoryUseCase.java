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
import co.com.compira.model.team.gateways.TeamRepositoryGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class GetTaskHistoryUseCase {
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TeamRepositoryGateway teamRepositoryGateway;
    private final TaskAuthorization taskAuthorization;

    public GetTaskHistoryUseCase(TaskRepositoryGateway taskRepositoryGateway,
                                 TeamRepositoryGateway teamRepositoryGateway,
                                 TaskUserDirectoryGateway taskUserDirectoryGateway) {
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.teamRepositoryGateway = teamRepositoryGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Flux<TaskHistoryEntry> execute(String actorEmail, UUID taskId) {
        return taskAuthorization.requireActor(actorEmail)
                .flatMap(actor -> loadTask(taskId).flatMap(task -> ensureScope(actor, task)))
                .flatMapMany(task -> taskRepositoryGateway.findHistory(task.id()));
    }

    private Mono<Task> ensureScope(TaskUser actor, Task task) {
        if (actor.hasRole(RoleCode.ADMINISTRATOR.name())) {
            return Mono.just(task);
        }
        if (actor.hasRole(RoleCode.COORDINATOR.name())) {
            return requireCurrentTeamCoordinator(actor, task);
        }
        return Mono.error(forbidden());
    }

    private Mono<Task> requireCurrentTeamCoordinator(TaskUser actor, Task task) {
        return teamRepositoryGateway.findByTaskId(task.id())
                .filter(team -> actor.id().equals(team.coordinatorUserId()))
                .map(team -> task)
                .switchIfEmpty(Mono.error(forbidden()));
    }

    private Mono<Task> loadTask(UUID taskId) {
        return taskRepositoryGateway.findById(taskId)
                .switchIfEmpty(Mono.error(new CompiraException(
                        TaskErrorCode.TASK_NOT_FOUND,
                        TaskMessage.TASK_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)));
    }

    private CompiraException forbidden() {
        return new CompiraException(
                TaskErrorCode.ACTOR_NOT_COORDINATOR,
                TaskMessage.ACTOR_NOT_COORDINATOR,
                ErrorCategory.FORBIDDEN);
    }
}
