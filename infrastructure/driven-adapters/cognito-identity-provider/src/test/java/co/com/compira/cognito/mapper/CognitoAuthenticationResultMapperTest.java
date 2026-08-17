package co.com.compira.cognito.mapper;

import co.com.compira.model.auth.AuthenticationStatus;
import co.com.compira.model.auth.MfaChannel;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CognitoAuthenticationResultMapperTest {
    private final CognitoAuthenticationResultMapper mapper = new CognitoAuthenticationResultMapper();

    @Test
    void shouldMapSuccessfulAuthentication() {
        InitiateAuthResponse response = InitiateAuthResponse.builder()
                .authenticationResult(AuthenticationResultType.builder()
                        .accessToken("access-token")
                        .idToken("id-token")
                        .refreshToken("refresh-token")
                        .expiresIn(3600)
                        .tokenType("Bearer")
                        .build())
                .build();

        var result = mapper.fromInitiateAuthResponse(response);

        assertEquals(AuthenticationStatus.AUTHENTICATED, result.status());
        assertEquals("access-token", result.tokens().accessToken());
        assertNull(result.challenge());
    }

    @Test
    void shouldMapChallengeAuthentication() {
        InitiateAuthResponse response = InitiateAuthResponse.builder()
                .challengeName("SELECT_MFA_TYPE")
                .session("challenge-session")
                .challengeParameters(Map.of("MFAS_CAN_CHOOSE", "[EMAIL_OTP,SMS_MFA]"))
                .availableChallenges(List.of(ChallengeNameType.EMAIL_OTP, ChallengeNameType.SMS_MFA))
                .build();

        var result = mapper.fromInitiateAuthResponse(response);

        assertEquals(AuthenticationStatus.CHALLENGE_REQUIRED, result.status());
        assertEquals(MfaChannel.EMAIL, result.challenge().availableMfaChannels().get(0));
        assertEquals(MfaChannel.SMS, result.challenge().availableMfaChannels().get(1));
        assertEquals("challenge-session", result.challenge().session());
    }
}
