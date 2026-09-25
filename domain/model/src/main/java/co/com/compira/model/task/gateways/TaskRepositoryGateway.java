package co.com.compira.model.task.gateways;

import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskObservation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TaskRepositoryGateway {
    Mono<Task> save(Task task);

    Mono<Task> updateResponsible(UUID taskId, UUID responsibleUserId);

    Mono<Task> updateStatus(UUID taskId, String status);

    Mono<Task> findById(UUID taskId);

    Flux<Task> findByResponsible(UUID responsibleUserId);

    Flux<Task> findByCreator(UUID createdByUserId);

    Flux<Task> findAll();

    Mono<TaskObservation> saveObservation(TaskObservation observation);

    Flux<TaskObservation> findObservations(UUID taskId);

    Mono<TaskHistoryEntry> appendHistory(TaskHistoryEntry entry);

    Flux<TaskHistoryEntry> findHistory(UUID taskId);
}
