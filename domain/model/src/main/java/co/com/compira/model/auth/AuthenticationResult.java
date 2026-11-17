package co.com.compira.model.auth;

public record AuthenticationResult(
        AuthenticationStatus status,
        ApplicationUser user,
        AuthenticationTokens tokens,
        AuthenticationChallenge challenge) {

    public static AuthenticationResult authenticated(AuthenticationTokens tokens) {
        return new AuthenticationResult(AuthenticationStatus.AUTHENTICATED, null, tokens, null);
    }

    public static AuthenticationResult challengeRequired(AuthenticationChallenge challenge) {
        return new AuthenticationResult(AuthenticationStatus.CHALLENGE_REQUIRED, null, null, challenge);
    }

    public AuthenticationResult withUser(ApplicationUser applicationUser) {
        return new AuthenticationResult(status, applicationUser, tokens, challenge);
    }
}
