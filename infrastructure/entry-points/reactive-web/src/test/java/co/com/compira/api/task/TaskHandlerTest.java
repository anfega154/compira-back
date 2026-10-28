package co.com.compira.api.task;

import co.com.compira.api.router.TaskRouterRest;
import co.com.compira.api.task.mapper.TaskRequestMapper;
import co.com.compira.api.task.mapper.TaskResponseMapper;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.task.CreateTaskCommand;
import co.com.compira.model.task.TaskErrorCode;
import co.com.compira.model.task.TaskMessage;
import co.com.compira.model.task.TaskStatus;
import co.com.compira.usecase.addtaskobservation.AddTaskObservationUseCase;
import co.com.compira.usecase.approvetask.ApproveTaskUseCase;
import co.com.compira.usecase.assigntask.AssignTaskUseCase;
import co.com.compira.usecase.canceltask.CancelTaskUseCase;
import co.com.compira.usecase.createtask.CreateTaskUseCase;
import co.com.compira.usecase.gettask.GetTaskUseCase;
import co.com.compira.usecase.gettaskhistory.GetTaskHistoryUseCase;
import co.com.compira.usecase.gettaskobservations.GetTaskObservationsUseCase;
import co.com.compira.usecase.listassignedtasks.ListAssignedTasksUseCase;
import co.com.compira.usecase.listmanagedtasks.ListManagedTasksUseCase;
import co.com.compira.usecase.reassigntask.ReassignTaskUseCase;
import co.com.compira.usecase.updatetaskstatus.UpdateTaskStatusUseCase;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskHandlerTest {
    private final CreateTaskUseCase createTaskUseCase = mock(CreateTaskUseCase.class);
    private final AssignTaskUseCase assignTaskUseCase = mock(AssignTaskUseCase.class);
    private final ReassignTaskUseCase reassignTaskUseCase = mock(ReassignTaskUseCase.class);
    private final UpdateTaskStatusUseCase updateTaskStatusUseCase = mock(UpdateTaskStatusUseCase.class);
    private final AddTaskObservationUseCase addTaskObservationUseCase = mock(AddTaskObservationUseCase.class);
    private final CancelTaskUseCase cancelTaskUseCase = mock(CancelTaskUseCase.class);
    private final ApproveTaskUseCase approveTaskUseCase = mock(ApproveTaskUseCase.class);
    private final ListAssignedTasksUseCase listAssignedTasksUseCase = mock(ListAssignedTasksUseCase.class);
    private final ListManagedTasksUseCase listManagedTasksUseCase = mock(ListManagedTasksUseCase.class);
    private final GetTaskHistoryUseCase getTaskHistoryUseCase = mock(GetTaskHistoryUseCase.class);
    private final GetTaskObservationsUseCase getTaskObservationsUseCase = mock(GetTaskObservationsUseCase.class);
    private final co.com.compira.usecase.gettaskindicators.GetTaskIndicatorsUseCase getTaskIndicatorsUseCase = mock(co.com.compira.usecase.gettaskindicators.GetTaskIndicatorsUseCase.class);
    private final co.com.compira.usecase.gettaskreports.GetTaskReportsUseCase getTaskReportsUseCase = mock(co.com.compira.usecase.gettaskreports.GetTaskReportsUseCase.class);
    private final GetTaskUseCase getTaskUseCase = mock(GetTaskUseCase.class);
    private WebTestClient webTestClient;

    private static final String BASE = TaskRoute.API_V1 + TaskRoute.TASKS_BASE;

    @BeforeEach
    void setUp() {
        org.springframework.transaction.reactive.TransactionalOperator transactions = mock(org.springframework.transaction.reactive.TransactionalOperator.class);
        when(transactions.transactional(org.mockito.ArgumentMatchers.<Mono<Object>>any())).thenAnswer(invocation -> invocation.getArgument(0));
        co.com.compira.usecase.teams.TeamsUseCase teams = mock(co.com.compira.usecase.teams.TeamsUseCase.class);
        when(teams.requireTaskAccess(any(), any())).thenReturn(Mono.empty());
        TaskHandler taskHandler = new TaskHandler(
                createTaskUseCase,
                assignTaskUseCase,
                reassignTaskUseCase,
                updateTaskStatusUseCase,
                addTaskObservationUseCase,
                cancelTaskUseCase,
                approveTaskUseCase,
                listAssignedTasksUseCase,
                listManagedTasksUseCase,
                getTaskHistoryUseCase,
                getTaskObservationsUseCase,
                getTaskIndicatorsUseCase,
                getTaskReportsUseCase,
                getTaskUseCase,
                new TaskRequestValidator(Validation.buildDefaultValidatorFactory().getValidator()),
                new TaskRequestMapper(),
                new TaskResponseMapper(() -> java.time.OffsetDateTime.parse("2026-09-24T10:00:00Z")),
                new TaskErrorHandler(), transactions);

        webTestClient = WebTestClient.bindToRouterFunction(new TaskRouterRest().taskRouterFunction(taskHandler, teams, new TaskErrorHandler()))
                .webFilter((exchange, chain) -> {
                    String email = exchange.getRequest().getHeaders().getFirst(TaskRoute.ACTOR_EMAIL_HEADER);
                    return chain.filter(email == null ? exchange : exchange.mutate()
                            .principal(Mono.just((java.security.Principal) () -> email)).build());
                }).build();
    }

    @Test
    void shouldCreateTask() {
        givenTaskCreated();

        webTestClient.post()
                .uri(BASE)
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateBody())
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    void shouldRejectCreateWithoutAuthenticatedPrincipal() {
        webTestClient.post()
                .uri(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validCreateBody())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectCreateWhenTitleMissing() {
        webTestClient.post()
                .uri(BASE)
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBodyWithout("title"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    private static final String TEAM_ID = "55555555-5555-5555-5555-555555555555";

    /** Fecha límite futura de los cuerpos válidos; el handler no la compara con el reloj (lo hace el caso de uso). */
    private static final String FUTURE_DUE_DATE = "2030-01-01T10:00:00Z";

    private void givenTaskCreated() {
        when(createTaskUseCase.execute(any())).thenReturn(Mono.just(TaskApiTestData.task(TaskStatus.PENDING)));
    }

    private void givenUseCaseFails(String code, String message, ErrorCategory category) {
        when(createTaskUseCase.execute(any())).thenReturn(Mono.error(new CompiraException(code, message, category)));
    }

    private static Map<String, Object> validCreateBody() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("title", "Preparar informe");
        body.put("description", "Detalle de la tarea");
        body.put("dueDate", FUTURE_DUE_DATE);
        body.put("teamId", TEAM_ID);
        return body;
    }

    private static Map<String, Object> createBodyWith(String key, Object value) {
        Map<String, Object> body = validCreateBody();
        body.put(key, value);
        return body;
    }

    private static Map<String, Object> createBodyWithout(String... keys) {
        Map<String, Object> body = validCreateBody();
        for (String key : keys) {
            body.remove(key);
        }
        return body;
    }

    /**
     * HU Crear tarea - E3 / spec C.4 (O6).
     * Caso: la solicitud llega sin descripción.
     * Esperado: se rechaza con "La descripción de la tarea es obligatoria" y la tarea no se crea.
     * Resultado: HTTP 400 con ese mensaje y el caso de uso nunca se invoca.
     */
    @Test
    @DisplayName("E3 - Sin descripción: 400 'La descripción de la tarea es obligatoria'")
    void shouldRejectCreateWithoutDescription() {
        postCreate(createBodyWithout("description"))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo(TaskValidationMessage.DESCRIPTION_REQUIRED);

        verifyNoInteractions(createTaskUseCase);
    }

    /**
     * HU Crear tarea - E3 / spec C.4 (O6).
     * Caso: la descripción solo contiene espacios.
     * Esperado: se rechaza con "La descripción de la tarea es obligatoria".
     * Resultado: HTTP 400 y el caso de uso nunca se invoca.
     */
    @Test
    @DisplayName("E3 - Descripción en blanco: 400 'La descripción de la tarea es obligatoria'")
    void shouldRejectCreateWithBlankDescription() {
        postCreate(createBodyWith("description", "   "))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo(TaskValidationMessage.DESCRIPTION_REQUIRED);

        verifyNoInteractions(createTaskUseCase);
    }

    /**
     * HU Crear tarea - E3 / spec C.4 (O7).
     * Caso: la solicitud llega sin fecha límite.
     * Esperado: se rechaza con "La fecha límite es obligatoria" y la tarea no se crea.
     * Resultado: HTTP 400 con ese mensaje y el caso de uso nunca se invoca.
     */
    @Test
    @DisplayName("E3 - Sin fecha límite: 400 'La fecha límite es obligatoria'")
    void shouldRejectCreateWithoutDueDate() {
        postCreate(createBodyWithout("dueDate"))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo(TaskValidationMessage.DUE_DATE_REQUIRED);

        verifyNoInteractions(createTaskUseCase);
    }

    /**
     * HU Crear tarea - E3 / spec C.4 (O6 y O7).
     * Caso: la solicitud llega sin descripción ni fecha límite.
     * Esperado: se informan ambos mensajes, ordenados alfabéticamente y separados por ", ".
     * Resultado: HTTP 400 con "La descripción de la tarea es obligatoria, La fecha límite es obligatoria".
     */
    @Test
    @DisplayName("E3 - Sin descripción ni fecha: 400 con ambos mensajes ordenados")
    void shouldRejectCreateWithoutDescriptionNorDueDate() {
        postCreate(createBodyWithout("description", "dueDate"))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo(
                        TaskValidationMessage.DESCRIPTION_REQUIRED + ", " + TaskValidationMessage.DUE_DATE_REQUIRED);

        verifyNoInteractions(createTaskUseCase);
    }

    /**
     * HU Crear tarea - E3 (todos los campos obligatorios vacíos).
     * Caso: la solicitud llega sin título, descripción, fecha límite ni equipo.
     * Esperado: se informan los cuatro mensajes, ordenados alfabéticamente y separados por ", ", y la tarea no se crea.
     * Resultado: HTTP 400 con los cuatro mensajes y el caso de uso nunca se invoca.
     */
    @Test
    @DisplayName("E3 - Sin título, descripción, fecha ni equipo: 400 con los cuatro mensajes ordenados")
    void shouldRejectCreateWithoutAnyRequiredField() {
        postCreate(createBodyWithout("title", "description", "dueDate", "teamId"))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo(String.join(", ",
                        TaskValidationMessage.TEAM_REQUIRED,
                        TaskValidationMessage.TITLE_REQUIRED,
                        TaskValidationMessage.DESCRIPTION_REQUIRED,
                        TaskValidationMessage.DUE_DATE_REQUIRED));

        verifyNoInteractions(createTaskUseCase);
    }

    /**
     * HU Crear tarea - E1 / spec C.4 (O9).
     * Caso: cuerpo completo sin responsable.
     * Esperado: el responsable sigue siendo opcional y la tarea se crea; el comando llega sin responsable.
     * Resultado: HTTP 201 y `responsibleEmail` nulo en el comando enviado al caso de uso.
     */
    @Test
    @DisplayName("E1 - Cuerpo completo sin responsable: 201 y responsable opcional")
    void shouldCreateTaskWithoutResponsible() {
        givenTaskCreated();

        postCreate(validCreateBody()).expectStatus().isCreated();

        ArgumentCaptor<CreateTaskCommand> command =
                ArgumentCaptor.forClass(CreateTaskCommand.class);
        verify(createTaskUseCase).execute(command.capture());
        assertNull(command.getValue().responsibleEmail());
    }

    /**
     * HU Crear tarea - E3 / spec D.5 (R-D2, límite).
     * Caso: descripción de exactamente 2000 caracteres.
     * Esperado: se acepta y la tarea se crea.
     * Resultado: HTTP 201.
     */
    @Test
    @DisplayName("E3 - Descripción de 2000 caracteres es aceptada")
    void shouldAcceptDescriptionWithMaximumLength() {
        givenTaskCreated();

        postCreate(createBodyWith("description", "a".repeat(2000))).expectStatus().isCreated();
    }

    private WebTestClient.ResponseSpec postCreate(Map<String, Object> body) {
        return webTestClient.post()
                .uri(BASE)
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    /**
     * HU Crear tarea - E2.
     * Caso: solicitud completa (equipo, título, descripción, fecha límite y correo del responsable).
     * Esperado: los datos llegan intactos al caso de uso y se responde 201 con la tarea en estado PENDING.
     * Resultado: HTTP 201, estado "PENDING" y comando con título, descripción, responsable y equipo de la solicitud.
     */
    @Test
    @DisplayName("E2 - Solicitud completa: 201, tarea Pendiente y datos mapeados al comando")
    void shouldMapFullRequestToCommandAndRespondCreated() {
        givenTaskCreated();

        postCreate(Map.of("title", "Preparar informe", "description", "Detalle",
                "dueDate", FUTURE_DUE_DATE, "responsibleEmail", "colab@compira.co", "teamId", TEAM_ID))
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.status").isEqualTo("PENDING");

        ArgumentCaptor<CreateTaskCommand> command =
                ArgumentCaptor.forClass(CreateTaskCommand.class);
        verify(createTaskUseCase).execute(command.capture());
        assertEquals(TaskApiTestData.ACTOR_EMAIL, command.getValue().actorEmail());
        assertEquals("Preparar informe", command.getValue().title());
        assertEquals("Detalle", command.getValue().description());
        assertEquals("colab@compira.co", command.getValue().responsibleEmail());
        assertEquals(java.util.UUID.fromString(TEAM_ID), command.getValue().teamId());
        assertEquals(java.time.OffsetDateTime.parse(FUTURE_DUE_DATE).toInstant(), command.getValue().dueDate().toInstant());
    }

    /**
     * HU Crear tarea - E3 (límite del título).
     * Caso: título de exactamente 150 caracteres.
     * Esperado: se acepta y la tarea se crea.
     * Resultado: HTTP 201.
     */
    @Test
    @DisplayName("E3 - Título de 150 caracteres es aceptado")
    void shouldAcceptTitleWithMaximumLength() {
        givenTaskCreated();

        postCreate(createBodyWith("title", "a".repeat(150))).expectStatus().isCreated();
    }

    /**
     * HU Crear tarea - E3 (título demasiado largo).
     * Caso: título de 151 caracteres.
     * Esperado: se rechaza con "El título puede tener máximo 150 caracteres" y la tarea no se crea.
     * Resultado: HTTP 400 y el caso de uso nunca se invoca.
     */
    @Test
    @DisplayName("E3 - Título de 151 caracteres: 400 y no se crea la tarea")
    void shouldRejectTitleLongerThanMaximumLength() {
        postCreate(createBodyWith("title", "a".repeat(151)))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo(TaskValidationMessage.TITLE_LENGTH);

        verifyNoInteractions(createTaskUseCase);
    }

    /**
     * HU Crear tarea - E3 (título vacío).
     * Caso: título compuesto solo por espacios.
     * Esperado: se rechaza con "El título de la tarea es obligatorio" y la tarea no se crea.
     * Resultado: HTTP 400 y el caso de uso nunca se invoca.
     */
    @Test
    @DisplayName("E3 - Título en blanco: 400 y no se crea la tarea")
    void shouldRejectBlankTitle() {
        postCreate(createBodyWith("title", "   "))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo(TaskValidationMessage.TITLE_REQUIRED);

        verifyNoInteractions(createTaskUseCase);
    }

    /**
     * HU Crear tarea - E3 (equipo obligatorio).
     * Caso: solicitud sin equipo.
     * Esperado: se rechaza con "El equipo es obligatorio" y la tarea no se crea.
     * Resultado: HTTP 400 y el caso de uso nunca se invoca.
     */
    @Test
    @DisplayName("E3 - Sin equipo: 400 y no se crea la tarea")
    void shouldRejectCreateWithoutTeam() {
        postCreate(createBodyWithout("teamId"))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo(TaskValidationMessage.TEAM_REQUIRED);

        verifyNoInteractions(createTaskUseCase);
    }

    /**
     * HU Crear tarea - E3 (descripción demasiado larga).
     * Caso: descripción de 2001 caracteres (supera el máximo vigente de 2000 caracteres).
     * Esperado: se rechaza con "La descripción puede tener máximo 2000 caracteres" y la tarea no se crea.
     * Resultado: HTTP 400 y el caso de uso nunca se invoca.
     */
    @Test
    @DisplayName("E3 - Descripción sobre el máximo vigente (2000): 400 y no se crea la tarea")
    void shouldRejectDescriptionLongerThanMaximumLength() {
        postCreate(createBodyWith("description", "a".repeat(2001)))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo(TaskValidationMessage.DESCRIPTION_LENGTH);

        verifyNoInteractions(createTaskUseCase);
    }

    /**
     * HU Crear tarea - E2/E5 (formato del correo del responsable).
     * Caso: correo del responsable con formato inválido.
     * Esperado: se rechaza con "El correo del responsable no tiene un formato válido" y la tarea no se crea.
     * Resultado: HTTP 400 y el caso de uso nunca se invoca.
     */
    @Test
    @DisplayName("E5 - Correo de responsable con formato inválido: 400 y no se crea la tarea")
    void shouldRejectInvalidResponsibleEmailFormat() {
        postCreate(createBodyWith("responsibleEmail", "no-es-un-correo"))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message").isEqualTo(TaskValidationMessage.RESPONSIBLE_EMAIL_INVALID);

        verifyNoInteractions(createTaskUseCase);
    }

    /**
     * HU Crear tarea - E4.
     * Caso: el caso de uso rechaza la fecha límite por ser anterior al momento actual.
     * Esperado: la API responde el mensaje "La fecha límite no puede ser anterior al momento actual".
     * Resultado: HTTP 400 con código TASK_011 y el mensaje esperado.
     */
    @Test
    @DisplayName("E4 - Fecha límite pasada: 400 con el mensaje esperado")
    void shouldRespondBadRequestWhenDueDateIsInThePast() {
        givenUseCaseFails(TaskErrorCode.DUE_DATE_IN_PAST, TaskMessage.DUE_DATE_IN_PAST, ErrorCategory.BAD_REQUEST);

        postCreate(createBodyWith("dueDate", "2020-01-01T10:00:00Z"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TASK_011")
                .jsonPath("$.message").isEqualTo("La fecha límite no puede ser anterior al momento actual");
    }

    /**
     * HU Crear tarea - E5.
     * Caso: el caso de uso rechaza al responsable (no existe o está inactivo).
     * Esperado: la API responde "No se encontró el colaborador responsable indicado".
     * Resultado: HTTP 404 con código TASK_004 y el mensaje esperado.
     */
    @Test
    @DisplayName("E5 - Responsable no encontrado: 404 con el mensaje esperado")
    void shouldRespondNotFoundWhenResponsibleDoesNotExist() {
        givenUseCaseFails(TaskErrorCode.RESPONSIBLE_NOT_FOUND, TaskMessage.RESPONSIBLE_NOT_FOUND, ErrorCategory.NOT_FOUND);

        postCreate(createBodyWith("responsibleEmail", "nadie@compira.co"))
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TASK_004")
                .jsonPath("$.message").isEqualTo("No se encontró el colaborador responsable indicado");
    }

    /**
     * HU Crear tarea - E5.
     * Caso: el caso de uso rechaza al responsable por no tener rol de colaborador.
     * Esperado: la API responde "El responsable de una tarea debe ser un colaborador".
     * Resultado: HTTP 400 con código TASK_005 y el mensaje esperado.
     */
    @Test
    @DisplayName("E5 - Responsable sin rol de colaborador: 400 con el mensaje esperado")
    void shouldRespondBadRequestWhenResponsibleIsNotCollaborator() {
        givenUseCaseFails(TaskErrorCode.RESPONSIBLE_NOT_COLLABORATOR, TaskMessage.RESPONSIBLE_NOT_COLLABORATOR,
                ErrorCategory.BAD_REQUEST);

        postCreate(createBodyWith("responsibleEmail", "jefe@compira.co"))
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TASK_005")
                .jsonPath("$.message").isEqualTo("El responsable de una tarea debe ser un colaborador");
    }

    /**
     * HU Crear tarea - E5.
     * Caso: el caso de uso rechaza al responsable por pertenecer a otro equipo.
     * Esperado: la API responde "El responsable debe pertenecer al equipo de la tarea".
     * Resultado: HTTP 409 con el mensaje esperado.
     */
    @Test
    @DisplayName("E5 - Responsable de otro equipo: 409 con el mensaje esperado")
    void shouldRespondConflictWhenResponsibleBelongsToAnotherTeam() {
        givenUseCaseFails("TEAM_001", "El responsable debe pertenecer al equipo de la tarea", ErrorCategory.CONFLICT);

        postCreate(createBodyWith("responsibleEmail", "otro@compira.co"))
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.message").isEqualTo("El responsable debe pertenecer al equipo de la tarea");
    }

    /**
     * HU Crear tarea - E6/E8.
     * Caso: el caso de uso rechaza al usuario por no ser coordinador o no coordinar el equipo indicado.
     * Esperado: la API responde acceso prohibido y no crea la tarea.
     * Resultado: HTTP 403 con código TASK_007 y el mensaje "Esta operación solo puede realizarla un coordinador".
     */
    @Test
    @DisplayName("E8 - Usuario sin rol de coordinador: 403 con el mensaje esperado")
    void shouldRespondForbiddenWhenActorIsNotCoordinator() {
        givenUseCaseFails(TaskErrorCode.ACTOR_NOT_COORDINATOR, TaskMessage.ACTOR_NOT_COORDINATOR, ErrorCategory.FORBIDDEN);

        postCreate(validCreateBody())
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TASK_007")
                .jsonPath("$.message").isEqualTo("Esta operación solo puede realizarla un coordinador");
    }

    @Test
    void shouldListManagedTasks() {
        when(listManagedTasksUseCase.execute(TaskApiTestData.ACTOR_EMAIL))
                .thenReturn(Flux.just(TaskApiTestData.task(TaskStatus.PENDING)));

        webTestClient.get()
                .uri(BASE)
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldListAssignedTasks() {
        when(listAssignedTasksUseCase.execute(TaskApiTestData.ACTOR_EMAIL))
                .thenReturn(Flux.just(TaskApiTestData.task(TaskStatus.IN_PROGRESS)));

        webTestClient.get()
                .uri(BASE + TaskRoute.ASSIGNED)
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldUpdateStatus() {
        when(updateTaskStatusUseCase.execute(any())).thenReturn(Mono.just(TaskApiTestData.task(TaskStatus.IN_PROGRESS)));

        webTestClient.post()
                .uri(BASE + "/" + TaskApiTestData.TASK_ID + "/status")
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("status", "IN_PROGRESS"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldApproveTask() {
        when(approveTaskUseCase.execute(any())).thenReturn(Mono.just(TaskApiTestData.task(TaskStatus.CLOSED)));

        webTestClient.post()
                .uri(BASE + "/" + TaskApiTestData.TASK_ID + "/approve")
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldCancelTask() {
        when(cancelTaskUseCase.execute(any())).thenReturn(Mono.just(TaskApiTestData.task(TaskStatus.CANCELLED)));

        webTestClient.post()
                .uri(BASE + "/" + TaskApiTestData.TASK_ID + "/cancel")
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("reason", "Ya no aplica"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldReassignTask() {
        when(reassignTaskUseCase.execute(any())).thenReturn(Mono.just(TaskApiTestData.task(TaskStatus.PENDING)));

        webTestClient.post()
                .uri(BASE + "/" + TaskApiTestData.TASK_ID + "/reassign")
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("newResponsibleEmail", "nuevo@compira.co"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAssignTask() {
        when(assignTaskUseCase.execute(any())).thenReturn(Mono.just(TaskApiTestData.task(TaskStatus.PENDING)));

        webTestClient.post()
                .uri(BASE + "/" + TaskApiTestData.TASK_ID + "/assign")
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("responsibleEmail", "colab@compira.co"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAddObservation() {
        when(addTaskObservationUseCase.execute(any())).thenReturn(Mono.just(TaskApiTestData.observation()));

        webTestClient.post()
                .uri(BASE + "/" + TaskApiTestData.TASK_ID + "/observations")
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("content", "Avance del 50%"))
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    void shouldListObservations() {
        when(getTaskObservationsUseCase.execute(any(), any()))
                .thenReturn(Flux.just(TaskApiTestData.observation()));

        webTestClient.get()
                .uri(BASE + "/" + TaskApiTestData.TASK_ID + "/observations")
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldListHistory() {
        when(getTaskHistoryUseCase.execute(any(), any()))
                .thenReturn(Flux.just(TaskApiTestData.historyEntry()));

        webTestClient.get()
                .uri(BASE + "/" + TaskApiTestData.TASK_ID + "/history")
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldGetTask() {
        when(getTaskUseCase.execute(any(), any()))
                .thenReturn(Mono.just(TaskApiTestData.task(TaskStatus.PENDING)));

        webTestClient.get()
                .uri(BASE + "/" + TaskApiTestData.TASK_ID)
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldGetIndicators() {
        when(getTaskIndicatorsUseCase.execute(TaskApiTestData.ACTOR_EMAIL))
                .thenReturn(Mono.just(new co.com.compira.model.task.TaskIndicators(
                        5, 2, 1, 2, 1, 50,
                        java.util.List.of(new co.com.compira.model.task.TaskIndicators.AssigneeWorkload(
                                TaskApiTestData.TASK_ID, "Ana", "ana@compira.co", 3)),
                        java.util.List.of(new co.com.compira.model.task.TaskIndicators.Assignee(
                                TaskApiTestData.TASK_ID, "Ana", "ana@compira.co")))));

        webTestClient.get()
                .uri(BASE + "/indicators")
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.compliancePercentage").isEqualTo(50)
                .jsonPath("$.workloadByAssignee[0].assigneeName").isEqualTo("Ana");
    }

    @Test
    void shouldGetReports() {
        when(getTaskReportsUseCase.execute(TaskApiTestData.ACTOR_EMAIL))
                .thenReturn(Mono.just(new co.com.compira.model.task.TaskReport(
                        java.util.List.of(new co.com.compira.model.task.TaskReport.AssigneeReportRow(
                                TaskApiTestData.TASK_ID, "Ana", "ana@compira.co", 4, 2, 1, 1, 1, 100, 12.5)))));

        webTestClient.get()
                .uri(BASE + "/reports")
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.rows[0].assigneeName").isEqualTo("Ana")
                .jsonPath("$.rows[0].averageClosureHours").isEqualTo(12.5);
    }
}
