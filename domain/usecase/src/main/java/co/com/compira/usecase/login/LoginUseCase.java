package co.com.compira.usecase.login;

import co.com.compira.model.auth.AuthenticationResult;
import co.com.compira.model.auth.AuthenticationStatus;
import co.com.compira.model.auth.LoginCommand;
import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import reactor.core.publisher.Mono;

public class LoginUseCase {
    private final AuthenticationGateway authenticationGateway;
    private final ApplicationUserRepositoryGateway applicationUserRepositoryGateway;

    public LoginUseCase(AuthenticationGateway authenticationGateway,
                        ApplicationUserRepositoryGateway applicationUserRepositoryGateway) {
        this.authenticationGateway = authenticationGateway;
        this.applicationUserRepositoryGateway = applicationUserRepositoryGateway;
    }

    public Mono<AuthenticationResult> execute(LoginCommand command) {
        return authenticationGateway.login(command)
                .flatMap(result -> AuthenticationStatus.AUTHENTICATED.equals(result.status())
                        ? applicationUserRepositoryGateway.updateLastLogin(command.username())
                        .map(result::withUser)
                        : Mono.just(result));
    }
}
