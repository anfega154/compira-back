package co.com.compira.usecase.passwordrecovery;

import co.com.compira.model.auth.AuthenticationErrorCode;
import co.com.compira.model.auth.AuthenticationMessage;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.usecase.auth.AuthenticationTestData;
import co.com.compira.usecase.confirmpasswordrecovery.ConfirmPasswordRecoveryUseCase;
import co.com.compira.usecase.startpasswordrecovery.StartPasswordRecoveryUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(strings = {"AUTH_007", "AUTH_010", "AUTH_006", "AUTH_016"})
    void shouldReturnGenericResponseWhenProviderRevealsAccountState(String errorCode) {
        when(authenticationGateway.startPasswordRecovery(AuthenticationTestData.startPasswordRecoveryCommand()))
                .thenReturn(Mono.error(new CompiraException(errorCode, "no debe filtrarse", ErrorCategory.NOT_FOUND)));

        StepVerifier.create(startPasswordRecoveryUseCase.execute(AuthenticationTestData.startPasswordRecoveryCommand()))
                .assertNext(result -> {
                    assert result.codeDeliveryDetails() != null;
                    assert "EMAIL".equals(result.codeDeliveryDetails().deliveryMedium());
                    // El destino es una versión enmascarada del correo solicitado, no el valor real de Cognito.
                    assert result.codeDeliveryDetails().destination().contains("***");
                })
                .verifyComplete();
    }

    @Test
    void shouldPropagateNonEnumerationErrors() {
        when(authenticationGateway.startPasswordRecovery(AuthenticationTestData.startPasswordRecoveryCommand()))
                .thenReturn(Mono.error(new CompiraException(
                        AuthenticationErrorCode.TOO_MANY_REQUESTS,
                        AuthenticationMessage.TOO_MANY_REQUESTS,
                        ErrorCategory.TOO_MANY_REQUESTS)));

        StepVerifier.create(startPasswordRecoveryUseCase.execute(AuthenticationTestData.startPasswordRecoveryCommand()))
                .expectErrorMatches(error -> error instanceof CompiraException compiraException
                        && AuthenticationErrorCode.TOO_MANY_REQUESTS.equals(compiraException.getCode()))
                .verify();
    }

    @Test
    void shouldConfirmPasswordRecovery() {
        when(authenticationGateway.confirmPasswordRecovery(AuthenticationTestData.confirmPasswordRecoveryCommand()))
                .thenReturn(Mono.empty());

        StepVerifier.create(confirmPasswordRecoveryUseCase.execute(AuthenticationTestData.confirmPasswordRecoveryCommand()))
                .verifyComplete();
    }
}
