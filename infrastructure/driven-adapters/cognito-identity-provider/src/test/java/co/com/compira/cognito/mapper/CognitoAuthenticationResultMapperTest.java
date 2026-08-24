package co.com.compira.cognito.mapper;

import co.com.compira.model.auth.AuthenticationChallengeName;
import co.com.compira.model.auth.AuthenticationResult;
import co.com.compira.model.auth.AuthenticationStatus;
import co.com.compira.model.auth.MfaChannel;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeResponse;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CognitoAuthenticationResultMapperTest {
    private final CognitoAuthenticationResultMapper mapper = new CognitoAuthenticationResultMapper();

    @Test
    void shouldMapInitiateAuthResponseWithTokens() {
        InitiateAuthResponse response = InitiateAuthResponse.builder()
                .authenticationResult(AuthenticationResultType.builder()
                        .accessToken("access")
                        .idToken("id")
                        .refreshToken("refresh")
                        .expiresIn(3600)
                        .tokenType("Bearer")
                        .build())
                .build();

        AuthenticationResult result = mapper.fromInitiateAuthResponse(response);

        assertEquals(AuthenticationStatus.AUTHENTICATED, result.status());
        assertNotNull(result.tokens());
        assertEquals("access", result.tokens().accessToken());
        assertEquals("id", result.tokens().idToken());
        assertEquals("refresh", result.tokens().refreshToken());
        assertEquals(3600, result.tokens().expiresIn());
        assertEquals("Bearer", result.tokens().tokenType());
        assertNull(result.challenge());
    }

    @Test
    void shouldMapInitiateAuthResponseWithEmailOtpChallenge() {
        InitiateAuthResponse response = InitiateAuthResponse.builder()
                .challengeName("EMAIL_OTP")
                .session("session-abc")
                .challengeParameters(Map.of(
                        "CODE_DELIVERY_DESTINATION", "j***@test.com",
                        "CODE_DELIVERY_DELIVERY_MEDIUM", "EMAIL"))
                .build();

        AuthenticationResult result = mapper.fromInitiateAuthResponse(response);

        assertEquals(AuthenticationStatus.CHALLENGE_REQUIRED, result.status());
        assertNull(result.tokens());
        assertNotNull(result.challenge());
        assertEquals(AuthenticationChallengeName.EMAIL_OTP, result.challenge().challengeName());
        assertEquals("session-abc", result.challenge().session());
        assertNotNull(result.challenge().codeDeliveryDetails());
        assertEquals("j***@test.com", result.challenge().codeDeliveryDetails().destination());
        assertEquals("EMAIL", result.challenge().codeDeliveryDetails().deliveryMedium());
    }

    @Test
    void shouldMapInitiateAuthResponseWithNewPasswordRequired() {
        InitiateAuthResponse response = InitiateAuthResponse.builder()
                .challengeName("NEW_PASSWORD_REQUIRED")
                .session("session-pwd")
                .challengeParameters(Map.of())
                .build();

        AuthenticationResult result = mapper.fromInitiateAuthResponse(response);

        assertEquals(AuthenticationStatus.CHALLENGE_REQUIRED, result.status());
        assertEquals(AuthenticationChallengeName.NEW_PASSWORD_REQUIRED, result.challenge().challengeName());
        assertEquals("session-pwd", result.challenge().session());
    }

    @Test
    void shouldMapInitiateAuthResponseWithSelectMfaType() {
        InitiateAuthResponse response = InitiateAuthResponse.builder()
                .challengeName("SELECT_MFA_TYPE")
                .session("session-mfa")
                .challengeParameters(Map.of("MFAS_CAN_CHOOSE", "[EMAIL_OTP, SMS_MFA]"))
                .availableChallenges(List.of(ChallengeNameType.EMAIL_OTP, ChallengeNameType.SMS_MFA))
                .build();

        AuthenticationResult result = mapper.fromInitiateAuthResponse(response);

        assertEquals(AuthenticationStatus.CHALLENGE_REQUIRED, result.status());
        assertEquals(AuthenticationChallengeName.SELECT_MFA_TYPE, result.challenge().challengeName());
        assertEquals(2, result.challenge().availableMfaChannels().size());
        assertEquals(MfaChannel.EMAIL, result.challenge().availableMfaChannels().get(0));
        assertEquals(MfaChannel.SMS, result.challenge().availableMfaChannels().get(1));
    }

    @Test
    void shouldMapRespondToChallengeResponseWithTokens() {
        RespondToAuthChallengeResponse response = RespondToAuthChallengeResponse.builder()
                .authenticationResult(AuthenticationResultType.builder()
                        .accessToken("at")
                        .idToken("it")
                        .refreshToken("rt")
                        .expiresIn(1800)
                        .tokenType("Bearer")
                        .build())
                .build();

        AuthenticationResult result = mapper.fromRespondToChallengeResponse(response);

        assertEquals(AuthenticationStatus.AUTHENTICATED, result.status());
        assertEquals("at", result.tokens().accessToken());
    }

    @Test
    void shouldMapRespondToChallengeResponseWithAnotherChallenge() {
        RespondToAuthChallengeResponse response = RespondToAuthChallengeResponse.builder()
                .challengeName("EMAIL_OTP")
                .session("chained-session")
                .challengeParameters(Map.of(
                        "CODE_DELIVERY_DESTINATION", "a***@test.co",
                        "CODE_DELIVERY_DELIVERY_MEDIUM", "EMAIL"))
                .build();

        AuthenticationResult result = mapper.fromRespondToChallengeResponse(response);

        assertEquals(AuthenticationStatus.CHALLENGE_REQUIRED, result.status());
        assertEquals(AuthenticationChallengeName.EMAIL_OTP, result.challenge().challengeName());
        assertEquals("chained-session", result.challenge().session());
    }

    @Test
    void shouldMapSelectMfaFromChallengeParameters() {
        InitiateAuthResponse response = InitiateAuthResponse.builder()
                .challengeName("SELECT_MFA_TYPE")
                .session("session")
                .challengeParameters(Map.of("MFAS_CAN_CHOOSE", "[EMAIL_OTP, SMS_MFA]"))
                .availableChallenges(List.of())
                .build();

        AuthenticationResult result = mapper.fromInitiateAuthResponse(response);

        assertEquals(2, result.challenge().availableMfaChannels().size());
    }

    @Test
    void shouldHandleEmptyMfaOptions() {
        InitiateAuthResponse response = InitiateAuthResponse.builder()
                .challengeName("EMAIL_OTP")
                .session("session")
                .challengeParameters(Map.of())
                .availableChallenges(List.of())
                .build();

        AuthenticationResult result = mapper.fromInitiateAuthResponse(response);

        assertEquals(0, result.challenge().availableMfaChannels().size());
        assertNull(result.challenge().codeDeliveryDetails());
    }
}
