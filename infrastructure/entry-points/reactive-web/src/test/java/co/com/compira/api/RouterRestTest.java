package co.com.compira.api;

import co.com.compira.api.auth.AuthenticationHandler;
import co.com.compira.api.router.AuthRouterRest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.web.reactive.function.server.ServerResponse;

class RouterRestTest {
    private final AuthenticationHandler authenticationHandler = mock(AuthenticationHandler.class);
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToRouterFunction(new AuthRouterRest().routerFunction(authenticationHandler)).build();
    }

    @Test
    void shouldRouteRegisterRequestForApiV1() {
        when(authenticationHandler.registerUser(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue("{\"status\":\"ok-v1\"}"));

        webTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ok-v1");
    }

    @Test
    void shouldRouteRegisterRequestForApiV2() {
        when(authenticationHandler.registerUser(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue("{\"status\":\"ok-v2\"}"));

        webTestClient.post()
                .uri("/api/v2/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ok-v2");
    }

    @Test
    void shouldReturnNotFoundForRemovedCompaniesRoute() {

        webTestClient.get()
                .uri("/api/v1/companies")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }
}
