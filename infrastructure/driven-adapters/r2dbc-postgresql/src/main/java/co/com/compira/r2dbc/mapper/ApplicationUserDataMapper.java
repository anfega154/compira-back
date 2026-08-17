package co.com.compira.r2dbc.mapper;

import co.com.compira.model.auth.ApplicationUser;
import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.auth.UserStatus;
import co.com.compira.r2dbc.data.ApplicationUserData;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ApplicationUserDataMapper {
    public ApplicationUserData fromRow(Map<String, Object> row, List<String> roles) {
        return new ApplicationUserData(
                getUuid(row, "id"),
                getString(row, "cognito_sub"),
                getString(row, "email"),
                getString(row, "first_name"),
                getString(row, "last_name"),
                getString(row, "phone_number"),
                getString(row, "preferred_mfa_channel") == null ? null : MfaChannel.fromValue(getString(row, "preferred_mfa_channel")),
                UserStatus.valueOf(getString(row, "status")),
                roles,
                getOffsetDateTime(row, "created_at"),
                getOffsetDateTime(row, "updated_at"),
                getOffsetDateTime(row, "last_login_at"));
    }

    public ApplicationUser toDomain(ApplicationUserData data) {
        return new ApplicationUser(
                data.id(),
                data.cognitoSub(),
                data.email(),
                data.firstName(),
                data.lastName(),
                data.phoneNumber(),
                data.preferredMfaChannel(),
                data.status(),
                data.roles(),
                data.createdAt(),
                data.updatedAt(),
                data.lastLoginAt());
    }

    private UUID getUuid(Map<String, Object> row, String key) {
        return (UUID) row.get(key);
    }

    private String getString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private OffsetDateTime getOffsetDateTime(Map<String, Object> row, String key) {
        return (OffsetDateTime) row.get(key);
    }
}
