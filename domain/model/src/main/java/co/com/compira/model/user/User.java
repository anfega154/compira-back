package co.com.compira.model.user;

import java.time.OffsetDateTime;
import java.util.UUID;

public record User(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
