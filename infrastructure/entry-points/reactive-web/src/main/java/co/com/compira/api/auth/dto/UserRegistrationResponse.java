package co.com.compira.api.auth.dto;

public record UserRegistrationResponse(
        String cognitoSub,
        String username,
        String userStatus) {
}
