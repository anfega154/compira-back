package co.com.compira.api.auth.dto;

public record AuthenticationTokenResponse(
        String accessToken,
        String idToken,
        String refreshToken,
        Integer expiresIn,
        String tokenType) {
}
