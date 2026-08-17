package co.com.compira.usecase.respondauthenticationchallenge;

import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.usecase.auth.AuthenticationTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RespondAuthenticationChallengeUseCaseTest {
    private final AuthenticationGateway authenticationGateway = mock(AuthenticationGateway.class);
    private final ApplicationUserRepositoryGateway applicationUserRepositoryGateway = mock(ApplicationUserRepositoryGateway.class);
    private final RespondAuthenticationChallengeUseCase useCase = new RespondAuthenticationChallengeUseCase(authenticationGateway, applicationUserRepositoryGateway);

    @Test
    void shouldUpdateLastLoginAfterChallengeCompletion() {
        when(authenticationGateway.respondToChallenge(AuthenticationTestData.respondAuthenticationChallengeCommand()))
                .thenReturn(Mono.just(AuthenticationTestData.authenticatedResult()));
        when(applicationUserRepositoryGateway.updateLastLogin("john.doe@compira.co"))
                .thenReturn(Mono.just(AuthenticationTestData.activeApplicationUser()));

        StepVerifier.create(useCase.execute(AuthenticationTestData.respondAuthenticationChallengeCommand()))
                .assertNext(result -> org.junit.jupiter.api.Assertions.assertEquals("john.doe@compira.co", result.user().user().email()))
                .verifyComplete();
    }
}
