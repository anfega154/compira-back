package co.com.compira.api.auth;

import co.com.compira.api.auth.dto.ConfirmPasswordRecoveryRequest;
import co.com.compira.api.auth.dto.ConfirmUserRegistrationRequest;
import co.com.compira.api.auth.dto.LoginRequest;
import co.com.compira.api.auth.dto.RegisterUserRequest;
import co.com.compira.api.auth.dto.RespondAuthenticationChallengeRequest;
import co.com.compira.api.auth.dto.StartPasswordRecoveryRequest;
import co.com.compira.api.auth.mapper.AuthenticationRequestMapper;
import co.com.compira.api.auth.mapper.AuthenticationResponseMapper;
import co.com.compira.usecase.confirmpasswordrecovery.ConfirmPasswordRecoveryUseCase;
import co.com.compira.usecase.confirmuserregistration.ConfirmUserRegistrationUseCase;
import co.com.compira.usecase.login.LoginUseCase;
import co.com.compira.usecase.registeruser.RegisterUserUseCase;
import co.com.compira.usecase.respondauthenticationchallenge.RespondAuthenticationChallengeUseCase;
import co.com.compira.usecase.startpasswordrecovery.StartPasswordRecoveryUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class AuthenticationHandler {
    private final RegisterUserUseCase registerUserUseCase;
    private final ConfirmUserRegistrationUseCase confirmUserRegistrationUseCase;
    private final LoginUseCase loginUseCase;
    private final RespondAuthenticationChallengeUseCase respondAuthenticationChallengeUseCase;
    private final StartPasswordRecoveryUseCase startPasswordRecoveryUseCase;
    private final ConfirmPasswordRecoveryUseCase confirmPasswordRecoveryUseCase;
    private final AuthenticationRequestValidator authenticationRequestValidator;
    private final AuthenticationRequestMapper authenticationRequestMapper;
    private final AuthenticationResponseMapper authenticationResponseMapper;
    private final AuthenticationErrorHandler authenticationErrorHandler;

    public AuthenticationHandler(RegisterUserUseCase registerUserUseCase,
                                 ConfirmUserRegistrationUseCase confirmUserRegistrationUseCase,
                                 LoginUseCase loginUseCase,
                                 RespondAuthenticationChallengeUseCase respondAuthenticationChallengeUseCase,
                                 StartPasswordRecoveryUseCase startPasswordRecoveryUseCase,
                                 ConfirmPasswordRecoveryUseCase confirmPasswordRecoveryUseCase,
                                 AuthenticationRequestValidator authenticationRequestValidator,
                                 AuthenticationRequestMapper authenticationRequestMapper,
                                 AuthenticationResponseMapper authenticationResponseMapper,
                                 AuthenticationErrorHandler authenticationErrorHandler) {
        this.registerUserUseCase = registerUserUseCase;
        this.confirmUserRegistrationUseCase = confirmUserRegistrationUseCase;
        this.loginUseCase = loginUseCase;
        this.respondAuthenticationChallengeUseCase = respondAuthenticationChallengeUseCase;
        this.startPasswordRecoveryUseCase = startPasswordRecoveryUseCase;
        this.confirmPasswordRecoveryUseCase = confirmPasswordRecoveryUseCase;
        this.authenticationRequestValidator = authenticationRequestValidator;
        this.authenticationRequestMapper = authenticationRequestMapper;
        this.authenticationResponseMapper = authenticationResponseMapper;
        this.authenticationErrorHandler = authenticationErrorHandler;
    }

    public Mono<ServerResponse> registerUser(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(RegisterUserRequest.class)
                .flatMap(authenticationRequestValidator::validateRegisterUserRequest)
                .map(authenticationRequestMapper::toCommand)
                .flatMap(registerUserUseCase::execute)
                .map(authenticationResponseMapper::toResponse)
                .flatMap(response -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(authenticationErrorHandler::handle);
    }

    public Mono<ServerResponse> confirmUserRegistration(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(ConfirmUserRegistrationRequest.class)
                .flatMap(authenticationRequestValidator::validateConfirmRegistrationRequest)
                .map(authenticationRequestMapper::toCommand)
                .flatMap(confirmUserRegistrationUseCase::execute)
                .map(authenticationResponseMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(authenticationErrorHandler::handle);
    }

    public Mono<ServerResponse> login(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(LoginRequest.class)
                .flatMap(authenticationRequestValidator::validateLoginRequest)
                .map(authenticationRequestMapper::toCommand)
                .flatMap(loginUseCase::execute)
                .map(authenticationResponseMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(authenticationErrorHandler::handle);
    }

    public Mono<ServerResponse> respondAuthenticationChallenge(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(RespondAuthenticationChallengeRequest.class)
                .flatMap(authenticationRequestValidator::validateRespondChallengeRequest)
                .map(authenticationRequestMapper::toCommand)
                .flatMap(respondAuthenticationChallengeUseCase::execute)
                .map(authenticationResponseMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(authenticationErrorHandler::handle);
    }

    public Mono<ServerResponse> startPasswordRecovery(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(StartPasswordRecoveryRequest.class)
                .flatMap(authenticationRequestValidator::validateStartPasswordRecoveryRequest)
                .map(authenticationRequestMapper::toCommand)
                .flatMap(startPasswordRecoveryUseCase::execute)
                .map(authenticationResponseMapper::toResponse)
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response))
                .onErrorResume(authenticationErrorHandler::handle);
    }

    public Mono<ServerResponse> confirmPasswordRecovery(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(ConfirmPasswordRecoveryRequest.class)
                .flatMap(authenticationRequestValidator::validateConfirmPasswordRecoveryRequest)
                .map(authenticationRequestMapper::toCommand)
                .flatMap(confirmPasswordRecoveryUseCase::execute)
                .then(ServerResponse.noContent().build())
                .onErrorResume(authenticationErrorHandler::handle);
    }
}
