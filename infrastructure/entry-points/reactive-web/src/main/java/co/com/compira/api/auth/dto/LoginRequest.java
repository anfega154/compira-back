package co.com.compira.api.auth.dto;

import co.com.compira.api.auth.AuthenticationValidationMessage;
import co.com.compira.api.auth.AuthenticationValidationPattern;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@SuppressWarnings("java:S2068")
public record LoginRequest(
        @NotBlank(message = AuthenticationValidationMessage.EMAIL_REQUIRED)
        @Email(regexp = AuthenticationValidationPattern.EMAIL, message = AuthenticationValidationMessage.EMAIL_INVALID)
        String email,
        @NotBlank(message = AuthenticationValidationMessage.PASSWORD_REQUIRED)
        String password) {
}
