package co.com.compira.usecase.respondauthenticationchallenge;

import co.com.compira.model.auth.AuthenticationChallenge;
import co.com.compira.model.auth.AuthenticationChallengeName;
import co.com.compira.model.auth.AuthenticationResult;
import co.com.compira.model.auth.AuthenticationStatus;
import co.com.compira.model.auth.CodeDeliveryDetails;
import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.auth.RespondAuthenticationChallengeCommand;
import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.usecase.auth.AuthenticationTestData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RespondAuthenticationChallengeUseCaseTest {
    private final AuthenticationGateway authenticationGateway = mock(AuthenticationGateway.class);
    private final ApplicationUserRepositoryGateway applicationUserRepositoryGateway = mock(ApplicationUserRepositoryGateway.class);
    private final RespondAuthenticationChallengeUseCase useCase = new RespondAuthenticationChallengeUseCase(authenticationGateway, applicationUserRepositoryGateway);

    @Test
    void shouldUpdateLastLoginWhenChallengeCompletesWithAuthentication() {
        when(authenticationGateway.respondToChallenge(AuthenticationTestData.respondAuthenticationChallengeCommand()))
                .thenReturn(Mono.just(AuthenticationTestData.authenticatedResult()));
        when(applicationUserRepositoryGateway.updateLastLogin("john.doe@compira.co"))
                .thenReturn(Mono.just(AuthenticationTestData.activeApplicationUser()));

        StepVerifier.create(useCase.execute(AuthenticationTestData.respondAuthenticationChallengeCommand()))
                .assertNext(result -> {
                    Assertions.assertEquals(AuthenticationStatus.AUTHENTICATED, result.status());
                    Assertions.assertEquals("john.doe@compira.co", result.user().user().email());
                    Assertions.assertNotNull(result.tokens());
                })
                .verifyComplete();

        verify(applicationUserRepositoryGateway).updateLastLogin("john.doe@compira.co");
    }

    @Test
    void shouldReturnChallengeWithoutUpdatingLastLoginWhenCognitoReturnsAnotherChallenge() {
        AuthenticationResult emailOtpChallenge = AuthenticationResult.challengeRequired(
                new AuthenticationChallenge(
                        AuthenticationChallengeName.EMAIL_OTP,
                        "new-session-token",
                        List.of(MfaChannel.EMAIL),
                        new CodeDeliveryDetails("j***@c***.co", "EMAIL", "email")));

        RespondAuthenticationChallengeCommand newPasswordCommand = new RespondAuthenticationChallengeCommand(
                "john.doe@compira.co",
                "session-token",
                AuthenticationChallengeName.NEW_PASSWORD_REQUIRED,
                null,
                null,
                "NewSecurePassword123!");

        when(authenticationGateway.respondToChallenge(newPasswordCommand))
                .thenReturn(Mono.just(emailOtpChallenge));

        StepVerifier.create(useCase.execute(newPasswordCommand))
                .assertNext(result -> {
                    Assertions.assertEquals(AuthenticationStatus.CHALLENGE_REQUIRED, result.status());
                    Assertions.assertEquals(AuthenticationChallengeName.EMAIL_OTP, result.challenge().challengeName());
                    Assertions.assertEquals("new-session-token", result.challenge().session());
                    Assertions.assertNull(result.tokens());
                    Assertions.assertNull(result.user());
                })
                .verifyComplete();

        verify(applicationUserRepositoryGateway, never()).updateLastLogin(any());
    }

    @Test
    void shouldPropagateGatewayErrorForInvalidCode() {
        CompiraException codeError = new CompiraException("AUTH_003", "El código de confirmación es inválido", ErrorCategory.BAD_REQUEST);

        when(authenticationGateway.respondToChallenge(AuthenticationTestData.respondAuthenticationChallengeCommand()))
                .thenReturn(Mono.error(codeError));

        StepVerifier.create(useCase.execute(AuthenticationTestData.respondAuthenticationChallengeCommand()))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && ((CompiraException) error).getCode().equals("AUTH_003"))
                .verify();

        verify(applicationUserRepositoryGateway, never()).updateLastLogin(any());
    }

    @Test
    void shouldPropagateGatewayErrorForExpiredSession() {
        CompiraException sessionError = new CompiraException("AUTH_005", "Las credenciales ingresadas no son válidas", ErrorCategory.UNAUTHORIZED);

        RespondAuthenticationChallengeCommand expiredSessionCommand = new RespondAuthenticationChallengeCommand(
                "john.doe@compira.co",
                "expired-session",
                AuthenticationChallengeName.EMAIL_OTP,
                "123456",
                null,
                null);

        when(authenticationGateway.respondToChallenge(expiredSessionCommand))
                .thenReturn(Mono.error(sessionError));

        StepVerifier.create(useCase.execute(expiredSessionCommand))
                .expectErrorMatches(error -> error instanceof CompiraException
                        && ((CompiraException) error).getCode().equals("AUTH_005"))
                .verify();

        verify(applicationUserRepositoryGateway, never()).updateLastLogin(any());
    }

    @Test
    void shouldHandleNewPasswordChallengeFollowedByAuthentication() {
        RespondAuthenticationChallengeCommand newPasswordCommand = new RespondAuthenticationChallengeCommand(
                "john.doe@compira.co",
                "session-token",
                AuthenticationChallengeName.NEW_PASSWORD_REQUIRED,
                null,
                null,
                "NewSecurePassword123!");

        when(authenticationGateway.respondToChallenge(newPasswordCommand))
                .thenReturn(Mono.just(AuthenticationTestData.authenticatedResult()));
        when(applicationUserRepositoryGateway.updateLastLogin("john.doe@compira.co"))
                .thenReturn(Mono.just(AuthenticationTestData.activeApplicationUser()));

        StepVerifier.create(useCase.execute(newPasswordCommand))
                .assertNext(result -> {
                    Assertions.assertEquals(AuthenticationStatus.AUTHENTICATED, result.status());
                    Assertions.assertNotNull(result.tokens());
                    Assertions.assertNotNull(result.user());
                })
                .verifyComplete();

        verify(applicationUserRepositoryGateway).updateLastLogin("john.doe@compira.co");
    }
}
