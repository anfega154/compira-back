package co.com.compira.usecase.passwordrecovery;

import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.usecase.auth.AuthenticationTestData;
import co.com.compira.usecase.confirmpasswordrecovery.ConfirmPasswordRecoveryUseCase;
import co.com.compira.usecase.startpasswordrecovery.StartPasswordRecoveryUseCase;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordRecoveryUseCaseTest {
    private final AuthenticationGateway authenticationGateway = mock(AuthenticationGateway.class);
    private final StartPasswordRecoveryUseCase startPasswordRecoveryUseCase = new StartPasswordRecoveryUseCase(authenticationGateway);
    private final ConfirmPasswordRecoveryUseCase confirmPasswordRecoveryUseCase = new ConfirmPasswordRecoveryUseCase(authenticationGateway);

    @Test
    void shouldStartPasswordRecovery() {
        when(authenticationGateway.startPasswordRecovery(AuthenticationTestData.startPasswordRecoveryCommand()))
                .thenReturn(Mono.just(AuthenticationTestData.passwordRecoveryResult()));

        StepVerifier.create(startPasswordRecoveryUseCase.execute(AuthenticationTestData.startPasswordRecoveryCommand()))
                .expectNext(AuthenticationTestData.passwordRecoveryResult())
                .verifyComplete();
    }

    @Test
    void shouldConfirmPasswordRecovery() {
        when(authenticationGateway.confirmPasswordRecovery(AuthenticationTestData.confirmPasswordRecoveryCommand()))
                .thenReturn(Mono.empty());

        StepVerifier.create(confirmPasswordRecoveryUseCase.execute(AuthenticationTestData.confirmPasswordRecoveryCommand()))
                .verifyComplete();
    }
}
