package co.com.compira.api.auth.dto;

import java.util.List;

public record AuthenticationChallengeResponse(
        String challengeName,
        String session,
        List<String> availableMfaChannels,
        CodeDeliveryDetailsResponse codeDeliveryDetails) {
}
