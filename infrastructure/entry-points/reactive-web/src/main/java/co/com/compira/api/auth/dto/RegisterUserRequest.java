package co.com.compira.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must contain between 8 and 128 characters")
        String password,
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name supports up to 100 characters")
        String firstName,
        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name supports up to 100 characters")
        String lastName,
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must use E.164 format")
        String phoneNumber,
        @NotBlank(message = "Preferred MFA channel is required")
        @Pattern(regexp = "^(EMAIL|SMS)$", message = "Preferred MFA channel must be EMAIL or SMS")
        String preferredMfaChannel) {
}
