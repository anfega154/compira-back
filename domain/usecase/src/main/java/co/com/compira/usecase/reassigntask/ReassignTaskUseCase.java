package co.com.compira.usecase.reassigntask;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.ReassignTaskCommand;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskHistoryEvent;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskStatusPolicy;
import co.com.compira.model.task.TaskUser;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class ReassignTaskUseCase {
    private static final String NO_PREVIOUS_RESPONSIBLE = "<sin-responsable>";

    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskUserDirectoryGateway taskUserDirectoryGateway;
    private final TaskAuthorization taskAuthorization;

    public ReassignTaskUseCase(TaskRepositoryGateway taskRepositoryGateway,
                               TaskUserDirectoryGateway taskUserDirectoryGateway) {
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskUserDirectoryGateway = taskUserDirectoryGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Mono<Task> execute(ReassignTaskCommand command) {
        return taskAuthorization.requireCoordinator(command.actorEmail())
                .flatMap(coordinator -> loadTask(command.taskId())
                        .flatMap(task -> ensureReassignable(task)
                                .then(Mono.defer(() -> taskAuthorization.resolveCollaborator(command.newResponsibleEmail())))
                                .flatMap(newResponsible -> reassign(task, coordinator, newResponsible))));
    }

    private Mono<Task> reassign(Task task, TaskUser coordinator, TaskUser newResponsible) {
        return resolvePreviousResponsibleEmail(task.responsibleUserId())
                .flatMap(previousEmail -> taskRepositoryGateway.updateResponsible(task.id(), newResponsible.id())
                        .flatMap(updatedTask -> taskRepositoryGateway.appendHistory(new TaskHistoryEntry(
                                        null,
                                        task.id(),
                                        TaskHistoryEvent.REASSIGNED,
                                        coordinator.id(),
                                        previousEmail,
                                        newResponsible.email(),
                                        task.status().name(),
                                        null))
                                .thenReturn(updatedTask)));
    }

    private Mono<String> resolvePreviousResponsibleEmail(UUID previousResponsibleId) {
        if (previousResponsibleId == null) {
            return Mono.just(NO_PREVIOUS_RESPONSIBLE);
        }
        return taskUserDirectoryGateway.findById(previousResponsibleId)
                .map(TaskUser::email)
                .defaultIfEmpty(NO_PREVIOUS_RESPONSIBLE);
    }

    private Mono<Void> ensureReassignable(Task task) {
        return TaskStatusPolicy.isReassignable(task.status())
                ? Mono.empty()
                : Mono.error(new CompiraException(
                        TaskErrorCode.TASK_ALREADY_CLOSED,
                        TaskMessage.TASK_ALREADY_CLOSED,
                        ErrorCategory.CONFLICT));
    }

    private Mono<Task> loadTask(UUID taskId) {
        return taskRepositoryGateway.findById(taskId)
                .switchIfEmpty(Mono.error(new CompiraException(
                        TaskErrorCode.TASK_NOT_FOUND,
                        TaskMessage.TASK_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)));
    }
}
