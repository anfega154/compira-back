package co.com.compira.model.auth;

public record RegisterUserCommand(
        String email,
        String password,
        String firstName,
        String lastName,
        String phoneNumber,
        MfaChannel preferredMfaChannel) {
}
