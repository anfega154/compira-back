package co.com.compira.api.router;

import co.com.compira.api.task.TaskHandler;
import co.com.compira.api.task.TaskRoute;
import co.com.compira.api.task.dto.TaskHistoryEntryResponse;
import co.com.compira.api.task.dto.TaskObservationResponse;
import co.com.compira.api.task.dto.AddObservationRequest;
import co.com.compira.api.task.dto.UpdateTaskStatusRequest;
import co.com.compira.api.task.dto.TaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class TaskRouterRest {
    @Bean
    @RouterOperations({
        @RouterOperation(path = TaskRoute.API_V1 + TaskRoute.TASKS_BASE + TaskRoute.STATUS, method = RequestMethod.POST,
            beanClass = TaskHandler.class, beanMethod = "updateTaskStatus",
            operation = @Operation(operationId = "updateTaskStatusAndNotifyCoordinator", summary = "Actualizar estado como responsable; completar genera aviso al coordinador en la misma transacción",
                security = @SecurityRequirement(name = "bearerAuth"),
                parameters = @Parameter(name = "taskId", in = ParameterIn.PATH, required = true, schema = @Schema(type = "string", format = "uuid")),
                requestBody = @RequestBody(required = true, content = @Content(schema = @Schema(implementation = UpdateTaskStatusRequest.class))),
                responses = {@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = TaskResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Estado o identificador inválido"), @ApiResponse(responseCode = "401", description = "Autenticación requerida"),
                    @ApiResponse(responseCode = "403", description = "Colaborador responsable requerido"), @ApiResponse(responseCode = "404", description = "Tarea no encontrada"),
                    @ApiResponse(responseCode = "409", description = "Transición inválida o tarea sin equipo"), @ApiResponse(responseCode = "500", description = "La operación no se confirmó; se revierten sus cambios")})),
        @RouterOperation(path = TaskRoute.API_V1 + TaskRoute.TASKS_BASE + TaskRoute.HISTORY, method = RequestMethod.GET,
            beanClass = TaskHandler.class, beanMethod = "listHistory",
            operation = @Operation(operationId = "taskHistoryWithActors", summary = "Consultar historial con autor de cada acción; actor nulo identifica al sistema",
                security = @SecurityRequirement(name = "bearerAuth"),
                parameters = @Parameter(name = "taskId", in = ParameterIn.PATH, required = true, schema = @Schema(type = "string", format = "uuid")),
                responses = {@ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TaskHistoryEntryResponse.class)))),
                    @ApiResponse(responseCode = "401", description = "Autenticación requerida"), @ApiResponse(responseCode = "403", description = "Sin acceso al historial"), @ApiResponse(responseCode = "404", description = "Tarea no encontrada")})),
        @RouterOperation(path = TaskRoute.API_V1 + TaskRoute.TASKS_BASE + TaskRoute.OBSERVATIONS, method = RequestMethod.GET,
            beanClass = TaskHandler.class, beanMethod = "listObservations",
            operation = @Operation(operationId = "taskObservationsWithAuthors", summary = "Consultar observaciones y nombre/correo de sus autores; requiere acceso a la tarea",
                security = @SecurityRequirement(name = "bearerAuth"),
                parameters = @Parameter(name = "taskId", in = ParameterIn.PATH, required = true, schema = @Schema(type = "string", format = "uuid")),
                responses = {@ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TaskObservationResponse.class)))),
                    @ApiResponse(responseCode = "401", description = "Autenticación requerida"), @ApiResponse(responseCode = "403", description = "Sin acceso a la tarea"), @ApiResponse(responseCode = "404", description = "Tarea no encontrada")})),
        @RouterOperation(path = TaskRoute.API_V1 + TaskRoute.TASKS_BASE + TaskRoute.OBSERVATIONS, method = RequestMethod.POST,
            beanClass = TaskHandler.class, beanMethod = "addObservation",
            operation = @Operation(operationId = "addTaskObservationAndNotifyCoordinator", summary = "Guardar observación, historial y aviso al coordinador vigente de forma atómica",
                security = @SecurityRequirement(name = "bearerAuth"),
                parameters = @Parameter(name = "taskId", in = ParameterIn.PATH, required = true, schema = @Schema(type = "string", format = "uuid")),
                requestBody = @RequestBody(required = true, content = @Content(schema = @Schema(implementation = AddObservationRequest.class))),
                responses = {@ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = TaskObservationResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Observación inválida"), @ApiResponse(responseCode = "401", description = "Autenticación requerida"),
                    @ApiResponse(responseCode = "403", description = "Coordinador del equipo o colaborador responsable requerido"), @ApiResponse(responseCode = "404", description = "Tarea no encontrada"),
                    @ApiResponse(responseCode = "409", description = "La tarea no tiene equipo"), @ApiResponse(responseCode = "500", description = "No se confirmó la operación; se revierten sus cambios")}))
    })
    public RouterFunction<ServerResponse> taskRouterFunction(TaskHandler taskHandler, co.com.compira.usecase.teams.TeamsUseCase teams, co.com.compira.api.task.TaskErrorHandler errors) {
        return RouterFunctions.route()
                .path(TaskRoute.API_V1 + TaskRoute.TASKS_BASE, builder -> builder
                        .GET(TaskRoute.ASSIGNED, taskHandler::listAssignedTasks)
                        .GET(TaskRoute.INDICATORS, taskHandler::getIndicators)
                        .GET(TaskRoute.REPORTS, taskHandler::getReports)
                        .GET(TaskRoute.HISTORY, taskHandler::listHistory)
                        .GET(TaskRoute.OBSERVATIONS, taskHandler::listObservations)
                        .POST(TaskRoute.ASSIGN, taskHandler::assignTask)
                        .POST(TaskRoute.REASSIGN, taskHandler::reassignTask)
                        .POST(TaskRoute.STATUS, taskHandler::updateTaskStatus)
                        .POST(TaskRoute.CANCEL, taskHandler::cancelTask)
                        .POST(TaskRoute.APPROVE, taskHandler::approveTask)
                        .POST(TaskRoute.OBSERVATIONS, taskHandler::addObservation)
                        .GET(TaskRoute.BY_ID, taskHandler::getTask)
                        .GET("", taskHandler::listManagedTasks)
                        .POST("", taskHandler::createTask))
                .filter((request, next) -> {
                    String taskId = request.pathVariables().get(TaskRoute.TASK_ID_VARIABLE);
                    if (taskId == null) return next.handle(request);
                    return request.principal().flatMap(principal -> reactor.core.publisher.Mono.fromCallable(() -> java.util.UUID.fromString(taskId))
                                    .flatMap(id -> teams.requireTaskAccess(principal.getName(), id)))
                            .then(next.handle(request)).onErrorResume(errors::handle);
                })
                .build();
    }
}
