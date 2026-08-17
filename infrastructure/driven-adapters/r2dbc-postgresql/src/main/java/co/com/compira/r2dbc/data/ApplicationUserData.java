package co.com.compira.r2dbc.data;

import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.auth.UserStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record ApplicationUserData(
        UserData user,
        String cognitoSub,
        MfaChannel preferredMfaChannel,
        UserStatus status,
        List<String> roles,
        OffsetDateTime lastLoginAt) {
}
