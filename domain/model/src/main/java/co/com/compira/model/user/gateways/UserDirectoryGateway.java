package co.com.compira.model.user.gateways;

import co.com.compira.model.user.OrganizationUser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface UserDirectoryGateway {
    Flux<OrganizationUser> findAll();

    Mono<OrganizationUser> findByEmail(String email);

    Mono<OrganizationUser> replaceRoles(String email, List<String> roleCodes);
}
