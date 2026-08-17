package co.com.compira.api.auth.dto;

import co.com.compira.api.auth.AuthenticationValidationMessage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RespondAuthenticationChallengeRequest(
        @NotBlank(message = AuthenticationValidationMessage.EMAIL_REQUIRED)
        @Email(message = AuthenticationValidationMessage.EMAIL_INVALID)
        String email,
        @NotBlank(message = AuthenticationValidationMessage.SESSION_REQUIRED)
        String session,
        @NotBlank(message = AuthenticationValidationMessage.CHALLENGE_NAME_REQUIRED)
        @Pattern(regexp = "^(EMAIL_OTP|SMS_MFA|SELECT_MFA_TYPE|SOFTWARE_TOKEN_MFA|NEW_PASSWORD_REQUIRED)$",
                message = AuthenticationValidationMessage.CHALLENGE_NAME_INVALID)
        String challengeName,
        String code,
        @Pattern(regexp = "^(EMAIL|SMS)?$", message = AuthenticationValidationMessage.MFA_CHANNEL_INVALID)
        String mfaChannel) {
}
