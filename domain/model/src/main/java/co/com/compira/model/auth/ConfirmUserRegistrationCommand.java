package co.com.compira.model.auth;

public record ConfirmUserRegistrationCommand(String email, String confirmationCode) {
}
