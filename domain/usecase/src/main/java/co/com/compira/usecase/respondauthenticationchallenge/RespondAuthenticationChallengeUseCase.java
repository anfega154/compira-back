package co.com.compira.usecase.respondauthenticationchallenge;

import co.com.compira.model.auth.AuthenticationResult;
import co.com.compira.model.auth.AuthenticationStatus;
import co.com.compira.model.auth.RespondAuthenticationChallengeCommand;
import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import reactor.core.publisher.Mono;

public class RespondAuthenticationChallengeUseCase {
    private final AuthenticationGateway authenticationGateway;
    private final ApplicationUserRepositoryGateway applicationUserRepositoryGateway;

    public RespondAuthenticationChallengeUseCase(AuthenticationGateway authenticationGateway,
                                                 ApplicationUserRepositoryGateway applicationUserRepositoryGateway) {
        this.authenticationGateway = authenticationGateway;
        this.applicationUserRepositoryGateway = applicationUserRepositoryGateway;
    }

    public Mono<AuthenticationResult> execute(RespondAuthenticationChallengeCommand command) {
        return authenticationGateway.respondToChallenge(command)
                .flatMap(result -> AuthenticationStatus.AUTHENTICATED.equals(result.status())
                        ? applicationUserRepositoryGateway.updateLastLogin(command.username())
                        .map(result::withUser)
                        : Mono.just(result));
    }
}
