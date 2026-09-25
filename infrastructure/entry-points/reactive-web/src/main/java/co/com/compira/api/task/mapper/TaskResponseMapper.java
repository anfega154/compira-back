package co.com.compira.api.task.mapper;

import co.com.compira.api.task.dto.TaskHistoryEntryResponse;
import co.com.compira.api.task.dto.TaskObservationResponse;
import co.com.compira.api.task.dto.TaskResponse;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskObservation;
import org.springframework.stereotype.Component;

@Component
public class TaskResponseMapper {
    public TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.id(),
                task.title(),
                task.description(),
                task.dueDate(),
                task.status().name(),
                task.responsibleUserId(),
                task.createdByUserId(),
                task.createdAt(),
                task.updatedAt());
    }

    public TaskObservationResponse toResponse(TaskObservation observation) {
        return new TaskObservationResponse(
                observation.id(),
                observation.taskId(),
                observation.authorUserId(),
                observation.content(),
                observation.createdAt());
    }

    public TaskHistoryEntryResponse toResponse(TaskHistoryEntry entry) {
        return new TaskHistoryEntryResponse(
                entry.id(),
                entry.taskId(),
                entry.event().name(),
                entry.actorUserId(),
                entry.previousValue(),
                entry.newValue(),
                entry.detail(),
                entry.createdAt());
    }
}
