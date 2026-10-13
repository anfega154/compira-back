package co.com.compira.usecase.createtask;

import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.task.CreateTaskCommand;
import co.com.compira.model.task.Task;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskHistoryEntry;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.model.task.gateways.TaskClockGateway;
import co.com.compira.model.task.gateways.TaskRepositoryGateway;
import co.com.compira.model.task.gateways.TaskUserDirectoryGateway;
import co.com.compira.usecase.task.TaskTestData;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.notification.NotificationType;
import co.com.compira.model.task.TaskHistoryEvent;
import co.com.compira.model.task.TaskMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreateTaskUseCaseTest {
    private final co.com.compira.usecase.teams.TeamsUseCase teams = mock(co.com.compira.usecase.teams.TeamsUseCase.class);
    private final co.com.compira.usecase.notifications.TaskNotificationsUseCase notifications = mock(co.com.compira.usecase.notifications.TaskNotificationsUseCase.class);
    @org.junit.jupiter.api.BeforeEach
    void configureTeamAndNotifications() {
        when(teams.requireTaskCoordinator(any(), any())).thenReturn(Mono.empty());
        when(teams.requireTaskMember(any(), any())).thenReturn(Mono.empty());
        when(teams.requireMember(any(), any())).thenReturn(Mono.empty());
        when(teams.requireCoordinator(any(), any())).thenReturn(Mono.just(TaskTestData.team()));
        when(teams.linkNewTask(any(), any())).thenReturn(Mono.empty());
        when(notifications.assignment(any(), any(), any(), any())).thenReturn(Mono.empty());
    }

    private final TaskRepositoryGateway taskRepositoryGateway = mock(TaskRepositoryGateway.class);
    private final TaskUserDirectoryGateway taskUserDirectoryGateway = mock(TaskUserDirectoryGateway.class);
    private final TaskClockGateway taskClockGateway = () -> TaskTestData.now();
    private final CreateTaskUseCase useCase = new CreateTaskUseCase(taskRepositoryGateway, taskUserDirectoryGateway, taskClockGateway, notifications, teams);

    private static final java.util.UUID TEAM_ID = TaskTestData.TEAM_ID;

    private void givenCoordinatorAndPersistence() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(taskRepositoryGateway.save(any(Task.class)))
                .thenReturn(Mono.just(TaskTestData.task(TaskStatus.PENDING)));
        when(taskRepositoryGateway.appendHistory(any(TaskHistoryEntry.class)))
                .thenReturn(Mono.just(mock(TaskHistoryEntry.class)));
    }

    /**
     * HU Crear tarea - E1.
     * Caso: el coordinador crea una tarea de su equipo con título, descripción y fecha futura, sin responsable.
     * Esperado: la tarea se guarda en estado PENDING sin responsable, se vincula al equipo, se registra únicamente
     * el evento CREATED y no se genera notificación de asignación.
     * Resultado: el flujo completa con la tarea PENDING y sin evento ASSIGNED ni notificación.
     */
    @Test
    @DisplayName("E1 - Crea tarea Pendiente sin responsable y registra solo el evento Creada")
    void shouldCreatePendingTaskWithoutResponsibleAndRecordOnlyCreatedEvent() {
        givenCoordinatorAndPersistence();
        CreateTaskCommand command = new CreateTaskCommand(TaskTestData.COORDINATOR_EMAIL, "Preparar informe",
                "Detalle", TaskTestData.now().plusDays(2), null, TEAM_ID);

        StepVerifier.create(useCase.execute(command))
                .assertNext(this::assertStatusPending)
                .verifyComplete();

        ArgumentCaptor<Task> savedTask = ArgumentCaptor.forClass(Task.class);
        verify(taskRepositoryGateway).save(savedTask.capture());
        Assertions.assertEquals(TaskStatus.PENDING, savedTask.getValue().status());
        Assertions.assertNull(savedTask.getValue().responsibleUserId());
        Assertions.assertEquals("Preparar informe", savedTask.getValue().title());
        Assertions.assertEquals("Detalle", savedTask.getValue().description());
        Assertions.assertEquals(TaskTestData.now().plusDays(2), savedTask.getValue().dueDate());
        Assertions.assertEquals(TaskTestData.COORDINATOR_ID, savedTask.getValue().createdByUserId());

        ArgumentCaptor<TaskHistoryEntry> history = ArgumentCaptor.forClass(TaskHistoryEntry.class);
        verify(taskRepositoryGateway, times(1)).appendHistory(history.capture());
        Assertions.assertEquals(TaskHistoryEvent.CREATED, history.getValue().event());
        verify(teams).linkNewTask(TaskTestData.TASK_ID, TEAM_ID);
        verifyNoInteractions(notifications);
    }

    /**
     * HU Crear tarea - E2.
     * Caso: el coordinador crea la tarea indicando el correo de un colaborador activo del equipo.
     * Esperado: la tarea queda PENDING con ese responsable, se registran los eventos CREATED y ASSIGNED (en ese orden)
     * y se notifica la asignación al colaborador.
     * Resultado: se guardan dos eventos de historial y se envía una notificación de tipo ASSIGNED al responsable.
     */
    @Test
    @DisplayName("E2 - Crea tarea con responsable, registra Creada y Asignada y notifica la asignación")
    void shouldRecordCreatedAndAssignedEventsAndNotifyResponsible() {
        givenCoordinatorAndPersistence();
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));
        CreateTaskCommand command = new CreateTaskCommand(TaskTestData.COORDINATOR_EMAIL, "Preparar informe",
                "Detalle", TaskTestData.now().plusDays(2), TaskTestData.COLLABORATOR_EMAIL, TEAM_ID);

        StepVerifier.create(useCase.execute(command))
                .assertNext(this::assertStatusPending)
                .verifyComplete();

        ArgumentCaptor<Task> savedTask = ArgumentCaptor.forClass(Task.class);
        verify(taskRepositoryGateway).save(savedTask.capture());
        Assertions.assertEquals(TaskTestData.COLLABORATOR_ID, savedTask.getValue().responsibleUserId());
        Assertions.assertEquals(TaskStatus.PENDING, savedTask.getValue().status());

        ArgumentCaptor<TaskHistoryEntry> history = ArgumentCaptor.forClass(TaskHistoryEntry.class);
        verify(taskRepositoryGateway, times(2)).appendHistory(history.capture());
        Assertions.assertEquals(TaskHistoryEvent.CREATED, history.getAllValues().get(0).event());
        Assertions.assertEquals(TaskHistoryEvent.ASSIGNED, history.getAllValues().get(1).event());
        Assertions.assertEquals(TaskTestData.COLLABORATOR_EMAIL, history.getAllValues().get(1).newValue());
        verify(notifications).assignment(any(Task.class), eq(TaskTestData.COLLABORATOR_ID),
                eq(NotificationType.ASSIGNED), any());
    }

    /**
     * HU Crear tarea - E4.
     * Caso: el coordinador indica una fecha límite anterior al momento actual.
     * Esperado: se rechaza con DUE_DATE_IN_PAST y el mensaje "La fecha límite no puede ser anterior al momento actual",
     * sin crear la tarea.
     * Resultado: error BAD_REQUEST con el mensaje esperado y sin interacción con el repositorio ni con notificaciones.
     */
    @Test
    @DisplayName("E4 - Fecha límite pasada: devuelve el mensaje esperado y no crea la tarea")
    void shouldNotPersistNorNotifyWhenDueDateIsInThePast() {
        CreateTaskCommand command = new CreateTaskCommand(TaskTestData.COORDINATOR_EMAIL, "Preparar informe",
                null, TaskTestData.now().minusSeconds(1), TaskTestData.COLLABORATOR_EMAIL, TEAM_ID);

        StepVerifier.create(useCase.execute(command))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && TaskErrorCode.DUE_DATE_IN_PAST.equals(failure.getCode())
                        && TaskMessage.DUE_DATE_IN_PAST.equals(failure.getMessage())
                        && failure.getErrorCategory() == ErrorCategory.BAD_REQUEST)
                .verify();

        verifyNoInteractions(taskRepositoryGateway, notifications);
    }

    /**
     * HU Crear tarea - E4 (límite).
     * Caso: la fecha límite es exactamente el momento actual (no es "anterior" al momento actual).
     * Esperado: no se rechaza por fecha y la tarea se crea.
     * Resultado: el flujo completa con la tarea PENDING.
     */
    @Test
    @DisplayName("E4 (límite) - Fecha límite igual al momento actual no se rechaza")
    void shouldAcceptDueDateEqualToCurrentMoment() {
        givenCoordinatorAndPersistence();
        CreateTaskCommand command = new CreateTaskCommand(TaskTestData.COORDINATOR_EMAIL, "Preparar informe",
                "Detalle", TaskTestData.now(), null, TEAM_ID);

        StepVerifier.create(useCase.execute(command))
                .assertNext(this::assertStatusPending)
                .verifyComplete();
    }

    /**
     * HU Crear tarea - E5 (no existe o está inactivo).
     * Caso: el correo del responsable no corresponde a un usuario activo.
     * Esperado: no se crea la tarea; error RESPONSIBLE_NOT_FOUND con el mensaje
     * "No se encontró el colaborador responsable indicado".
     * Resultado: error NOT_FOUND con ese mensaje y sin guardar tarea, historial ni notificación.
     */
    @Test
    @DisplayName("E5 - Responsable inexistente o inactivo: no se encontró el colaborador responsable")
    void shouldRejectWhenResponsibleDoesNotExistOrIsInactive() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(taskUserDirectoryGateway.findByEmail("inactivo@compira.co")).thenReturn(Mono.empty());
        CreateTaskCommand command = new CreateTaskCommand(TaskTestData.COORDINATOR_EMAIL, "Preparar informe",
                null, TaskTestData.now().plusDays(2), "inactivo@compira.co", TEAM_ID);

        StepVerifier.create(useCase.execute(command))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && TaskErrorCode.RESPONSIBLE_NOT_FOUND.equals(failure.getCode())
                        && TaskMessage.RESPONSIBLE_NOT_FOUND.equals(failure.getMessage())
                        && failure.getErrorCategory() == ErrorCategory.NOT_FOUND)
                .verify();

        verify(taskRepositoryGateway, never()).save(any());
        verify(taskRepositoryGateway, never()).appendHistory(any());
        verifyNoInteractions(notifications);
    }

    /**
     * HU Crear tarea - E5 (sin rol de colaborador).
     * Caso: el responsable existe pero no tiene el rol de colaborador.
     * Esperado: no se crea la tarea; mensaje "El responsable de una tarea debe ser un colaborador".
     * Resultado: error BAD_REQUEST con ese mensaje y sin guardar tarea ni notificar.
     */
    @Test
    @DisplayName("E5 - Responsable sin rol de colaborador: el responsable debe ser un colaborador")
    void shouldNotPersistWhenResponsibleHasNoCollaboratorRole() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(taskUserDirectoryGateway.findByEmail("boss@compira.co"))
                .thenReturn(Mono.just(TaskTestData.administrator()));
        CreateTaskCommand command = new CreateTaskCommand(TaskTestData.COORDINATOR_EMAIL, "Preparar informe",
                null, TaskTestData.now().plusDays(2), "boss@compira.co", TEAM_ID);

        StepVerifier.create(useCase.execute(command))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && TaskMessage.RESPONSIBLE_NOT_COLLABORATOR.equals(failure.getMessage())
                        && failure.getErrorCategory() == ErrorCategory.BAD_REQUEST)
                .verify();

        verify(taskRepositoryGateway, never()).save(any());
        verifyNoInteractions(notifications);
    }

    /**
     * HU Crear tarea - E5 (otro equipo).
     * Caso: el responsable es colaborador pero pertenece a un equipo distinto al de la tarea.
     * Esperado: no se crea la tarea; mensaje "El responsable debe pertenecer al equipo de la tarea".
     * Resultado: error CONFLICT con ese mensaje; no se guarda la tarea, no se vincula al equipo ni se notifica.
     */
    @Test
    @DisplayName("E5 - Responsable de otro equipo: debe pertenecer al equipo de la tarea")
    void shouldRejectWhenResponsibleBelongsToAnotherTeam() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));
        java.util.concurrent.atomic.AtomicBoolean persisted = new java.util.concurrent.atomic.AtomicBoolean(false);
        when(taskRepositoryGateway.save(any(Task.class))).thenReturn(Mono.fromSupplier(() -> {
            persisted.set(true);
            return TaskTestData.task(TaskStatus.PENDING);
        }));
        when(teams.requireMember(TEAM_ID, TaskTestData.COLLABORATOR_ID)).thenReturn(Mono.error(new CompiraException(
                "TEAM_001", "El responsable debe pertenecer al equipo de la tarea", ErrorCategory.CONFLICT)));
        CreateTaskCommand command = new CreateTaskCommand(TaskTestData.COORDINATOR_EMAIL, "Preparar informe",
                null, TaskTestData.now().plusDays(2), TaskTestData.COLLABORATOR_EMAIL, TEAM_ID);

        StepVerifier.create(useCase.execute(command))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && "El responsable debe pertenecer al equipo de la tarea".equals(failure.getMessage())
                        && failure.getErrorCategory() == ErrorCategory.CONFLICT)
                .verify();

        Assertions.assertFalse(persisted.get(), "La tarea no debe persistirse");
        verify(taskRepositoryGateway, never()).appendHistory(any());
        verify(teams, never()).linkNewTask(any(), any());
        verifyNoInteractions(notifications);
    }

    /**
     * HU Crear tarea - E6.
     * Caso: el coordinador intenta crear una tarea en un equipo que no coordina (por ejemplo, no tiene equipo asignado).
     * Esperado: se rechaza con error de acceso prohibido y no se crea la tarea.
     * Resultado: error FORBIDDEN y sin guardar tarea, historial ni notificación.
     */
    @Test
    @DisplayName("E6 - Coordinador sin ese equipo asignado: acceso prohibido y no se crea la tarea")
    void shouldRejectWhenCoordinatorDoesNotCoordinateTheTeam() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COORDINATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.coordinator()));
        when(teams.requireCoordinator(any(), any())).thenReturn(Mono.error(new CompiraException(
                "TEAM_001", "No tienes acceso a este equipo", ErrorCategory.FORBIDDEN)));
        CreateTaskCommand command = new CreateTaskCommand(TaskTestData.COORDINATOR_EMAIL, "Preparar informe",
                null, TaskTestData.now().plusDays(2), null, TEAM_ID);

        StepVerifier.create(useCase.execute(command))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && failure.getErrorCategory() == ErrorCategory.FORBIDDEN)
                .verify();

        verify(taskRepositoryGateway, never()).save(any());
        verifyNoInteractions(notifications);
    }

    /**
     * HU Crear tarea - E8 (usuario inexistente).
     * Caso: el correo de quien intenta crear la tarea no existe en el directorio de usuarios.
     * Esperado: se rechaza con ACTOR_NOT_FOUND y no se crea la tarea.
     * Resultado: error NOT_FOUND con el mensaje "No se encontró el usuario que realiza la operación".
     */
    @Test
    @DisplayName("E8 - Usuario que crea la tarea no existe: no se crea la tarea")
    void shouldRejectWhenActorDoesNotExist() {
        when(taskUserDirectoryGateway.findByEmail("fantasma@compira.co")).thenReturn(Mono.empty());
        CreateTaskCommand command = new CreateTaskCommand("fantasma@compira.co", "Preparar informe",
                null, TaskTestData.now().plusDays(2), null, TEAM_ID);

        StepVerifier.create(useCase.execute(command))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && TaskErrorCode.ACTOR_NOT_FOUND.equals(failure.getCode())
                        && TaskMessage.ACTOR_NOT_FOUND.equals(failure.getMessage())
                        && failure.getErrorCategory() == ErrorCategory.NOT_FOUND)
                .verify();

        verify(taskRepositoryGateway, never()).save(any());
    }

    /**
     * HU Crear tarea - E8 (rol).
     * Caso: un usuario sin rol de coordinador intenta crear una tarea.
     * Esperado: se rechaza con "Esta operación solo puede realizarla un coordinador" y no se crea la tarea.
     * Resultado: error FORBIDDEN sin guardar tarea ni notificar.
     */
    @Test
    @DisplayName("E8 - Usuario sin rol de coordinador: operación prohibida y no se crea la tarea")
    void shouldNotPersistWhenActorIsNotCoordinator() {
        when(taskUserDirectoryGateway.findByEmail(TaskTestData.COLLABORATOR_EMAIL))
                .thenReturn(Mono.just(TaskTestData.collaborator()));
        CreateTaskCommand command = new CreateTaskCommand(TaskTestData.COLLABORATOR_EMAIL, "Preparar informe",
                null, TaskTestData.now().plusDays(2), null, TEAM_ID);

        StepVerifier.create(useCase.execute(command))
                .expectErrorMatches(error -> error instanceof CompiraException failure
                        && TaskMessage.ACTOR_NOT_COORDINATOR.equals(failure.getMessage())
                        && failure.getErrorCategory() == ErrorCategory.FORBIDDEN)
                .verify();

        verify(taskRepositoryGateway, never()).save(any());
        verifyNoInteractions(notifications);
    }

    /**
     * HU Crear tarea - E1 (responsable en blanco).
     * Caso: el correo del responsable llega vacío o solo con espacios (el formulario puede enviar "").
     * Esperado: se trata como "sin responsable": no se busca al usuario, no se registra ASSIGNED ni se notifica.
     * Resultado: tarea PENDING sin responsable, un solo evento CREATED y sin notificación.
     */
    @Test
    @DisplayName("E1 - Correo de responsable en blanco se trata como sin responsable")
    void shouldTreatBlankResponsibleEmailAsNoResponsible() {
        givenCoordinatorAndPersistence();
        CreateTaskCommand command = new CreateTaskCommand(TaskTestData.COORDINATOR_EMAIL, "Preparar informe",
                "Detalle", TaskTestData.now().plusDays(2), "  ", TEAM_ID);

        StepVerifier.create(useCase.execute(command))
                .assertNext(this::assertStatusPending)
                .verifyComplete();

        ArgumentCaptor<Task> savedTask = ArgumentCaptor.forClass(Task.class);
        verify(taskRepositoryGateway).save(savedTask.capture());
        Assertions.assertNull(savedTask.getValue().responsibleUserId());
        verify(taskUserDirectoryGateway, never()).findByEmail("  ");
        verify(taskRepositoryGateway, times(1)).appendHistory(any(TaskHistoryEntry.class));
        verifyNoInteractions(notifications);
    }

    /**
     * Contrato de capas (E3).
     * Caso: el comando llega sin fecha límite al caso de uso.
     * Esperado: la obligatoriedad de la fecha se valida en la capa de entrada (CreateTaskRequest), no aquí; el caso de
     * uso solo rechaza fechas anteriores al momento actual y no falla con una fecha nula.
     * Resultado: el flujo completa con la tarea PENDING.
     */
    @Test
    @DisplayName("Contrato - La fecha nula no se rechaza en el caso de uso (se valida en la entrada)")
    void shouldNotRejectNullDueDateBecauseValidationLivesInTheInputLayer() {
        givenCoordinatorAndPersistence();
        CreateTaskCommand command = new CreateTaskCommand(TaskTestData.COORDINATOR_EMAIL, "Preparar informe",
                "Detalle", null, null, TEAM_ID);

        StepVerifier.create(useCase.execute(command))
                .assertNext(this::assertStatusPending)
                .verifyComplete();
    }

    private void assertStatusPending(Task task) {
        Assertions.assertEquals(TaskStatus.PENDING, task.status());
    }
}
