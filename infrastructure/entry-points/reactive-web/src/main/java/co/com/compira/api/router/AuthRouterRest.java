package co.com.compira.api;

import co.com.compira.api.auth.AuthenticationHandler;
import co.com.compira.api.auth.AuthenticationRoute;
import co.com.compira.api.auth.dto.ApplicationUserResponse;
import co.com.compira.api.auth.dto.AuthenticationResponse;
import co.com.compira.api.auth.dto.PasswordRecoveryResponse;
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
public class RouterRest {
    @RouterOperations({
            @RouterOperation(
                    path = "/api/v1/auth/register",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "registerUser",
                    method = RequestMethod.POST,
                    operation = @Operation(
                            summary = "Register a user",
                            responses = {
                                    @ApiResponse(responseCode = "201", description = "User registered", content = @Content(schema = @Schema(implementation = UserRegistrationResponse.class))),
                                    @ApiResponse(responseCode = "400", description = "Validation error")
                            }
                    )
            ),
            @RouterOperation(
                    path = "/api/v1/auth/register/confirm",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "confirmUserRegistration",
                    method = RequestMethod.POST,
                    operation = @Operation(
                            summary = "Confirm user registration",
                            responses = {
                                    @ApiResponse(responseCode = "200", description = "User confirmed", content = @Content(schema = @Schema(implementation = ApplicationUserResponse.class))),
                                    @ApiResponse(responseCode = "400", description = "Invalid code")
                            }
                    )
            ),
            @RouterOperation(
                    path = "/api/v1/auth/login",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "login",
                    method = RequestMethod.POST,
                    operation = @Operation(
                            summary = "Authenticate a user",
                            responses = {
                                    @ApiResponse(responseCode = "200", description = "Authentication result", content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
                                    @ApiResponse(responseCode = "401", description = "Authentication failed")
                            }
                    )
            ),
            @RouterOperation(
                    path = "/api/v1/auth/login/challenge",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "respondAuthenticationChallenge",
                    method = RequestMethod.POST,
                    operation = @Operation(
                            summary = "Respond to an authentication challenge",
                            responses = {
                                    @ApiResponse(responseCode = "200", description = "Authentication result", content = @Content(schema = @Schema(implementation = AuthenticationResponse.class))),
                                    @ApiResponse(responseCode = "400", description = "Invalid challenge")
                            }
                    )
            ),
            @RouterOperation(
                    path = "/api/v1/auth/password-recovery",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "startPasswordRecovery",
                    method = RequestMethod.POST,
                    operation = @Operation(
                            summary = "Start password recovery",
                            responses = {
                                    @ApiResponse(responseCode = "200", description = "Recovery code sent", content = @Content(schema = @Schema(implementation = PasswordRecoveryResponse.class)))
                            }
                    )
            ),
            @RouterOperation(
                    path = "/api/v1/auth/password-recovery/confirm",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "confirmPasswordRecovery",
                    method = RequestMethod.POST,
                    operation = @Operation(
                            summary = "Confirm password recovery",
                            responses = {
                                    @ApiResponse(responseCode = "204", description = "Password updated")
                            }
                    )
            ),
            @RouterOperation(
                    path = "/api/v2/auth/register",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "registerUser",
                    method = RequestMethod.POST
            ),
            @RouterOperation(
                    path = "/api/v2/auth/register/confirm",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "confirmUserRegistration",
                    method = RequestMethod.POST
            ),
            @RouterOperation(
                    path = "/api/v2/auth/login",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "login",
                    method = RequestMethod.POST
            ),
            @RouterOperation(
                    path = "/api/v2/auth/login/challenge",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "respondAuthenticationChallenge",
                    method = RequestMethod.POST
            ),
            @RouterOperation(
                    path = "/api/v2/auth/password-recovery",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "startPasswordRecovery",
                    method = RequestMethod.POST
            ),
            @RouterOperation(
                    path = "/api/v2/auth/password-recovery/confirm",
                    beanClass = AuthenticationHandler.class,
                    beanMethod = "confirmPasswordRecovery",
                    method = RequestMethod.POST
            )
    })
    @Bean
    public RouterFunction<ServerResponse> routerFunction(AuthenticationHandler authenticationHandler) {
        return RouterFunctions.route()
                .path(AuthenticationRoute.API_V1, builder -> builder
                        .path(AuthenticationRoute.AUTH_BASE, authBuilder -> authBuilder
                                .POST(AuthenticationRoute.REGISTER, authenticationHandler::registerUser)
                                .POST(AuthenticationRoute.REGISTER_CONFIRMATION, authenticationHandler::confirmUserRegistration)
                                .POST(AuthenticationRoute.LOGIN, authenticationHandler::login)
                                .POST(AuthenticationRoute.LOGIN_CHALLENGE, authenticationHandler::respondAuthenticationChallenge)
                                .POST(AuthenticationRoute.PASSWORD_RECOVERY, authenticationHandler::startPasswordRecovery)
                                .POST(AuthenticationRoute.PASSWORD_RECOVERY_CONFIRMATION, authenticationHandler::confirmPasswordRecovery)))
                .build();
    }
}
