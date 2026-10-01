package co.com.compira.model.user;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrganizationUser(
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
