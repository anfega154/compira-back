package co.com.compira.api.auth;

import co.com.compira.api.auth.dto.ConfirmPasswordRecoveryRequest;
import co.com.compira.api.auth.dto.DeleteUserRequest;
import co.com.compira.api.auth.dto.LoginRequest;
import co.com.compira.api.auth.dto.LogoutRequest;
import co.com.compira.api.auth.dto.RegisterUserRequest;
import co.com.compira.api.auth.dto.ResendConfirmationCodeRequest;
import co.com.compira.api.auth.dto.RespondAuthenticationChallengeRequest;
import co.com.compira.api.auth.dto.StartPasswordRecoveryRequest;
import co.com.compira.model.auth.AuthenticationLogSanitizer;
import co.com.compira.api.auth.mapper.AuthenticationRequestMapper;
import co.com.compira.api.auth.mapper.AuthenticationResponseMapper;
import co.com.compira.usecase.confirmpasswordrecovery.ConfirmPasswordRecoveryUseCase;
import co.com.compira.usecase.deleteuser.DeleteUserUseCase;
import co.com.compira.usecase.login.LoginUseCase;
import co.com.compira.usecase.logout.LogoutUseCase;
import co.com.compira.usecase.registeruser.RegisterUserUseCase;
import co.com.compira.usecase.resendconfirmationcode.ResendConfirmationCodeUseCase;
import co.com.compira.usecase.respondauthenticationchallenge.RespondAuthenticationChallengeUseCase;
import co.com.compira.usecase.startpasswordrecovery.StartPasswordRecoveryUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@SuppressWarnings("java:S2068")
@Component
public class AuthenticationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationHandler.class);
    private static final String LOG_REGISTER_REQUEST = "Inicio solicitud de registro. email={}";
    private static final String LOG_REGISTER_SUCCESS = "Registro procesado correctamente. email={} cognitoSub={} status={}";
    private static final String LOG_LOGIN_REQUEST = "Inicio autenticación. email={}";
    private static final String LOG_LOGIN_SUCCESS = "Autenticación procesada. email={} resultado={}";
    private static final String LOG_LOGOUT_REQUEST = "Inicio cierre de sesión. accessToken={}";
    private static final String LOG_LOGOUT_SUCCESS = "Cierre de sesión completado. accessToken={}";
    private static final String LOG_CHALLENGE_REQUEST = "Inicio respuesta de reto. email={} challenge={} session={}";
    private static final String LOG_CHALLENGE_SUCCESS = "Reto procesado. email={} resultado={}";
    private static final String LOG_PASSWORD_RECOVERY_REQUEST = "Inicio recuperación de contraseña. email={}";
    private static final String LOG_PASSWORD_RECOVERY_SUCCESS = "Recuperación de contraseña iniciada. email={} medio={}";
    private static final String LOG_PASSWORD_RECOVERY_CONFIRM_REQUEST = "Inicio confirmación de recuperación de contraseña. email={}";
    private static final String LOG_PASSWORD_RECOVERY_CONFIRM_SUCCESS = "Confirmación de recuperación de contraseña completada. email={}";
    private static final String LOG_DELETE_USER_REQUEST = "Inicio eliminación de usuario. email={}";
    private static final String LOG_DELETE_USER_SUCCESS = "Eliminación de usuario completada. email={}";
    private static final String LOG_RESEND_CODE_REQUEST = "Inicio reenvío de código de confirmación. email={}";
    private static final String LOG_RESEND_CODE_SUCCESS = "Reenvío de código completado. email={} medio={}";

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final LogoutUseCase logoutUseCase;
    private final RespondAuthenticationChallengeUseCase respondAuthenticationChallengeUseCase;
    private final StartPasswordRecoveryUseCase startPasswordRecoveryUseCase;
    private final ConfirmPasswordRecoveryUseCase confirmPasswordRecoveryUseCase;
    private final DeleteUserUseCase deleteUserUseCase;
    private final ResendConfirmationCodeUseCase resendConfirmationCodeUseCase;
    private final AuthenticationRequestValidator authenticationRequestValidator;
    private final AuthenticationRequestMapper authenticationRequestMapper;
    private final AuthenticationResponseMapper authenticationResponseMapper;
    private final AuthenticationErrorHandler authenticationErrorHandler;

    public AuthenticationHandler(RegisterUserUseCase registerUserUseCase,
                                 LoginUseCase loginUseCase,
                                 LogoutUseCase logoutUseCase,
                                 RespondAuthenticationChallengeUseCase respondAuthenticationChallengeUseCase,
                                 StartPasswordRecoveryUseCase startPasswordRecoveryUseCase,
                                 ConfirmPasswordRecoveryUseCase confirmPasswordRecoveryUseCase,
                                 DeleteUserUseCase deleteUserUseCase,
                                 ResendConfirmationCodeUseCase resendConfirmationCodeUseCase,
                                 AuthenticationRequestValidator authenticationRequestValidator,
                                 AuthenticationRequestMapper authenticationRequestMapper,
                                 AuthenticationResponseMapper authenticationResponseMapper,
                                 AuthenticationErrorHandler authenticationErrorHandler) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.logoutUseCase = logoutUseCase;
        this.respondAuthenticationChallengeUseCase = respondAuthenticationChallengeUseCase;
        this.startPasswordRecoveryUseCase = startPasswordRecoveryUseCase;
        this.confirmPasswordRecoveryUseCase = confirmPasswordRecoveryUseCase;
        this.deleteUserUseCase = deleteUserUseCase;
        this.resendConfirmationCodeUseCase = resendConfirmationCodeUseCase;
        this.authenticationRequestValidator = authenticationRequestValidator;
        this.authenticationRequestMapper = authenticationRequestMapper;
        this.authenticationResponseMapper = authenticationResponseMapper;
        this.authenticationErrorHandler = authenticationErrorHandler;
    }

    public Mono<ServerResponse> registerUser(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(RegisterUserRequest.class)
                .doOnNext(request -> LOGGER.info(LOG_REGISTER_REQUEST, AuthenticationLogSanitizer.maskEmail(request.email())))
                .flatMap(request -> authenticationRequestValidator.validateRegisterUserRequest(request)
                        .map(authenticationRequestMapper::toCommand)
                        .flatMap(registerUserUseCase::execute)
                        .doOnNext(response -> LOGGER.info(
                                LOG_REGISTER_SUCCESS,
                                AuthenticationLogSanitizer.maskEmail(request.email()),
                                response.cognitoSub(),
                                response.userStatus()))
                        .map(authenticationResponseMapper::toResponse)
                        .flatMap(response -> ServerResponse.status(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(response)))
                .onErrorResume(authenticationErrorHandler::handle);
    }

    public Mono<ServerResponse> login(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(LoginRequest.class)
                .doOnNext(request -> LOGGER.info(LOG_LOGIN_REQUEST, AuthenticationLogSanitizer.maskEmail(request.email())))
                .flatMap(request -> authenticationRequestValidator.validateLoginRequest(request)
                        .map(authenticationRequestMapper::toCommand)
                        .flatMap(loginUseCase::execute)
                        .doOnNext(result -> LOGGER.info(
                                LOG_LOGIN_SUCCESS,
                                AuthenticationLogSanitizer.maskEmail(request.email()),
                                result.status()))
                        .map(authenticationResponseMapper::toResponse)
                        .flatMap(response -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(response)))
                .onErrorResume(authenticationErrorHandler::handle);
    }

    public Mono<ServerResponse> logout(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(LogoutRequest.class)
                .doOnNext(request -> LOGGER.info(
                        LOG_LOGOUT_REQUEST,
                        AuthenticationLogSanitizer.maskAccessToken(request.accessToken())))
                .flatMap(request -> authenticationRequestValidator.validateLogoutRequest(request)
                        .map(authenticationRequestMapper::toCommand)
                        .flatMap(logoutUseCase::execute)
                        .doOnSuccess(ignored -> LOGGER.info(
                                LOG_LOGOUT_SUCCESS,
                                AuthenticationLogSanitizer.maskAccessToken(request.accessToken())))
                        .then(ServerResponse.noContent().build()))
                .onErrorResume(authenticationErrorHandler::handle);
    }

    public Mono<ServerResponse> respondAuthenticationChallenge(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(RespondAuthenticationChallengeRequest.class)
                .doOnNext(request -> LOGGER.info(
                        LOG_CHALLENGE_REQUEST,
                        AuthenticationLogSanitizer.maskEmail(request.email()),
                        request.challengeName(),
                        AuthenticationLogSanitizer.maskSession(request.session())))
                .flatMap(request -> authenticationRequestValidator.validateRespondChallengeRequest(request)
                        .map(authenticationRequestMapper::toCommand)
                        .flatMap(respondAuthenticationChallengeUseCase::execute)
                        .doOnNext(result -> LOGGER.info(
                                LOG_CHALLENGE_SUCCESS,
                                AuthenticationLogSanitizer.maskEmail(request.email()),
                                result.status()))
                        .map(authenticationResponseMapper::toResponse)
                        .flatMap(response -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(response)))
                .onErrorResume(authenticationErrorHandler::handle);
    }

    public Mono<ServerResponse> startPasswordRecovery(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(StartPasswordRecoveryRequest.class)
                .doOnNext(request -> LOGGER.info(LOG_PASSWORD_RECOVERY_REQUEST, AuthenticationLogSanitizer.maskEmail(request.email())))
                .flatMap(request -> authenticationRequestValidator.validateStartPasswordRecoveryRequest(request)
                        .map(authenticationRequestMapper::toCommand)
                        .flatMap(startPasswordRecoveryUseCase::execute)
                        .doOnNext(result -> LOGGER.info(
                                LOG_PASSWORD_RECOVERY_SUCCESS,
                                AuthenticationLogSanitizer.maskEmail(request.email()),
                                result.codeDeliveryDetails() != null ? result.codeDeliveryDetails().deliveryMedium() : "<sin-medio>"))
                        .map(authenticationResponseMapper::toResponse)
                        .flatMap(response -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(response)))
                .onErrorResume(authenticationErrorHandler::handle);
    }

    public Mono<ServerResponse> confirmPasswordRecovery(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(ConfirmPasswordRecoveryRequest.class)
                .doOnNext(request -> LOGGER.info(LOG_PASSWORD_RECOVERY_CONFIRM_REQUEST, AuthenticationLogSanitizer.maskEmail(request.email())))
                .flatMap(request -> authenticationRequestValidator.validateConfirmPasswordRecoveryRequest(request)
                        .map(authenticationRequestMapper::toCommand)
                        .flatMap(confirmPasswordRecoveryUseCase::execute)
                        .doOnSuccess(ignored -> LOGGER.info(
                                LOG_PASSWORD_RECOVERY_CONFIRM_SUCCESS,
                                AuthenticationLogSanitizer.maskEmail(request.email())))
                        .then(ServerResponse.noContent().build()))
                .onErrorResume(authenticationErrorHandler::handle);
    }

    public Mono<ServerResponse> deleteUser(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(DeleteUserRequest.class)
                .doOnNext(request -> LOGGER.info(LOG_DELETE_USER_REQUEST, AuthenticationLogSanitizer.maskEmail(request.email())))
                .flatMap(request -> authenticationRequestValidator.validateDeleteUserRequest(request)
                        .map(authenticationRequestMapper::toCommand)
                        .flatMap(deleteUserUseCase::execute)
                        .doOnSuccess(ignored -> LOGGER.info(
                                LOG_DELETE_USER_SUCCESS,
                                AuthenticationLogSanitizer.maskEmail(request.email())))
                        .then(ServerResponse.noContent().build()))
                .onErrorResume(authenticationErrorHandler::handle);
    }

    public Mono<ServerResponse> resendConfirmationCode(ServerRequest serverRequest) {
        return serverRequest.bodyToMono(ResendConfirmationCodeRequest.class)
                .doOnNext(request -> LOGGER.info(LOG_RESEND_CODE_REQUEST, AuthenticationLogSanitizer.maskEmail(request.email())))
                .flatMap(request -> authenticationRequestValidator.validateResendConfirmationCodeRequest(request)
                        .map(authenticationRequestMapper::toCommand)
                        .flatMap(resendConfirmationCodeUseCase::execute)
                        .doOnNext(result -> LOGGER.info(
                                LOG_RESEND_CODE_SUCCESS,
                                AuthenticationLogSanitizer.maskEmail(request.email()),
                                result.codeDeliveryDetails() != null ? result.codeDeliveryDetails().deliveryMedium() : "<sin-medio>"))
                        .map(authenticationResponseMapper::toResponse)
                        .flatMap(response -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(response)))
                .onErrorResume(authenticationErrorHandler::handle);
    }
}
