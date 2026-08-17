package co.com.compira.model.auth.gateways;

import co.com.compira.model.auth.AuthenticationResult;
import co.com.compira.model.auth.ConfirmPasswordRecoveryCommand;
import co.com.compira.model.auth.ConfirmUserRegistrationCommand;
import co.com.compira.model.auth.LoginCommand;
import co.com.compira.model.auth.LogoutCommand;
import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.auth.PasswordRecoveryResult;
import co.com.compira.model.auth.RegisterUserCommand;
import co.com.compira.model.auth.RespondAuthenticationChallengeCommand;
import co.com.compira.model.auth.StartPasswordRecoveryCommand;
import co.com.compira.model.auth.UserRegistrationResult;
import reactor.core.publisher.Mono;

public interface AuthenticationGateway {
    Mono<UserRegistrationResult> registerUser(RegisterUserCommand command);

    Mono<Void> deleteUser(String username);

    Mono<Void> confirmUserRegistration(ConfirmUserRegistrationCommand command, MfaChannel preferredMfaChannel);

    Mono<AuthenticationResult> login(LoginCommand command);

    Mono<Void> logout(LogoutCommand command);

    Mono<AuthenticationResult> respondToChallenge(RespondAuthenticationChallengeCommand command);

    Mono<PasswordRecoveryResult> startPasswordRecovery(StartPasswordRecoveryCommand command);

    Mono<Void> confirmPasswordRecovery(ConfirmPasswordRecoveryCommand command);
}
