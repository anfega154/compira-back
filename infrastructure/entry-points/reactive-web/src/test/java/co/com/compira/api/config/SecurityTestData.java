package co.com.compira.api.config;

import co.com.compira.model.auth.ApplicationUser;
import co.com.compira.model.auth.MfaChannel;
import co.com.compira.model.auth.UserStatus;
import co.com.compira.model.user.User;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SecurityTestData {
    public static final String SUBJECT = "test-subject";
    public static final String EMAIL = "verified@compira.co";
    private SecurityTestData() { }

    public static co.com.compira.model.team.Team team() {
        return new co.com.compira.model.team.Team(UUID.fromString("66666666-6666-6666-6666-666666666666"), "Operaciones",
                user("COORDINATOR", UserStatus.ACTIVE).user().id(), EMAIL);
    }

    public static Jwt expiredToken() {
        return claims("https://issuer.example", "expected-client", "access", Instant.now().minusSeconds(300));
    }

    public static Jwt token() {
        return Jwt.withTokenValue("valid-token").header("alg", "RS256").subject(SUBJECT)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
    }

    public static Jwt claims(String issuer, String clientId, String tokenUse, java.time.Instant expiration) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(SUBJECT).issuer(issuer)
                .claim("client_id", clientId).claim("token_use", tokenUse).expiresAt(expiration).build();
    }

    public static ApplicationUser user(String role, UserStatus status) {
        return new ApplicationUser(new User(UUID.fromString("33333333-3333-3333-3333-333333333333"), EMAIL,
                "Ana", "García", "+573001112233", null, null), SUBJECT, status, MfaChannel.EMAIL, List.of(role), null);
    }
}
