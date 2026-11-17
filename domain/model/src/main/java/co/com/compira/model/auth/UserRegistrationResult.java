package co.com.compira.model.auth;

public record UserRegistrationResult(
        String cognitoSub,
        String username,
        String userStatus) {
}
