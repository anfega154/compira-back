package co.com.compira.api.auth.dto;

import co.com.compira.api.auth.AuthenticationValidationMessage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@SuppressWarnings("java:S2068")
public record RegisterUserRequest(
        @NotBlank(message = AuthenticationValidationMessage.EMAIL_REQUIRED)
        @Email(message = AuthenticationValidationMessage.EMAIL_INVALID)
        String email,
        @NotBlank(message = AuthenticationValidationMessage.PASSWORD_REQUIRED)
        @Size(min = 8, max = 128, message = AuthenticationValidationMessage.PASSWORD_LENGTH)
        String password,
        @NotBlank(message = AuthenticationValidationMessage.FIRST_NAME_REQUIRED)
        @Size(max = 100, message = AuthenticationValidationMessage.FIRST_NAME_LENGTH)
        String firstName,
        @NotBlank(message = AuthenticationValidationMessage.LAST_NAME_REQUIRED)
        @Size(max = 100, message = AuthenticationValidationMessage.LAST_NAME_LENGTH)
        String lastName,
        @NotBlank(message = AuthenticationValidationMessage.PHONE_NUMBER_REQUIRED)
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = AuthenticationValidationMessage.PHONE_NUMBER_INVALID)
        String phoneNumber,
        @NotBlank(message = AuthenticationValidationMessage.MFA_CHANNEL_REQUIRED)
        @Pattern(regexp = "^(EMAIL|SMS)$", message = AuthenticationValidationMessage.MFA_CHANNEL_INVALID)
        String preferredMfaChannel,
        @Pattern(regexp = "^(ADMINISTRATOR|COORDINATOR|COLLABORATOR)$", message = AuthenticationValidationMessage.ROLE_CODE_INVALID)
        String roleCode) {
}
