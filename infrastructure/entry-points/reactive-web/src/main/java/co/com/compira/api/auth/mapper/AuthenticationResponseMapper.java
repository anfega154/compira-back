package co.com.compira.api.auth.mapper;

import co.com.compira.api.auth.dto.ApplicationUserResponse;
import co.com.compira.api.auth.dto.AuthenticationChallengeResponse;
import co.com.compira.api.auth.dto.AuthenticationResponse;
import co.com.compira.api.auth.dto.AuthenticationTokenResponse;
import co.com.compira.api.auth.dto.CodeDeliveryDetailsResponse;
import co.com.compira.api.auth.dto.PasswordRecoveryResponse;
import co.com.compira.api.auth.dto.UserRegistrationResponse;
import co.com.compira.model.auth.ApplicationUser;
import co.com.compira.model.auth.AuthenticationChallenge;
import co.com.compira.model.auth.AuthenticationResult;
import co.com.compira.model.auth.AuthenticationTokens;
import co.com.compira.model.auth.CodeDeliveryDetails;
import co.com.compira.model.auth.PasswordRecoveryResult;
import co.com.compira.model.auth.UserRegistrationResult;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationResponseMapper {
    public UserRegistrationResponse toResponse(UserRegistrationResult result) {
        return new UserRegistrationResponse(
                result.cognitoSub(),
                result.userConfirmed(),
                mapCodeDeliveryDetails(result.codeDeliveryDetails()));
    }

    public ApplicationUserResponse toResponse(ApplicationUser applicationUser) {
        return new ApplicationUserResponse(
                applicationUser.id(),
                applicationUser.cognitoSub(),
                applicationUser.email(),
                applicationUser.firstName(),
                applicationUser.lastName(),
                applicationUser.phoneNumber(),
                applicationUser.preferredMfaChannel().name(),
                applicationUser.status().name(),
                applicationUser.roles(),
                applicationUser.createdAt(),
                applicationUser.updatedAt(),
                applicationUser.lastLoginAt());
    }

    public AuthenticationResponse toResponse(AuthenticationResult result) {
        return new AuthenticationResponse(
                result.status().name(),
                result.user() == null ? null : toResponse(result.user()),
                mapTokens(result.tokens()),
                mapChallenge(result.challenge()));
    }

    public PasswordRecoveryResponse toResponse(PasswordRecoveryResult result) {
        return new PasswordRecoveryResponse(mapCodeDeliveryDetails(result.codeDeliveryDetails()));
    }

    private AuthenticationTokenResponse mapTokens(AuthenticationTokens tokens) {
        return tokens == null
                ? null
                : new AuthenticationTokenResponse(
                tokens.accessToken(),
                tokens.idToken(),
                tokens.refreshToken(),
                tokens.expiresIn(),
                tokens.tokenType());
    }

    private AuthenticationChallengeResponse mapChallenge(AuthenticationChallenge challenge) {
        return challenge == null
                ? null
                : new AuthenticationChallengeResponse(
                challenge.challengeName().name(),
                challenge.session(),
                challenge.availableMfaChannels().stream().map(Enum::name).toList(),
                mapCodeDeliveryDetails(challenge.codeDeliveryDetails()));
    }

    private CodeDeliveryDetailsResponse mapCodeDeliveryDetails(CodeDeliveryDetails codeDeliveryDetails) {
        return codeDeliveryDetails == null
                ? null
                : new CodeDeliveryDetailsResponse(
                codeDeliveryDetails.destination(),
                codeDeliveryDetails.deliveryMedium(),
                codeDeliveryDetails.attributeName());
    }
}
