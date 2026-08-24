package co.com.compira.model.auth;

@SuppressWarnings("java:S2068")
public record RespondAuthenticationChallengeCommand(
        String username,
        String session,
        AuthenticationChallengeName challengeName,
        String code,
        MfaChannel mfaChannel,
        String newPassword) {
}
