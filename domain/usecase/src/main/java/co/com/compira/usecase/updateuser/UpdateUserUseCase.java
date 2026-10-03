package co.com.compira.usecase.updateuser;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.auth.gateways.AuthenticationGateway;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.model.user.OrganizationUser;
import co.com.compira.model.user.gateways.UserDirectoryGateway;
import reactor.core.publisher.Mono;

import java.util.List;

public class UpdateUserUseCase {
    private static final String FORBIDDEN_CODE = "USER_ADMIN_403";
    private static final String FORBIDDEN_MESSAGE = "La gestión de usuarios es exclusiva del Administrador";
    private static final String ROLES_REQUIRED_CODE = "USER_ADMIN_422";
    private static final String ROLES_REQUIRED_MESSAGE = "El usuario debe conservar al menos un rol";
    private static final String INVALID_ROLE_CODE = "USER_ADMIN_400";
    private static final String INVALID_ROLE_MESSAGE = "Rol inválido";
    private static final String USER_NOT_FOUND_CODE = "USER_ADMIN_404";
    private static final String USER_NOT_FOUND_MESSAGE = "Usuario no encontrado";

    private final UserDirectoryGateway userDirectoryGateway;
    private final AuthenticationGateway authenticationGateway;
    private final TaskAuthorization authorization;

    public UpdateUserUseCase(UserDirectoryGateway userDirectoryGateway,
                             AuthenticationGateway authenticationGateway,
                             TaskUserDirectoryGateway userDirectory) {
        this.userDirectoryGateway = userDirectoryGateway;
        this.authenticationGateway = authenticationGateway;
        this.authorization = new TaskAuthorization(userDirectory);
    }

    public Mono<OrganizationUser> updateRoles(String actorEmail, String targetEmail, List<String> roleCodes) {
        return requireAdministrator(actorEmail)
                .then(Mono.defer(() -> validateRoles(roleCodes)))
                .then(Mono.defer(() -> requireExistingUser(targetEmail)))
                .flatMap(existing -> userDirectoryGateway.replaceRoles(targetEmail, normalize(roleCodes)));
    }

    public Mono<Void> resetPassword(String actorEmail, String targetEmail, String temporaryPassword) {
        return requireAdministrator(actorEmail)
                .then(Mono.defer(() -> requireExistingUser(targetEmail)))
                .flatMap(existing -> authenticationGateway.resetUserPassword(existing.email(), temporaryPassword));
    }

    private Mono<Void> validateRoles(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Mono.error(new CompiraException(ROLES_REQUIRED_CODE, ROLES_REQUIRED_MESSAGE, ErrorCategory.BAD_REQUEST));
        }
        return Mono.fromRunnable(() -> normalize(roleCodes).forEach(RoleCode::fromValue))
                .onErrorMap(error -> error instanceof CompiraException ? error
                        : new CompiraException(INVALID_ROLE_CODE, INVALID_ROLE_MESSAGE, ErrorCategory.BAD_REQUEST))
                .then();
    }

    private List<String> normalize(List<String> roleCodes) {
        return roleCodes.stream().filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase()).distinct().toList();
    }

    private Mono<OrganizationUser> requireExistingUser(String email) {
        return userDirectoryGateway.findByEmail(email)
                .switchIfEmpty(Mono.error(new CompiraException(
                        USER_NOT_FOUND_CODE, USER_NOT_FOUND_MESSAGE, ErrorCategory.NOT_FOUND)));
    }

    private Mono<Void> requireAdministrator(String actorEmail) {
        return authorization.requireActor(actorEmail)
                .filter(actor -> actor.hasRole(RoleCode.ADMINISTRATOR.name()))
                .switchIfEmpty(Mono.error(new CompiraException(
                        FORBIDDEN_CODE, FORBIDDEN_MESSAGE, ErrorCategory.FORBIDDEN)))
                .then();
    }
}
