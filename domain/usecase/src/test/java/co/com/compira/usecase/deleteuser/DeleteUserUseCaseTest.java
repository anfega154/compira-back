package co.com.compira.usecase.deleteuser;

import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.usecase.auth.AuthenticationTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteUserUseCaseTest {
    private final AuthenticationGateway authenticationGateway = mock(AuthenticationGateway.class);
    private final ApplicationUserRepositoryGateway applicationUserRepositoryGateway = mock(ApplicationUserRepositoryGateway.class);
    private final DeleteUserUseCase useCase = new DeleteUserUseCase(authenticationGateway, applicationUserRepositoryGateway);

    @Test
    void shouldDeleteUserFromCognitoAndLocalRepository() {
        when(applicationUserRepositoryGateway.findByEmail("john.doe@compira.co"))
                .thenReturn(Mono.just(AuthenticationTestData.activeApplicationUser()));
        when(authenticationGateway.deleteUser("john.doe@compira.co"))
                .thenReturn(Mono.empty());
        when(applicationUserRepositoryGateway.deleteByEmail("john.doe@compira.co"))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(AuthenticationTestData.deleteUserCommand()))
                .verifyComplete();

        verify(authenticationGateway).deleteUser("john.doe@compira.co");
        verify(applicationUserRepositoryGateway).deleteByEmail("john.doe@compira.co");
    }
}
