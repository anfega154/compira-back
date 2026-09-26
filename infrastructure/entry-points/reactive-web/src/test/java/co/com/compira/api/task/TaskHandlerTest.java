package co.com.compira.api.task;

import co.com.compira.api.router.TaskRouterRest;
import co.com.compira.api.task.mapper.TaskRequestMapper;
import co.com.compira.api.task.mapper.TaskResponseMapper;
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
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
                getTaskUseCase,
                new TaskRequestValidator(Validation.buildDefaultValidatorFactory().getValidator()),
                new TaskRequestMapper(),
                new TaskResponseMapper(),
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
        when(createTaskUseCase.execute(any())).thenReturn(Mono.just(TaskApiTestData.task(TaskStatus.PENDING)));

        webTestClient.post()
                .uri(BASE)
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("title", "Preparar informe", "teamId", "55555555-5555-5555-5555-555555555555"))
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    void shouldRejectCreateWithoutAuthenticatedPrincipal() {
        webTestClient.post()
                .uri(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("title", "Preparar informe", "teamId", "55555555-5555-5555-5555-555555555555"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectCreateWhenTitleMissing() {
        webTestClient.post()
                .uri(BASE)
                .header(TaskRoute.ACTOR_EMAIL_HEADER, TaskApiTestData.ACTOR_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("description", "Sin titulo"))
                .exchange()
                .expectStatus().isBadRequest();
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
}
