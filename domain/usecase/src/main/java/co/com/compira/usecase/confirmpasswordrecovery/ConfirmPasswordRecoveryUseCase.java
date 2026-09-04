package co.com.compira.usecase.confirmpasswordrecovery;

import co.com.compira.model.auth.ConfirmPasswordRecoveryCommand;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import reactor.core.publisher.Mono;

public class ConfirmPasswordRecoveryUseCase {
    private final AuthenticationGateway authenticationGateway;

    public ConfirmPasswordRecoveryUseCase(AuthenticationGateway authenticationGateway) {
        this.authenticationGateway = authenticationGateway;
    }

    public Mono<Void> execute(ConfirmPasswordRecoveryCommand command) {
        return authenticationGateway.confirmPasswordRecovery(command);
    }
}
