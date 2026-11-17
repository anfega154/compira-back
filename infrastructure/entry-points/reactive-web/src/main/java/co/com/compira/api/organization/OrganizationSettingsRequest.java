package co.com.compira.api.organization;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.ZoneId;

public record OrganizationSettingsRequest(@NotBlank(message = OrganizationSettingsRequest.REQUIRED_FIELD) @Size(max = 100, message = OrganizationSettingsRequest.ZONE_LENGTH) String timeZone,
                                          @NotNull(message = OrganizationSettingsRequest.REQUIRED_FIELD) Boolean notificationsEnabled) {
    private static final String REQUIRED_FIELD = "Campo obligatorio";
    private static final String ZONE_LENGTH = "La zona horaria no puede superar 100 caracteres";
    private static final String INVALID_ZONE = "Selecciona una zona horaria válida";
    @AssertTrue(message = OrganizationSettingsRequest.INVALID_ZONE)
    public boolean isTimeZoneValid() {
        return timeZone != null && ZoneId.getAvailableZoneIds().contains(timeZone);
    }
}
