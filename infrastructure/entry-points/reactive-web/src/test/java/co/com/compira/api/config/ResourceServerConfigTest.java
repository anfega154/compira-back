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
        client.post().uri(TeamHandler.TASKS, SecurityTestData.SUBJECT).headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isNoContent();
    }

    @Test
    void deniesTeamLinkingForCollaboratorsAndAnonymousRequests() {
        client.post().uri(TeamHandler.MEMBERS, SecurityTestData.SUBJECT).headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isForbidden();
        client.post().uri(TeamHandler.TASKS, SecurityTestData.SUBJECT).headers(headers -> headers.setBearerAuth("valid-token"))
                .exchange().expectStatus().isForbidden();
        client.post().uri(TeamHandler.MEMBERS, SecurityTestData.SUBJECT).exchange().expectStatus().isUnauthorized();
        client.post().uri(TeamHandler.TASKS, SecurityTestData.SUBJECT).exchange().expectStatus().isUnauthorized();
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
                    .POST(TeamHandler.TASKS, request -> ServerResponse.noContent().build())
                    .POST("/api/v1/auth/register", request -> ServerResponse.ok().build())
                    .DELETE("/api/v1/auth/users", request -> ServerResponse.ok().build())
                    .PUT("/api/v1/organization/settings", request -> ServerResponse.ok().build()).build();
        }
    }
}
