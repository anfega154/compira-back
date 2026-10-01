package co.com.compira.api.router;

import co.com.compira.api.user.UserAdminHandler;
import co.com.compira.api.user.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class UserRouterRest {
    @Bean
    @RouterOperations({
            @RouterOperation(path = UserAdminHandler.BASE, method = RequestMethod.GET,
                    beanClass = UserAdminHandler.class, beanMethod = "list",
                    operation = @Operation(operationId = "listOrganizationUsers",
                            summary = "Listar usuarios de la organización (Administrador)",
                            description = "HU-40. Devuelve los usuarios de la única organización con sus roles y equipo.",
                            security = @SecurityRequirement(name = "bearerAuth"),
                            responses = {
                                    @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponse.class)))),
                                    @ApiResponse(responseCode = "401", description = "Autenticación requerida"),
                                    @ApiResponse(responseCode = "403", description = "Requiere rol Administrador"),
                                    @ApiResponse(responseCode = "500", description = "Error interno")})),
            @RouterOperation(path = UserAdminHandler.ROLES, method = RequestMethod.PUT,
                    beanClass = UserAdminHandler.class, beanMethod = "updateRoles",
                    operation = @Operation(operationId = "updateUserRoles",
                            summary = "Actualizar el conjunto de roles de un usuario (Administrador)",
                            description = "HU-10. El conjunto de roles no puede quedar vacío (DEC-004).",
                            security = @SecurityRequirement(name = "bearerAuth"),
                            requestBody = @RequestBody(required = true, content = @Content(schema = @Schema(implementation = UserAdminHandler.UpdateRolesRequest.class))),
                            responses = {
                                    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = UserResponse.class))),
                                    @ApiResponse(responseCode = "400", description = "Solicitud inválida o sin roles"),
                                    @ApiResponse(responseCode = "401", description = "Autenticación requerida"),
                                    @ApiResponse(responseCode = "403", description = "Requiere rol Administrador"),
                                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
                                    @ApiResponse(responseCode = "500", description = "Error interno")})),
            @RouterOperation(path = UserAdminHandler.PASSWORD_RESET, method = RequestMethod.POST,
                    beanClass = UserAdminHandler.class, beanMethod = "resetPassword",
                    operation = @Operation(operationId = "resetUserPassword",
                            summary = "Restablecer la contraseña temporal de un usuario (Administrador)",
                            description = "HU-10. Reemite una contraseña temporal; el usuario deberá cambiarla en el próximo ingreso.",
                            security = @SecurityRequirement(name = "bearerAuth"),
                            requestBody = @RequestBody(required = true, content = @Content(schema = @Schema(implementation = UserAdminHandler.ResetPasswordRequest.class))),
                            responses = {
                                    @ApiResponse(responseCode = "204", description = "Contraseña restablecida"),
                                    @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
                                    @ApiResponse(responseCode = "401", description = "Autenticación requerida"),
                                    @ApiResponse(responseCode = "403", description = "Requiere rol Administrador"),
                                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
                                    @ApiResponse(responseCode = "500", description = "Error interno")}))
    })
    public RouterFunction<ServerResponse> userRouterFunction(UserAdminHandler handler) {
        return RouterFunctions.route()
                .GET(UserAdminHandler.BASE, handler::list)
                .PUT(UserAdminHandler.ROLES, handler::updateRoles)
                .POST(UserAdminHandler.PASSWORD_RESET, handler::resetPassword)
                .build();
    }
}
