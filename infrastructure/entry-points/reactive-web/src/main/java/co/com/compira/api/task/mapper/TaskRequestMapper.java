package co.com.compira.api.task.mapper;

import co.com.compira.api.task.dto.AddObservationRequest;
import co.com.compira.api.task.dto.AssignTaskRequest;
import co.com.compira.api.task.dto.CancelTaskRequest;
import co.com.compira.api.task.dto.CreateTaskRequest;
import co.com.compira.api.task.dto.ReassignTaskRequest;
import co.com.compira.api.task.dto.UpdateTaskStatusRequest;
import co.com.compira.model.task.AddTaskObservationCommand;
import co.com.compira.model.task.ApproveTaskCommand;
import co.com.compira.model.task.AssignTaskCommand;
import co.com.compira.model.task.CancelTaskCommand;
import co.com.compira.model.task.CreateTaskCommand;
import co.com.compira.model.task.ReassignTaskCommand;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.UpdateTaskStatusCommand;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TaskRequestMapper {
    public CreateTaskCommand toCommand(String actorEmail, CreateTaskRequest request) {
        return new CreateTaskCommand(
                actorEmail,
                request.title(),
                request.description(),
                request.dueDate(),
                request.responsibleEmail());
    }

    public AssignTaskCommand toCommand(String actorEmail, UUID taskId, AssignTaskRequest request) {
        return new AssignTaskCommand(actorEmail, taskId, request.responsibleEmail());
    }

    public ReassignTaskCommand toCommand(String actorEmail, UUID taskId, ReassignTaskRequest request) {
        return new ReassignTaskCommand(actorEmail, taskId, request.newResponsibleEmail());
    }

    public UpdateTaskStatusCommand toCommand(String actorEmail, UUID taskId, UpdateTaskStatusRequest request) {
        return new UpdateTaskStatusCommand(actorEmail, taskId, TaskStatus.fromValue(request.status()));
    }

    public AddTaskObservationCommand toCommand(String actorEmail, UUID taskId, AddObservationRequest request) {
        return new AddTaskObservationCommand(actorEmail, taskId, request.content());
    }

    public CancelTaskCommand toCommand(String actorEmail, UUID taskId, CancelTaskRequest request) {
        return new CancelTaskCommand(actorEmail, taskId, request == null ? null : request.reason());
    }

    public ApproveTaskCommand toApproveCommand(String actorEmail, UUID taskId) {
        return new ApproveTaskCommand(actorEmail, taskId);
    }
}
