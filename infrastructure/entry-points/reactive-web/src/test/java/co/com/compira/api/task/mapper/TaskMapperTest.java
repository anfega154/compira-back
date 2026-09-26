package co.com.compira.api.task.mapper;

import co.com.compira.api.task.TaskApiTestData;
import co.com.compira.api.task.dto.AddObservationRequest;
import co.com.compira.api.task.dto.AssignTaskRequest;
import co.com.compira.api.task.dto.CancelTaskRequest;
import co.com.compira.api.task.dto.CreateTaskRequest;
import co.com.compira.api.task.dto.ReassignTaskRequest;
import co.com.compira.api.task.dto.TaskHistoryEntryResponse;
import co.com.compira.api.task.dto.TaskObservationResponse;
import co.com.compira.api.task.dto.TaskResponse;
import co.com.compira.api.task.dto.UpdateTaskStatusRequest;
import co.com.compira.model.task.AddTaskObservationCommand;
import co.com.compira.model.task.AssignTaskCommand;
import co.com.compira.model.task.CancelTaskCommand;
import co.com.compira.model.task.CreateTaskCommand;
import co.com.compira.model.task.ReassignTaskCommand;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.UpdateTaskStatusCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskMapperTest {
    private final TaskRequestMapper requestMapper = new TaskRequestMapper();
    private final TaskResponseMapper responseMapper = new TaskResponseMapper();

    @Test
    void shouldMapCreateRequestToCommand() {
        CreateTaskRequest request = new CreateTaskRequest("Titulo", "Detalle", TaskApiTestData.referenceDate(), "colab@compira.co", java.util.UUID.fromString("55555555-5555-5555-5555-555555555555"));
        CreateTaskCommand command = requestMapper.toCommand(TaskApiTestData.ACTOR_EMAIL, request);

        assertEquals(TaskApiTestData.ACTOR_EMAIL, command.actorEmail());
        assertEquals("Titulo", command.title());
        assertEquals("colab@compira.co", command.responsibleEmail());
    }

    @Test
    void shouldMapAssignRequestToCommand() {
        AssignTaskCommand command = requestMapper.toCommand(
                TaskApiTestData.ACTOR_EMAIL, TaskApiTestData.TASK_ID, new AssignTaskRequest("colab@compira.co"));
        assertEquals("colab@compira.co", command.responsibleEmail());
        assertEquals(TaskApiTestData.TASK_ID, command.taskId());
    }

    @Test
    void shouldMapReassignRequestToCommand() {
        ReassignTaskCommand command = requestMapper.toCommand(
                TaskApiTestData.ACTOR_EMAIL, TaskApiTestData.TASK_ID, new ReassignTaskRequest("nuevo@compira.co"));
        assertEquals("nuevo@compira.co", command.newResponsibleEmail());
    }

    @Test
    void shouldMapUpdateStatusRequestToCommand() {
        UpdateTaskStatusCommand command = requestMapper.toCommand(
                TaskApiTestData.ACTOR_EMAIL, TaskApiTestData.TASK_ID, new UpdateTaskStatusRequest("IN_PROGRESS"));
        assertEquals(TaskStatus.IN_PROGRESS, command.targetStatus());
    }

    @Test
    void shouldMapObservationRequestToCommand() {
        AddTaskObservationCommand command = requestMapper.toCommand(
                TaskApiTestData.ACTOR_EMAIL, TaskApiTestData.TASK_ID, new AddObservationRequest("Nota"));
        assertEquals("Nota", command.content());
    }

    @Test
    void shouldMapCancelRequestToCommand() {
        CancelTaskCommand command = requestMapper.toCommand(
                TaskApiTestData.ACTOR_EMAIL, TaskApiTestData.TASK_ID, new CancelTaskRequest("Motivo"));
        assertEquals("Motivo", command.reason());
    }

    @Test
    void shouldMapTaskToResponse() {
        TaskResponse response = responseMapper.toResponse(TaskApiTestData.task(TaskStatus.PENDING));
        assertEquals(TaskApiTestData.TASK_ID, response.id());
        assertEquals("PENDING", response.status());
    }

    @Test
    void shouldMapObservationToResponse() {
        TaskObservationResponse response = responseMapper.toResponse(TaskApiTestData.observation());
        assertEquals("Avance del 50%", response.content());
    }

    @Test
    void shouldMapHistoryEntryToResponse() {
        TaskHistoryEntryResponse response = responseMapper.toResponse(TaskApiTestData.historyEntry());
        assertEquals("STATUS_CHANGED", response.event());
        assertEquals("PENDING", response.previousValue());
        assertEquals("IN_PROGRESS", response.newValue());
    }
}
