package co.com.compira.usecase.teams;

import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.TaskAuthorization;
import co.com.compira.model.task.TaskUser;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.model.team.Team;
import co.com.compira.model.team.gateways.TeamRepositoryGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public class TeamsUseCase {
    private static final String TEAM_ERROR = "TEAM_001";
    private static final String ALREADY_MEMBER = "El colaborador ya pertenece a otro equipo";
    private static final String SAME_TEAM = "El colaborador ya pertenece al equipo destino";
    private static final String TASK_NOT_FOUND = "Tarea no encontrada";
    private static final String INVALID_MEMBER = "El responsable debe pertenecer al equipo de la tarea";
    private static final String FORBIDDEN = "No tienes acceso a este equipo";
    private static final String NOT_FOUND = "Equipo no encontrado";
    private final TeamRepositoryGateway teams;
    private final TaskRepositoryGateway tasks;
    private final TaskAuthorization authorization;

    public TeamsUseCase(TeamRepositoryGateway teams, TaskRepositoryGateway tasks, TaskUserDirectoryGateway users) {
        this.teams = teams;
        this.tasks = tasks;
        this.authorization = new TaskAuthorization(users);
    }

    public Flux<Team> list(String email) {
        return authorization.requireActor(email).flatMapMany(user -> user.hasRole(RoleCode.ADMINISTRATOR.name())
                ? teams.findAll() : teams.findByCoordinator(user.id()));
    }

    public Mono<Team> create(String email, String name, String coordinatorEmail) {
        return requireAdministrator(email).then(authorization.requireCoordinator(coordinatorEmail))
                .flatMap(coordinator -> teams.create(name, coordinator.id()));
    }

    public Mono<Team> changeCoordinator(String email, UUID teamId, String coordinatorEmail) {
        return requireAdministrator(email).then(requireTeam(teamId))
                .then(authorization.requireCoordinator(coordinatorEmail))
                .flatMap(coordinator -> teams.changeCoordinator(teamId, coordinator.id()));
    }

    public Mono<Void> addMember(String email, UUID teamId, String memberEmail) {
        return requireTeamLinkingAccess(email, teamId)
                .flatMap(team -> authorization.resolveCollaborator(memberEmail))
                .flatMap(member -> teams.findByMemberId(member.id())
                        .flatMap(existing -> existing.id().equals(teamId) ? Mono.just(existing)
                                : Mono.<Team>error(error(ALREADY_MEMBER, ErrorCategory.CONFLICT)))
                        .switchIfEmpty(Mono.defer(() -> teams.addMember(teamId, member.id()).then(requireTeam(teamId)))))
                .then();
    }

    public Mono<Void> reassignMember(String email, UUID teamId, String memberEmail) {
        return requireAdministrator(email)
                .then(Mono.defer(() -> requireTeam(teamId)))
                .then(Mono.defer(() -> authorization.resolveCollaborator(memberEmail)))
                .flatMap(member -> teams.findByMemberId(member.id())
                        .flatMap(current -> current.id().equals(teamId)
                                ? Mono.<Boolean>error(error(SAME_TEAM, ErrorCategory.CONFLICT))
                                : teams.reassignMember(teamId, member.id()).thenReturn(Boolean.TRUE))
                        .defaultIfEmpty(Boolean.FALSE)
                        .flatMap(reassigned -> Boolean.TRUE.equals(reassigned)
                                ? Mono.empty()
                                : teams.addMember(teamId, member.id())))
                .then();
    }

    public Mono<Void> linkExistingTask(String email, UUID taskId, UUID teamId) {
        return requireTeamLinkingAccess(email, teamId).flatMap(team -> tasks.findById(taskId))
                .switchIfEmpty(Mono.error(error(TASK_NOT_FOUND, ErrorCategory.NOT_FOUND)))
                .flatMap(task -> requireMember(teamId, task.responsibleUserId())
                        .then(Mono.defer(() -> teams.linkTask(taskId, teamId))));
    }

    public Mono<Team> requireCoordinator(UUID teamId, UUID coordinatorId) {
        return requireTeam(teamId).filter(team -> team.coordinatorUserId().equals(coordinatorId))
                .switchIfEmpty(Mono.error(error(FORBIDDEN, ErrorCategory.FORBIDDEN)));
    }

    public Mono<Void> requireTaskCoordinator(UUID taskId, UUID coordinatorId) {
        return teams.findByTaskId(taskId).filter(team -> team.coordinatorUserId().equals(coordinatorId))
                .switchIfEmpty(Mono.error(error(FORBIDDEN, ErrorCategory.FORBIDDEN))).then();
    }

    public Mono<Void> requireTaskMember(UUID taskId, UUID memberId) {
        return teams.findByTaskId(taskId).switchIfEmpty(Mono.error(error(NOT_FOUND, ErrorCategory.CONFLICT)))
                .flatMap(team -> requireMember(team.id(), memberId));
    }

    public Mono<Void> requireMember(UUID teamId, UUID memberId) {
        if (memberId == null) return Mono.empty();
        return teams.findByMemberId(memberId).filter(team -> team.id().equals(teamId))
                .switchIfEmpty(Mono.error(error(INVALID_MEMBER, ErrorCategory.CONFLICT)))
                .then();
    }

    public Mono<Void> requireTaskAccess(String email, UUID taskId) {
        return authorization.requireActor(email).flatMap(actor -> {
            if (actor.hasRole(RoleCode.ADMINISTRATOR.name())) return Mono.empty();
            return tasks.findById(taskId).switchIfEmpty(Mono.error(error(TASK_NOT_FOUND, ErrorCategory.NOT_FOUND)))
                    .flatMap(task -> actor.hasRole(RoleCode.COLLABORATOR.name()) && actor.id().equals(task.responsibleUserId()) ? Mono.empty()
                            : actor.hasRole(RoleCode.COORDINATOR.name()) ? requireTaskCoordinator(taskId, actor.id())
                            : Mono.error(error(FORBIDDEN, ErrorCategory.FORBIDDEN)));
        });
    }

    public Mono<Void> linkNewTask(UUID taskId, UUID teamId) {
        return teams.linkTask(taskId, teamId);
    }

    private Mono<Team> requireTeam(UUID id) {
        return teams.findById(id).switchIfEmpty(Mono.error(error(NOT_FOUND, ErrorCategory.NOT_FOUND)));
    }

    private Mono<Void> requireAdministrator(String email) {
        return authorization.requireActor(email).filter(user -> user.hasRole(RoleCode.ADMINISTRATOR.name()))
                .switchIfEmpty(Mono.error(error(FORBIDDEN, ErrorCategory.FORBIDDEN))).then();
    }

    private Mono<Team> requireTeamLinkingAccess(String email, UUID teamId) {
        return authorization.requireActor(email).flatMap(actor -> requireTeam(teamId)
                .filter(team -> actor.hasRole(RoleCode.ADMINISTRATOR.name())
                        || actor.hasRole(RoleCode.COORDINATOR.name()) && team.coordinatorUserId().equals(actor.id()))
                .switchIfEmpty(Mono.error(error(FORBIDDEN, ErrorCategory.FORBIDDEN))));
    }

    private CompiraException error(String message, ErrorCategory category) {
        return new CompiraException(TEAM_ERROR, message, category);
    }
}
