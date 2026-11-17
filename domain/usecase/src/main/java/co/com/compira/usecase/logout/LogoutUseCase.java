package co.com.compira.usecase.logout;

import co.com.compira.model.auth.LogoutCommand;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import reactor.core.publisher.Mono;

public class LogoutUseCase {
    private final AuthenticationGateway authenticationGateway;

    public LogoutUseCase(AuthenticationGateway authenticationGateway) {
        this.authenticationGateway = authenticationGateway;
    }

    public Mono<Void> execute(LogoutCommand command) {
        return authenticationGateway.logout(command);
    }
}
