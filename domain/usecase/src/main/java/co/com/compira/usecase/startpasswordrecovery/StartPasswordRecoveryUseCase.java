package co.com.compira.usecase.startpasswordrecovery;

import co.com.compira.model.auth.PasswordRecoveryResult;
import co.com.compira.model.auth.StartPasswordRecoveryCommand;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import reactor.core.publisher.Mono;

public class StartPasswordRecoveryUseCase {
    private final AuthenticationGateway authenticationGateway;

    public StartPasswordRecoveryUseCase(AuthenticationGateway authenticationGateway) {
        this.authenticationGateway = authenticationGateway;
    }

    public Mono<PasswordRecoveryResult> execute(StartPasswordRecoveryCommand command) {
        return authenticationGateway.startPasswordRecovery(command);
    }
}
