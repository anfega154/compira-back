package co.com.compira.model.auth;

public record ConfirmPasswordRecoveryCommand(String email, String confirmationCode, String newPassword) {
}
