package co.com.compira.r2dbc.mapper;

import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskHistoryEvent;
import co.com.compira.model.task.TaskObservation;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.TaskUser;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class TaskDataMapper {
    public Task toTask(Map<String, Object> row) {
        return new Task(
                getUuid(row, "id"),
                getString(row, "title"),
                getString(row, "description"),
                getOffsetDateTime(row, "due_date"),
                TaskStatus.valueOf(getString(row, "status")),
                getUuid(row, "responsible_user_id"),
                getUuid(row, "created_by_user_id"),
                getOffsetDateTime(row, "created_at"),
                getOffsetDateTime(row, "updated_at"));
    }

    public TaskObservation toObservation(Map<String, Object> row) {
        return new TaskObservation(
                getUuid(row, "id"),
                getUuid(row, "task_id"),
                getUuid(row, "author_user_id"),
                getString(row, "content"),
                getOffsetDateTime(row, "created_at"));
    }

    public TaskHistoryEntry toHistoryEntry(Map<String, Object> row) {
        return new TaskHistoryEntry(
                getUuid(row, "id"),
                getUuid(row, "task_id"),
                TaskHistoryEvent.valueOf(getString(row, "event")),
                getUuid(row, "actor_user_id"),
                getString(row, "previous_value"),
                getString(row, "new_value"),
                getString(row, "detail"),
                getOffsetDateTime(row, "created_at"));
    }

    public TaskUser toTaskUser(Map<String, Object> row, List<String> roles) {
        return new TaskUser(
                getUuid(row, "id"),
                getString(row, "email"),
                getString(row, "first_name"),
                getString(row, "last_name"),
                roles);
    }

    private UUID getUuid(Map<String, Object> row, String key) {
        return (UUID) row.get(key);
    }

    private String getString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private OffsetDateTime getOffsetDateTime(Map<String, Object> row, String key) {
        return (OffsetDateTime) row.get(key);
    }
}
