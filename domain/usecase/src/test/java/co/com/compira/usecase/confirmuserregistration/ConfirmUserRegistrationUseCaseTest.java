package co.com.compira.usecase.confirmuserregistration;

import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.usecase.auth.AuthenticationTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfirmUserRegistrationUseCaseTest {
    private final AuthenticationGateway authenticationGateway = mock(AuthenticationGateway.class);
    private final ApplicationUserRepositoryGateway applicationUserRepositoryGateway = mock(ApplicationUserRepositoryGateway.class);
    private final ConfirmUserRegistrationUseCase useCase = new ConfirmUserRegistrationUseCase(authenticationGateway, applicationUserRepositoryGateway);

    @Test
    void shouldConfirmUserRegistrationAndActivateLocalProfile() {
        when(applicationUserRepositoryGateway.findByEmail("john.doe@compira.co"))
                .thenReturn(Mono.just(AuthenticationTestData.pendingApplicationUser()));
        when(authenticationGateway.confirmUserRegistration(AuthenticationTestData.confirmUserRegistrationCommand(), AuthenticationTestData.pendingApplicationUser().preferredMfaChannel()))
                .thenReturn(Mono.empty());
        when(applicationUserRepositoryGateway.activateUser("john.doe@compira.co"))
                .thenReturn(Mono.just(AuthenticationTestData.activeApplicationUser()));

        StepVerifier.create(useCase.execute(AuthenticationTestData.confirmUserRegistrationCommand()))
                .expectNext(AuthenticationTestData.activeApplicationUser())
                .verifyComplete();
    }
}
