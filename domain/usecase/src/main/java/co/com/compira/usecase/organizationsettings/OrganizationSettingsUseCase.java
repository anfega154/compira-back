package co.com.compira.usecase.organizationsettings;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.organization.OrganizationSettings;
import co.com.compira.model.organization.gateways.OrganizationSettingsGateway;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import reactor.core.publisher.Mono;

public class OrganizationSettingsUseCase {
    private static final String FORBIDDEN_CODE = "SETTINGS_001";
    private static final String FORBIDDEN = "Acceso no permitido";
    private final OrganizationSettingsGateway settings;
    private final TaskAuthorization authorization;

    public OrganizationSettingsUseCase(OrganizationSettingsGateway settings, TaskUserDirectoryGateway users) {
        this.settings = settings;
        this.authorization = new TaskAuthorization(users);
    }

    public Mono<OrganizationSettings> get(String email) {
        return requireAdministrator(email).then(settings.get());
    }

    public Mono<OrganizationSettings> save(String email, OrganizationSettings configuration) {
        return requireAdministrator(email).then(settings.save(configuration));
    }

    private Mono<Void> requireAdministrator(String email) {
        return authorization.requireActor(email)
                .filter(user -> user.hasRole(RoleCode.ADMINISTRATOR.name()))
                .switchIfEmpty(Mono.error(new CompiraException(FORBIDDEN_CODE, FORBIDDEN, ErrorCategory.FORBIDDEN)))
                .then();
    }
}
