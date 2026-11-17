package co.com.compira.r2dbc.data;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserData(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
