package co.com.compira.model.auth;

import co.com.compira.model.user.User;

import java.time.OffsetDateTime;
import java.util.List;

public record ApplicationUser(
        User user,
        String cognitoSub,
        UserStatus status,
        MfaChannel preferredMfaChannel,
        List<String> roles,
        OffsetDateTime lastLoginAt) {
}
