package co.com.compira.usecase.user;

import co.com.compira.model.user.OrganizationUser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class UserAdminTestData {
    public static final String ADMIN_EMAIL = "admin@compira.co";
    public static final String TARGET_EMAIL = "collaborator@compira.co";
    public static final UUID TARGET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private UserAdminTestData() {
    }

    public static OrganizationUser organizationUser(List<String> roles) {
        return new OrganizationUser(
                TARGET_ID,
                TARGET_EMAIL,
                "Colab",
                "Uno",
                "+573001112233",
                "ACTIVE",
                roles,
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                "Operaciones",
                OffsetDateTime.parse("2026-09-20T10:00:00Z"),
                OffsetDateTime.parse("2026-09-24T08:00:00Z"));
    }
}
