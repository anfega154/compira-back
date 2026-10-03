package co.com.compira.model.auth.gateways;

import co.com.compira.model.auth.ApplicationUser;
import co.com.compira.model.auth.RegisterUserCommand;
import reactor.core.publisher.Mono;

public interface ApplicationUserRepositoryGateway {
    Mono<ApplicationUser> createPendingUser(RegisterUserCommand command, String cognitoSub);

    Mono<ApplicationUser> findByCognitoSub(String cognitoSub);

    Mono<ApplicationUser> findByEmail(String email);

    Mono<ApplicationUser> activateUser(String email);

    Mono<ApplicationUser> disableUser(String email);

    Mono<ApplicationUser> completeLogin(String email);

    Mono<Void> deleteByEmail(String email);
}
