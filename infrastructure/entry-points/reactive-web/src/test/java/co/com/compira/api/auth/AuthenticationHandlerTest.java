package co.com.compira.api.auth;

import co.com.compira.api.auth.mapper.AuthenticationRequestMapper;
import co.com.compira.api.auth.mapper.AuthenticationResponseMapper;
import co.com.compira.usecase.confirmpasswordrecovery.ConfirmPasswordRecoveryUseCase;
import co.com.compira.usecase.confirmuserregistration.ConfirmUserRegistrationUseCase;
import co.com.compira.usecase.login.LoginUseCase;
import co.com.compira.usecase.registeruser.RegisterUserUseCase;
import co.com.compira.usecase.respondauthenticationchallenge.RespondAuthenticationChallengeUseCase;
import co.com.compira.usecase.startpasswordrecovery.StartPasswordRecoveryUseCase;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunctions;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationHandlerTest {
    private final RegisterUserUseCase registerUserUseCase = mock(RegisterUserUseCase.class);
    private final ConfirmUserRegistrationUseCase confirmUserRegistrationUseCase = mock(ConfirmUserRegistrationUseCase.class);
    private final LoginUseCase loginUseCase = mock(LoginUseCase.class);
    private final RespondAuthenticationChallengeUseCase respondAuthenticationChallengeUseCase = mock(RespondAuthenticationChallengeUseCase.class);
    private final StartPasswordRecoveryUseCase startPasswordRecoveryUseCase = mock(StartPasswordRecoveryUseCase.class);
    private final ConfirmPasswordRecoveryUseCase confirmPasswordRecoveryUseCase = mock(ConfirmPasswordRecoveryUseCase.class);
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        AuthenticationHandler authenticationHandler = new AuthenticationHandler(
                registerUserUseCase,
                confirmUserRegistrationUseCase,
                loginUseCase,
                respondAuthenticationChallengeUseCase,
                startPasswordRecoveryUseCase,
                confirmPasswordRecoveryUseCase,
                new AuthenticationRequestValidator(Validation.buildDefaultValidatorFactory().getValidator()),
                new AuthenticationRequestMapper(),
                new AuthenticationResponseMapper(),
                new AuthenticationErrorHandler());

        webTestClient = WebTestClient.bindToRouterFunction(RouterFunctions.route()
                        .POST(AuthenticationRoute.API_V1 + AuthenticationRoute.AUTH_BASE + AuthenticationRoute.REGISTER, authenticationHandler::registerUser)
                        .POST(AuthenticationRoute.API_V1 + AuthenticationRoute.AUTH_BASE + AuthenticationRoute.LOGIN, authenticationHandler::login)
                        .POST(AuthenticationRoute.API_V1 + AuthenticationRoute.AUTH_BASE + AuthenticationRoute.LOGIN_CHALLENGE, authenticationHandler::respondAuthenticationChallenge)
                        .POST(AuthenticationRoute.API_V1 + AuthenticationRoute.AUTH_BASE + AuthenticationRoute.PASSWORD_RECOVERY, authenticationHandler::startPasswordRecovery)
                        .POST(AuthenticationRoute.API_V1 + AuthenticationRoute.AUTH_BASE + AuthenticationRoute.PASSWORD_RECOVERY_CONFIRMATION, authenticationHandler::confirmPasswordRecovery)
                        .build())
                .build();
    }

    @Test
    void shouldRegisterUser() {
        when(registerUserUseCase.execute(any())).thenReturn(Mono.just(AuthenticationApiTestData.userRegistrationResult()));

        webTestClient.post()
                .uri(AuthenticationRoute.API_V1 + AuthenticationRoute.AUTH_BASE + AuthenticationRoute.REGISTER)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(AuthenticationApiTestData.registerUserRequest())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.cognitoSub").isEqualTo("cognito-sub-123");
    }

    @Test
    void shouldLoginUser() {
        when(loginUseCase.execute(any())).thenReturn(Mono.just(AuthenticationApiTestData.authenticatedResult()));

        webTestClient.post()
                .uri(AuthenticationRoute.API_V1 + AuthenticationRoute.AUTH_BASE + AuthenticationRoute.LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(AuthenticationApiTestData.loginRequest())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("AUTHENTICATED")
                .jsonPath("$.tokens.accessToken").isEqualTo("access-token");
    }

    @Test
    void shouldRespondAuthenticationChallenge() {
        when(respondAuthenticationChallengeUseCase.execute(any())).thenReturn(Mono.just(AuthenticationApiTestData.authenticatedResult()));

        webTestClient.post()
                .uri(AuthenticationRoute.API_V1 + AuthenticationRoute.AUTH_BASE + AuthenticationRoute.LOGIN_CHALLENGE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(AuthenticationApiTestData.respondChallengeRequest())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tokens.idToken").isEqualTo("id-token");
    }

    @Test
    void shouldStartPasswordRecovery() {
        when(startPasswordRecoveryUseCase.execute(any())).thenReturn(Mono.just(AuthenticationApiTestData.passwordRecoveryResult()));

        webTestClient.post()
                .uri(AuthenticationRoute.API_V1 + AuthenticationRoute.AUTH_BASE + AuthenticationRoute.PASSWORD_RECOVERY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(AuthenticationApiTestData.startPasswordRecoveryRequest())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.codeDeliveryDetails.deliveryMedium").isEqualTo("EMAIL");
    }

    @Test
    void shouldConfirmPasswordRecovery() {
        when(confirmPasswordRecoveryUseCase.execute(any())).thenReturn(Mono.empty());

        webTestClient.post()
                .uri(AuthenticationRoute.API_V1 + AuthenticationRoute.AUTH_BASE + AuthenticationRoute.PASSWORD_RECOVERY_CONFIRMATION)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(AuthenticationApiTestData.confirmPasswordRecoveryRequest())
                .exchange()
                .expectStatus().isNoContent();
    }
}
