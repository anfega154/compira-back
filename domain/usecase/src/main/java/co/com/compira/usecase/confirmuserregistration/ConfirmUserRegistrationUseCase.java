package co.com.compira.usecase.confirmuserregistration;

import co.com.compira.model.auth.ApplicationUser;
import co.com.compira.model.auth.ConfirmUserRegistrationCommand;
import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.auth.AuthenticationErrorCode;
import co.com.compira.model.auth.AuthenticationMessage;
import reactor.core.publisher.Mono;

public class ConfirmUserRegistrationUseCase {
    private final AuthenticationGateway authenticationGateway;
    private final ApplicationUserRepositoryGateway applicationUserRepositoryGateway;

    public ConfirmUserRegistrationUseCase(AuthenticationGateway authenticationGateway,
                                          ApplicationUserRepositoryGateway applicationUserRepositoryGateway) {
        this.authenticationGateway = authenticationGateway;
        this.applicationUserRepositoryGateway = applicationUserRepositoryGateway;
    }

    public Mono<ApplicationUser> execute(ConfirmUserRegistrationCommand command) {
        return applicationUserRepositoryGateway.findByEmail(command.email())
                .switchIfEmpty(Mono.error(new CompiraException(
                        AuthenticationErrorCode.LOCAL_USER_NOT_FOUND,
                        AuthenticationMessage.LOCAL_USER_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)))
                .flatMap(user -> authenticationGateway.confirmUserRegistration(command, user.preferredMfaChannel())
                        .then(applicationUserRepositoryGateway.activateUser(command.email())));
    }
}
