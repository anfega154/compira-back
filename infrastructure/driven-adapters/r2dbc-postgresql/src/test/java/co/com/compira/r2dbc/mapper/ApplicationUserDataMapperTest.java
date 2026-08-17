package co.com.compira.r2dbc.mapper;

import co.com.compira.model.auth.MfaChannel;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationUserDataMapperTest {
    private final ApplicationUserDataMapper mapper = new ApplicationUserDataMapper();

    @Test
    void shouldMapDatabaseRowToDomainUser() {
        UUID userId = UUID.fromString("7ab78f2a-35fe-48dd-8c33-a6b82f6d5c56");
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-16T12:00:00Z");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-08-16T12:10:00Z");

        Map<String, Object> row = Map.ofEntries(
                Map.entry("id", userId),
                Map.entry("cognito_sub", "cognito-sub-123"),
                Map.entry("email", "john.doe@compira.co"),
                Map.entry("first_name", "John"),
                Map.entry("last_name", "Doe"),
                Map.entry("phone_number", "+573001112233"),
                Map.entry("preferred_mfa_channel", "EMAIL"),
                Map.entry("status", "ACTIVE"),
                Map.entry("created_at", createdAt),
                Map.entry("updated_at", updatedAt),
                Map.entry("last_login_at", updatedAt));

        var domainUser = mapper.toDomain(mapper.fromRow(row, List.of("USER")));

        assertEquals(userId, domainUser.id());
        assertEquals(MfaChannel.EMAIL, domainUser.preferredMfaChannel());
        assertEquals("USER", domainUser.roles().getFirst());
    }
}
