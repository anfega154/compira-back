package co.com.compira.usecase.createtask;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.CreateTaskCommand;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskHistoryEvent;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.TaskUser;
import co.com.compira.model.task.gateways.TaskClockGateway;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Mono;
import co.com.compira.usecase.teams.TeamsUseCase;
import co.com.compira.usecase.notifications.TaskNotificationsUseCase;
import co.com.compira.model.notification.NotificationType;

public class CreateTaskUseCase {
    private final TaskNotificationsUseCase notifications;
    private final TeamsUseCase teams;
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskClockGateway taskClockGateway;
    private final TaskAuthorization taskAuthorization;

    public CreateTaskUseCase(TaskRepositoryGateway taskRepositoryGateway,
                             TaskUserDirectoryGateway taskUserDirectoryGateway,
                             TaskClockGateway taskClockGateway, TaskNotificationsUseCase notifications, TeamsUseCase teams) {
        this.notifications = notifications;
        this.teams = teams;
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskClockGateway = taskClockGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Mono<Task> execute(CreateTaskCommand command) {
        if (command.dueDate() != null && command.dueDate().isBefore(taskClockGateway.now())) {
            return Mono.error(new CompiraException(
                    TaskErrorCode.DUE_DATE_IN_PAST,
                    TaskMessage.DUE_DATE_IN_PAST,
                    ErrorCategory.BAD_REQUEST));
        }

        boolean hasResponsible = command.responsibleEmail() != null && !command.responsibleEmail().isBlank();
        return taskAuthorization.requireCoordinator(command.actorEmail())
                .flatMap(coordinator -> teams.requireCoordinator(command.teamId(), coordinator.id()).then(Mono.defer(() -> hasResponsible
                        ? taskAuthorization.resolveCollaborator(command.responsibleEmail())
                                .flatMap(responsible -> persistTask(command, coordinator, responsible))
                        : persistTask(command, coordinator, null))));
    }

    private Mono<Task> persistTask(CreateTaskCommand command, TaskUser coordinator, TaskUser responsible) {
        Task newTask = new Task(
                null,
                command.title(),
                command.description(),
                command.dueDate(),
                TaskStatus.PENDING,
                responsible == null ? null : responsible.id(),
                coordinator.id(),
                null,
                null);

        return teams.requireMember(command.teamId(), responsible == null ? null : responsible.id()).then(taskRepositoryGateway.save(newTask))
                .flatMap(savedTask -> teams.linkNewTask(savedTask.id(), command.teamId()).then(taskRepositoryGateway.appendHistory(new TaskHistoryEntry(
                                null,
                                savedTask.id(),
                                TaskHistoryEvent.CREATED,
                                coordinator.id(),
                                null,
                                TaskStatus.PENDING.name(),
                                savedTask.title(),
                                null)))
                        .then(appendAssignmentHistory(savedTask, coordinator, responsible))
                        .thenReturn(savedTask));
    }

    private Mono<Void> appendAssignmentHistory(Task task, TaskUser coordinator, TaskUser responsible) {
        if (responsible == null) {
            return Mono.empty();
        }
        return taskRepositoryGateway.appendHistory(new TaskHistoryEntry(
                        null,
                        task.id(),
                        TaskHistoryEvent.ASSIGNED,
                        coordinator.id(),
                        null,
                        responsible.email(),
                        null,
                        null))
                .flatMap(event -> notifications.assignment(task, responsible.id(), NotificationType.ASSIGNED, event.id()));
    }
}
