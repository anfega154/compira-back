package co.com.compira.api.config;

import co.com.compira.model.auth.UserStatus;
import co.com.compira.api.team.TeamHandler;
import co.com.compira.model.auth.RoleCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import co.com.compira.model.auth.gateways.ApplicationUserRepositoryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.junit.jupiter.api.AfterEach;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ResourceServerConfigTest {
    private AnnotationConfigApplicationContext context;
    private ApplicationUserRepositoryGateway users;
    private WebTestClient client;

    @BeforeEach
    void configure() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        users = context.getBean(ApplicationUserRepositoryGateway.class);
        when(users.findByCognitoSub(SecurityTestData.SUBJECT)).thenReturn(Mono.just(SecurityTestData.user("COLLABORATOR", UserStatus.ACTIVE)));
        client = WebTestClient.bindToApplicationContext(context).build();
    }

    @AfterEach
    void closeContext() { context.close(); }

    @Test
    void rejectsMissingTokenEvenWithSpoofedActorHeader() {
        client.get().uri("/api/v1/notifications").header("X-Actor-Email", SecurityTestData.EMAIL).exchange().expectStatus().isUnauthorized();
        verifyNoInteractions(users);
    }

    @Test
    void usesVerifiedSubjectAndLocalEmailInsteadOfActorHeader() {
        client.get().uri("/api/v1/notifications").headers(headers -> headers.setBearerAuth("valid-token"))
                .header("X-Actor-Email", "admin@compira.co").exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo(SecurityTestData.EMAIL);
    }

    @Test
    void deniesInvalidOrExpiredTokens() {
        client.get().uri("/api/v1/notifications").headers(headers -> headers.setBearerAuth("invalid-token"))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void rejectsExpiredJwtBeforeAccessingTheAccount() {
        client.get().uri("/api/v1/notifications").headers(headers -> headers.setBearerAuth("expired-token"))
                .exchange().expectStatus().isUnauthorized()
                .expectHeader().valueMatches("WWW-Authenticate", ".*invalid_token.*");
        verifyNoInteractions(users);
    }

    @Test
    void deniesDisabledAccounts() {
        when(users.findByCognitoSub(SecurityTestData.SUBJECT)).thenReturn(Mono.just(SecurityTestData.user("COLLABORATOR", UserStatus.DISABLED)));
        client.get().uri("/api/v1/notifications").headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void requiresAdministratorForOrganizationAndTeamChanges() {
        client.put().uri("/api/v1/organization/settings").headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isForbidden();
        client.post().uri("/api/v1/teams").headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isForbidden();
        when(users.findByCognitoSub(SecurityTestData.SUBJECT)).thenReturn(Mono.just(SecurityTestData.user("ADMINISTRATOR", UserStatus.ACTIVE)));
        client.put().uri("/api/v1/organization/settings").headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isOk();
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"COORDINATOR", "COLLABORATOR"})
    void deniesUserManagementForNonAdministrators(RoleCode role) {
        when(users.findByCognitoSub(SecurityTestData.SUBJECT))
                .thenReturn(Mono.just(SecurityTestData.user(role.name(), UserStatus.ACTIVE)));
        client.post().uri("/api/v1/auth/register").headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void allowsUserManagementForActiveAdministrators() {
        when(users.findByCognitoSub(SecurityTestData.SUBJECT))
                .thenReturn(Mono.just(SecurityTestData.user("ADMINISTRATOR", UserStatus.ACTIVE)));
        client.post().uri("/api/v1/auth/register").headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isOk();
    }

    @Test
    void deniesRetiredPhysicalUserDeletionEvenForActiveAdministrators() {
        when(users.findByCognitoSub(SecurityTestData.SUBJECT))
                .thenReturn(Mono.just(SecurityTestData.user("ADMINISTRATOR", UserStatus.ACTIVE)));
        client.delete().uri("/api/v1/auth/users").headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void deniesPendingAccountsEvenWithValidTokens() {
        when(users.findByCognitoSub(SecurityTestData.SUBJECT))
                .thenReturn(Mono.just(SecurityTestData.user("COLLABORATOR", UserStatus.PENDING_CONFIRMATION)));
        client.get().uri("/api/v1/notifications").headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isUnauthorized();
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"ADMINISTRATOR", "COORDINATOR"})
    void allowsTeamLinkingEndpointsForAdministratorsAndCoordinators(RoleCode role) {
        when(users.findByCognitoSub(SecurityTestData.SUBJECT))
                .thenReturn(Mono.just(SecurityTestData.user(role.name(), UserStatus.ACTIVE)));
        client.post().uri(TeamHandler.MEMBERS, SecurityTestData.SUBJECT).headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isNoContent();
    }

    @Test
    void deniesTeamLinkingForCollaboratorsAndAnonymousRequests() {
        client.post().uri(TeamHandler.MEMBERS, SecurityTestData.SUBJECT).headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isForbidden();
        client.post().uri(TeamHandler.MEMBERS, SecurityTestData.SUBJECT).exchange().expectStatus().isUnauthorized();
    }

    private static final String MEMBERS_PATH = "/api/v1/teams/" + SecurityTestData.team().id() + "/members";
    private static final String ORGANIZATION_SETTINGS_PATH = "/api/v1/organization/settings";

    /**
     * Spec HU-12 Parte A (R1, R3).
     * Caso: un administrador o un coordinador activo consulta `GET /teams/{id}/members`.
     * Esperado: la solicitud pasa la autorización (la regla "solo su equipo" la aplica el caso de uso).
     * Resultado: HTTP 200 para cada rol.
     */
    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"ADMINISTRATOR", "COORDINATOR"})
    @org.junit.jupiter.api.DisplayName("R1/R3 - Administrador y coordinador pueden consultar miembros del equipo")
    void allowsTeamMembersQueryForAdministratorsAndCoordinators(RoleCode role) {
        when(users.findByCognitoSub(SecurityTestData.SUBJECT))
                .thenReturn(Mono.just(SecurityTestData.user(role.name(), UserStatus.ACTIVE)));
        client.get().uri(MEMBERS_PATH).headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isOk();
    }

    /**
     * Spec HU-12 Parte A (R5).
     * Caso: un colaborador activo o una solicitud sin token consulta `GET /teams/{id}/members`.
     * Esperado: se deniega el acceso.
     * Resultado: HTTP 403 para el colaborador y 401 sin token.
     */
    @Test
    @org.junit.jupiter.api.DisplayName("R5 - Colaborador recibe 403 y sin token 401 al consultar miembros")
    void deniesTeamMembersQueryForCollaboratorsAndAnonymousRequests() {
        client.get().uri(MEMBERS_PATH).headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isForbidden();
        client.get().uri(MEMBERS_PATH).exchange().expectStatus().isUnauthorized();
    }

    /**
     * Regresión de seguridad de /teams/**.
     * Caso: un coordinador activo invoca rutas de equipos distintas de `GET /teams/{id}/members`:
     * reasignar miembros (POST y GET sobre `/members/reassign`) y una ruta con un segmento adicional bajo `/members`.
     * Esperado: la apertura de `GET /teams/{id}/members` no amplía los permisos del resto de `/teams/**`
     * (la creación de equipos y el cambio de coordinador ya están cubiertos por
     * `deniesCoordinatorTeamCreationAndCoordinatorReplacement`).
     * Resultado: HTTP 403 en cada ruta.
     */
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "POST,/members/reassign",
            "GET,/members/reassign",
            "GET,/members/otro-segmento"})
    @org.junit.jupiter.api.DisplayName("Regresión - Coordinador no accede a otras rutas de /teams/** al abrir GET members")
    void keepsOtherTeamRoutesForbiddenForCoordinators(String method, String suffix) {
        when(users.findByCognitoSub(SecurityTestData.SUBJECT))
                .thenReturn(Mono.just(SecurityTestData.user("COORDINATOR", UserStatus.ACTIVE)));
        client.method(org.springframework.http.HttpMethod.valueOf(method))
                .uri("/api/v1/teams/" + SecurityTestData.team().id() + suffix)
                .headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isForbidden();
    }

    /**
     * Spec HU-12 Parte E (V5-3).
     * Caso: administrador y coordinador leen la configuración de la organización con `GET`.
     * Esperado: ambos pueden leerla (el coordinador la necesita para la zona horaria de la fecha límite).
     * Resultado: HTTP 200 para cada rol.
     */
    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"ADMINISTRATOR", "COORDINATOR"})
    @org.junit.jupiter.api.DisplayName("V5-3 - Administrador y coordinador pueden leer la configuración de la organización")
    void allowsOrganizationSettingsReadForAdministratorsAndCoordinators(RoleCode role) {
        when(users.findByCognitoSub(SecurityTestData.SUBJECT))
                .thenReturn(Mono.just(SecurityTestData.user(role.name(), UserStatus.ACTIVE)));
        client.get().uri(ORGANIZATION_SETTINGS_PATH).headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isOk();
    }

    /**
     * Spec HU-12 Parte E (V5-3).
     * Caso: un colaborador lee, o un coordinador modifica, la configuración de la organización.
     * Esperado: el colaborador no puede leerla y modificarla sigue siendo solo del administrador.
     * Resultado: HTTP 403 en ambos casos y 401 para el GET sin token.
     */
    @Test
    @org.junit.jupiter.api.DisplayName("V5-3 - Colaborador no lee y coordinador no modifica la configuración")
    void deniesOrganizationSettingsReadForCollaboratorsAndWriteForCoordinators() {
        client.get().uri(ORGANIZATION_SETTINGS_PATH).headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isForbidden();
        client.get().uri(ORGANIZATION_SETTINGS_PATH).exchange().expectStatus().isUnauthorized();

        when(users.findByCognitoSub(SecurityTestData.SUBJECT))
                .thenReturn(Mono.just(SecurityTestData.user("COORDINATOR", UserStatus.ACTIVE)));
        client.put().uri(ORGANIZATION_SETTINGS_PATH).headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isForbidden();
    }

    private static final String CREATE_TASK_PATH =
            co.com.compira.api.task.TaskRoute.API_V1 + co.com.compira.api.task.TaskRoute.TASKS_BASE;

    /**
     * HU Crear tarea - E8.
     * Caso: un usuario activo con rol de coordinador invoca el endpoint de crear tarea.
     * Esperado: la solicitud pasa la autorización y llega al handler.
     * Resultado: HTTP 204 (respuesta del handler de prueba).
     */
    @Test
    @org.junit.jupiter.api.DisplayName("E8 - Coordinador activo puede acceder al endpoint de crear tarea")
    void allowsCoordinatorToCreateTasks() {
        when(users.findByCognitoSub(SecurityTestData.SUBJECT))
                .thenReturn(Mono.just(SecurityTestData.user(RoleCode.COORDINATOR.name(), UserStatus.ACTIVE)));
        client.post().uri(CREATE_TASK_PATH).headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isNoContent();
    }

    /**
     * HU Crear tarea - E8.
     * Caso: un usuario activo sin rol de coordinador (administrador o colaborador) invoca el endpoint de crear tarea.
     * Esperado: se deniega el acceso y la tarea no se crea.
     * Resultado: HTTP 403 (Forbidden) para cada rol.
     */
    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"ADMINISTRATOR", "COLLABORATOR"})
    @org.junit.jupiter.api.DisplayName("E8 - Usuario sin rol de coordinador recibe 403 al crear tarea")
    void deniesTaskCreationForNonCoordinators(RoleCode role) {
        when(users.findByCognitoSub(SecurityTestData.SUBJECT))
                .thenReturn(Mono.just(SecurityTestData.user(role.name(), UserStatus.ACTIVE)));
        client.post().uri(CREATE_TASK_PATH).headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isForbidden();
    }

    /**
     * HU Crear tarea - E8.
     * Caso: una solicitud sin token intenta crear una tarea.
     * Esperado: se rechaza por falta de autenticación.
     * Resultado: HTTP 401 (Unauthorized).
     */
    @Test
    @org.junit.jupiter.api.DisplayName("E8 - Solicitud sin token recibe 401 al crear tarea")
    void deniesAnonymousTaskCreation() {
        client.post().uri(CREATE_TASK_PATH).exchange().expectStatus().isUnauthorized();
    }

    @Test
    void deniesCoordinatorTeamCreationAndCoordinatorReplacement() {
        when(users.findByCognitoSub(SecurityTestData.SUBJECT))
                .thenReturn(Mono.just(SecurityTestData.user("COORDINATOR", UserStatus.ACTIVE)));
        client.post().uri(TeamHandler.BASE).headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isForbidden();
        client.put().uri(TeamHandler.COORDINATOR, SecurityTestData.SUBJECT).headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void validatesIssuerClientAccessTokenUseAndExpiration() {
        var validator = new ResourceServerConfig().cognitoTokenValidator("https://issuer.example", "expected-client");
        var future = java.time.Instant.now().plusSeconds(300);
        org.junit.jupiter.api.Assertions.assertFalse(validator.validate(SecurityTestData.claims("https://issuer.example", "expected-client", "access", future)).hasErrors());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(SecurityTestData.claims("https://other.example", "expected-client", "access", future)).hasErrors());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(SecurityTestData.claims("https://issuer.example", "other-client", "access", future)).hasErrors());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(SecurityTestData.claims("https://issuer.example", "expected-client", "id", future)).hasErrors());
        org.junit.jupiter.api.Assertions.assertTrue(validator.validate(SecurityTestData.claims("https://issuer.example", "expected-client", "access", future.minusSeconds(1000))).hasErrors());
    }

    @Configuration
    @EnableWebFlux
    @org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
    static class TestConfig {
        @Bean
        ApplicationUserRepositoryGateway users() { return mock(ApplicationUserRepositoryGateway.class); }
        @Bean
        ReactiveJwtDecoder decoder() {
            return token -> {
                if ("expired-token".equals(token)) {
                    var validation = new ResourceServerConfig().cognitoTokenValidator("https://issuer.example", "expected-client")
                            .validate(SecurityTestData.expiredToken());
                    return validation.hasErrors()
                            ? Mono.error(new org.springframework.security.oauth2.jwt.JwtValidationException("Expired token", validation.getErrors()))
                            : Mono.just(SecurityTestData.expiredToken());
                }
                return "valid-token".equals(token) ? Mono.just(SecurityTestData.token()) : Mono.error(new BadJwtException("Invalid token"));
            };
        }
        @Bean
        SecurityWebFilterChain security(ServerHttpSecurity http, ReactiveJwtDecoder decoder, ApplicationUserRepositoryGateway users) {
            return new ResourceServerConfig().securityWebFilterChain(http, decoder, users);
        }
        @Bean
        RouterFunction<ServerResponse> routes() {
            return RouterFunctions.route().GET("/api/v1/notifications", request -> request.principal()
                            .flatMap(principal -> ServerResponse.ok().bodyValue(principal.getName())))
                    .POST(TeamHandler.MEMBERS, request -> ServerResponse.noContent().build())
                    .GET(TeamHandler.MEMBERS, request -> ServerResponse.ok().build())
                    .GET("/api/v1/organization/settings", request -> ServerResponse.ok().build())
                    .POST(co.com.compira.api.task.TaskRoute.API_V1 + co.com.compira.api.task.TaskRoute.TASKS_BASE,
                            request -> ServerResponse.noContent().build())
                    .POST("/api/v1/auth/register", request -> ServerResponse.ok().build())
                    .DELETE("/api/v1/auth/users", request -> ServerResponse.ok().build())
                    .PUT("/api/v1/organization/settings", request -> ServerResponse.ok().build()).build();
        }
    }
}
