package co.com.compira.r2dbc.data;

import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.auth.UserStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ApplicationUserData(
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
