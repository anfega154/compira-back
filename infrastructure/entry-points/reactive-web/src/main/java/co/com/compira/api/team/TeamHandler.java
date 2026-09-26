package co.com.compira.api.team;

import co.com.compira.api.task.TaskErrorHandler;
import co.com.compira.api.task.TaskRequestValidator;
import co.com.compira.usecase.teams.TeamsUseCase;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Component
public class TeamHandler {
    private static final String REQUIRED_FIELD = "Campo obligatorio";
    private static final String INVALID_EMAIL = "Correo inválido";
    private static final String REQUEST_REQUIRED = "Solicitud obligatoria";
    public static final String BASE = "/api/v1/teams";
    public static final String COORDINATOR = BASE + "/{teamId}/coordinator";
    public static final String MEMBERS = BASE + "/{teamId}/members";
    public static final String TASKS = BASE + "/{teamId}/tasks";
    private static final String TEAM_ID = "teamId";
    private final TeamsUseCase teams;
    private final TaskRequestValidator validator;
    private final TaskErrorHandler errors;
    private final TransactionalOperator transactions;
    private final TeamResponseMapper mapper;

    public TeamHandler(TeamsUseCase teams, TaskRequestValidator validator, TaskErrorHandler errors, TransactionalOperator transactions, TeamResponseMapper mapper) {
        this.teams = teams;
        this.validator = validator;
        this.errors = errors;
        this.transactions = transactions;
        this.mapper = mapper;
    }

    public record CreateTeamRequest(@NotBlank(message = REQUIRED_FIELD) String name, @NotBlank(message = REQUIRED_FIELD) @Email(message = INVALID_EMAIL) String coordinatorEmail) { }
    public record UserEmailRequest(@NotBlank(message = REQUIRED_FIELD) @Email(message = INVALID_EMAIL) String email) { }
    public record LinkTaskRequest(@NotNull(message = REQUIRED_FIELD) UUID taskId) { }

    public Mono<ServerResponse> list(ServerRequest request) {
        return request.principal().flatMap(principal -> teams.list(principal.getName()).map(mapper::toResponse).collectList())
                .flatMap(result -> ServerResponse.ok().bodyValue(result)).onErrorResume(errors::handle);
    }

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.principal().flatMap(principal -> body(request, CreateTeamRequest.class)
                        .flatMap(input -> teams.create(principal.getName(), input.name().trim(), input.coordinatorEmail())))
                .map(mapper::toResponse).flatMap(team -> ServerResponse.status(201).bodyValue(team))
                .as(transactions::transactional).onErrorResume(errors::handle);
    }

    public Mono<ServerResponse> changeCoordinator(ServerRequest request) {
        return request.principal().flatMap(principal -> body(request, UserEmailRequest.class)
                        .flatMap(input -> teams.changeCoordinator(principal.getName(), UUID.fromString(request.pathVariable(TEAM_ID)), input.email())))
                .map(mapper::toResponse).flatMap(team -> ServerResponse.ok().bodyValue(team))
                .as(transactions::transactional).onErrorResume(errors::handle);
    }

    public Mono<ServerResponse> addMember(ServerRequest request) {
        return request.principal().flatMap(principal -> body(request, UserEmailRequest.class)
                        .flatMap(input -> teams.addMember(principal.getName(), UUID.fromString(request.pathVariable(TEAM_ID)), input.email())))
                .then(ServerResponse.noContent().build()).as(transactions::transactional).onErrorResume(errors::handle);
    }

    public Mono<ServerResponse> linkTask(ServerRequest request) {
        return request.principal().flatMap(principal -> body(request, LinkTaskRequest.class)
                        .flatMap(input -> teams.linkExistingTask(principal.getName(), input.taskId(), UUID.fromString(request.pathVariable(TEAM_ID)))))
                .then(ServerResponse.noContent().build()).as(transactions::transactional).onErrorResume(errors::handle);
    }

    private <T> Mono<T> body(ServerRequest request, Class<T> type) {
        return request.bodyToMono(type).switchIfEmpty(Mono.error(new IllegalArgumentException(REQUEST_REQUIRED)))
                .flatMap(validator::validate);
    }
}
