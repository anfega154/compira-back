package co.com.compira.api.auth.dto;

public record AuthenticationResponse(
        String status,
        ApplicationUserResponse user,
        AuthenticationTokenResponse tokens,
        AuthenticationChallengeResponse challenge) {
}
