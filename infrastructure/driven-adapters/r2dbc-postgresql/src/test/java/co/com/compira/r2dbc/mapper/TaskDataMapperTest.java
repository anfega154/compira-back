package co.com.compira.r2dbc.mapper;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskHistoryEvent;
import co.com.compira.model.task.TaskObservation;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.TaskUser;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaskDataMapperTest {
    private final TaskDataMapper mapper = new TaskDataMapper();

    private static final UUID TASK_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-24T10:00:00Z");

    @Test
    void shouldMapRowToTask() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", TASK_ID);
        row.put("title", "Preparar informe");
        row.put("description", "Detalle");
        row.put("due_date", NOW.plusDays(2));
        row.put("status", "IN_PROGRESS");
        row.put("responsible_user_id", USER_ID);
        row.put("created_by_user_id", USER_ID);
        row.put("created_at", NOW);
        row.put("updated_at", NOW);

        Task task = mapper.toTask(row);

        assertEquals(TASK_ID, task.id());
        assertEquals("Preparar informe", task.title());
        assertEquals(TaskStatus.IN_PROGRESS, task.status());
        assertEquals(USER_ID, task.responsibleUserId());
    }

    @Test
    void shouldMapRowWithNullOptionalFields() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", TASK_ID);
        row.put("title", "Sin descripcion");
        row.put("description", null);
        row.put("due_date", null);
        row.put("status", "PENDING");
        row.put("responsible_user_id", null);
        row.put("created_by_user_id", USER_ID);
        row.put("created_at", NOW);
        row.put("updated_at", NOW);

        Task task = mapper.toTask(row);

        assertNull(task.description());
        assertNull(task.dueDate());
        assertNull(task.responsibleUserId());
        assertEquals(TaskStatus.PENDING, task.status());
    }

    @Test
    void shouldMapRowToObservation() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", TASK_ID);
        row.put("task_id", TASK_ID);
        row.put("author_user_id", USER_ID);
        row.put("content", "Avance del 50%");
        row.put("created_at", NOW);

        TaskObservation observation = mapper.toObservation(row);

        assertEquals("Avance del 50%", observation.content());
        assertEquals(USER_ID, observation.authorUserId());
    }

    @Test
    void shouldMapRowToHistoryEntry() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", TASK_ID);
        row.put("task_id", TASK_ID);
        row.put("event", "STATUS_CHANGED");
        row.put("actor_user_id", USER_ID);
        row.put("previous_value", "PENDING");
        row.put("new_value", "IN_PROGRESS");
        row.put("detail", null);
        row.put("created_at", NOW);

        TaskHistoryEntry entry = mapper.toHistoryEntry(row);

        assertEquals(TaskHistoryEvent.STATUS_CHANGED, entry.event());
        assertEquals("PENDING", entry.previousValue());
        assertEquals("IN_PROGRESS", entry.newValue());
    }

    @Test
    void shouldMapRowToTaskUser() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", USER_ID);
        row.put("email", "user@compira.co");
        row.put("first_name", "User");
        row.put("last_name", "Test");

        TaskUser user = mapper.toTaskUser(row, List.of(RoleCode.COORDINATOR.name()));

        assertEquals(USER_ID, user.id());
        assertEquals("user@compira.co", user.email());
        assertEquals(true, user.hasRole(RoleCode.COORDINATOR.name()));
    }
}
