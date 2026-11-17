package co.com.compira.api.router;

import co.com.compira.api.auth.AuthenticationHandler;
import co.com.compira.api.auth.AuthenticationRoute;
import co.com.compira.api.auth.dto.AuthenticationResponse;
import co.com.compira.api.auth.dto.PasswordRecoveryResponse;
import co.com.compira.api.auth.dto.ResendConfirmationCodeResponse;
import co.com.compira.api.auth.dto.UserRegistrationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class AuthRouterRest {
    @RouterOperations({
            @RouterOperation(
                    path = "/api/v1/auth/register",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "registerUser",
                    method = RequestMethod.POST,
                    operation = @Operation(
                            summary = "Registrar un usuario (solo administrador)",
                            responses = {
                                    @ApiResponse(responseCode = "201", description = "Usuario registrado", content = @Content(schema = @Schema(implementation = UserRegistrationResponse.class))),
                                    @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
                                    @ApiResponse(responseCode = "401", description = "Autenticación requerida"),
                                    @ApiResponse(responseCode = "403", description = "Requiere rol Administrador")
                            }
                    )
            ),
            @RouterOperation(
                    path = "/api/v1/auth/login",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "login",
                    method = RequestMethod.POST,
                    operation = @Operation(
                            summary = "Autenticar un usuario",
                            description = "Al completar la autenticación, activa el perfil local pendiente y registra el ingreso. Las cuentas deshabilitadas no pueden iniciar sesión.",
                            responses = {
                                    @ApiResponse(responseCode = "400", description = "Correo inválido: requiere dirección completa con dominio y extensión"),
                                    @ApiResponse(responseCode = "200", description = "Resultado de autenticación", content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
                                    @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos; no distingue cuentas inexistentes")
                            }
                    )
            ),
            @RouterOperation(
                    path = "/api/v1/auth/logout",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "logout",
                    method = RequestMethod.POST,
                    operation = @Operation(
                            summary = "Cerrar sesión",
                            responses = {
                                    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
                            }
                    )
            ),
            @RouterOperation(
                    path = "/api/v1/auth/login/challenge",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "respondAuthenticationChallenge",
                    method = RequestMethod.POST,
                    operation = @Operation(
                            summary = "Responder un reto de autenticación (OTP o cambio de contraseña obligatorio)",
                            description = "El perfil local pendiente se activa únicamente cuando no quedan retos y la autenticación se completa. Las cuentas deshabilitadas permanecen bloqueadas.",
                            responses = {
                                    @ApiResponse(responseCode = "200", description = "Resultado de autenticación", content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
                                    @ApiResponse(responseCode = "400", description = "Solicitud del reto inválida"),
                                    @ApiResponse(responseCode = "401", description = "Autenticación fallida")
                            }
                    )
            ),
            @RouterOperation(
                    path = "/api/v1/auth/login/resend-code",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "resendConfirmationCode",
                    method = RequestMethod.POST,
                    operation = @Operation(
                            summary = "Reenviar código OTP de inicio de sesión",
                            responses = {
                                    @ApiResponse(responseCode = "200", description = "Código reenviado", content = @Content(schema = @Schema(implementation = ResendConfirmationCodeResponse.class))),
                                    @ApiResponse(responseCode = "400", description = "Solicitud inválida"),
                                    @ApiResponse(responseCode = "404", description = "Usuario no encontrado"),
                                    @ApiResponse(responseCode = "429", description = "Demasiadas solicitudes")
                            }
                    )
            ),
            @RouterOperation(
                    path = "/api/v1/auth/password-recovery",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "startPasswordRecovery",
                    method = RequestMethod.POST,
                    operation = @Operation(
                            summary = "Iniciar recuperación de contraseña",
                            responses = {
                                    @ApiResponse(responseCode = "200", description = "Código de recuperación enviado", content = @Content(schema = @Schema(implementation = PasswordRecoveryResponse.class)))
                            }
                    )
            ),
            @RouterOperation(
                    path = "/api/v1/auth/password-recovery/confirm",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "confirmPasswordRecovery",
                    method = RequestMethod.POST,
                    operation = @Operation(
                            summary = "Confirmar recuperación de contraseña",
                            responses = {
                                    @ApiResponse(responseCode = "204", description = "Contraseña actualizada")
                            }
                    )
            )
    })
    @Bean
    public RouterFunction<ServerResponse> routerFunction(AuthenticationHandler authenticationHandler) {
        return RouterFunctions.route()
                .path(AuthenticationRoute.API_V1, builder -> builder
                        .path(AuthenticationRoute.AUTH_BASE, authBuilder -> authBuilder
                                .POST(AuthenticationRoute.REGISTER, authenticationHandler::registerUser)
                                .POST(AuthenticationRoute.LOGIN, authenticationHandler::login)
                                .POST(AuthenticationRoute.LOGOUT, authenticationHandler::logout)
                                .POST(AuthenticationRoute.LOGIN_CHALLENGE, authenticationHandler::respondAuthenticationChallenge)
                                .POST(AuthenticationRoute.RESEND_CONFIRMATION_CODE, authenticationHandler::resendConfirmationCode)
                                .POST(AuthenticationRoute.PASSWORD_RECOVERY, authenticationHandler::startPasswordRecovery)
                                .POST(AuthenticationRoute.PASSWORD_RECOVERY_CONFIRMATION, authenticationHandler::confirmPasswordRecovery)))
                .build();
    }
}
