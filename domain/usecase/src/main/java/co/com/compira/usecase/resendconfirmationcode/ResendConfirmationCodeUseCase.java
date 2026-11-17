package co.com.compira.usecase.resendconfirmationcode;

import co.com.compira.model.auth.ResendConfirmationCodeCommand;
import co.com.compira.model.auth.ResendConfirmationCodeResult;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import reactor.core.publisher.Mono;

public class ResendConfirmationCodeUseCase {
    private final AuthenticationGateway authenticationGateway;

    public ResendConfirmationCodeUseCase(AuthenticationGateway authenticationGateway) {
        this.authenticationGateway = authenticationGateway;
    }

    public Mono<ResendConfirmationCodeResult> execute(ResendConfirmationCodeCommand command) {
        return authenticationGateway.resendConfirmationCode(command);
    }
}
