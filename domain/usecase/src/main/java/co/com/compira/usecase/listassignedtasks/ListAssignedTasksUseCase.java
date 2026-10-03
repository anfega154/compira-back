package co.com.compira.usecase.listassignedtasks;

import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Flux;

public class ListAssignedTasksUseCase {
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskAuthorization taskAuthorization;

    public ListAssignedTasksUseCase(TaskRepositoryGateway taskRepositoryGateway,
                                    TaskUserDirectoryGateway taskUserDirectoryGateway) {
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Flux<Task> execute(String actorEmail) {
        return taskAuthorization.requireActor(actorEmail)
                .flatMapMany(actor -> taskRepositoryGateway.findByResponsible(actor.id()));
    }
}
