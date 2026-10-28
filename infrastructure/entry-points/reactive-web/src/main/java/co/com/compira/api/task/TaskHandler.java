package co.com.compira.api.task;

import co.com.compira.api.task.dto.AddObservationRequest;
import co.com.compira.api.task.dto.AssignTaskRequest;
import co.com.compira.api.task.dto.CancelTaskRequest;
import co.com.compira.api.task.dto.CreateTaskRequest;
import co.com.compira.api.task.dto.ReassignTaskRequest;
import co.com.compira.api.task.dto.UpdateTaskStatusRequest;
import co.com.compira.api.task.mapper.TaskRequestMapper;
import co.com.compira.api.task.mapper.TaskResponseMapper;
import co.com.compira.usecase.addtaskobservation.AddTaskObservationUseCase;
import co.com.compira.usecase.approvetask.ApproveTaskUseCase;
import co.com.compira.usecase.assigntask.AssignTaskUseCase;
import co.com.compira.usecase.canceltask.CancelTaskUseCase;
import co.com.compira.usecase.createtask.CreateTaskUseCase;
import co.com.compira.usecase.gettask.GetTaskUseCase;
import co.com.compira.usecase.gettaskhistory.GetTaskHistoryUseCase;
import co.com.compira.usecase.gettaskobservations.GetTaskObservationsUseCase;
import co.com.compira.usecase.gettaskindicators.GetTaskIndicatorsUseCase;
import co.com.compira.usecase.gettaskreports.GetTaskReportsUseCase;
import co.com.compira.usecase.listassignedtasks.ListAssignedTasksUseCase;
import co.com.compira.usecase.listmanagedtasks.ListManagedTasksUseCase;
import co.com.compira.usecase.reassigntask.ReassignTaskUseCase;
import co.com.compira.usecase.updatetaskstatus.UpdateTaskStatusUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class TaskHandler {
    private final org.springframework.transaction.reactive.TransactionalOperator transactions;
    private final CreateTaskUseCase createTaskUseCase;
    private final AssignTaskUseCase assignTaskUseCase;
    private final ReassignTaskUseCase reassignTaskUseCase;
    private final UpdateTaskStatusUseCase updateTaskStatusUseCase;
    private final AddTaskObservationUseCase addTaskObservationUseCase;
    private final CancelTaskUseCase cancelTaskUseCase;
    private final ApproveTaskUseCase approveTaskUseCase;
    private final ListAssignedTasksUseCase listAssignedTasksUseCase;
    private final ListManagedTasksUseCase listManagedTasksUseCase;
    private final GetTaskHistoryUseCase getTaskHistoryUseCase;
    private final GetTaskObservationsUseCase getTaskObservationsUseCase;
    private final GetTaskIndicatorsUseCase getTaskIndicatorsUseCase;
    private final GetTaskReportsUseCase getTaskReportsUseCase;
    private final GetTaskUseCase getTaskUseCase;
    private final TaskRequestValidator taskRequestValidator;
    private final TaskRequestMapper taskRequestMapper;
    private final TaskResponseMapper taskResponseMapper;
    private final TaskErrorHandler taskErrorHandler;

    public TaskHandler(CreateTaskUseCase createTaskUseCase,
                       AssignTaskUseCase assignTaskUseCase,
                       ReassignTaskUseCase reassignTaskUseCase,
                       UpdateTaskStatusUseCase updateTaskStatusUseCase,
                       AddTaskObservationUseCase addTaskObservationUseCase,
                       CancelTaskUseCase cancelTaskUseCase,
                       ApproveTaskUseCase approveTaskUseCase,
                       ListAssignedTasksUseCase listAssignedTasksUseCase,
                       ListManagedTasksUseCase listManagedTasksUseCase,
                       GetTaskHistoryUseCase getTaskHistoryUseCase,
                       GetTaskObservationsUseCase getTaskObservationsUseCase,
                       GetTaskIndicatorsUseCase getTaskIndicatorsUseCase,
                       GetTaskReportsUseCase getTaskReportsUseCase,
                       GetTaskUseCase getTaskUseCase,
                       TaskRequestValidator taskRequestValidator,
                       TaskRequestMapper taskRequestMapper,
                       TaskResponseMapper taskResponseMapper,
                       TaskErrorHandler taskErrorHandler, org.springframework.transaction.reactive.TransactionalOperator transactions) {
        this.transactions = transactions;
        this.createTaskUseCase = createTaskUseCase;
        this.assignTaskUseCase = assignTaskUseCase;
        this.reassignTaskUseCase = reassignTaskUseCase;
        this.updateTaskStatusUseCase = updateTaskStatusUseCase;
        this.addTaskObservationUseCase = addTaskObservationUseCase;
        this.cancelTaskUseCase = cancelTaskUseCase;
        this.approveTaskUseCase = approveTaskUseCase;
        this.listAssignedTasksUseCase = listAssignedTasksUseCase;
        this.listManagedTasksUseCase = listManagedTasksUseCase;
        this.getTaskHistoryUseCase = getTaskHistoryUseCase;
        this.getTaskObservationsUseCase = getTaskObservationsUseCase;
        this.getTaskIndicatorsUseCase = getTaskIndicatorsUseCase;
        this.getTaskReportsUseCase = getTaskReportsUseCase;
        this.getTaskUseCase = getTaskUseCase;
        this.taskRequestValidator = taskRequestValidator;
        this.taskRequestMapper = taskRequestMapper;
        this.taskResponseMapper = taskResponseMapper;
        this.taskErrorHandler = taskErrorHandler;
    }

    public Mono<ServerResponse> createTask(ServerRequest serverRequest) {
        return actorEmail(serverRequest)
                .flatMap(actorEmail -> serverRequest.bodyToMono(CreateTaskRequest.class)
                        .flatMap(taskRequestValidator::validate)
                        .map(request -> taskRequestMapper.toCommand(actorEmail, request))
                        .flatMap(createTaskUseCase::execute)
                        .map(taskResponseMapper::toResponse)
                        .flatMap(response -> ServerResponse.status(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(response)))
                .as(transactions::transactional)
                .onErrorResume(taskErrorHandler::handle);
    }

    public Mono<ServerResponse> listManagedTasks(ServerRequest serverRequest) {
        return actorEmail(serverRequest)
                .flatMap(actorEmail -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(listManagedTasksUseCase.execute(actorEmail)
                                .map(taskResponseMapper::toResponse), Object.class))
                .onErrorResume(taskErrorHandler::handle);
    }

    public Mono<ServerResponse> getIndicators(ServerRequest serverRequest) {
        return actorEmail(serverRequest)
                .flatMap(getTaskIndicatorsUseCase::execute)
                .map(taskResponseMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(taskErrorHandler::handle);
    }

    public Mono<ServerResponse> getReports(ServerRequest serverRequest) {
        return actorEmail(serverRequest)
                .flatMap(getTaskReportsUseCase::execute)
                .map(taskResponseMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(taskErrorHandler::handle);
    }

    public Mono<ServerResponse> getTask(ServerRequest serverRequest) {
        return Mono.zip(actorEmail(serverRequest), taskIdMono(serverRequest))
                .flatMap(context -> getTaskUseCase.execute(context.getT1(), context.getT2()))
                .map(taskResponseMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(taskErrorHandler::handle);
    }

    public Mono<ServerResponse> listAssignedTasks(ServerRequest serverRequest) {
        return actorEmail(serverRequest)
                .flatMap(actorEmail -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(listAssignedTasksUseCase.execute(actorEmail)
                                .map(taskResponseMapper::toResponse), Object.class))
                .onErrorResume(taskErrorHandler::handle);
    }

    public Mono<ServerResponse> assignTask(ServerRequest serverRequest) {
        return Mono.zip(actorEmail(serverRequest), taskIdMono(serverRequest))
                .flatMap(context -> serverRequest.bodyToMono(AssignTaskRequest.class)
                        .flatMap(taskRequestValidator::validate)
                        .map(request -> taskRequestMapper.toCommand(context.getT1(), context.getT2(), request))
                        .flatMap(assignTaskUseCase::execute)
                        .map(taskResponseMapper::toResponse)
                        .flatMap(response -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(response)))
                .as(transactions::transactional)
                .onErrorResume(taskErrorHandler::handle);
    }

    public Mono<ServerResponse> reassignTask(ServerRequest serverRequest) {
        return Mono.zip(actorEmail(serverRequest), taskIdMono(serverRequest))
                .flatMap(context -> serverRequest.bodyToMono(ReassignTaskRequest.class)
                        .flatMap(taskRequestValidator::validate)
                        .map(request -> taskRequestMapper.toCommand(context.getT1(), context.getT2(), request))
                        .flatMap(reassignTaskUseCase::execute)
                        .map(taskResponseMapper::toResponse)
                        .flatMap(response -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(response)))
                .as(transactions::transactional)
                .onErrorResume(taskErrorHandler::handle);
    }

    public Mono<ServerResponse> updateTaskStatus(ServerRequest serverRequest) {
        return Mono.zip(actorEmail(serverRequest), taskIdMono(serverRequest))
                .flatMap(context -> serverRequest.bodyToMono(UpdateTaskStatusRequest.class)
                        .flatMap(taskRequestValidator::validate)
                        .map(request -> taskRequestMapper.toCommand(context.getT1(), context.getT2(), request))
                        .flatMap(updateTaskStatusUseCase::execute)
                        .map(taskResponseMapper::toResponse)
                        .flatMap(response -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(response)))
                .onErrorResume(taskErrorHandler::handle);
    }

    public Mono<ServerResponse> cancelTask(ServerRequest serverRequest) {
        return Mono.zip(actorEmail(serverRequest), taskIdMono(serverRequest))
                .flatMap(context -> serverRequest.bodyToMono(CancelTaskRequest.class)
                        .defaultIfEmpty(new CancelTaskRequest(null))
                        .flatMap(taskRequestValidator::validate)
                        .map(request -> taskRequestMapper.toCommand(context.getT1(), context.getT2(), request))
                        .flatMap(cancelTaskUseCase::execute)
                        .map(taskResponseMapper::toResponse)
                        .flatMap(response -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(response)))
                .onErrorResume(taskErrorHandler::handle);
    }

    public Mono<ServerResponse> approveTask(ServerRequest serverRequest) {
        return Mono.zip(actorEmail(serverRequest), taskIdMono(serverRequest))
                .map(context -> taskRequestMapper.toApproveCommand(context.getT1(), context.getT2()))
                .flatMap(approveTaskUseCase::execute)
                .map(taskResponseMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(taskErrorHandler::handle);
    }

    public Mono<ServerResponse> addObservation(ServerRequest serverRequest) {
        return Mono.zip(actorEmail(serverRequest), taskIdMono(serverRequest))
                .flatMap(context -> serverRequest.bodyToMono(AddObservationRequest.class)
                        .flatMap(taskRequestValidator::validate)
                        .map(request -> taskRequestMapper.toCommand(context.getT1(), context.getT2(), request))
                        .flatMap(addTaskObservationUseCase::execute)
                        .map(taskResponseMapper::toResponse)
                        .flatMap(response -> ServerResponse.status(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(response)))
                .onErrorResume(taskErrorHandler::handle);
    }

    public Mono<ServerResponse> listObservations(ServerRequest serverRequest) {
        return Mono.zip(actorEmail(serverRequest), taskIdMono(serverRequest))
                .flatMap(context -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(getTaskObservationsUseCase.execute(context.getT1(), context.getT2())
                                .map(taskResponseMapper::toResponse), Object.class))
                .onErrorResume(taskErrorHandler::handle);
    }

    public Mono<ServerResponse> listHistory(ServerRequest serverRequest) {
        return Mono.zip(actorEmail(serverRequest), taskIdMono(serverRequest))
                .flatMap(context -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(getTaskHistoryUseCase.execute(context.getT1(), context.getT2())
                                .map(taskResponseMapper::toResponse), Object.class))
                .onErrorResume(taskErrorHandler::handle);
    }

    private Mono<String> actorEmail(ServerRequest serverRequest) {
        return serverRequest.principal().map(java.security.Principal::getName)
                .switchIfEmpty(Mono.error(new co.com.compira.model.common.error.CompiraException(
                        "SEC_001", "Autenticación requerida", co.com.compira.model.common.error.ErrorCategory.UNAUTHORIZED)));
    }

    private Mono<UUID> taskIdMono(ServerRequest serverRequest) {
        return Mono.fromCallable(() -> UUID.fromString(serverRequest.pathVariable(TaskRoute.TASK_ID_VARIABLE)))
                .onErrorMap(IllegalArgumentException.class, error -> new co.com.compira.model.common.error.CompiraException(
                        co.com.compira.model.task.TaskErrorCode.INVALID_TASK_REQUEST,
                        co.com.compira.api.task.TaskValidationMessage.TASK_ID_INVALID,
                        co.com.compira.model.common.error.ErrorCategory.BAD_REQUEST));
    }
}
