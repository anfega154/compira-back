package co.com.compira.usecase.auth;

import co.com.compira.model.auth.ApplicationUser;
import co.com.compira.model.auth.AuthenticationChallenge;
import co.com.compira.model.auth.AuthenticationChallengeName;
import co.com.compira.model.auth.AuthenticationResult;
import co.com.compira.model.auth.AuthenticationStatus;
import co.com.compira.model.auth.AuthenticationTokens;
import co.com.compira.model.auth.CodeDeliveryDetails;
import co.com.compira.model.auth.ConfirmPasswordRecoveryCommand;
import co.com.compira.model.auth.ConfirmUserRegistrationCommand;
import co.com.compira.model.auth.DeleteUserCommand;
import co.com.compira.model.auth.LoginCommand;
import co.com.compira.model.auth.LogoutCommand;
import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.auth.PasswordRecoveryResult;
import co.com.compira.model.auth.RegisterUserCommand;
import co.com.compira.model.auth.RespondAuthenticationChallengeCommand;
import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.auth.StartPasswordRecoveryCommand;
import co.com.compira.model.user.User;
import co.com.compira.model.auth.UserRegistrationResult;
import co.com.compira.model.auth.UserStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AuthenticationTestData {
    private AuthenticationTestData() {
    }

    public static RegisterUserCommand registerUserCommand() {
        return new RegisterUserCommand(
                "john.doe@compira.co",
                "Password123!",
                "John",
                "Doe",
                "+573001112233",
                MfaChannel.EMAIL,
                RoleCode.COLLABORATOR);
    }

    public static ConfirmUserRegistrationCommand confirmUserRegistrationCommand() {
        return new ConfirmUserRegistrationCommand("john.doe@compira.co", "123456");
    }

    public static LoginCommand loginCommand() {
        return new LoginCommand("john.doe@compira.co", "Password123!");
    }

    public static LogoutCommand logoutCommand() {
        return new LogoutCommand("access-token");
    }

    public static RespondAuthenticationChallengeCommand respondAuthenticationChallengeCommand() {
        return new RespondAuthenticationChallengeCommand(
                "john.doe@compira.co",
                "session-token",
                AuthenticationChallengeName.EMAIL_OTP,
                "654321",
                null);
    }

    public static StartPasswordRecoveryCommand startPasswordRecoveryCommand() {
        return new StartPasswordRecoveryCommand("john.doe@compira.co");
    }

    public static ConfirmPasswordRecoveryCommand confirmPasswordRecoveryCommand() {
        return new ConfirmPasswordRecoveryCommand("john.doe@compira.co", "123456", "NewPassword123!");
    }

    public static DeleteUserCommand deleteUserCommand() {
        return new DeleteUserCommand("john.doe@compira.co");
    }

    public static UserRegistrationResult userRegistrationResult() {
        return new UserRegistrationResult("cognito-sub-123", false, codeDeliveryDetails());
    }

    public static CodeDeliveryDetails codeDeliveryDetails() {
        return new CodeDeliveryDetails("j***@c***.co", "EMAIL", "email");
    }

    public static ApplicationUser pendingApplicationUser() {
        return new ApplicationUser(
                user(OffsetDateTime.parse("2026-08-16T12:00:00Z"), OffsetDateTime.parse("2026-08-16T12:00:00Z")),
                "cognito-sub-123",
                UserStatus.PENDING_CONFIRMATION,
                MfaChannel.EMAIL,
                List.of(RoleCode.COLLABORATOR.name()),
                null);
    }

    public static ApplicationUser activeApplicationUser() {
        return new ApplicationUser(
                user(OffsetDateTime.parse("2026-08-16T12:00:00Z"), OffsetDateTime.parse("2026-08-16T12:05:00Z")),
                "cognito-sub-123",
                UserStatus.ACTIVE,
                MfaChannel.EMAIL,
                List.of(RoleCode.COLLABORATOR.name()),
                OffsetDateTime.parse("2026-08-16T12:05:00Z"));
    }

    public static AuthenticationResult authenticatedResult() {
        return new AuthenticationResult(
                AuthenticationStatus.AUTHENTICATED,
                null,
                new AuthenticationTokens("access-token", "id-token", "refresh-token", 3600, "Bearer"),
                null);
    }

    public static AuthenticationResult challengeRequiredResult() {
        return new AuthenticationResult(
                AuthenticationStatus.CHALLENGE_REQUIRED,
                null,
                null,
                new AuthenticationChallenge(
                        AuthenticationChallengeName.SELECT_MFA_TYPE,
                        "challenge-session",
                        List.of(MfaChannel.EMAIL, MfaChannel.SMS),
                        null));
    }

    public static PasswordRecoveryResult passwordRecoveryResult() {
        return new PasswordRecoveryResult(codeDeliveryDetails());
    }

    private static User user(OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        return new User(
                UUID.fromString("fbb1401d-95a6-4f73-b5b8-a6d1d6f3c812"),
                "john.doe@compira.co",
                "John",
                "Doe",
                "+573001112233",
                createdAt,
                updatedAt);
    }
}
