package co.com.compira.api.auth.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ApplicationUserResponse(
        UUID id,
        String cognitoSub,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String preferredMfaChannel,
        String status,
        List<String> roles,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastLoginAt) {
}
