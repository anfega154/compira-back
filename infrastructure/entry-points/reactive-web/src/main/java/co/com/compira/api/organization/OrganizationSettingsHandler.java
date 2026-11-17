package co.com.compira.api.organization;

import co.com.compira.api.task.TaskErrorHandler;
import co.com.compira.api.task.TaskRequestValidator;
import co.com.compira.usecase.organizationsettings.OrganizationSettingsUseCase;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class OrganizationSettingsHandler {
    private static final String SETTINGS_REQUIRED = "La configuración es obligatoria";
    public static final String BASE = "/api/v1/organization/settings";
    private final OrganizationSettingsUseCase settings;
    private final OrganizationSettingsMapper mapper;
    private final TaskRequestValidator validator;
    private final TaskErrorHandler errors;

    public OrganizationSettingsHandler(OrganizationSettingsUseCase settings, OrganizationSettingsMapper mapper,
                                       TaskRequestValidator validator, TaskErrorHandler errors) {
        this.settings = settings;
        this.mapper = mapper;
        this.validator = validator;
        this.errors = errors;
    }

    public Mono<ServerResponse> get(ServerRequest request) {
        return request.principal().flatMap(principal -> settings.get(principal.getName()))
                .map(mapper::toResponse).flatMap(configuration -> ServerResponse.ok().bodyValue(configuration))
                .onErrorResume(errors::handle);
    }

    public Mono<ServerResponse> save(ServerRequest request) {
        return request.principal().flatMap(principal -> request.bodyToMono(OrganizationSettingsRequest.class)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(SETTINGS_REQUIRED)))
                        .flatMap(validator::validate)
                        .map(mapper::toDomain).flatMap(configuration -> settings.save(principal.getName(), configuration)))
                .map(mapper::toResponse).flatMap(configuration -> ServerResponse.ok().bodyValue(configuration))
                .onErrorResume(errors::handle);
    }
}
