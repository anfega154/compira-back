package co.com.compira.usecase.assigntask;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.AssignTaskCommand;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskHistoryEvent;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskUser;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Mono;
import co.com.compira.usecase.teams.TeamsUseCase;
import co.com.compira.usecase.notifications.TaskNotificationsUseCase;
import co.com.compira.model.notification.NotificationType;

public class AssignTaskUseCase {
    private final TaskNotificationsUseCase notifications;
    private final TeamsUseCase teams;
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskAuthorization taskAuthorization;

    public AssignTaskUseCase(TaskRepositoryGateway taskRepositoryGateway,
                             TaskUserDirectoryGateway taskUserDirectoryGateway, TaskNotificationsUseCase notifications, TeamsUseCase teams) {
        this.notifications = notifications;
        this.teams = teams;
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Mono<Task> execute(AssignTaskCommand command) {
        return taskAuthorization.requireCoordinator(command.actorEmail())
                .flatMap(coordinator -> teams.requireTaskCoordinator(command.taskId(), coordinator.id()).then(loadTask(command.taskId()))
                        .flatMap(task -> ensureNotClosed(task)
                                .then(Mono.defer(() -> taskAuthorization.resolveCollaborator(command.responsibleEmail())))
                                .flatMap(responsible -> teams.requireTaskMember(task.id(), responsible.id()).then(Mono.defer(() -> assign(task, coordinator, responsible))))));
    }

    private Mono<Task> assign(Task task, TaskUser coordinator, TaskUser responsible) {
        return taskRepositoryGateway.updateResponsible(task.id(), responsible.id())
                .flatMap(updatedTask -> taskRepositoryGateway.appendHistory(new TaskHistoryEntry(
                                null,
                                task.id(),
                                TaskHistoryEvent.ASSIGNED,
                                coordinator.id(),
                                null,
                                responsible.email(),
                                null,
                                null))
                        .flatMap(event -> notifications.assignment(updatedTask, responsible.id(), NotificationType.ASSIGNED, event.id()))
                        .thenReturn(updatedTask));
    }

    private Mono<Void> ensureNotClosed(Task task) {
        return task.status().isTerminal()
                ? Mono.error(new CompiraException(
                        TaskErrorCode.TASK_ALREADY_CLOSED,
                        TaskMessage.TASK_ALREADY_CLOSED,
                        ErrorCategory.CONFLICT))
                : Mono.empty();
    }

    private Mono<Task> loadTask(java.util.UUID taskId) {
        return taskRepositoryGateway.findById(taskId)
                .switchIfEmpty(Mono.error(new CompiraException(
                        TaskErrorCode.TASK_NOT_FOUND,
                        TaskMessage.TASK_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)));
    }
}
