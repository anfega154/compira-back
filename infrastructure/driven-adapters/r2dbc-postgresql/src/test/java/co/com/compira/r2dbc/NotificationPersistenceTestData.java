package co.com.compira.r2dbc;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class NotificationPersistenceTestData {
    public static final UUID COORDINATOR = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID CURRENT_COORDINATOR = UUID.fromString("66666666-6666-6666-6666-666666666666");
    public static final UUID COLLABORATOR = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID TEAM = UUID.fromString("55555555-5555-5555-5555-555555555555");
    public static final UUID TASK = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-26T12:00:00Z");
    public static final String FIXTURES = """
            INSERT INTO users (id, cognito_sub, email, phone_number, preferred_mfa_channel, status) VALUES
              ('22222222-2222-2222-2222-222222222222', 'coordinator', 'coordinator@compira.co', '+573001112233', 'EMAIL', 'ACTIVE'),
              ('66666666-6666-6666-6666-666666666666', 'current', 'current@compira.co', '+573001112234', 'EMAIL', 'ACTIVE'),
              ('33333333-3333-3333-3333-333333333333', 'collaborator', 'collaborator@compira.co', '+573001112235', 'EMAIL', 'ACTIVE');
            INSERT INTO teams (id, name, coordinator_user_id) VALUES
              ('55555555-5555-5555-5555-555555555555', 'Operaciones', '66666666-6666-6666-6666-666666666666');
            INSERT INTO team_members (user_id, team_id) VALUES
              ('33333333-3333-3333-3333-333333333333', '55555555-5555-5555-5555-555555555555');
            INSERT INTO tasks (id, title, due_date, responsible_user_id, created_by_user_id) VALUES
              ('11111111-1111-1111-1111-111111111111', 'Informe', '2026-09-27T12:00:00Z', '33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222');
            INSERT INTO task_teams (task_id, team_id) VALUES
              ('11111111-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555');
            """;
    private NotificationPersistenceTestData() { }
}
