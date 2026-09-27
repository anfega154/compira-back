package co.com.compira.api.router;

import co.com.compira.api.notification.NotificationHandler;
import co.com.compira.api.team.TeamHandler;
import co.com.compira.api.team.TeamResponse;
import co.com.compira.api.notification.NotificationResponse;
import co.com.compira.api.organization.OrganizationSettingsHandler;
import co.com.compira.api.organization.OrganizationSettingsRequest;
import co.com.compira.api.organization.OrganizationSettingsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class NotificationRouterRest {
    @Bean
    @RouterOperations({
        @RouterOperation(path = TeamHandler.BASE, method = RequestMethod.GET, beanClass = TeamHandler.class, beanMethod = "list",
            operation = @Operation(operationId = "listTeam", summary = "Listar equipos visibles", security = @SecurityRequirement(name = "bearerAuth"),
                responses = {@ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TeamResponse.class)))),
                    @ApiResponse(responseCode = "400", description = "Solicitud inválida"), @ApiResponse(responseCode = "401", description = "Autenticación requerida"),
                    @ApiResponse(responseCode = "403", description = "Acceso no permitido"), @ApiResponse(responseCode = "404", description = "Equipo, usuario o tarea no encontrado"),
                    @ApiResponse(responseCode = "409", description = "Vinculación o rol incompatible"), @ApiResponse(responseCode = "500", description = "Error interno")})),
        @RouterOperation(path = TeamHandler.BASE, method = RequestMethod.POST, beanClass = TeamHandler.class, beanMethod = "create",
            operation = @Operation(operationId = "createTeam", summary = "Crear equipo (Administrador)", security = @SecurityRequirement(name = "bearerAuth"),
                requestBody = @RequestBody(required = true, content = @Content(schema = @Schema(implementation = TeamHandler.CreateTeamRequest.class))),
                responses = {@ApiResponse(responseCode = "201", content = @Content(schema = @Schema(implementation = TeamResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Solicitud inválida"), @ApiResponse(responseCode = "401", description = "Autenticación requerida"),
                    @ApiResponse(responseCode = "403", description = "Acceso no permitido"), @ApiResponse(responseCode = "404", description = "Equipo, usuario o tarea no encontrado"),
                    @ApiResponse(responseCode = "409", description = "Vinculación o rol incompatible"), @ApiResponse(responseCode = "500", description = "Error interno")})),
        @RouterOperation(path = TeamHandler.COORDINATOR, method = RequestMethod.PUT, beanClass = TeamHandler.class, beanMethod = "changeCoordinator",
            operation = @Operation(operationId = "changeCoordinatorTeam", summary = "Cambiar coordinador (Administrador)", security = @SecurityRequirement(name = "bearerAuth"),
                parameters = @Parameter(name = "teamId", in = ParameterIn.PATH, required = true, schema = @Schema(type = "string", format = "uuid")),
                requestBody = @RequestBody(required = true, content = @Content(schema = @Schema(implementation = TeamHandler.UserEmailRequest.class))),
                responses = {@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = TeamResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Solicitud inválida"), @ApiResponse(responseCode = "401", description = "Autenticación requerida"),
                    @ApiResponse(responseCode = "403", description = "Acceso no permitido"), @ApiResponse(responseCode = "404", description = "Equipo, usuario o tarea no encontrado"),
                    @ApiResponse(responseCode = "409", description = "Vinculación o rol incompatible"), @ApiResponse(responseCode = "500", description = "Error interno")})),
        @RouterOperation(path = TeamHandler.MEMBERS, method = RequestMethod.POST, beanClass = TeamHandler.class, beanMethod = "addMember",
            operation = @Operation(operationId = "addMemberTeam", summary = "Vincular colaborador sin equipo (Administrador o Coordinador del equipo)", security = @SecurityRequirement(name = "bearerAuth"),
                parameters = @Parameter(name = "teamId", in = ParameterIn.PATH, required = true, schema = @Schema(type = "string", format = "uuid")),
                requestBody = @RequestBody(required = true, content = @Content(schema = @Schema(implementation = TeamHandler.UserEmailRequest.class))),
                responses = {@ApiResponse(responseCode = "204", description = "Vinculación guardada"),
                    @ApiResponse(responseCode = "400", description = "Solicitud inválida"), @ApiResponse(responseCode = "401", description = "Autenticación requerida"),
                    @ApiResponse(responseCode = "403", description = "Acceso no permitido"), @ApiResponse(responseCode = "404", description = "Equipo, usuario o tarea no encontrado"),
                    @ApiResponse(responseCode = "409", description = "Vinculación o rol incompatible"), @ApiResponse(responseCode = "500", description = "Error interno")})),
        @RouterOperation(path = TeamHandler.TASKS, method = RequestMethod.POST, beanClass = TeamHandler.class, beanMethod = "linkTask",
            operation = @Operation(operationId = "linkTaskTeam", summary = "Vincular tarea existente sin equipo (Administrador o Coordinador del equipo)", security = @SecurityRequirement(name = "bearerAuth"),
                parameters = @Parameter(name = "teamId", in = ParameterIn.PATH, required = true, schema = @Schema(type = "string", format = "uuid")),
                requestBody = @RequestBody(required = true, content = @Content(schema = @Schema(implementation = TeamHandler.LinkTaskRequest.class))),
                responses = {@ApiResponse(responseCode = "204", description = "Vinculación guardada"),
                    @ApiResponse(responseCode = "400", description = "Solicitud inválida"), @ApiResponse(responseCode = "401", description = "Autenticación requerida"),
                    @ApiResponse(responseCode = "403", description = "Acceso no permitido"), @ApiResponse(responseCode = "404", description = "Equipo, usuario o tarea no encontrado"),
                    @ApiResponse(responseCode = "409", description = "Vinculación o rol incompatible"), @ApiResponse(responseCode = "500", description = "Error interno")})),
        @RouterOperation(path = NotificationHandler.BASE, method = RequestMethod.GET, beanClass = NotificationHandler.class, beanMethod = "list",
            operation = @Operation(operationId = "listNotifications", summary = "Consultar hasta 50 avisos propios, más recientes primero",
                security = @SecurityRequirement(name = "bearerAuth"),
                parameters = @Parameter(name = "before", in = ParameterIn.QUERY, description = "ID exclusivo del último aviso de la página anterior", schema = @Schema(type = "integer", format = "int64", minimum = "1")),
                responses = {@ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = NotificationResponse.class)))),
                    @ApiResponse(responseCode = "400", description = "Cursor inválido"), @ApiResponse(responseCode = "401", description = "Token inválido o vencido"),
                    @ApiResponse(responseCode = "403", description = "Rol no autorizado"), @ApiResponse(responseCode = "500", description = "Error interno")})),
        @RouterOperation(path = NotificationHandler.STREAM, method = RequestMethod.GET, beanClass = NotificationHandler.class, beanMethod = "stream",
            operation = @Operation(operationId = "streamNotifications", summary = "SSE autenticado de los últimos 50 avisos propios",
                description = "Evento notifications: arreglo JSON. Reconectar con Bearer válido; cierre al vencer el token. Sin entrega mientras el interruptor esté apagado.",
                security = @SecurityRequirement(name = "bearerAuth"),
                responses = {@ApiResponse(responseCode = "200", content = @Content(mediaType = "text/event-stream", array = @ArraySchema(schema = @Schema(implementation = NotificationResponse.class)))),
                    @ApiResponse(responseCode = "401", description = "Token inválido o vencido"), @ApiResponse(responseCode = "403", description = "Rol no autorizado"),
                    @ApiResponse(responseCode = "500", description = "Error interno; un fallo posterior a la conexión cierra el flujo")})),
        @RouterOperation(path = OrganizationSettingsHandler.BASE, method = RequestMethod.GET, beanClass = OrganizationSettingsHandler.class, beanMethod = "get",
            operation = @Operation(operationId = "getOrganizationSettings", summary = "Consultar configuración global (Administrador)", security = @SecurityRequirement(name = "bearerAuth"),
                responses = {@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = OrganizationSettingsResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Autenticación requerida"), @ApiResponse(responseCode = "403", description = "Administrador requerido"), @ApiResponse(responseCode = "500", description = "Error interno")})),
        @RouterOperation(path = OrganizationSettingsHandler.BASE, method = RequestMethod.PUT, beanClass = OrganizationSettingsHandler.class, beanMethod = "save",
            operation = @Operation(operationId = "saveOrganizationSettings", summary = "Guardar zona IANA e interruptor global (Administrador)", security = @SecurityRequirement(name = "bearerAuth"),
                requestBody = @RequestBody(required = true, content = @Content(schema = @Schema(implementation = OrganizationSettingsRequest.class))),
                responses = {@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = OrganizationSettingsResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Configuración inválida"), @ApiResponse(responseCode = "401", description = "Autenticación requerida"),
                    @ApiResponse(responseCode = "403", description = "Administrador requerido"), @ApiResponse(responseCode = "500", description = "Error interno")}))
    })
    public RouterFunction<ServerResponse> notificationRoutes(NotificationHandler notifications, OrganizationSettingsHandler settings, TeamHandler teams) {
        return RouterFunctions.route().GET(TeamHandler.BASE, teams::list).POST(TeamHandler.BASE, teams::create)
                .PUT(TeamHandler.COORDINATOR, teams::changeCoordinator).POST(TeamHandler.MEMBERS, teams::addMember)
                .POST(TeamHandler.TASKS, teams::linkTask).GET(NotificationHandler.STREAM, notifications::stream)
                .GET(NotificationHandler.BASE, notifications::list)
                .GET(OrganizationSettingsHandler.BASE, settings::get)
                .PUT(OrganizationSettingsHandler.BASE, settings::save).build();
    }
}
