package co.com.compira.usecase.logout;

import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.usecase.auth.AuthenticationTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogoutUseCaseTest {
    private final AuthenticationGateway authenticationGateway = mock(AuthenticationGateway.class);
    private final LogoutUseCase useCase = new LogoutUseCase(authenticationGateway);

    @Test
    void shouldLogoutUser() {
        when(authenticationGateway.logout(AuthenticationTestData.logoutCommand())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(AuthenticationTestData.logoutCommand()))
                .verifyComplete();

        verify(authenticationGateway).logout(AuthenticationTestData.logoutCommand());
    }
}
