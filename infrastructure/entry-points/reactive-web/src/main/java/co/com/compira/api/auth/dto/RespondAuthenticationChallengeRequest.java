package co.com.compira.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RespondAuthenticationChallengeRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,
        @NotBlank(message = "Session is required")
        String session,
        @NotBlank(message = "Challenge name is required")
        @Pattern(regexp = "^(EMAIL_OTP|SMS_MFA|SELECT_MFA_TYPE|SOFTWARE_TOKEN_MFA|NEW_PASSWORD_REQUIRED)$",
                message = "Challenge name is not supported")
        String challengeName,
        String code,
        @Pattern(regexp = "^(EMAIL|SMS)?$", message = "MFA channel must be EMAIL or SMS")
        String mfaChannel) {
}
