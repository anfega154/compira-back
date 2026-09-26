package co.com.compira.usecase.addtaskobservation;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.AddTaskObservationCommand;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskHistoryEvent;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskObservation;
import co.com.compira.model.task.TaskUser;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class AddTaskObservationUseCase {
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskAuthorization taskAuthorization;

    public AddTaskObservationUseCase(TaskRepositoryGateway taskRepositoryGateway,
                                     TaskUserDirectoryGateway taskUserDirectoryGateway) {
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Mono<TaskObservation> execute(AddTaskObservationCommand command) {
        return taskAuthorization.requireActor(command.actorEmail())
                .flatMap(actor -> loadTask(command.taskId())
                        .flatMap(task -> registerObservation(actor, task, command.content())));
    }

    private Mono<TaskObservation> registerObservation(TaskUser actor, Task task, String content) {
        if (!actor.id().equals(task.responsibleUserId())) {
            return Mono.error(new CompiraException(
                    TaskErrorCode.ACTOR_NOT_RESPONSIBLE,
                    TaskMessage.ACTOR_NOT_RESPONSIBLE,
                    ErrorCategory.FORBIDDEN));
        }

        TaskObservation observation = new TaskObservation(null, task.id(), actor.id(), content, null);
        return taskRepositoryGateway.saveObservation(observation)
                .flatMap(savedObservation -> taskRepositoryGateway.appendHistory(new TaskHistoryEntry(
                                null,
                                task.id(),
                                TaskHistoryEvent.OBSERVATION_ADDED,
                                actor.id(),
                                null,
                                null,
                                content,
                                null))
                        .thenReturn(savedObservation));
    }

    private Mono<Task> loadTask(UUID taskId) {
        return taskRepositoryGateway.findById(taskId)
                .switchIfEmpty(Mono.error(new CompiraException(
                        TaskErrorCode.TASK_NOT_FOUND,
                        TaskMessage.TASK_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)));
    }
}
