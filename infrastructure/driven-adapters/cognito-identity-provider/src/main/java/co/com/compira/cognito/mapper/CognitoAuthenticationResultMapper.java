package co.com.compira.cognito.mapper;

import co.com.compira.cognito.CognitoAuthenticationConstants;
import co.com.compira.model.auth.AuthenticationChallenge;
import co.com.compira.model.auth.AuthenticationChallengeName;
import co.com.compira.model.auth.AuthenticationResult;
import co.com.compira.model.auth.AuthenticationTokens;
import co.com.compira.model.auth.CodeDeliveryDetails;
import co.com.compira.model.auth.MfaChannel;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.RespondToAuthChallengeResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class CognitoAuthenticationResultMapper {
    public AuthenticationResult fromInitiateAuthResponse(InitiateAuthResponse response) {
        return fromAuthenticationResponse(
                response.authenticationResult(),
                response.challengeNameAsString(),
                response.session(),
                response.challengeParameters(),
                response.availableChallenges().stream().map(ChallengeNameType::toString).toList());
    }

    public AuthenticationResult fromRespondToChallengeResponse(RespondToAuthChallengeResponse response) {
        return fromAuthenticationResponse(
                response.authenticationResult(),
                response.challengeNameAsString(),
                response.session(),
                response.challengeParameters(),
                List.of());
    }

    private AuthenticationResult fromAuthenticationResponse(AuthenticationResultType authenticationResult,
                                                            String challengeName,
                                                            String session,
                                                            Map<String, String> challengeParameters,
                                                            List<String> availableChallenges) {
        if (authenticationResult != null) {
            return AuthenticationResult.authenticated(new AuthenticationTokens(
                    authenticationResult.accessToken(),
                    authenticationResult.idToken(),
                    authenticationResult.refreshToken(),
                    authenticationResult.expiresIn(),
                    authenticationResult.tokenType()));
        }

        return AuthenticationResult.challengeRequired(new AuthenticationChallenge(
                AuthenticationChallengeName.fromValue(challengeName),
                session,
                mapAvailableMfaChannels(challengeParameters, availableChallenges),
                mapCodeDeliveryDetails(challengeParameters)));
    }

    private List<MfaChannel> mapAvailableMfaChannels(Map<String, String> challengeParameters,
                                                     List<String> availableChallenges) {
        if (availableChallenges != null && !availableChallenges.isEmpty()) {
            return availableChallenges.stream()
                    .filter(value -> AuthenticationChallengeName.EMAIL_OTP.name().equalsIgnoreCase(value)
                            || AuthenticationChallengeName.SMS_MFA.name().equalsIgnoreCase(value))
                    .map(AuthenticationChallengeName::fromValue)
                    .map(this::toMfaChannel)
                    .toList();
        }

        String availableMfas = challengeParameters.get(CognitoAuthenticationConstants.MFA_OPTIONS_PARAMETER);
        if (availableMfas == null || availableMfas.isBlank()) {
            return List.of();
        }

        return Arrays.stream(availableMfas.replace("[", "").replace("]", "").split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(AuthenticationChallengeName::fromValue)
                .map(this::toMfaChannel)
                .toList();
    }

    private MfaChannel toMfaChannel(AuthenticationChallengeName challengeName) {
        return AuthenticationChallengeName.EMAIL_OTP.equals(challengeName) ? MfaChannel.EMAIL : MfaChannel.SMS;
    }

    private CodeDeliveryDetails mapCodeDeliveryDetails(Map<String, String> challengeParameters) {
        String destination = challengeParameters.get(CognitoAuthenticationConstants.CHALLENGE_DELIVERY_DESTINATION_PARAMETER);
        String deliveryMedium = challengeParameters.get(CognitoAuthenticationConstants.CHALLENGE_DELIVERY_MEDIUM_PARAMETER);
        return destination == null && deliveryMedium == null
                ? null
                : new CodeDeliveryDetails(destination, deliveryMedium, null);
    }
}
