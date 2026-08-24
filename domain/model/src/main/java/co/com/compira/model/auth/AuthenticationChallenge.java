package co.com.compira.model.auth;

import java.util.List;

public record AuthenticationChallenge(
        AuthenticationChallengeName challengeName,
        String session,
        List<MfaChannel> availableMfaChannels,
        CodeDeliveryDetails codeDeliveryDetails) {
}
