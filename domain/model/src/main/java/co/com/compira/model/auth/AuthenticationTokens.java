package co.com.compira.model.auth;

public record AuthenticationTokens(
        String accessToken,
        String idToken,
        String refreshToken,
        Integer expiresIn,
        String tokenType) {
}
