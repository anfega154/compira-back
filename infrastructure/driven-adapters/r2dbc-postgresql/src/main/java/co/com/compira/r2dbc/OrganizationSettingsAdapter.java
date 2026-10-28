package co.com.compira.r2dbc;

import co.com.compira.model.organization.OrganizationSettings;
import co.com.compira.model.organization.gateways.OrganizationSettingsGateway;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class OrganizationSettingsAdapter implements OrganizationSettingsGateway {
    private static final String SELECT = "SELECT time_zone, notifications_enabled FROM organization_settings WHERE id = 1";
    private static final String UPDATE = """
            UPDATE organization_settings SET time_zone = :timeZone, notifications_enabled = :enabled
            WHERE id = 1 RETURNING time_zone, notifications_enabled
            """;
    private final DatabaseClient database;

    public OrganizationSettingsAdapter(DatabaseClient database) {
        this.database = database;
    }

    @Override
    public Mono<OrganizationSettings> get() {
        return database.sql(SELECT).map((row, metadata) -> new OrganizationSettings(
                row.get("time_zone", String.class), Boolean.TRUE.equals(row.get("notifications_enabled", Boolean.class)))).one();
    }

    @Override
    public Mono<OrganizationSettings> save(OrganizationSettings settings) {
        return database.sql(UPDATE).bind("timeZone", settings.timeZone()).bind("enabled", settings.notificationsEnabled())
                .map((row, metadata) -> new OrganizationSettings(row.get("time_zone", String.class),
                        Boolean.TRUE.equals(row.get("notifications_enabled", Boolean.class)))).one();
    }
}
