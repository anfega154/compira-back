package co.com.compira.usecase.registeruser;

import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.usecase.auth.AuthenticationTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterUserUseCaseTest {
    private final AuthenticationGateway authenticationGateway = mock(AuthenticationGateway.class);
    private final ApplicationUserRepositoryGateway applicationUserRepositoryGateway = mock(ApplicationUserRepositoryGateway.class);
    private final RegisterUserUseCase useCase = new RegisterUserUseCase(authenticationGateway, applicationUserRepositoryGateway);

    @Test
    void shouldRegisterUserAndPersistLocalProfile() {
        when(authenticationGateway.registerUser(AuthenticationTestData.registerUserCommand()))
                .thenReturn(Mono.just(AuthenticationTestData.userRegistrationResult()));
        when(applicationUserRepositoryGateway.createPendingUser(AuthenticationTestData.registerUserCommand(), "cognito-sub-123"))
                .thenReturn(Mono.just(AuthenticationTestData.pendingApplicationUser()));

        StepVerifier.create(useCase.execute(AuthenticationTestData.registerUserCommand()))
                .expectNext(AuthenticationTestData.userRegistrationResult())
                .verifyComplete();
    }

    @Test
    void shouldDeleteCognitoUserWhenLocalPersistenceFails() {
        when(authenticationGateway.registerUser(AuthenticationTestData.registerUserCommand()))
                .thenReturn(Mono.just(AuthenticationTestData.userRegistrationResult()));
        when(applicationUserRepositoryGateway.createPendingUser(AuthenticationTestData.registerUserCommand(), "cognito-sub-123"))
                .thenReturn(Mono.error(new IllegalStateException("database error")));
        when(authenticationGateway.deleteUser("john.doe@compira.co")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(AuthenticationTestData.registerUserCommand()))
                .expectErrorMessage("database error")
                .verify();

        verify(authenticationGateway).deleteUser(eq("john.doe@compira.co"));
    }
}
