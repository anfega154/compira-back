package co.com.compira.api.team;

import co.com.compira.api.task.TaskErrorHandler;
import co.com.compira.api.task.TaskRequestValidator;
import co.com.compira.model.common.error.CompiraException;
import co.com.compira.model.common.error.ErrorCategory;
import co.com.compira.model.team.TeamMember;
import co.com.compira.usecase.teams.TeamsUseCase;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.reactive.function.server.RouterFunctions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeamHandlerTest {
    private static final String ACTOR_HEADER = "X-Test-Actor";
    private static final String ACTOR = "coordinador@compira.co";
    private static final UUID TEAM_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String MEMBERS_URI = "/api/v1/teams/{teamId}/members";

    private final TeamsUseCase teams = mock(TeamsUseCase.class);
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        TransactionalOperator transactions = mock(TransactionalOperator.class);
        TeamHandler handler = new TeamHandler(teams,
                new TaskRequestValidator(Validation.buildDefaultValidatorFactory().getValidator()),
                new TaskErrorHandler(), transactions, new TeamResponseMapper());
        client = WebTestClient.bindToRouterFunction(RouterFunctions.route().GET(TeamHandler.MEMBERS, handler::members).build())
                .webFilter((exchange, chain) -> {
                    String actor = exchange.getRequest().getHeaders().getFirst(ACTOR_HEADER);
                    return chain.filter(actor == null ? exchange
                            : exchange.mutate().principal(Mono.just((Principal) () -> actor)).build());
                }).build();
    }

    private WebTestClient.ResponseSpec getMembers(String teamId) {
        return client.get().uri(MEMBERS_URI, teamId).header(ACTOR_HEADER, ACTOR).exchange();
    }

    /**
     * Spec HU-12 Parte A (R1, contrato).
     * Caso: el coordinador consulta los miembros de su equipo.
     * Esperado: lista JSON con `id`, `email`, `firstName` y `lastName`; los nombres nulos se conservan como null.
     * Resultado: HTTP 200, dos elementos con los campos del contrato y el actor tomado del token.
     */
    @Test
    @DisplayName("R1 - 200 con id, email, firstName y lastName de cada colaborador")
    void shouldRespondMembersWithContractFields() {
        UUID firstId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID secondId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        when(teams.listMembers(ACTOR, TEAM_ID)).thenReturn(Flux.just(
                new TeamMember(firstId, "colab@compira.co", "Colab", "Uno"),
                new TeamMember(secondId, "sin.nombre@compira.co", null, null)));

        getMembers(TEAM_ID.toString())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo(firstId.toString())
                .jsonPath("$[0].email").isEqualTo("colab@compira.co")
                .jsonPath("$[0].firstName").isEqualTo("Colab")
                .jsonPath("$[0].lastName").isEqualTo("Uno")
                .jsonPath("$[1].email").isEqualTo("sin.nombre@compira.co")
                .jsonPath("$[1].firstName").isEmpty()
                .jsonPath("$[1].lastName").isEmpty();
    }

    /**
     * Spec HU-12 Parte A (R1).
     * Caso: el equipo no tiene colaboradores activos.
     * Esperado: lista vacía.
     * Resultado: HTTP 200 con arreglo JSON vacío.
     */
    @Test
    @DisplayName("R1 - Equipo sin colaboradores: 200 con lista vacía")
    void shouldRespondEmptyListWhenTeamHasNoMembers() {
        when(teams.listMembers(ACTOR, TEAM_ID)).thenReturn(Flux.empty());

        getMembers(TEAM_ID.toString())
                .expectStatus().isOk()
                .expectBody().json("[]");
    }

    /**
     * Spec HU-12 Parte A (R4).
     * Caso: `teamId` no es un UUID válido.
     * Esperado: solicitud inválida, sin consultar el caso de uso.
     * Resultado: HTTP 400 y `listMembers` nunca se invoca.
     */
    @Test
    @DisplayName("R4 - teamId que no es UUID: 400 y no se consulta el caso de uso")
    void shouldRespondBadRequestWhenTeamIdIsNotAUuid() {
        getMembers("no-es-un-uuid").expectStatus().isBadRequest();

        verifyNoInteractions(teams);
    }

    /**
     * Spec HU-12 Parte A (R2 y R4).
     * Caso: el caso de uso rechaza la consulta porque el actor no tiene acceso al equipo (coordinador de otro equipo
     * o colaborador) o porque el equipo no existe.
     * Esperado: el error se mapea a su estado HTTP con código TEAM_001 y el mensaje del caso de uso.
     * Resultado: 403 "No tienes acceso a este equipo" y 404 "Equipo no encontrado".
     */
    @ParameterizedTest(name = "{0} -> HTTP {1}")
    @CsvSource({
            "FORBIDDEN,403,No tienes acceso a este equipo",
            "NOT_FOUND,404,Equipo no encontrado"})
    @DisplayName("R2/R4 - Errores del caso de uso se mapean a 403 y 404 con TEAM_001 y su mensaje")
    void shouldMapUseCaseErrorsToHttpStatusWithCodeAndMessage(ErrorCategory category, int status, String message) {
        when(teams.listMembers(eq(ACTOR), any())).thenReturn(Flux.error(new CompiraException("TEAM_001", message, category)));

        getMembers(TEAM_ID.toString())
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.code").isEqualTo("TEAM_001")
                .jsonPath("$.message").isEqualTo(message);
    }
}
