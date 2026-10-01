package co.com.compira.api.notification;

import co.com.compira.api.config.SecurityTestData;
import co.com.compira.api.organization.OrganizationSettingsHandler;
import co.com.compira.api.organization.OrganizationSettingsMapper;
import co.com.compira.api.router.NotificationRouterRest;
import co.com.compira.api.task.TaskErrorHandler;
import co.com.compira.api.task.TaskRequestValidator;
import co.com.compira.api.team.TeamHandler;
import co.com.compira.api.team.TeamResponseMapper;
import co.com.compira.model.notification.NotificationType;
import co.com.compira.model.notification.TaskNotification;
import co.com.compira.model.organization.OrganizationSettings;
import co.com.compira.usecase.notifications.TaskNotificationsUseCase;
import co.com.compira.usecase.organizationsettings.OrganizationSettingsUseCase;
import co.com.compira.usecase.teams.TeamsUseCase;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.OffsetDateTime;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class Module3HandlerTest {
    private final TaskNotificationsUseCase notifications = mock(TaskNotificationsUseCase.class);
    private final OrganizationSettingsUseCase settings = mock(OrganizationSettingsUseCase.class);
    private final TeamsUseCase teams = mock(TeamsUseCase.class);
    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private WebTestClient client;

    @BeforeEach
    void configure() {
        var errors = new TaskErrorHandler();
        var validator = new TaskRequestValidator(factory.getValidator());
        var handler = new NotificationHandler(notifications, new NotificationResponseMapper(), errors);
        var organization = new OrganizationSettingsHandler(settings, new OrganizationSettingsMapper(), validator, errors);
        var teamHandler = new TeamHandler(teams, validator, errors, mock(TransactionalOperator.class), new TeamResponseMapper());
        var principal = new JwtAuthenticationToken(SecurityTestData.token(), List.of(), SecurityTestData.EMAIL);
        client = WebTestClient.bindToRouterFunction(new NotificationRouterRest().notificationRoutes(handler, organization, teamHandler))
                .webFilter((exchange, chain) -> chain.filter(exchange.mutate().principal(Mono.just(principal)).build())).build();
    }

    @AfterEach
    void closeValidator() { factory.close(); }

    @Test
    void mapsTeamsWithoutExposingDomainObjects() {
        when(teams.list(SecurityTestData.EMAIL)).thenReturn(Flux.just(SecurityTestData.team()));
        client.get().uri(TeamHandler.BASE).exchange().expectStatus().isOk().expectBody()
                .jsonPath("$[0].name").isEqualTo("Operaciones")
                .jsonPath("$[0].coordinatorEmail").isEqualTo(SecurityTestData.EMAIL);
    }

    @Test
    void listsOnlyAuthenticatedRecipientAndMapsIdsAsStrings() {
        var id = SecurityTestData.user("COLLABORATOR", co.com.compira.model.auth.UserStatus.ACTIVE).user().id();
        var notification = new TaskNotification(12L, id, "Informe", NotificationType.ASSIGNED, OffsetDateTime.parse("2026-09-26T12:00:00Z"), null);
        when(notifications.list(SecurityTestData.EMAIL, 20)).thenReturn(Flux.just(notification));
        client.get().uri(NotificationHandler.BASE + "?before=20").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectBody().jsonPath("$[0].id").isEqualTo("12").jsonPath("$[0].type").isEqualTo("ASSIGNED");
        verify(notifications).list(SecurityTestData.EMAIL, 20);
    }

    @Test
    void marksASingleNotificationAsRead() {
        when(notifications.markRead(SecurityTestData.EMAIL, 12L)).thenReturn(Mono.empty());
        client.post().uri(NotificationHandler.BASE + "/12/read").exchange().expectStatus().isNoContent();
        verify(notifications).markRead(SecurityTestData.EMAIL, 12L);
    }

    @Test
    void rejectsInvalidNotificationIdForRead() {
        client.post().uri(NotificationHandler.BASE + "/abc/read").exchange().expectStatus().isBadRequest();
        verify(notifications, never()).markRead(any(), anyLong());
    }

    @Test
    void marksAllNotificationsAsRead() {
        when(notifications.markAllRead(SecurityTestData.EMAIL)).thenReturn(Mono.empty());
        client.post().uri(NotificationHandler.READ_ALL).exchange().expectStatus().isNoContent();
        verify(notifications).markAllRead(SecurityTestData.EMAIL);
    }

    @Test
    void rejectsInvalidPaginationCursor() {
        client.get().uri(NotificationHandler.BASE + "?before=0").exchange().expectStatus().isBadRequest();
        client.get().uri(NotificationHandler.BASE + "?before=invalid").exchange().expectStatus().isBadRequest();
        verifyNoInteractions(notifications);
    }

    @Test
    void streamsAuthenticatedSnapshots() {
        when(notifications.list(SecurityTestData.EMAIL, Long.MAX_VALUE)).thenReturn(Flux.empty());
        var events = client.get().uri(NotificationHandler.STREAM).accept(MediaType.TEXT_EVENT_STREAM)
                .exchange().expectStatus().isOk().returnResult(String.class).getResponseBody();
        StepVerifier.create(events).expectNext("[]").thenCancel().verify(java.time.Duration.ofSeconds(5));
    }

    @Test
    void validatesAndPersistsOrganizationSettings() {
        var configuration = new OrganizationSettings("America/Bogota", true);
        when(settings.save(SecurityTestData.EMAIL, configuration)).thenReturn(Mono.just(configuration));
        client.put().uri(OrganizationSettingsHandler.BASE).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"timeZone\":\"America/Bogota\",\"notificationsEnabled\":true}").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.timeZone").isEqualTo("America/Bogota")
                .jsonPath("$.notificationsEnabled").isEqualTo(true);
        client.put().uri(OrganizationSettingsHandler.BASE).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"timeZone\":\"Invalid/Zone\",\"notificationsEnabled\":true}").exchange().expectStatus().isBadRequest();
        client.put().uri(OrganizationSettingsHandler.BASE).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"timeZone\":\"America/Bogota\"}").exchange().expectStatus().isBadRequest();
        verify(settings, times(1)).save(anyString(), any());
    }
}
