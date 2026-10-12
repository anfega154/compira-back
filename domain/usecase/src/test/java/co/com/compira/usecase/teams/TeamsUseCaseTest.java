package co.com.compira.usecase.teams;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.task.TaskUser;
import co.com.compira.model.team.TeamMember;
import org.junit.jupiter.api.DisplayName;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.auth.RoleCode;
import co.com.compira.model.common.error.ErrorCategory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Flux;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.model.team.gateways.TeamRepositoryGateway;
import co.com.compira.usecase.notifications.NotificationTestData;
import co.com.compira.usecase.task.TaskTestData;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TeamsUseCaseTest {
    private final TeamRepositoryGateway teams = mock(TeamRepositoryGateway.class);
    private final TaskRepositoryGateway tasks = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway users = mock(TaskUserDirectoryGateway.class);
    private final TeamsUseCase useCase = new TeamsUseCase(teams, tasks, users);

    @Test
    void rejectsResponsibleAfterCollaboratorRoleIsRemoved() {
        var previous = TaskTestData.collaborator();
        var actor = new TaskUser(previous.id(), previous.email(), previous.firstName(), previous.lastName(), java.util.List.of());
        when(users.findByEmail(actor.email())).thenReturn(Mono.just(actor));
        when(tasks.findById(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        StepVerifier.create(useCase.requireTaskAccess(actor.email(), TaskTestData.TASK_ID))
                .expectError(CompiraException.class).verify();
    }

    @Test
    void rejectsFormerCoordinatorAfterTeamCoordinatorChanges() {
        when(teams.findByTaskId(TaskTestData.TASK_ID)).thenReturn(Mono.just(NotificationTestData.team()));
        StepVerifier.create(useCase.requireTaskCoordinator(TaskTestData.TASK_ID, TaskTestData.COORDINATOR_ID))
                .expectError(CompiraException.class).verify();
        StepVerifier.create(useCase.requireTaskCoordinator(TaskTestData.TASK_ID, NotificationTestData.CURRENT_COORDINATOR)).verifyComplete();
    }

    @Test
    void rejectsUnrelatedCollaboratorFromReadingTaskDetails() {
        when(users.findByEmail(TaskTestData.OTHER_COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.otherCollaborator()));
        when(tasks.findById(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(teams.findByTaskId(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.team()));
        StepVerifier.create(useCase.requireTaskAccess(TaskTestData.OTHER_COLLABORATOR_EMAIL, TaskTestData.TASK_ID))
                .expectError(CompiraException.class).verify();
    }

    @Test
    void requiresAdministratorToCreateTeams() {
        when(users.findByEmail(TaskTestData.COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.collaborator()));
        when(users.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.coordinator()));
        StepVerifier.create(useCase.create(TaskTestData.COLLABORATOR_EMAIL, "Operaciones", TaskTestData.COORDINATOR_EMAIL))
                .expectError(CompiraException.class).verify();
        verifyNoInteractions(teams);
    }

    @Test
    void allowsAdministratorToCreateTeamWithValidCoordinator() {
        when(users.findByEmail("admin@compira.co")).thenReturn(Mono.just(TaskTestData.administrator()));
        when(users.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.coordinator()));
        when(teams.create("Operaciones", TaskTestData.COORDINATOR_ID)).thenReturn(Mono.just(TaskTestData.team()));
        StepVerifier.create(useCase.create("admin@compira.co", "Operaciones", TaskTestData.COORDINATOR_EMAIL))
                .expectNext(TaskTestData.team()).verifyComplete();
    }

    @Test
    void rejectsResponsiblesWhoDoNotBelongToTaskTeam() {
        when(teams.findByMemberId(TaskTestData.COLLABORATOR_ID)).thenReturn(Mono.empty());
        StepVerifier.create(useCase.requireMember(TaskTestData.TEAM_ID, TaskTestData.COLLABORATOR_ID))
                .expectError(CompiraException.class).verify();
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"ADMINISTRATOR", "COORDINATOR"})
    void allowsAuthorizedActorToLinkUnassignedCollaborator(RoleCode role) {
        var actor = role == RoleCode.ADMINISTRATOR ? TaskTestData.administrator() : TaskTestData.coordinator();
        when(users.findByEmail(actor.email())).thenReturn(Mono.just(actor));
        when(users.findByEmail(TaskTestData.COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.collaborator()));
        when(teams.findById(TaskTestData.TEAM_ID)).thenReturn(Mono.just(TaskTestData.team()));
        when(teams.findByMemberId(TaskTestData.COLLABORATOR_ID)).thenReturn(Mono.empty());
        when(teams.addMember(TaskTestData.TEAM_ID, TaskTestData.COLLABORATOR_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.addMember(actor.email(), TaskTestData.TEAM_ID, TaskTestData.COLLABORATOR_EMAIL)).verifyComplete();
        verify(teams).addMember(TaskTestData.TEAM_ID, TaskTestData.COLLABORATOR_ID);
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"ADMINISTRATOR", "COORDINATOR"})
    void allowsAuthorizedActorToLinkTaskWhoseResponsibleBelongsToTeam(RoleCode role) {
        var actor = role == RoleCode.ADMINISTRATOR ? TaskTestData.administrator() : TaskTestData.coordinator();
        when(users.findByEmail(actor.email())).thenReturn(Mono.just(actor));
        when(teams.findById(TaskTestData.TEAM_ID)).thenReturn(Mono.just(TaskTestData.team()));
        when(tasks.findById(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(teams.findByMemberId(TaskTestData.COLLABORATOR_ID)).thenReturn(Mono.just(TaskTestData.team()));
        when(teams.linkTask(TaskTestData.TASK_ID, TaskTestData.TEAM_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.linkExistingTask(actor.email(), TaskTestData.TASK_ID, TaskTestData.TEAM_ID)).verifyComplete();
        verify(teams).linkTask(TaskTestData.TASK_ID, TaskTestData.TEAM_ID);
    }

    @Test
    void rejectsCoordinatorLinkingAfterTeamCoordinatorChanges() {
        when(users.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.coordinator()));
        when(teams.findById(TaskTestData.TEAM_ID)).thenReturn(Mono.just(NotificationTestData.team()));
        when(users.findByEmail(TaskTestData.COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.collaborator()));
        when(tasks.findById(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));

        StepVerifier.create(useCase.addMember(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TEAM_ID, TaskTestData.COLLABORATOR_EMAIL))
                .expectErrorMatches(error -> error instanceof CompiraException failure && failure.getErrorCategory() == ErrorCategory.FORBIDDEN).verify();
        StepVerifier.create(useCase.linkExistingTask(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TASK_ID, TaskTestData.TEAM_ID))
                .expectErrorMatches(error -> error instanceof CompiraException failure && failure.getErrorCategory() == ErrorCategory.FORBIDDEN).verify();
        verify(teams, never()).addMember(any(), any());
        verify(teams, never()).linkTask(any(), any());
    }

    @Test
    void rejectsCollaboratorLinkingToAnyTeam() {
        when(users.findByEmail(TaskTestData.COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.collaborator()));
        when(users.findByEmail(TaskTestData.OTHER_COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.otherCollaborator()));
        when(teams.findById(TaskTestData.TEAM_ID)).thenReturn(Mono.just(TaskTestData.team()));
        when(tasks.findById(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));

        StepVerifier.create(useCase.addMember(TaskTestData.COLLABORATOR_EMAIL, TaskTestData.TEAM_ID, TaskTestData.OTHER_COLLABORATOR_EMAIL))
                .expectErrorMatches(error -> error instanceof CompiraException failure && failure.getErrorCategory() == ErrorCategory.FORBIDDEN).verify();
        StepVerifier.create(useCase.linkExistingTask(TaskTestData.COLLABORATOR_EMAIL, TaskTestData.TASK_ID, TaskTestData.TEAM_ID))
                .expectErrorMatches(error -> error instanceof CompiraException failure && failure.getErrorCategory() == ErrorCategory.FORBIDDEN).verify();
        verify(teams, never()).addMember(any(), any());
        verify(teams, never()).linkTask(any(), any());
    }

    @Test
    void listsOnlyTeamsCurrentlyCoordinatedByActor() {
        when(users.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.coordinator()));
        when(teams.findByCoordinator(TaskTestData.COORDINATOR_ID)).thenReturn(Flux.just(TaskTestData.team()));
        StepVerifier.create(useCase.list(TaskTestData.COORDINATOR_EMAIL)).expectNext(TaskTestData.team()).verifyComplete();
        verify(teams, never()).findAll();
    }

    /**
     * HU Crear tarea - E6.
     * Caso: un coordinador al que el Administrador no ha asignado ningún equipo consulta su lista de equipos.
     * Esperado: la lista llega vacía (el frontend muestra "Necesitas un equipo asignado por el Administrador para crear tareas.").
     * Resultado: el flujo completa sin elementos y no se consultan todos los equipos (solo los del coordinador).
     */
    @Test
    @DisplayName("E6 - Coordinador sin equipos asignados obtiene una lista vacía")
    void listsNoTeamsWhenCoordinatorHasNoneAssigned() {
        when(users.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.coordinator()));
        when(teams.findByCoordinator(TaskTestData.COORDINATOR_ID)).thenReturn(Flux.empty());
        StepVerifier.create(useCase.list(TaskTestData.COORDINATOR_EMAIL)).verifyComplete();
        verify(teams, never()).findAll();
    }

    /**
     * HU Crear tarea - E6.
     * Caso: un coordinador intenta crear una tarea en un equipo cuyo coordinador es otro usuario.
     * Esperado: se rechaza con "No tienes acceso a este equipo".
     * Resultado: error FORBIDDEN.
     */
    @Test
    @DisplayName("E6 - Coordinador de otro equipo no puede crear tareas en un equipo ajeno")
    void rejectsCoordinatorWhoDoesNotCoordinateTheTeam() {
        when(teams.findById(TaskTestData.TEAM_ID)).thenReturn(Mono.just(TaskTestData.team()));
        StepVerifier.create(useCase.requireCoordinator(TaskTestData.TEAM_ID, TaskTestData.OTHER_COLLABORATOR_ID))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && failure.getErrorCategory() == ErrorCategory.FORBIDDEN
                        && "No tienes acceso a este equipo".equals(failure.getMessage()))
                .verify();
    }

    /**
     * HU Crear tarea - E5 (otro equipo).
     * Caso: se valida como responsable a un colaborador que pertenece a un equipo distinto al de la tarea.
     * Esperado: se rechaza con "El responsable debe pertenecer al equipo de la tarea".
     * Resultado: error CONFLICT.
     */
    @Test
    @DisplayName("E5 - Responsable de otro equipo es rechazado con conflicto")
    void rejectsResponsibleFromAnotherTeamWithConflict() {
        when(teams.findByMemberId(TaskTestData.COLLABORATOR_ID)).thenReturn(Mono.just(TaskTestData.otherTeam()));
        StepVerifier.create(useCase.requireMember(TaskTestData.TEAM_ID, TaskTestData.COLLABORATOR_ID))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && failure.getErrorCategory() == ErrorCategory.CONFLICT
                        && "El responsable debe pertenecer al equipo de la tarea".equals(failure.getMessage()))
                .verify();
    }

    /**
     * HU Crear tarea - E1.
     * Caso: se crea una tarea sin responsable (responsable nulo).
     * Esperado: no se exige pertenencia a ningún equipo.
     * Resultado: el flujo completa sin consultar el equipo del miembro.
     */
    @Test
    @DisplayName("E1 - Sin responsable no se valida pertenencia al equipo")
    void skipsMembershipValidationWhenTaskHasNoResponsible() {
        StepVerifier.create(useCase.requireMember(TaskTestData.TEAM_ID, null)).verifyComplete();
        verifyNoInteractions(teams);
    }

    private static final TeamMember MEMBER_ONE = new TeamMember(
            TaskTestData.COLLABORATOR_ID, TaskTestData.COLLABORATOR_EMAIL, "Colab", "Uno");
    private static final TeamMember MEMBER_TWO = new TeamMember(
            TaskTestData.OTHER_COLLABORATOR_ID, TaskTestData.OTHER_COLLABORATOR_EMAIL, null, null);

    private void givenActor(TaskUser actor) {
        when(users.findByEmail(actor.email())).thenReturn(Mono.just(actor));
        when(teams.findById(TaskTestData.TEAM_ID)).thenReturn(Mono.just(TaskTestData.team()));
    }

    private void givenCoordinatorOfTheTeam() {
        givenActor(TaskTestData.coordinator());
    }

    private static TaskUser userWithRole(String email, RoleCode role) {
        return new TaskUser(TaskTestData.OTHER_COLLABORATOR_ID, email, "Usuario", "Prueba", java.util.List.of(role.name()));
    }

    /**
     * Spec HU-12 Parte A (R1).
     * Caso: el coordinador del equipo consulta los miembros de su equipo.
     * Esperado: recibe los colaboradores activos que entrega el repositorio, en el mismo orden.
     * Resultado: el flujo emite MEMBER_ONE y MEMBER_TWO (este último con nombre y apellido nulos).
     */
    @Test
    @DisplayName("R1 - Coordinador del equipo obtiene los colaboradores activos de su equipo")
    void listsActiveCollaboratorsForTheCoordinatorOfTheTeam() {
        givenCoordinatorOfTheTeam();
        when(teams.findActiveCollaborators(TaskTestData.TEAM_ID)).thenReturn(Flux.just(MEMBER_ONE, MEMBER_TWO));

        StepVerifier.create(useCase.listMembers(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TEAM_ID))
                .expectNext(MEMBER_ONE, MEMBER_TWO)
                .verifyComplete();
    }

    /**
     * Spec HU-12 Parte A (R1).
     * Caso: el equipo no tiene colaboradores activos.
     * Esperado: el flujo completa vacío (el front muestra "Este equipo aún no tiene colaboradores").
     * Resultado: sin elementos y sin error.
     */
    @Test
    @DisplayName("R1 - Equipo sin colaboradores activos devuelve un flujo vacío")
    void listsNoMembersWhenTeamHasNoActiveCollaborators() {
        givenCoordinatorOfTheTeam();
        when(teams.findActiveCollaborators(TaskTestData.TEAM_ID)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.listMembers(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TEAM_ID))
                .verifyComplete();
    }

    /**
     * Spec HU-12 Parte A (R2).
     * Caso: un coordinador consulta los miembros de un equipo que coordina otro usuario.
     * Esperado: se rechaza con "No tienes acceso a este equipo" y no se consultan los miembros.
     * Resultado: error FORBIDDEN y `findActiveCollaborators` nunca se invoca.
     */
    @Test
    @DisplayName("R2 - Coordinador de otro equipo: acceso prohibido y no consulta miembros")
    void rejectsMembersQueryFromCoordinatorOfAnotherTeam() {
        givenActor(userWithRole("otro.coord@compira.co", RoleCode.COORDINATOR));

        StepVerifier.create(useCase.listMembers("otro.coord@compira.co", TaskTestData.TEAM_ID))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && failure.getErrorCategory() == ErrorCategory.FORBIDDEN
                        && "No tienes acceso a este equipo".equals(failure.getMessage()))
                .verify();

        verify(teams, never()).findActiveCollaborators(any());
    }

    /**
     * Spec HU-12 Parte A (R3).
     * Caso: un administrador consulta los miembros de un equipo que no coordina.
     * Esperado: se permite y recibe los miembros.
     * Resultado: el flujo emite MEMBER_ONE.
     */
    @Test
    @DisplayName("R3 - Administrador obtiene los miembros de cualquier equipo")
    void listsMembersForAdministratorOfAnyTeam() {
        givenActor(userWithRole("admin@compira.co", RoleCode.ADMINISTRATOR));
        when(teams.findActiveCollaborators(TaskTestData.TEAM_ID)).thenReturn(Flux.just(MEMBER_ONE));

        StepVerifier.create(useCase.listMembers("admin@compira.co", TaskTestData.TEAM_ID))
                .expectNext(MEMBER_ONE)
                .verifyComplete();
    }

    /**
     * Spec HU-12 Parte A (R4).
     * Caso: se consultan los miembros de un equipo que no existe.
     * Esperado: se responde "Equipo no encontrado" y no se consultan los miembros.
     * Resultado: error NOT_FOUND y `findActiveCollaborators` nunca se invoca.
     */
    @Test
    @DisplayName("R4 - Equipo inexistente: Equipo no encontrado")
    void rejectsMembersQueryWhenTeamDoesNotExist() {
        givenActor(TaskTestData.coordinator());
        when(teams.findById(TaskTestData.TEAM_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.listMembers(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TEAM_ID))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && failure.getErrorCategory() == ErrorCategory.NOT_FOUND
                        && "Equipo no encontrado".equals(failure.getMessage()))
                .verify();

        verify(teams, never()).findActiveCollaborators(any());
    }

    /**
     * Spec HU-12 Parte A (R5).
     * Caso: un colaborador intenta consultar los miembros de un equipo.
     * Esperado: se rechaza con acceso prohibido.
     * Resultado: error FORBIDDEN y no se consultan los miembros.
     */
    @Test
    @DisplayName("R5 - Colaborador no puede consultar miembros del equipo")
    void rejectsMembersQueryFromCollaborator() {
        givenActor(TaskTestData.collaborator());

        StepVerifier.create(useCase.listMembers(TaskTestData.COLLABORATOR_EMAIL, TaskTestData.TEAM_ID))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && failure.getErrorCategory() == ErrorCategory.FORBIDDEN)
                .verify();

        verify(teams, never()).findActiveCollaborators(any());
    }

    /**
     * Spec HU-12 Parte A (R2).
     * Caso: el usuario que consulta los miembros no existe en el directorio.
     * Esperado: se rechaza como usuario no encontrado y no se consulta ni el equipo ni los miembros.
     * Resultado: error NOT_FOUND ("No se encontró el usuario que realiza la operación") sin llamadas a `findById`
     * ni a `findActiveCollaborators`.
     */
    @Test
    @DisplayName("R2 - Usuario inexistente: no se consulta el equipo ni los miembros")
    void rejectsMembersQueryWhenActorDoesNotExist() {
        when(users.findByEmail("fantasma@compira.co")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.listMembers("fantasma@compira.co", TaskTestData.TEAM_ID))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && failure.getErrorCategory() == ErrorCategory.NOT_FOUND)
                .verify();

        verify(teams, never()).findById(any());
        verify(teams, never()).findActiveCollaborators(any());
    }

    @Test
    void stillForbidsCoordinatorFromCreatingTeamsOrChangingCoordinator() {
        when(users.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.coordinator()));
        when(teams.findById(TaskTestData.TEAM_ID)).thenReturn(Mono.just(TaskTestData.team()));
        StepVerifier.create(useCase.create(TaskTestData.COORDINATOR_EMAIL, TaskTestData.team().name(), TaskTestData.COORDINATOR_EMAIL))
                .expectError(CompiraException.class).verify();
        StepVerifier.create(useCase.changeCoordinator(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TEAM_ID, TaskTestData.COORDINATOR_EMAIL))
                .expectError(CompiraException.class).verify();
        verify(teams, never()).create(any(), any());
        verify(teams, never()).changeCoordinator(any(), any());
    }

    @Test
    void rejectsMovingAnExistingMemberFromAnotherTeam() {
        when(users.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.coordinator()));
        when(users.findByEmail(TaskTestData.COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.collaborator()));
        when(teams.findById(TaskTestData.TEAM_ID)).thenReturn(Mono.just(TaskTestData.team()));
        when(teams.findByMemberId(TaskTestData.COLLABORATOR_ID)).thenReturn(Mono.just(TaskTestData.otherTeam()));
        StepVerifier.create(useCase.addMember(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TEAM_ID, TaskTestData.COLLABORATOR_EMAIL))
                .expectErrorMatches(error -> error instanceof CompiraException failure && failure.getErrorCategory() == ErrorCategory.CONFLICT).verify();
        verify(teams, never()).addMember(any(), any());
    }

    @Test
    void rejectsLinkingTaskWhenResponsibleBelongsToAnotherTeam() {
        when(users.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.coordinator()));
        when(teams.findById(TaskTestData.TEAM_ID)).thenReturn(Mono.just(TaskTestData.team()));
        when(tasks.findById(TaskTestData.TASK_ID)).thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(teams.findByMemberId(TaskTestData.COLLABORATOR_ID)).thenReturn(Mono.just(TaskTestData.otherTeam()));
        StepVerifier.create(useCase.linkExistingTask(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TASK_ID, TaskTestData.TEAM_ID))
                .expectErrorMatches(error -> error instanceof CompiraException failure && failure.getErrorCategory() == ErrorCategory.CONFLICT).verify();
        verify(teams, never()).linkTask(any(), any());
    }

    @Test
    void administratorReassignsCollaboratorToAnotherTeam() {
        when(users.findByEmail("admin@compira.co")).thenReturn(Mono.just(TaskTestData.administrator()));
        when(teams.findById(TaskTestData.TEAM_ID)).thenReturn(Mono.just(TaskTestData.team()));
        when(users.findByEmail(TaskTestData.COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.collaborator()));
        when(teams.findByMemberId(TaskTestData.COLLABORATOR_ID)).thenReturn(Mono.just(TaskTestData.otherTeam()));
        when(teams.reassignMember(TaskTestData.TEAM_ID, TaskTestData.COLLABORATOR_ID)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.reassignMember("admin@compira.co", TaskTestData.TEAM_ID, TaskTestData.COLLABORATOR_EMAIL))
                .verifyComplete();
        verify(teams).reassignMember(TaskTestData.TEAM_ID, TaskTestData.COLLABORATOR_ID);
    }

    @Test
    void rejectsReassignmentToSameTeam() {
        when(users.findByEmail("admin@compira.co")).thenReturn(Mono.just(TaskTestData.administrator()));
        when(teams.findById(TaskTestData.TEAM_ID)).thenReturn(Mono.just(TaskTestData.team()));
        when(users.findByEmail(TaskTestData.COLLABORATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.collaborator()));
        when(teams.findByMemberId(TaskTestData.COLLABORATOR_ID)).thenReturn(Mono.just(TaskTestData.team()));

        StepVerifier.create(useCase.reassignMember("admin@compira.co", TaskTestData.TEAM_ID, TaskTestData.COLLABORATOR_EMAIL))
                .expectErrorMatches(error -> error instanceof CompiraException failure && failure.getErrorCategory() == ErrorCategory.CONFLICT).verify();
        verify(teams, never()).reassignMember(any(), any());
    }

    @Test
    void forbidsNonAdministratorFromReassigningCollaborator() {
        when(users.findByEmail(TaskTestData.COORDINATOR_EMAIL)).thenReturn(Mono.just(TaskTestData.coordinator()));
        when(teams.findById(TaskTestData.TEAM_ID)).thenReturn(Mono.just(TaskTestData.team()));

        StepVerifier.create(useCase.reassignMember(TaskTestData.COORDINATOR_EMAIL, TaskTestData.TEAM_ID, TaskTestData.COLLABORATOR_EMAIL))
                .expectErrorMatches(error -> error instanceof CompiraException failure && failure.getErrorCategory() == ErrorCategory.FORBIDDEN).verify();
        verify(teams, never()).reassignMember(any(), any());
    }
}
