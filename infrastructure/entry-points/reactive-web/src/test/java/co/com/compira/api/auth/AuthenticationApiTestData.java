package co.com.compira.api.auth;

import co.com.compira.api.auth.dto.ConfirmPasswordRecoveryRequest;
import co.com.compira.api.auth.dto.DeleteUserRequest;
import co.com.compira.api.auth.dto.LoginRequest;
import co.com.compira.api.auth.dto.LogoutRequest;
import co.com.compira.api.auth.dto.RegisterUserRequest;
import co.com.compira.api.auth.dto.ResendConfirmationCodeRequest;
import co.com.compira.api.auth.dto.RespondAuthenticationChallengeRequest;
import co.com.compira.api.auth.dto.StartPasswordRecoveryRequest;
import co.com.compira.model.auth.ApplicationUser;
import co.com.compira.model.auth.AuthenticationChallenge;
import co.com.compira.model.auth.AuthenticationChallengeName;
import co.com.compira.model.auth.AuthenticationResult;
import co.com.compira.model.auth.AuthenticationStatus;
import co.com.compira.model.auth.AuthenticationTokens;
import co.com.compira.model.auth.CodeDeliveryDetails;
import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.auth.PasswordRecoveryResult;
import co.com.compira.model.auth.ResendConfirmationCodeResult;
import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.user.User;
import co.com.compira.model.auth.UserRegistrationResult;
import co.com.compira.model.auth.UserStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AuthenticationApiTestData {
    private AuthenticationApiTestData() {
    }

    public static RegisterUserRequest registerUserRequest() {
        return new RegisterUserRequest(
                "john.doe@compira.co",
                "Password123!",
                "John",
                "Doe",
                "+573001112233",
                "EMAIL",
                RoleCode.COLLABORATOR.name());
    }

    public static LoginRequest loginRequest() {
        return new LoginRequest("john.doe@compira.co", "Password123!");
    }

    public static LogoutRequest logoutRequest() {
        return new LogoutRequest("access-token");
    }

    public static RespondAuthenticationChallengeRequest respondChallengeRequest() {
        return new RespondAuthenticationChallengeRequest(
                "john.doe@compira.co",
                "challenge-session",
                "EMAIL_OTP",
                "654321",
                null,
                null);
    }

    public static RespondAuthenticationChallengeRequest selectMfaTypeRequest() {
        return new RespondAuthenticationChallengeRequest(
                "john.doe@compira.co",
                "challenge-session",
                "SELECT_MFA_TYPE",
                null,
                "EMAIL",
                null);
    }

    public static StartPasswordRecoveryRequest startPasswordRecoveryRequest() {
        return new StartPasswordRecoveryRequest("john.doe@compira.co");
    }

    public static ConfirmPasswordRecoveryRequest confirmPasswordRecoveryRequest() {
        return new ConfirmPasswordRecoveryRequest("john.doe@compira.co", "123456", "NewPassword123!");
    }

    public static DeleteUserRequest deleteUserRequest() {
        return new DeleteUserRequest("john.doe@compira.co");
    }

    public static ResendConfirmationCodeRequest resendConfirmationCodeRequest() {
        return new ResendConfirmationCodeRequest("john.doe@compira.co");
    }

    public static ResendConfirmationCodeResult resendConfirmationCodeResult() {
        return new ResendConfirmationCodeResult(codeDeliveryDetails());
    }

    public static UserRegistrationResult userRegistrationResult() {
        return new UserRegistrationResult("cognito-sub-123", "john.doe@compira.co", "FORCE_CHANGE_PASSWORD");
    }

    public static ApplicationUser applicationUser() {
        return new ApplicationUser(
                new User(
                        UUID.fromString("0efdd5b1-c983-4332-980e-30700c8ca6ee"),
                        "john.doe@compira.co",
                        "John",
                        "Doe",
                        "+573001112233",
                        OffsetDateTime.parse("2026-08-16T12:00:00Z"),
                        OffsetDateTime.parse("2026-08-16T12:10:00Z")),
                "cognito-sub-123",
                UserStatus.ACTIVE,
                MfaChannel.EMAIL,
                List.of(RoleCode.COLLABORATOR.name()),
                OffsetDateTime.parse("2026-08-16T12:10:00Z"));
    }

    public static AuthenticationResult authenticatedResult() {
        return new AuthenticationResult(
                AuthenticationStatus.AUTHENTICATED,
                applicationUser(),
                new AuthenticationTokens("access-token", "id-token", "refresh-token", 3600, "Bearer"),
                null);
    }

    public static AuthenticationResult challengeRequiredResult() {
        return new AuthenticationResult(
                AuthenticationStatus.CHALLENGE_REQUIRED,
                null,
                null,
                new AuthenticationChallenge(
                        AuthenticationChallengeName.EMAIL_OTP,
                        "challenge-session",
                        List.of(MfaChannel.EMAIL),
                        codeDeliveryDetails()));
    }

    public static PasswordRecoveryResult passwordRecoveryResult() {
        return new PasswordRecoveryResult(codeDeliveryDetails());
    }

    public static CodeDeliveryDetails codeDeliveryDetails() {
        return new CodeDeliveryDetails("j***@c***.co", "EMAIL", "email");
    }
}
