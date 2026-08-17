package co.com.compira.usecase.deleteuser;

import co.com.compira.model.auth.AuthenticationErrorCode;
import co.com.compira.model.auth.AuthenticationMessage;
import co.com.compira.model.auth.DeleteUserCommand;
import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import reactor.core.publisher.Mono;

public class DeleteUserUseCase {
    private final AuthenticationGateway authenticationGateway;
    private final ApplicationUserRepositoryGateway applicationUserRepositoryGateway;

    public DeleteUserUseCase(AuthenticationGateway authenticationGateway,
                             ApplicationUserRepositoryGateway applicationUserRepositoryGateway) {
        this.authenticationGateway = authenticationGateway;
        this.applicationUserRepositoryGateway = applicationUserRepositoryGateway;
    }

    public Mono<Void> execute(DeleteUserCommand command) {
        return applicationUserRepositoryGateway.findByEmail(command.email())
                .switchIfEmpty(Mono.error(new CompiraException(
                        AuthenticationErrorCode.LOCAL_USER_NOT_FOUND,
                        AuthenticationMessage.LOCAL_USER_NOT_FOUND,
                        ErrorCategory.NOT_FOUND)))
                .flatMap(applicationUser -> authenticationGateway.deleteUser(applicationUser.user().email())
                        .then(applicationUserRepositoryGateway.deleteByEmail(applicationUser.user().email())));
    }
}
