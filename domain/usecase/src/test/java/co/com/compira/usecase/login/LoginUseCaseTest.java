package co.com.compira.usecase.login;

import co.com.compira.model.auth.UserStatus;

import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.usecase.auth.AuthenticationTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LoginUseCaseTest {
    private final AuthenticationGateway authenticationGateway = mock(AuthenticationGateway.class);
    private final ApplicationUserRepositoryGateway applicationUserRepositoryGateway = mock(ApplicationUserRepositoryGateway.class);
    private final LoginUseCase useCase = new LoginUseCase(authenticationGateway, applicationUserRepositoryGateway);

    @Test
    void shouldCompleteLoginWhenAuthenticationSucceeds() {
        when(authenticationGateway.login(AuthenticationTestData.loginCommand()))
                .thenReturn(Mono.just(AuthenticationTestData.authenticatedResult()));
        when(applicationUserRepositoryGateway.completeLogin("john.doe@compira.co"))
                .thenReturn(Mono.just(AuthenticationTestData.activeApplicationUser()));

        StepVerifier.create(useCase.execute(AuthenticationTestData.loginCommand()))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertEquals("john.doe@compira.co", result.user().user().email());
                    org.junit.jupiter.api.Assertions.assertEquals(UserStatus.ACTIVE, result.user().status());
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnChallengeWithoutUpdatingLastLogin() {
        when(authenticationGateway.login(AuthenticationTestData.loginCommand()))
                .thenReturn(Mono.just(AuthenticationTestData.challengeRequiredResult()));

        StepVerifier.create(useCase.execute(AuthenticationTestData.loginCommand()))
                .expectNext(AuthenticationTestData.challengeRequiredResult())
                .verifyComplete();
        verifyNoInteractions(applicationUserRepositoryGateway);
    }

    @Test
    void shouldNotCompleteLoginWhenIdentityProviderRejectsCredentials() {
        when(authenticationGateway.login(AuthenticationTestData.loginCommand()))
                .thenReturn(Mono.error(new IllegalStateException()));
        StepVerifier.create(useCase.execute(AuthenticationTestData.loginCommand()))
                .expectError(IllegalStateException.class).verify();
        verifyNoInteractions(applicationUserRepositoryGateway);
    }

    @Test
    void shouldNotReturnTokensWhenLocalLoginCompletionFails() {
        when(authenticationGateway.login(AuthenticationTestData.loginCommand()))
                .thenReturn(Mono.just(AuthenticationTestData.authenticatedResult()));
        when(applicationUserRepositoryGateway.completeLogin(AuthenticationTestData.loginCommand().username()))
                .thenReturn(Mono.error(new IllegalStateException()));
        StepVerifier.create(useCase.execute(AuthenticationTestData.loginCommand()))
                .expectError(IllegalStateException.class).verify();
    }
}
