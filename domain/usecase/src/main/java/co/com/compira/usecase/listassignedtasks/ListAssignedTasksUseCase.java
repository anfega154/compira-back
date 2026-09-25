package co.com.compira.usecase.listassignedtasks;

import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.gateways.TaskClockGateway;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Flux;

public class ListAssignedTasksUseCase {
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskClockGateway taskClockGateway;
    private final TaskAuthorization taskAuthorization;

    public ListAssignedTasksUseCase(TaskRepositoryGateway taskRepositoryGateway,
                                    TaskUserDirectoryGateway taskUserDirectoryGateway,
                                    TaskClockGateway taskClockGateway) {
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskClockGateway = taskClockGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Flux<Task> execute(String actorEmail) {
        return taskAuthorization.requireActor(actorEmail)
                .flatMapMany(actor -> taskRepositoryGateway.findByResponsible(actor.id()))
                .map(this::withDerivedStatus);
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
