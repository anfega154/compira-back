package co.com.compira.api.auth.dto;

import co.com.compira.api.auth.AuthenticationValidationMessage;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = AuthenticationValidationMessage.ACCESS_TOKEN_REQUIRED)
        String accessToken) {
}
