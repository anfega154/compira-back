package co.com.compira.api.auth.mapper;

import co.com.compira.api.auth.dto.ConfirmPasswordRecoveryRequest;
import co.com.compira.api.auth.dto.ConfirmUserRegistrationRequest;
import co.com.compira.api.auth.dto.DeleteUserRequest;
import co.com.compira.api.auth.dto.LoginRequest;
import co.com.compira.api.auth.dto.LogoutRequest;
import co.com.compira.api.auth.dto.RegisterUserRequest;
import co.com.compira.api.auth.dto.RespondAuthenticationChallengeRequest;
import co.com.compira.api.auth.dto.StartPasswordRecoveryRequest;
import co.com.compira.model.auth.AuthenticationChallengeName;
import co.com.compira.model.auth.ConfirmPasswordRecoveryCommand;
import co.com.compira.model.auth.ConfirmUserRegistrationCommand;
import co.com.compira.model.auth.DeleteUserCommand;
import co.com.compira.model.auth.LoginCommand;
import co.com.compira.model.auth.LogoutCommand;
import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.auth.RegisterUserCommand;
import co.com.compira.model.auth.RespondAuthenticationChallengeCommand;
import co.com.compira.model.auth.StartPasswordRecoveryCommand;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationRequestMapper {
    public RegisterUserCommand toCommand(RegisterUserRequest request) {
        return new RegisterUserCommand(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName(),
                request.phoneNumber(),
                MfaChannel.fromValue(request.preferredMfaChannel()));
    }

    public ConfirmUserRegistrationCommand toCommand(ConfirmUserRegistrationRequest request) {
        return new ConfirmUserRegistrationCommand(request.email(), request.confirmationCode());
    }

    public LoginCommand toCommand(LoginRequest request) {
        return new LoginCommand(request.email(), request.password());
    }

    public LogoutCommand toCommand(LogoutRequest request) {
        return new LogoutCommand(request.accessToken());
    }

    public RespondAuthenticationChallengeCommand toCommand(RespondAuthenticationChallengeRequest request) {
        return new RespondAuthenticationChallengeCommand(
                request.email(),
                request.session(),
                AuthenticationChallengeName.fromValue(request.challengeName()),
                request.code(),
                request.mfaChannel() == null || request.mfaChannel().isBlank() ? null : MfaChannel.fromValue(request.mfaChannel()));
    }

    public StartPasswordRecoveryCommand toCommand(StartPasswordRecoveryRequest request) {
        return new StartPasswordRecoveryCommand(request.email());
    }

    public ConfirmPasswordRecoveryCommand toCommand(ConfirmPasswordRecoveryRequest request) {
        return new ConfirmPasswordRecoveryCommand(request.email(), request.confirmationCode(), request.newPassword());
    }

    public DeleteUserCommand toCommand(DeleteUserRequest request) {
        return new DeleteUserCommand(request.email());
    }
}
