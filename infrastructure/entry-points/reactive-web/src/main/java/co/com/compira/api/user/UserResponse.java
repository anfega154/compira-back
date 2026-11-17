package co.com.compira.api.user;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String status,
        List<String> roles,
        UUID teamId,
        String teamName,
        OffsetDateTime createdAt,
        OffsetDateTime lastLoginAt) {
}
