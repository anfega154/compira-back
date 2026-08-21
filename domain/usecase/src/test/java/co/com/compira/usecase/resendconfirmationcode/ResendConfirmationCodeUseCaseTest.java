package co.com.compira.usecase.resendconfirmationcode;

import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.usecase.auth.AuthenticationTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResendConfirmationCodeUseCaseTest {
    private final AuthenticationGateway authenticationGateway = mock(AuthenticationGateway.class);
    private final ResendConfirmationCodeUseCase useCase = new ResendConfirmationCodeUseCase(authenticationGateway);

    @Test
    void shouldResendConfirmationCode() {
        when(authenticationGateway.resendConfirmationCode(AuthenticationTestData.resendConfirmationCodeCommand()))
                .thenReturn(Mono.just(AuthenticationTestData.resendConfirmationCodeResult()));

        StepVerifier.create(useCase.execute(AuthenticationTestData.resendConfirmationCodeCommand()))
                .expectNext(AuthenticationTestData.resendConfirmationCodeResult())
                .verifyComplete();

        verify(authenticationGateway).resendConfirmationCode(AuthenticationTestData.resendConfirmationCodeCommand());
    }
}
