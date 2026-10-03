package co.com.compira.api.user;

import co.com.compira.api.task.TaskErrorHandler;
import co.com.compira.api.task.TaskRequestValidator;
import co.com.compira.usecase.listusers.ListUsersUseCase;
import co.com.compira.usecase.updateuser.UpdateUserUseCase;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class UserAdminHandler {
    private static final String REQUIRED_FIELD = "Campo obligatorio";
    private static final String INVALID_EMAIL = "Correo inválido";
    private static final String REQUEST_REQUIRED = "Solicitud obligatoria";
    private static final String ROLES_REQUIRED = "Debe seleccionar al menos un rol";
    private static final String PASSWORD_LENGTH = "La contraseña temporal no cumple la longitud requerida";
    private static final int PASSWORD_MIN_LENGTH = 10;
    private static final int PASSWORD_MAX_LENGTH = 128;
    public static final String BASE = "/api/v1/users";
    public static final String ROLES = BASE + "/roles";
    public static final String PASSWORD_RESET = BASE + "/password-reset";

    private final ListUsersUseCase listUsers;
    private final UpdateUserUseCase updateUser;
    private final UserResponseMapper mapper;
    private final TaskRequestValidator validator;
    private final TaskErrorHandler errors;
    private final TransactionalOperator transactions;

    public UserAdminHandler(ListUsersUseCase listUsers, UpdateUserUseCase updateUser, UserResponseMapper mapper,
                            TaskRequestValidator validator, TaskErrorHandler errors, TransactionalOperator transactions) {
        this.listUsers = listUsers;
        this.updateUser = updateUser;
        this.mapper = mapper;
        this.validator = validator;
        this.errors = errors;
        this.transactions = transactions;
    }

    public record UpdateRolesRequest(
            @NotBlank(message = REQUIRED_FIELD) @Email(message = INVALID_EMAIL) String email,
            @NotEmpty(message = ROLES_REQUIRED) List<@NotBlank(message = REQUIRED_FIELD) String> roles) { }

    public record ResetPasswordRequest(
            @NotBlank(message = REQUIRED_FIELD) @Email(message = INVALID_EMAIL) String email,
            @NotBlank(message = REQUIRED_FIELD)
            @Size(min = PASSWORD_MIN_LENGTH, max = PASSWORD_MAX_LENGTH, message = PASSWORD_LENGTH) String temporaryPassword) { }

    public Mono<ServerResponse> list(ServerRequest request) {
        return request.principal()
                .flatMap(principal -> listUsers.execute(principal.getName()).map(mapper::toResponse).collectList())
                .flatMap(result -> ServerResponse.ok().bodyValue(result))
                .onErrorResume(errors::handle);
    }

    public Mono<ServerResponse> updateRoles(ServerRequest request) {
        return request.principal()
                .flatMap(principal -> body(request, UpdateRolesRequest.class)
                        .flatMap(input -> updateUser.updateRoles(principal.getName(), input.email(), input.roles())))
                .map(mapper::toResponse)
                .flatMap(user -> ServerResponse.ok().bodyValue(user))
                .as(transactions::transactional)
                .onErrorResume(errors::handle);
    }

    public Mono<ServerResponse> resetPassword(ServerRequest request) {
        return request.principal()
                .flatMap(principal -> body(request, ResetPasswordRequest.class)
                        .flatMap(input -> updateUser.resetPassword(principal.getName(), input.email(), input.temporaryPassword())))
                .then(ServerResponse.noContent().build())
                .onErrorResume(errors::handle);
    }

    private <T> Mono<T> body(ServerRequest request, Class<T> type) {
        return request.bodyToMono(type)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(REQUEST_REQUIRED)))
                .flatMap(validator::validate);
    }
}
