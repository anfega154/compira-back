package co.com.compira.model.auth;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ApplicationUser(
        UUID id,
        String cognitoSub,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        MfaChannel preferredMfaChannel,
        UserStatus status,
        List<String> roles,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime lastLoginAt) {
}
