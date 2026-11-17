package co.com.compira.model.auth;

@SuppressWarnings("java:S2068")
public record ConfirmPasswordRecoveryCommand(String email, String confirmationCode, String newPassword) {
}
