package co.com.compira.model.organization.gateways;

import co.com.compira.model.organization.OrganizationSettings;
import reactor.core.publisher.Mono;

public interface OrganizationSettingsGateway {
    Mono<OrganizationSettings> get();
    Mono<OrganizationSettings> save(OrganizationSettings settings);
}
