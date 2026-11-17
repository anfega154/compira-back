package co.com.compira.api.organization;

import co.com.compira.model.organization.OrganizationSettings;
import org.springframework.stereotype.Component;

@Component
public class OrganizationSettingsMapper {
    public OrganizationSettings toDomain(OrganizationSettingsRequest request) {
        return new OrganizationSettings(request.timeZone(), request.notificationsEnabled());
    }

    public OrganizationSettingsResponse toResponse(OrganizationSettings settings) {
        return new OrganizationSettingsResponse(settings.timeZone(), settings.notificationsEnabled());
    }
}
