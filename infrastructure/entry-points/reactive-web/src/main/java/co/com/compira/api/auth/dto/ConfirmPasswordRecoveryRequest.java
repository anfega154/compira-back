package co.com.compira.api.auth.dto;

import co.com.compira.api.auth.AuthenticationValidationMessage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@SuppressWarnings("java:S2068")
public record ConfirmPasswordRecoveryRequest(
        @NotBlank(message = AuthenticationValidationMessage.EMAIL_REQUIRED)
        @Email(message = AuthenticationValidationMessage.EMAIL_INVALID)
        String email,
        @NotBlank(message = AuthenticationValidationMessage.CHALLENGE_CODE_REQUIRED)
        String confirmationCode,
        @NotBlank(message = AuthenticationValidationMessage.NEW_PASSWORD_REQUIRED)
        @Size(min = 8, max = 128, message = AuthenticationValidationMessage.NEW_PASSWORD_LENGTH)
        String newPassword) {
}
