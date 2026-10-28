package co.com.compira.usecase.gettaskobservations;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskObservation;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class GetTaskObservationsUseCase {
    private final TaskRepositoryGateway taskRepositoryGateway;
    private final TaskAuthorization taskAuthorization;

    public GetTaskObservationsUseCase(TaskRepositoryGateway taskRepositoryGateway,
                                      TaskUserDirectoryGateway taskUserDirectoryGateway) {
        this.taskRepositoryGateway = taskRepositoryGateway;
        this.taskAuthorization = new TaskAuthorization(taskUserDirectoryGateway);
    }

    public Flux<TaskObservation> execute(String actorEmail, UUID taskId) {
        return taskAuthorization.requireActor(actorEmail)
                .then(ensureTaskExists(taskId))
                .flatMapMany(taskRepositoryGateway::findObservations);
    }

    private Mono<UUID> ensureTaskExists(UUID taskId) {
        return taskRepositoryGateway.findById(taskId)
                .switchIfEmpty(Mono.error(new CompiraException(
                        TaskErrorCode.TASK_NOT_FOUND,
                        TaskMessage.TASK_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)))
                .map(task -> taskId);
    }
}
