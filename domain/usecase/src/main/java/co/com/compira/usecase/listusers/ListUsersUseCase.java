package co.com.compira.usecase.listusers;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.model.user.OrganizationUser;
import co.com.compira.model.user.gateways.UserDirectoryGateway;
import reactor.core.publisher.Flux;

public class ListUsersUseCase {
    private static final String FORBIDDEN_CODE = "USER_ADMIN_403";
    private static final String FORBIDDEN_MESSAGE = "La gestión de usuarios es exclusiva del Administrador";

    private final UserDirectoryGateway userDirectoryGateway;
    private final TaskAuthorization authorization;

    public ListUsersUseCase(UserDirectoryGateway userDirectoryGateway, TaskUserDirectoryGateway userDirectory) {
        this.userDirectoryGateway = userDirectoryGateway;
        this.authorization = new TaskAuthorization(userDirectory);
    }

    public Flux<OrganizationUser> execute(String actorEmail) {
        return authorization.requireActor(actorEmail)
                .filter(actor -> actor.hasRole(RoleCode.ADMINISTRATOR.name()))
                .switchIfEmpty(reactor.core.publisher.Mono.error(new CompiraException(
                        FORBIDDEN_CODE, FORBIDDEN_MESSAGE, ErrorCategory.FORBIDDEN)))
                .flatMapMany(actor -> userDirectoryGateway.findAll());
    }
}
