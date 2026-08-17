package co.com.compira.model.auth;

public record RespondAuthenticationChallengeCommand(
        String username,
        String session,
        AuthenticationChallengeName challengeName,
        String code,
        MfaChannel mfaChannel) {
}
