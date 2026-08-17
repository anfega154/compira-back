package co.com.compira.usecase.registeruser;

import co.com.compira.model.auth.ApplicationUser;
import co.com.compira.model.auth.RegisterUserCommand;
import co.com.compira.model.auth.UserRegistrationResult;
import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import reactor.core.publisher.Mono;

public class RegisterUserUseCase {
    private final AuthenticationGateway authenticationGateway;
    private final ApplicationUserRepositoryGateway applicationUserRepositoryGateway;

    public RegisterUserUseCase(AuthenticationGateway authenticationGateway,
                               ApplicationUserRepositoryGateway applicationUserRepositoryGateway) {
        this.authenticationGateway = authenticationGateway;
        this.applicationUserRepositoryGateway = applicationUserRepositoryGateway;
    }

    public Mono<UserRegistrationResult> execute(RegisterUserCommand command) {
        return authenticationGateway.registerUser(command)
                .flatMap(result -> applicationUserRepositoryGateway.createPendingUser(command, result.cognitoSub())
                        .map(ApplicationUser::cognitoSub)
                        .thenReturn(result)
                        .onErrorResume(error -> authenticationGateway.deleteUser(command.email())
                                .then(Mono.error(error))));
    }
}
