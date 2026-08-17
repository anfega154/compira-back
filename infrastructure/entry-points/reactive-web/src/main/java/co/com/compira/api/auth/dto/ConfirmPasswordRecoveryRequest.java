package co.com.compira.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmPasswordRecoveryRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,
        @NotBlank(message = "Confirmation code is required")
        String confirmationCode,
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 128, message = "New password must contain between 8 and 128 characters")
        String newPassword) {
}
